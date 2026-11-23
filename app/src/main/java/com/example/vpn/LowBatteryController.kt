package com.example.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.example.model.BatteryMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean

data class LowBatteryState(
    val batteryPercent: Int = -1,
    val isCharging: Boolean = false,
    val isFeatureEnabled: Boolean = true,
    val thresholdPercent: Int = LowBatteryController.DEFAULT_THRESHOLD_PERCENT,
    val pausedBackgroundOperations: Int = 0
) {
    /**
     * Automatically engages whenever Low-Battery Operation is enabled and device battery
     * drops strictly below the configured threshold (default 20%).
     */
    val isLowBatteryActive: Boolean
        get() = isFeatureEnabled && batteryPercent in 0 until thresholdPercent
}

data class LowBatteryPolicy(
    val isLowBatteryActive: Boolean,
    val pauseHighResourceBackgroundTraffic: Boolean,
    val maxConcurrentBackgroundStreams: Int,
    val maxPingConcurrency: Int,
    val pingPacingDelayMs: Long,
    val minSinglePingCooldownMs: Long,
    val allowAutoPingOnOpen: Boolean,
    val allowAutoExitIpCheck: Boolean,
    val allowBulkBenchmark: Boolean,
    val sshKeepAliveIntervalMs: Int,
    val telemetrySampleIntervalMs: Long,
    val notificationBatchTicks: Int,
    val activeIdleTimeoutMs: Long,
    val halfClosedIdleTimeoutMs: Long,
    val autoReconnectInitialBackoffMs: Long,
    val autoReconnectMaxBackoffMs: Long
)

/**
 * Zero-permission real-time Android battery monitor that drives Low-Battery Operation mode:
 * when battery drops below 20%, automatically pauses high-resource background VPN traffic
 * (screen-off background bursts, automatic Exit IP checks, bulk download benchmarks, heavy
 * per-app telemetry snapshots) and sharply reduces ping & keepalive frequency.
 */
object LowBatteryController {

    const val DEFAULT_THRESHOLD_PERCENT = 20

    private val _state = MutableStateFlow(LowBatteryState())
    val state: StateFlow<LowBatteryState> = _state.asStateFlow()

