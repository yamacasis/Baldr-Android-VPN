package com.example.vpn

import android.content.Context
import com.example.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

data class RemoteUpdateState(
    val forced: Boolean = false,
    val apkUrl: String = "",
    val latestVersionCode: Long = 0L,
    val updateMessage: String = "",
    val hasUpdate: Boolean = false,
    val checkedAtMillis: Long = 0L
)

object RemoteUpdateManager {

    const val KEY_FORCED_UPDATE = "forced_update"
    const val KEY_APK_URL = "apk_url"
    const val KEY_LATEST_VERSION_CODE = "latest_version_code"
    const val KEY_UPDATE_MESSAGE = "update_message"

    private val _updateState = MutableStateFlow(RemoteUpdateState())
    val updateState: StateFlow<RemoteUpdateState> = _updateState.asStateFlow()

    suspend fun refresh(context: Context, forceFetch: Boolean = false): RemoteUpdateState {
        return runCatching {
            val appCtx = context.applicationContext
            if (FirebaseApp.getApps(appCtx).isEmpty()) {
                FirebaseApp.initializeApp(appCtx)
            }
            val rc = Firebase.remoteConfig
            val minFetchInterval = when {
                forceFetch -> 0L
                BuildConfig.DEBUG -> 0L
                else -> 300L
            }
            rc.setConfigSettingsAsync(
                remoteConfigSettings {
                    minimumFetchIntervalInSeconds = minFetchInterval
                    fetchTimeoutInSeconds = 12L
                }
            ).await()

            rc.setDefaultsAsync(
                mapOf(
                    KEY_FORCED_UPDATE to false,
                    KEY_APK_URL to "",
                    KEY_LATEST_VERSION_CODE to 0L,
                    KEY_UPDATE_MESSAGE to ""
                )
            ).await()

            rc.fetchAndActivate().await()

            val forcedFlag = parseBooleanFlag(
                rawBool = rc.getBoolean(KEY_FORCED_UPDATE),
                rawString = rc.getString(KEY_FORCED_UPDATE)
            )
            val apkUrl = rc.getString(KEY_APK_URL).trim()
            val latestCode = rc.getLong(KEY_LATEST_VERSION_CODE)
            val message = rc.getString(KEY_UPDATE_MESSAGE).trim()

            val evaluated = evaluateUpdateState(
                forcedUpdate = forcedFlag,
                apkUrl = apkUrl,
                latestVersionCode = latestCode,
                currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
                updateMessage = message,
                checkedAtMillis = System.currentTimeMillis()
            )
            _updateState.value = evaluated
            if (evaluated.hasUpdate) {
                AppAnalytics.logUpdatePrompted(evaluated.forced, evaluated.apkUrl)
            }
            evaluated
        }.getOrElse {
            _updateState.value
        }
    }

    fun dismissOptionalUpdate() {
        val current = _updateState.value
        if (!current.forced && current.hasUpdate) {
            _updateState.value = current.copy(hasUpdate = false)
        }
    }

    internal fun parseBooleanFlag(rawBool: Boolean, rawString: String): Boolean {
        if (rawBool) return true
        val norm = rawString.trim().lowercase()
        return norm == "true" || norm == "1" || norm == "yes"
    }

    internal fun evaluateUpdateState(
        forcedUpdate: Boolean,
        apkUrl: String,
        latestVersionCode: Long,
        currentVersionCode: Long,
        updateMessage: String = "",
        checkedAtMillis: Long = System.currentTimeMillis()
    ): RemoteUpdateState {
        val trimmedUrl = apkUrl.trim()
        val isValidUrl = trimmedUrl.startsWith("https://", ignoreCase = true) ||
            trimmedUrl.startsWith("http://", ignoreCase = true)
        val versionNeedsUpdate = latestVersionCode <= 0L || latestVersionCode > currentVersionCode
        val hasUpdate = isValidUrl && versionNeedsUpdate
        return RemoteUpdateState(
            forced = hasUpdate && forcedUpdate,
            apkUrl = trimmedUrl,
            latestVersionCode = latestVersionCode,
            updateMessage = updateMessage,
            hasUpdate = hasUpdate,
            checkedAtMillis = checkedAtMillis
        )
    }
}
