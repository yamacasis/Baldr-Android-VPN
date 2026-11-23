package com.example.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.SettingsRepository
import com.example.model.ConnectionState
import com.example.model.VpnProfile
import com.example.widget.BaldrVpnWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object VpnShortcutController {

    private const val PREFS_NAME = "baldr_shortcut_prefs"
    private const val KEY_LAST_PROFILE_ID = "last_profile_id"
    private const val KEY_LAST_PROFILE_NAME = "last_profile_name"
    private const val KEY_LAST_PROTOCOL_BADGE = "last_protocol_badge"

    const val EXTRA_AUTO_CONNECT_LAST = "extra_auto_connect_last"

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun cacheLastServer(context: Context, profile: VpnProfile?) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (profile == null) {
            prefs.edit().clear().apply()
            return
        }
        prefs.edit()
            .putLong(KEY_LAST_PROFILE_ID, profile.id)
            .putString(KEY_LAST_PROFILE_NAME, profile.name)
            .putString(KEY_LAST_PROTOCOL_BADGE, profile.protocol.shortBadge)
            .apply()
    }

    fun getCachedLastServerId(context: Context): Long {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_PROFILE_ID, -1L)
    }

    fun getCachedLastServerName(context: Context): String? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_PROFILE_NAME, null)?.takeIf { it.isNotBlank() }
    }

    fun getCachedProtocolBadge(context: Context): String? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_PROTOCOL_BADGE, null)?.takeIf { it.isNotBlank() }
    }

    suspend fun resolveLastOrFirstProfile(context: Context, explicitProfileId: Long = -1L): VpnProfile? {
        val appCtx = context.applicationContext
        val db = AppDatabase.getInstance(appCtx)
        val dao = db.profileDao()
        val settingsRepo = SettingsRepository(appCtx)
        val settings = runCatching { settingsRepo.settingsFlow.first() }.getOrNull()

        val candidateId = when {
            explicitProfileId > 0L -> explicitProfileId
            (settings?.activeProfileId ?: -1L) > 0L -> settings!!.activeProfileId
            getCachedLastServerId(appCtx) > 0L -> getCachedLastServerId(appCtx)
            else -> -1L
        }

        val resolved = (if (candidateId > 0L) dao.getById(candidateId) else null)
            ?: dao.getAllOnce().firstOrNull()

        cacheLastServer(appCtx, resolved)
        if (resolved != null && settings?.activeProfileId != resolved.id) {
            runCatching { settingsRepo.setActiveProfileId(resolved.id) }
        }
        return resolved
    }

    fun refreshCachedServerAsync(context: Context) {
        val appCtx = context.applicationContext
        backgroundScope.launch {
            runCatching {
                resolveLastOrFirstProfile(appCtx)
                syncWidgetAndTile(appCtx)
            }
        }
    }

    fun isTunnelActiveOrBusy(state: ConnectionState): Boolean =
        state == ConnectionState.CONNECTED ||
            state == ConnectionState.CONNECTING ||
            state == ConnectionState.RECONNECTING ||
            state == ConnectionState.FIREWALL_LOCKED

    fun buildConnectServiceIntent(context: Context, profileId: Long = -1L): Intent {
        return Intent(context, NetShieldVpnService::class.java).apply {
            action = NetShieldVpnService.ACTION_CONNECT
            if (profileId > 0L) {
                putExtra(NetShieldVpnService.EXTRA_PROFILE_ID, profileId)
            }
        }
    }

    fun buildDisconnectServiceIntent(context: Context): Intent {
        return Intent(context, NetShieldVpnService::class.java).apply {
            action = NetShieldVpnService.ACTION_DISCONNECT
        }
    }

    fun buildAutoConnectActivityIntent(context: Context): Intent {
        return Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_AUTO_CONNECT_LAST, true)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }
    }

    /**
     * Toggles VPN connection for the last/selected server from external surfaces (Widget / QS Tile).
     * Disconnecting or connecting when VPN permission is already granted starts NetShieldVpnService
     * without flashing an Activity. If VPN permission hasn't been granted yet, falls back to launching MainActivity.
     */
    fun toggleFromExternalTrigger(
        context: Context,
        onNeedActivityLaunch: (Intent) -> Unit
    ) {
        val appCtx = context.applicationContext
        val currentState = TunnelTelemetryBus.connectionState.value
        if (isTunnelActiveOrBusy(currentState)) {
            runCatching { appCtx.startService(buildDisconnectServiceIntent(appCtx)) }
            syncWidgetAndTile(appCtx)
            return
        }

        val prepareIntent = runCatching { VpnService.prepare(appCtx) }.getOrNull()
        if (prepareIntent != null) {
            onNeedActivityLaunch(buildAutoConnectActivityIntent(appCtx))
            return
        }

        val cachedId = getCachedLastServerId(appCtx)
        if (cachedId > 0L) {
            ContextCompat.startForegroundService(appCtx, buildConnectServiceIntent(appCtx, cachedId))
            syncWidgetAndTile(appCtx)
            return
        }

        // First-ever shortcut tap before cache is warmed: inspect Room quickly, connect or open app if empty
        backgroundScope.launch {
            val profile = resolveLastOrFirstProfile(appCtx)
            if (profile != null) {
                ContextCompat.startForegroundService(appCtx, buildConnectServiceIntent(appCtx, profile.id))
                syncWidgetAndTile(appCtx)
            } else {
                onNeedActivityLaunch(buildAutoConnectActivityIntent(appCtx))
            }
        }
    }

    fun syncWidgetAndTile(context: Context) {
        val appCtx = context.applicationContext
        runCatching { BaldrVpnWidgetProvider.updateAll(appCtx) }
        runCatching { BaldrQuickTileService.requestSync(appCtx) }
    }
}
