package com.example

import android.app.Application
import com.example.data.SettingsRepository
import com.example.vpn.AppAnalytics
import com.example.vpn.CrashReporter
import com.example.vpn.LowBatteryController
import com.example.vpn.VpnShortcutController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BaldrApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Initialize Firebase Crashlytics, Analytics, FIAM lifecycle hooks, FCM channel, and battery monitor
        CrashReporter.init(this, enabled = true)
        AppAnalytics.init(this, analyticsEnabled = true)
        LowBatteryController.startMonitoring(this)

        appScope.launch {
            val prefs = runCatching { SettingsRepository(applicationContext).settingsFlow.first() }.getOrNull()
            if (prefs != null) {
                if (!prefs.crashlyticsEnabled) {
                    CrashReporter.setCollectionEnabled(applicationContext, false)
                }
                LowBatteryController.setFeatureEnabled(prefs.lowBatteryAutoModeEnabled)
            }
            VpnShortcutController.resolveLastOrFirstProfile(applicationContext)
            VpnShortcutController.syncWidgetAndTile(applicationContext)
        }
    }
}
