package com.example.vpn

import android.content.Context
import com.example.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.crashlytics

object CrashReporter {
    @Volatile
    private var initialized = false

    fun init(context: Context, enabled: Boolean) {
        runCatching {
            val appCtx = context.applicationContext
            if (FirebaseApp.getApps(appCtx).isEmpty()) {
                FirebaseApp.initializeApp(appCtx)
            }
            if (FirebaseApp.getApps(appCtx).isNotEmpty()) {
                val crashlytics = Firebase.crashlytics
                crashlytics.isCrashlyticsCollectionEnabled = enabled
                crashlytics.setCustomKey("app_version", BuildConfig.VERSION_NAME)
                crashlytics.setCustomKey("build_type", if (BuildConfig.DEBUG) "debug" else "release")
                initialized = true
            }
        }
    }

    fun setCollectionEnabled(context: Context, enabled: Boolean) {
        runCatching {
            val appCtx = context.applicationContext
            if (FirebaseApp.getApps(appCtx).isEmpty()) {
                FirebaseApp.initializeApp(appCtx)
            }
            if (FirebaseApp.getApps(appCtx).isNotEmpty()) {
                Firebase.crashlytics.isCrashlyticsCollectionEnabled = enabled
                initialized = true
            }
        }
    }

    fun logBreadcrumb(message: String) {
        if (!initialized) return
        runCatching {
            Firebase.crashlytics.log(message)
        }
    }

    fun setTunnelContext(
        protocol: String,
        tunnelMode: String,
        batteryMode: String,
        state: String
    ) {
        if (!initialized) return
        runCatching {
            val crashlytics = Firebase.crashlytics
            crashlytics.setCustomKey("vpn_protocol", protocol)
            crashlytics.setCustomKey("tunnel_mode", tunnelMode)
            crashlytics.setCustomKey("battery_mode", batteryMode)
            crashlytics.setCustomKey("vpn_state", state)
        }
    }

    fun recordNonFatal(tag: String, throwable: Throwable) {
        if (!initialized) return
        runCatching {
            val crashlytics = Firebase.crashlytics
            crashlytics.setCustomKey("component", tag)
            crashlytics.recordException(throwable)
        }
    }

    fun recordError(tag: String, message: String) {
        if (!initialized || message.isBlank()) return
        runCatching {
            val crashlytics = Firebase.crashlytics
            crashlytics.setCustomKey("component", tag)
            crashlytics.recordException(RuntimeException("[$tag] $message"))
        }
    }
}
