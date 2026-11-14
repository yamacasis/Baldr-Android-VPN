package com.example.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import com.example.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.inappmessaging.inAppMessaging
import com.google.firebase.installations.installations
import com.google.firebase.messaging.messaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppAnalytics {

    @Volatile
    private var analytics: FirebaseAnalytics? = null

    private val _fcmToken = MutableStateFlow<String?>(null)
    val fcmToken: StateFlow<String?> = _fcmToken.asStateFlow()

    private val _installationId = MutableStateFlow<String?>(null)
    val installationId: StateFlow<String?> = _installationId.asStateFlow()

    fun init(context: Context, analyticsEnabled: Boolean = true) {
        runCatching {
            val appCtx = context.applicationContext
            ensureFcmNotificationChannel(appCtx)
            if (FirebaseApp.getApps(appCtx).isEmpty()) {
                FirebaseApp.initializeApp(appCtx)
            }
            if (FirebaseApp.getApps(appCtx).isNotEmpty()) {
                val fa = Firebase.analytics
                fa.setAnalyticsCollectionEnabled(analyticsEnabled)
                analytics = fa

                runCatching {
                    Firebase.inAppMessaging.isAutomaticDataCollectionEnabled = analyticsEnabled
                    Firebase.inAppMessaging.setMessagesSuppressed(false)
                    Firebase.inAppMessaging.triggerEvent("app_ready")
                }

                runCatching {
                    Firebase.installations.id.addOnSuccessListener { id ->
                        if (!id.isNullOrBlank()) _installationId.value = id
                    }
                }

                runCatching {
                    Firebase.messaging.isAutoInitEnabled = true
                    Firebase.messaging.subscribeToTopic("all")
                    Firebase.messaging.token.addOnSuccessListener { token ->
                        if (!token.isNullOrBlank()) _fcmToken.value = token
                    }
                }
            }
        }
    }

    private fun ensureFcmNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (nm.getNotificationChannel(AppFirebaseMessagingService.FCM_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    AppFirebaseMessagingService.FCM_CHANNEL_ID,
                    "Announcements & Updates",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Firebase push notifications and app update alerts"
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    fun updateFcmToken(token: String) {
        if (token.isNotBlank()) {
            _fcmToken.value = token
        }
    }

    fun logScreen(screenName: String) {
        runCatching {
            val bundle = Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, "MainActivity")
            }
            analytics?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
            Firebase.inAppMessaging.triggerEvent("screen_$screenName")
        }
    }

    fun logVpnConnectAttempt(protocol: String, tunnelMode: String, batteryMode: String) {
        runCatching {
            val bundle = Bundle().apply {
                putString("protocol", protocol)
                putString("tunnel_mode", tunnelMode)
                putString("battery_mode", batteryMode)
            }
            analytics?.logEvent("vpn_connect_attempt", bundle)
        }
    }

    fun logVpnConnected(protocol: String) {
        runCatching {
            val bundle = Bundle().apply {
                putString("protocol", protocol)
            }
            analytics?.logEvent("vpn_connected", bundle)
            Firebase.inAppMessaging.triggerEvent("vpn_connected")
        }
    }

    fun logSubscriptionSynced(serverCount: Int) {
        runCatching {
            val bundle = Bundle().apply {
                putLong("server_count", serverCount.toLong())
            }
            analytics?.logEvent("subscription_synced", bundle)
        }
    }

    fun logConfigImported(source: String, count: Int) {
        runCatching {
            val bundle = Bundle().apply {
                putString("source", source)
                putLong("server_count", count.toLong())
            }
            analytics?.logEvent("config_imported", bundle)
        }
    }

    fun logUpdatePrompted(forced: Boolean, apkUrl: String) {
        runCatching {
            val bundle = Bundle().apply {
                putBoolean("forced", forced)
                putString("apk_host", apkUrl.substringAfter("://").substringBefore("/"))
            }
            analytics?.logEvent("remote_update_prompted", bundle)
        }
    }

    fun logUpdateClicked(forced: Boolean) {
        runCatching {
            val bundle = Bundle().apply {
                putBoolean("forced", forced)
            }
            analytics?.logEvent("remote_update_clicked", bundle)
        }
    }

    fun sendDiagnosticTestEvent(context: Context): Boolean {
        init(context, analyticsEnabled = true)
        return runCatching {
            val fa = analytics ?: Firebase.analytics.also { analytics = it }
            val bundle = Bundle().apply {
                putString("app_version", BuildConfig.VERSION_NAME)
                putLong("version_code", BuildConfig.VERSION_CODE.toLong())
                putLong("timestamp_ms", System.currentTimeMillis())
            }
            fa.logEvent("analytics_diagnostic_test", bundle)
            runCatching { Firebase.inAppMessaging.triggerEvent("analytics_diagnostic_test") }
            true
        }.getOrDefault(false)
    }
}
