package com.example.data

import com.example.model.VpnProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Background server ping latency tester that tracks per-server freshness, prioritizes untested
 * servers, runs bounded-concurrency TCP latency probes on [Dispatchers.IO], and emits live
 * per-server results so UI rows update immediately as each probe finishes.
 */
class ServerLatencyTester(
    private val staleAfterMs: Long = DEFAULT_STALE_AFTER_MS,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val measureFn: suspend (VpnProfile, List<VpnProfile>) -> Int? = { profile, all ->
        ExitIpChecker.measureLatency(profile, all)
    }
) {
    private val lastTestedAtByProfileId = ConcurrentHashMap<Long, Long>()

    /**
     * Selects profiles that need latency testing:
     * - If [forceAll] is true, returns all profiles (untested first).
     * - Otherwise returns untested profiles (`latencyMs == null`) first, followed by profiles
     *   whose last probe in this session is missing or older than [staleAfterMs].
     */
    fun selectDueProfiles(
        profiles: List<VpnProfile>,
        forceAll: Boolean = false
    ): List<VpnProfile> {
        if (profiles.isEmpty()) return emptyList()
        val now = clockMs()
        val candidates = if (forceAll) {
            profiles
        } else {
            profiles.filter { profile ->
                if (profile.latencyMs == null) {
                    true
                } else {
                    val lastAt = lastTestedAtByProfileId[profile.id]
                    lastAt == null || (now - lastAt >= staleAfterMs)
                }
            }
        }
        // Prioritize untested profiles so newly added servers display latency immediately
        return candidates.sortedBy { if (it.latencyMs == null) 0 else 1 }
    }

    /**
     * Checks whether a single manual ping should be skipped due to low-battery cooldown.
     */
    fun isWithinCooldown(profile: VpnProfile, cooldownMs: Long): Boolean {
        if (cooldownMs <= 0L || profile.latencyMs == null) return false
        val lastAt = lastTestedAtByProfileId[profile.id] ?: return false
        return clockMs() - lastAt < cooldownMs
    }

    fun recordTestedNow(profileId: Long) {
        lastTestedAtByProfileId[profileId] = clockMs()
    }

    suspend fun testSingle(
        profile: VpnProfile,
        allProfiles: List<VpnProfile>
    ): Int {
        recordTestedNow(profile.id)
        val measured = withContext(Dispatchers.IO) {
            measureFn(profile, allProfiles)
        }
        return measured ?: -1
    }

    /**
     * Concurrently tests [targets] in the background with bounded concurrency [maxConcurrency].
     * Calls [onStart] before each probe, [onResult] as soon as each probe finishes, and
     * [onProgress] after each completion. Returns a map of `profileId -> latencyMs` (`-1` on timeout).
     */
    suspend fun testBatch(
        targets: List<VpnProfile>,
        allProfiles: List<VpnProfile>,
        maxConcurrency: Int = DEFAULT_CONCURRENCY,
        pacingDelayMs: Long = 0L,
        onStart: (Long) -> Unit = {},
        onResult: suspend (profileId: Long, latencyMs: Int) -> Unit = { _, _ -> },
        onFinishOne: (Long) -> Unit = {},
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): Map<Long, Int> {
        if (targets.isEmpty()) return emptyMap()
        val total = targets.size
        val completed = AtomicInteger(0)
        val results = ConcurrentHashMap<Long, Int>(total)
        val semaphore = Semaphore(permits = maxConcurrency.coerceAtLeast(1))

        withContext(Dispatchers.IO) {
            coroutineScope {
                targets.mapIndexed { index, profile ->
                    async {
                        semaphore.withPermit {
                            if (pacingDelayMs > 0L && index > 0) {
                                delay(pacingDelayMs)
                            }
                            onStart(profile.id)
                            try {
                                recordTestedNow(profile.id)
                                val latency = measureFn(profile, allProfiles) ?: -1
                                results[profile.id] = latency
                                onResult(profile.id, latency)
                            } finally {
                                onFinishOne(profile.id)
                                onProgress(completed.incrementAndGet(), total)
                            }
                        }
                    }
                }.awaitAll()
            }
        }
        return results
    }

    companion object {
        const val DEFAULT_STALE_AFTER_MS = 120_000L // 2 minutes
        const val DEFAULT_CONCURRENCY = 6
    }
}
