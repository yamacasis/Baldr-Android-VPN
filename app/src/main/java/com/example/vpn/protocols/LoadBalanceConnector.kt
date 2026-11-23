package com.example.vpn.protocols

import com.example.model.LoadBalanceStrategy
import com.example.model.MultiTunnelStatus
import com.example.model.VpnProfile
import com.example.model.VpnProtocol
import com.example.vpn.TunnelTelemetryBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.io.InputStream
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * High-performance Multi-Mode connector supporting Dual-VPN Active + Warm Standby failover
 * as well as live EWMA latency-aware load balancing across multiple upstream profiles
 * (V2Ray, SSH, SOCKS4/5, HTTP Proxy).
 *
 * Key capabilities:
 * - Connects and keeps up to 2 VPNs warm simultaneously (Active Primary + Warm Standby)
 * - Automatic instant failover (<5ms switch, zero TUN teardown) on primary failure or severe lag
 * - Exponential circuit-breaker cooldown (10s -> 60s) with automatic background health recovery
 * - Live EWMA latency tracking and manual 1-tap Active <-> Standby switching
 */
class LoadBalanceConnector(
    private val profile: VpnProfile,
    private val members: List<VpnProfile>,
    private val protector: (Socket) -> Boolean,
    private val connectorFactory: (VpnProfile) -> UpstreamConnector = { member ->
        ProtocolConnectorFactory.create(member, emptyList(), protector)
    },
    private val statusPublisher: (MultiTunnelStatus) -> Unit = {
        TunnelTelemetryBus.updateMultiTunnelStatus(it)
    },
    private val logSink: (String) -> Unit = { TunnelTelemetryBus.appendLog(it) },
    private val enableBackgroundMonitor: Boolean = true
) : UpstreamConnector {

    private class MemberNode(
        val index: Int,
        val profile: VpnProfile,
        val connector: UpstreamConnector
    ) {
        @Volatile var healthy: Boolean = false
        @Volatile var warmed: Boolean = false
        @Volatile var ewmaLatencyMs: Double =
            profile.latencyMs?.takeIf { it > 0 }?.toDouble() ?: 0.0
        @Volatile var cooldownUntilMs: Long = 0L
        val consecutiveFailures = AtomicInteger(0)
        val activeStreams = AtomicInteger(0)

        fun isAvailable(nowMs: Long): Boolean =
            healthy && nowMs >= cooldownUntilMs && connector.isHealthy()

        fun displayLatencyMs(): Int? {
            val ewma = ewmaLatencyMs.roundToInt()
            if (ewma > 0) return ewma
            return profile.latencyMs?.takeIf { it > 0 }
        }
    }

    private val nodes: List<MemberNode> = members
        .filter { it.protocol != VpnProtocol.LOAD_BALANCE }
        .mapIndexed { idx, member ->
            MemberNode(
                index = idx,
                profile = member,
                connector = connectorFactory(member)
            )
        }

    private val rrCounter = AtomicInteger(0)
    private val switchCount = AtomicInteger(0)
    private val closed = AtomicBoolean(false)
    private val lowBatteryMode = AtomicBoolean(false)
    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    @Volatile private var activeIndex: Int = 0
    @Volatile private var standbyIndex: Int = -1

    override val supportsStatefulHealthCheck: Boolean = true

    override suspend fun connect() = withContext(Dispatchers.IO) {
        if (closed.get()) throw IOException("Multi-Mode connector is closed")
        if (nodes.isEmpty()) {
            throw IOException("Load-balance profile has no member servers")
        }

        // Concurrently connect & measure handshake RTT so top 2 VPNs start warm right away
        val results = nodes.map { node ->
            async {
                val startNs = System.nanoTime()
                val ok = withTimeoutOrNull(MEMBER_CONNECT_TIMEOUT_MS) {
                    runCatching { node.connector.connect() }.isSuccess
                } ?: false
                val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                if (ok) {
                    recordSuccess(node, elapsedMs)
                } else {
                    recordFailure(node, forceCooldown = true)
                }
                node to ok
            }
        }.awaitAll()

        val healthyRanked = results
            .filter { it.second }
            .map { it.first }
            .sortedBy { it.ewmaLatencyMs.takeIf { v -> v > 0.0 } ?: Double.MAX_VALUE }

        if (healthyRanked.isEmpty()) {
            throw IOException("All ${nodes.size} member servers failed to connect")
        }

        // Assign fastest connected VPN as Active Primary and 2nd fastest connected VPN as Warm Standby
        activeIndex = healthyRanked[0].index
        standbyIndex = if (healthyRanked.size >= 2) {
            healthyRanked[1].index
        } else {
            nodes.firstOrNull { it.index != activeIndex }?.index ?: -1
        }

        val activeNode = nodes[activeIndex]
        val standbyNode = standbyIndex.takeIf { it in nodes.indices }?.let { nodes[it] }
        logSink(
            buildString {
                append("Multi-Mode (${profile.loadBalanceStrategy.name}): ")
                append("Active=${activeNode.profile.name}")
                activeNode.displayLatencyMs()?.let { append(" (${it}ms)") }
                if (standbyNode != null) {
                    append(" • Standby=${standbyNode.profile.name}")
                    if (standbyNode.warmed && standbyNode.healthy) {
                        append(" [Ready")
                        standbyNode.displayLatencyMs()?.let { append(" ${it}ms") }
                        append("]")
                    } else {
                        append(" [Recovering]")
                    }
                }
            }
        )

        publishStatus()
        if (enableBackgroundMonitor) {
            startBackgroundMonitor()
        }
    }

    override suspend fun openTcpStream(dstHost: String, dstPort: Int): TunnelStream =
        withContext(Dispatchers.IO) {
            if (closed.get()) throw IOException("Multi-Mode connector is closed")
            maybeSwitchOnLatencyDegradation()

            val ordered = orderedCandidates()
            var lastError: Throwable? = null
            var failedPrimary = false

            for (node in ordered) {
                val startNs = System.nanoTime()
                try {
                    val raw = node.connector.openTcpStream(dstHost, dstPort)
                    val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                    recordSuccess(node, elapsedMs)

                    // If Active failed and a standby/backup VPN took over seamlessly, promote it
                    if (failedPrimary && node.index != activeIndex) {
                        promoteToActive(node, reason = "auto-failover")
                    } else if (node.index == activeIndex || node.index == standbyIndex) {
                        publishStatus()
                    }

                    node.activeStreams.incrementAndGet()
                    return@withContext TrackedTunnelStream(raw) {
                        node.activeStreams.updateAndGet { cur -> (cur - 1).coerceAtLeast(0) }
                    }
                } catch (e: Throwable) {
                    if (node.index == activeIndex) {
                        failedPrimary = true
                    }
                    recordFailure(
                        node = node,
                        forceCooldown = profile.loadBalanceStrategy == LoadBalanceStrategy.FAILOVER
                    )
                    lastError = e
                }
            }
            publishStatus()
            throw IOException(
                "All Multi-Mode servers failed for $dstHost:$dstPort (${lastError?.message})",
                lastError
            )
        }

    override suspend fun forwardDnsQuery(dnsQuery: ByteArray, dnsServer: String): ByteArray? =
        withContext(Dispatchers.IO) {
            if (closed.get()) return@withContext null
            val candidates = orderedCandidates()
            for (node in candidates) {
                val startNs = System.nanoTime()
                val resp = runCatching {
                    node.connector.forwardDnsQuery(dnsQuery, dnsServer)
                }.getOrNull()
                if (resp != null) {
                    val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                    recordSuccess(node, elapsedMs)
                    return@withContext resp
                } else {
                    recordFailure(node, forceCooldown = false)
                }
            }
            null
        }

    /**
     * Instantly swaps traffic from the current Active VPN to the Warm Standby VPN (or next best
     * healthy node) without tearing down the TUN interface.
     */
    suspend fun switchToStandbyNow(): Boolean = withContext(Dispatchers.IO) {
        if (closed.get() || nodes.size < 2) return@withContext false
        val now = System.currentTimeMillis()

        val preferredStandby = standbyIndex
            .takeIf { it in nodes.indices && it != activeIndex }
            ?.let { nodes[it] }

        val candidate = when {
            preferredStandby != null && preferredStandby.isAvailable(now) -> preferredStandby
            else -> {
                val otherHealthy = nodes
                    .filter { it.index != activeIndex && it.isAvailable(now) }
                    .minByOrNull { it.ewmaLatencyMs.takeIf { v -> v > 0.0 } ?: Double.MAX_VALUE }
                if (otherHealthy != null) {
                    otherHealthy
                } else if (preferredStandby != null) {
                    // Attempt to warm standby on demand
                    val startNs = System.nanoTime()
                    val ok = withTimeoutOrNull(MEMBER_PROBE_TIMEOUT_MS) {
                        runCatching { preferredStandby.connector.connect() }.isSuccess
                    } ?: false
                    if (ok) {
                        val elapsed = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                        recordSuccess(preferredStandby, elapsed)
                        preferredStandby
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        } ?: return@withContext false

        promoteToActive(candidate, reason = "manual switch")
        true
    }

    override suspend fun probeHealth(): Boolean = withContext(Dispatchers.IO) {
        if (closed.get() || nodes.isEmpty()) return@withContext false

        val active = nodes.getOrNull(activeIndex)
        val activeOk = if (active != null) {
            val startNs = System.nanoTime()
            val ok = withTimeoutOrNull(MEMBER_PROBE_TIMEOUT_MS) {
                runCatching { active.connector.probeHealth() }.getOrDefault(false)
            } ?: false
            if (ok) {
                val elapsed = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                recordSuccess(active, elapsed)
                true
            } else {
                recordFailure(active, forceCooldown = true)
                false
            }
        } else {
            false
        }

        if (activeOk) {
            publishStatus()
            return@withContext true
        }

        // Active failed — seamlessly promote warm standby or another healthy node!
        val fallbackOrder = buildList {
            standbyIndex.takeIf { it in nodes.indices && it != activeIndex }?.let { add(nodes[it]) }
            nodes.filter { it.index != activeIndex && it.index != standbyIndex }.forEach { add(it) }
        }
        for (candidate in fallbackOrder) {
            val startNs = System.nanoTime()
            val ok = withTimeoutOrNull(MEMBER_PROBE_TIMEOUT_MS) {
                val healthy = runCatching { candidate.connector.probeHealth() }.getOrDefault(false)
                if (healthy) true else runCatching { candidate.connector.connect() }.isSuccess
            } ?: false
            if (ok) {
                val elapsed = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                recordSuccess(candidate, elapsed)
                promoteToActive(candidate, reason = "health-watchdog failover")
                return@withContext true
            } else {
                recordFailure(candidate, forceCooldown = true)
            }
        }

        publishStatus()
        false
    }

    private fun startBackgroundMonitor() {
        monitorJob?.cancel()
        monitorJob = monitorScope.launch {
            while (isActive && !closed.get()) {
                val intervalMs = if (lowBatteryMode.get()) {
                    MONITOR_INTERVAL_LOW_BATTERY_MS
                } else {
                    MONITOR_INTERVAL_NORMAL_MS
                }
                delay(intervalMs)
                if (closed.get()) break

                val now = System.currentTimeMillis()
                // 1. Keep Warm Standby verified and measure live latency
                val currentStandby = standbyIndex.takeIf { it in nodes.indices }?.let { nodes[it] }
                if (currentStandby != null && currentStandby.index != activeIndex) {
                    val startNs = System.nanoTime()
                    val ok = withTimeoutOrNull(MEMBER_PROBE_TIMEOUT_MS) {
                        val alive = if (currentStandby.warmed && currentStandby.healthy) {
                            runCatching { currentStandby.connector.probeHealth() }.getOrDefault(false)
                        } else {
                            false
                        }
                        if (alive) true else runCatching { currentStandby.connector.connect() }.isSuccess
                    } ?: false
                    if (ok) {
                        val elapsed = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                        recordSuccess(currentStandby, elapsed)
                    } else {
                        recordFailure(currentStandby, forceCooldown = true)
                    }
                }

                // 2. Recover any cooled-down unhealthy member nodes automatically
                val toRecover = nodes.filter { node ->
                    node.index != activeIndex &&
                        node.index != standbyIndex &&
                        !node.healthy &&
                        now >= node.cooldownUntilMs
                }
                for (node in toRecover) {
                    val startNs = System.nanoTime()
                    val ok = withTimeoutOrNull(MEMBER_PROBE_TIMEOUT_MS) {
                        runCatching { node.connector.connect() }.isSuccess
                    } ?: false
                    if (ok) {
                        val elapsed = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                        recordSuccess(node, elapsed)
                        logSink("Multi-Mode: ${node.profile.name} recovered (${elapsed}ms)")
                    } else {
                        recordFailure(node, forceCooldown = true)
                    }
                }

                // 3. Re-elect best Standby if current standby is unavailable
                refreshStandbySelection(System.currentTimeMillis())
                publishStatus()
            }
        }
    }

    private fun maybeSwitchOnLatencyDegradation() {
        if (profile.loadBalanceStrategy != LoadBalanceStrategy.FAILOVER) return
        if (nodes.size < 2) return
        val now = System.currentTimeMillis()
        val active = nodes.getOrNull(activeIndex) ?: return
        val standby = nodes.getOrNull(standbyIndex) ?: return
        if (active.index == standby.index) return
        if (!standby.isAvailable(now) || !standby.warmed) return

        val activeLat = active.ewmaLatencyMs
        val standbyLat = standby.ewmaLatencyMs
        if (activeLat > LATENCY_DEGRADATION_FLOOR_MS &&
            standbyLat in 1.0..LATENCY_DEGRADATION_FLOOR_MS &&
            activeLat > standbyLat * LATENCY_DEGRADATION_RATIO
        ) {
            promoteToActive(
                newActive = standby,
                reason = "latency switch (${activeLat.roundToInt()}ms -> ${standbyLat.roundToInt()}ms)"
            )
        }
    }

    @Synchronized
    private fun promoteToActive(newActive: MemberNode, reason: String) {
        val oldActiveIdx = activeIndex
        if (oldActiveIdx == newActive.index) {
            publishStatus()
            return
        }
        activeIndex = newActive.index
        switchCount.incrementAndGet()

        val now = System.currentTimeMillis()
        val oldActive = nodes.getOrNull(oldActiveIdx)
        standbyIndex = if (oldActive != null && oldActive.isAvailable(now)) {
            oldActive.index
        } else {
            nodes
                .filter { it.index != activeIndex && it.isAvailable(now) }
                .minByOrNull { it.ewmaLatencyMs.takeIf { v -> v > 0.0 } ?: Double.MAX_VALUE }
                ?.index
                ?: oldActive?.index
                ?: -1
        }

        val standbyName = standbyIndex.takeIf { it in nodes.indices }?.let { nodes[it].profile.name } ?: "none"
        logSink("Dual-VPN ($reason) -> Active: ${newActive.profile.name} | Standby: $standbyName")
        publishStatus()
    }

    private fun refreshStandbySelection(nowMs: Long) {
        if (nodes.size < 2) return
        val currentStandby = nodes.getOrNull(standbyIndex)
        if (currentStandby != null && currentStandby.index != activeIndex && currentStandby.isAvailable(nowMs)) {
            return
        }
        val bestAlt = nodes
            .filter { it.index != activeIndex && it.isAvailable(nowMs) }
            .minByOrNull { it.ewmaLatencyMs.takeIf { v -> v > 0.0 } ?: Double.MAX_VALUE }
        if (bestAlt != null) {
            standbyIndex = bestAlt.index
        }
    }

    private fun orderedCandidates(): List<MemberNode> {
        if (nodes.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val available = nodes.filter { it.isAvailable(now) }
        val coolingOrDown = nodes.filter { !it.isAvailable(now) }
            .sortedBy { it.cooldownUntilMs }

        if (available.isEmpty()) {
            return coolingOrDown
        }

        val primaryOrder = when (profile.loadBalanceStrategy) {
            LoadBalanceStrategy.FAILOVER -> {
                val ordered = mutableListOf<MemberNode>()
                nodes.getOrNull(activeIndex)?.takeIf { it.isAvailable(now) }?.let { ordered.add(it) }
                nodes.getOrNull(standbyIndex)
                    ?.takeIf { it.isAvailable(now) && it.index != activeIndex }
                    ?.let { ordered.add(it) }
                available
                    .filter { n -> ordered.none { it.index == n.index } }
                    .sortedBy { it.ewmaLatencyMs.takeIf { v -> v > 0.0 } ?: Double.MAX_VALUE }
                    .forEach { ordered.add(it) }
                ordered
            }
            LoadBalanceStrategy.LEAST_LATENCY -> {
                val sorted = available.sortedBy { node ->
                    val lat = node.ewmaLatencyMs
                    if (lat > 0.0) lat else Double.MAX_VALUE
                }
                if (sorted.size >= 2) {
                    val bestLat = sorted[0].ewmaLatencyMs
                    val secondLat = sorted[1].ewmaLatencyMs
                    // Balance across top 2 when their latencies are within 1.5x of each other
                    if (bestLat > 0.0 && secondLat > 0.0 && secondLat <= bestLat * 1.5) {
                        val pick = (rrCounter.getAndIncrement() and 0x7FFFFFFF) % 2
                        if (pick == 1) {
                            listOf(sorted[1], sorted[0]) + sorted.drop(2)
                        } else {
                            sorted
                        }
                    } else {
                        sorted
                    }
                } else {
                    sorted
                }
            }
            LoadBalanceStrategy.ROUND_ROBIN -> {
                val start = (rrCounter.getAndIncrement() and 0x7FFFFFFF) % available.size
                List(available.size) { idx -> available[(start + idx) % available.size] }
            }
            LoadBalanceStrategy.RANDOM -> {
                val start = Random.nextInt(available.size)
                List(available.size) { idx -> available[(start + idx) % available.size] }
            }
        }
        return primaryOrder + coolingOrDown
    }

    private fun recordSuccess(node: MemberNode, latencyMs: Long) {
        node.healthy = true
        node.warmed = true
        node.consecutiveFailures.set(0)
        node.cooldownUntilMs = 0L
        val sample = latencyMs.coerceAtLeast(1L).toDouble()
        val prev = node.ewmaLatencyMs
        node.ewmaLatencyMs = if (prev <= 0.0) {
            sample
        } else {
            EWMA_ALPHA * sample + (1.0 - EWMA_ALPHA) * prev
        }
    }

    private fun recordFailure(node: MemberNode, forceCooldown: Boolean) {
        val fails = node.consecutiveFailures.incrementAndGet()
        if (forceCooldown || fails >= CIRCUIT_BREAKER_THRESHOLD) {
            node.healthy = false
            val shift = (fails - 1).coerceIn(0, 3)
            val cooldownMs = (INITIAL_COOLDOWN_MS shl shift).coerceAtMost(MAX_COOLDOWN_MS)
            node.cooldownUntilMs = System.currentTimeMillis() + cooldownMs
        }
    }

    fun currentStatusSnapshot(): MultiTunnelStatus {
        val now = System.currentTimeMillis()
        val active = nodes.getOrNull(activeIndex)
        val standby = standbyIndex.takeIf { it in nodes.indices && it != activeIndex }?.let { nodes[it] }
        val healthyCount = nodes.count { it.isAvailable(now) }
        return MultiTunnelStatus(
            isMultiMode = true,
            strategyName = profile.loadBalanceStrategy.name,
            activeNodeName = active?.profile?.name.orEmpty(),
            activeNodeBadge = active?.profile?.protocol?.shortBadge.orEmpty(),
            activeNodeLatencyMs = active?.displayLatencyMs(),
            standbyNodeName = standby?.profile?.name,
            standbyNodeBadge = standby?.profile?.protocol?.shortBadge,
            standbyNodeLatencyMs = standby?.displayLatencyMs(),
            standbyReady = standby != null && standby.warmed && standby.isAvailable(now),
            healthyCount = healthyCount,
            totalCount = nodes.size,
            switchCount = switchCount.get()
        )
    }

    private fun publishStatus() {
        statusPublisher(currentStatusSnapshot())
    }

    override fun isHealthy(): Boolean {
        if (closed.get()) return false
        val now = System.currentTimeMillis()
        return nodes.any { it.isAvailable(now) }
    }

    override fun setLowBatteryMode(enabled: Boolean) {
        lowBatteryMode.set(enabled)
        nodes.forEach { runCatching { it.connector.setLowBatteryMode(enabled) } }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        monitorJob?.cancel()
        monitorScope.cancel()
        nodes.forEach { runCatching { it.connector.close() } }
    }

    private class TrackedTunnelStream(
        private val delegate: TunnelStream,
        private val onClose: () -> Unit
    ) : TunnelStream {
        private val closedFlag = AtomicBoolean(false)

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            delegate.read(buffer, offset, length)

        override fun write(data: ByteArray, offset: Int, length: Int) =
            delegate.write(data, offset, length)

        override fun inputStream(): InputStream = delegate.inputStream()

        override fun close() {
            if (closedFlag.compareAndSet(false, true)) {
                runCatching { delegate.close() }
                onClose()
            }
        }
    }

    companion object {
        private const val MEMBER_CONNECT_TIMEOUT_MS = 8_000L
        private const val MEMBER_PROBE_TIMEOUT_MS = 4_500L
        private const val MONITOR_INTERVAL_NORMAL_MS = 20_000L
        private const val MONITOR_INTERVAL_LOW_BATTERY_MS = 60_000L
        private const val CIRCUIT_BREAKER_THRESHOLD = 2
        private const val INITIAL_COOLDOWN_MS = 10_000L
        private const val MAX_COOLDOWN_MS = 60_000L
        private const val EWMA_ALPHA = 0.30
        private const val LATENCY_DEGRADATION_FLOOR_MS = 450.0
        private const val LATENCY_DEGRADATION_RATIO = 3.0
    }
}
