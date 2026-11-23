package com.example

import android.os.BatteryManager
import com.example.data.AppSettings
import com.example.model.BatteryMode
import com.example.model.VpnProfile
import com.example.model.VpnProtocol
import com.example.vpn.LowBatteryController
import com.example.vpn.protocols.SshConnector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LowBatteryUnitTest {

    @Test
    fun lowBatteryController_activatesBelow20PercentAndReducesPingAndBackgroundPolicy() {
        LowBatteryController.resetForTesting()
        try {
            assertEquals(19, LowBatteryController.parseBatteryPercent(19, 100))
            assertEquals(15, LowBatteryController.parseBatteryPercent(3, 20))
            assertEquals(-1, LowBatteryController.parseBatteryPercent(-1, 100))
            assertEquals(-1, LowBatteryController.parseBatteryPercent(50, 0))

            // At 20%: remains in normal standby operation
            LowBatteryController.onBatteryReading(
                level = 20,
                scale = 100,
                status = BatteryManager.BATTERY_STATUS_DISCHARGING,
                plugged = 0
            )
            val at20 = LowBatteryController.state.value
            assertFalse(at20.isLowBatteryActive)
            val normalPolicy = LowBatteryController.resolvePolicy(at20, BatteryMode.BALANCED)
            assertFalse(normalPolicy.pauseHighResourceBackgroundTraffic)
            assertEquals(6, normalPolicy.maxPingConcurrency)
            assertEquals(0L, normalPolicy.pingPacingDelayMs)
            assertEquals(0L, normalPolicy.minSinglePingCooldownMs)
            assertTrue(normalPolicy.allowAutoPingOnOpen)
            assertTrue(normalPolicy.allowAutoExitIpCheck)
            assertTrue(normalPolicy.allowBulkBenchmark)
            assertEquals(12_000, normalPolicy.sshKeepAliveIntervalMs)
            assertEquals(1_000L, normalPolicy.telemetrySampleIntervalMs)
            assertEquals(1, normalPolicy.notificationBatchTicks)

            // Drops to 19%: automatically activates Low-Battery Operation
            LowBatteryController.onBatteryReading(
                level = 19,
                scale = 100,
                status = BatteryManager.BATTERY_STATUS_DISCHARGING,
                plugged = 0
            )
            LowBatteryController.updatePausedBackgroundOperations(4)
            val at19 = LowBatteryController.state.value
            assertTrue(at19.isLowBatteryActive)
            assertEquals(19, at19.batteryPercent)
            assertEquals(4, at19.pausedBackgroundOperations)

            val lowPolicy = LowBatteryController.resolvePolicy(at19, BatteryMode.BALANCED)
            assertTrue(lowPolicy.pauseHighResourceBackgroundTraffic)
            assertEquals(1, lowPolicy.maxPingConcurrency)
            assertEquals(1_500L, lowPolicy.pingPacingDelayMs)
            assertEquals(10_000L, lowPolicy.minSinglePingCooldownMs)
            assertFalse(lowPolicy.allowAutoPingOnOpen)
            assertFalse(lowPolicy.allowAutoExitIpCheck)
            assertFalse(lowPolicy.allowBulkBenchmark)
            assertEquals(60_000, lowPolicy.sshKeepAliveIntervalMs)
            assertEquals(5_000L, lowPolicy.telemetrySampleIntervalMs)
            assertEquals(3, lowPolicy.notificationBatchTicks)
            assertEquals(15_000L, lowPolicy.autoReconnectInitialBackoffMs)
            assertEquals(60_000L, lowPolicy.autoReconnectMaxBackoffMs)

            // Disabling toggle at 15% immediately deactivates & resets paused operations count
            LowBatteryController.onBatteryReading(
                level = 15,
                scale = 100,
                status = BatteryManager.BATTERY_STATUS_DISCHARGING,
                plugged = 0
            )
            LowBatteryController.setFeatureEnabled(false)
            val disabledState = LowBatteryController.state.value
            assertFalse(disabledState.isLowBatteryActive)
            assertEquals(0, disabledState.pausedBackgroundOperations)

            // Re-enable and recover to 25% charging -> returns to standby
            LowBatteryController.setFeatureEnabled(true)
            assertTrue(LowBatteryController.state.value.isLowBatteryActive)
            LowBatteryController.onBatteryReading(
                level = 25,
                scale = 100,
                status = BatteryManager.BATTERY_STATUS_CHARGING,
                plugged = BatteryManager.BATTERY_PLUGGED_USB
            )
            val recovered = LowBatteryController.state.value
            assertFalse(recovered.isLowBatteryActive)
            assertTrue(recovered.isCharging)
            assertEquals(0, recovered.pausedBackgroundOperations)
        } finally {
            LowBatteryController.resetForTesting()
        }
    }

    @Test
    fun sshConnector_reducesKeepAlivePingFrequencyInLowBatteryMode() {
        val profile = VpnProfile(
            name = "SSH-LowBattery",
            protocol = VpnProtocol.SSH,
            host = "127.0.0.1",
            port = 22,
            usernameOrUuid = "user",
            passwordOrSecret = "pass"
        )
        val connector = SshConnector(profile) { true }
        assertEquals(12_000, connector.effectiveKeepAliveIntervalMs())
        connector.setLowBatteryMode(true)
        assertEquals(60_000, connector.effectiveKeepAliveIntervalMs())
        connector.setLowBatteryMode(false)
        assertEquals(12_000, connector.effectiveKeepAliveIntervalMs())
        connector.close()
    }

    @Test
    fun appSettings_defaultsLowBatteryAutoModeEnabled() {
        val defaults = AppSettings()
        assertTrue(defaults.lowBatteryAutoModeEnabled)
    }
}