    private val monitoringStarted = AtomicBoolean(false)
    private val lastLoggedActive = AtomicBoolean(false)

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            onBatteryReading(level = level, scale = scale, status = status, plugged = plugged)
        }
    }

    fun startMonitoring(context: Context) {
        val appCtx = context.applicationContext
        if (monitoringStarted.compareAndSet(false, true)) {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val stickyIntent = runCatching {
                ContextCompat.registerReceiver(
                    appCtx,
                    batteryReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            }.getOrNull()

            if (stickyIntent != null) {
                val level = stickyIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = stickyIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = stickyIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val plugged = stickyIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                onBatteryReading(level = level, scale = scale, status = status, plugged = plugged)
            } else {
                readFallbackBatteryManager(appCtx)
            }
        } else if (_state.value.batteryPercent < 0) {
            readFallbackBatteryManager(appCtx)
        }
    }

    private fun readFallbackBatteryManager(context: Context) {
        val bm = runCatching {
            context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        }.getOrNull() ?: return
        val capacity = runCatching {
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }.getOrDefault(-1)
        if (capacity in 0..100) {
            val charging = runCatching { bm.isCharging }.getOrDefault(false)
            onBatteryReading(
                level = capacity,
                scale = 100,
                status = if (charging) BatteryManager.BATTERY_STATUS_CHARGING else BatteryManager.BATTERY_STATUS_DISCHARGING,
                plugged = if (charging) BatteryManager.BATTERY_PLUGGED_AC else 0
            )
        }
    }

    fun setFeatureEnabled(enabled: Boolean) {
        var transitionMessage: String? = null
        _state.update { prev ->
            val next = prev.copy(
                isFeatureEnabled = enabled,
                pausedBackgroundOperations = if (!enabled) 0 else prev.pausedBackgroundOperations
            )
            transitionMessage = detectTransitionLog(prev, next)
            next
        }
        transitionMessage?.let { TunnelTelemetryBus.appendLog(it) }
    }

    fun updatePausedBackgroundOperations(count: Int) {
        val sanitized = count.coerceAtLeast(0)
        _state.update { prev ->
            if (prev.pausedBackgroundOperations == sanitized) prev
            else prev.copy(pausedBackgroundOperations = sanitized)
        }
    }

    fun resetPausedBackgroundOperations() {
        updatePausedBackgroundOperations(0)
    }

    fun parseBatteryPercent(level: Int, scale: Int): Int {
        if (level < 0 || scale <= 0) return -1
        return ((level * 100f) / scale.toFloat()).toInt().coerceIn(0, 100)
    }

    internal fun onBatteryReading(level: Int, scale: Int, status: Int, plugged: Int) {
        val pct = parseBatteryPercent(level, scale)
        if (pct < 0) return
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL ||
            plugged != 0

        var transitionMessage: String? = null
        _state.update { prev ->
            val next = prev.copy(
                batteryPercent = pct,
                isCharging = charging,
                pausedBackgroundOperations = if (prev.isFeatureEnabled && pct < prev.thresholdPercent) {
                    prev.pausedBackgroundOperations
                } else {
                    0
                }
            )
            transitionMessage = detectTransitionLog(prev, next)
            next
        }
        transitionMessage?.let { TunnelTelemetryBus.appendLog(it) }
    }

    private fun detectTransitionLog(prev: LowBatteryState, next: LowBatteryState): String? {
        val wasActive = prev.isLowBatteryActive
        val isNowActive = next.isLowBatteryActive
        if (wasActive == isNowActive) return null
        lastLoggedActive.set(isNowActive)
        return if (isNowActive) {
            "Low-Battery Operation active (${next.batteryPercent}%): paused high-resource background VPN traffic & reduced ping frequency"
        } else if (next.batteryPercent >= 0) {
            "Low-Battery Operation ended (${next.batteryPercent}%): resumed normal VPN & ping operation"
        } else {
            "Low-Battery Operation disabled: resumed normal VPN & ping operation"
        }
    }

    fun resolvePolicy(
        state: LowBatteryState = _state.value,
        batteryMode: BatteryMode = BatteryMode.ULTRA_LOW_POWER
    ): LowBatteryPolicy {
        return if (state.isLowBatteryActive) {
            LowBatteryPolicy(
                isLowBatteryActive = true,
                pauseHighResourceBackgroundTraffic = true,
                maxConcurrentBackgroundStreams = 10,
                maxPingConcurrency = 1,
                pingPacingDelayMs = 1_500L,
                minSinglePingCooldownMs = 10_000L,
                allowAutoPingOnOpen = false,
                allowAutoExitIpCheck = false,
                allowBulkBenchmark = false,
                sshKeepAliveIntervalMs = 60_000,
                telemetrySampleIntervalMs = 5_000L,
                notificationBatchTicks = 3,
                activeIdleTimeoutMs = 45_000L,
                halfClosedIdleTimeoutMs = 15_000L,
                autoReconnectInitialBackoffMs = 15_000L,
                autoReconnectMaxBackoffMs = 60_000L
            )
        } else {
            LowBatteryPolicy(
                isLowBatteryActive = false,
                pauseHighResourceBackgroundTraffic = false,
                maxConcurrentBackgroundStreams = Int.MAX_VALUE,
                maxPingConcurrency = 6,
                pingPacingDelayMs = 0L,
                minSinglePingCooldownMs = 0L,
                allowAutoPingOnOpen = true,
                allowAutoExitIpCheck = true,
                allowBulkBenchmark = true,
                sshKeepAliveIntervalMs = 12_000,
                telemetrySampleIntervalMs = 1_000L,
                notificationBatchTicks = if (batteryMode == BatteryMode.ULTRA_LOW_POWER) 3 else 1,
                activeIdleTimeoutMs = 300_000L,
                halfClosedIdleTimeoutMs = 45_000L,
                autoReconnectInitialBackoffMs = 2_000L,
                autoReconnectMaxBackoffMs = 15_000L
            )
        }
    }

    internal fun resetForTesting() {
        _state.value = LowBatteryState()
        lastLoggedActive.set(false)
    }
}
