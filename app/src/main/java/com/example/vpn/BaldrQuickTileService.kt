package com.example.vpn

import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.example.R
import com.example.model.ConnectionState

class BaldrQuickTileService : TileService() {

    override fun onTileAdded() {
        super.onTileAdded()
        VpnShortcutController.refreshCachedServerAsync(applicationContext)
        syncTileVisualState()
    }

    override fun onStartListening() {
        super.onStartListening()
        VpnShortcutController.refreshCachedServerAsync(applicationContext)
        syncTileVisualState()
    }

    override fun onClick() {
        super.onClick()
        VpnShortcutController.toggleFromExternalTrigger(applicationContext) { activityIntent ->
            val pendingIntent = PendingIntent.getActivity(
                this,
                4201,
                activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(activityIntent)
            }
        }
        syncTileVisualState()
    }

    internal fun syncTileVisualState() {
        val tile = qsTile ?: return
        val state = TunnelTelemetryBus.connectionState.value
        val activeName = TunnelTelemetryBus.activeProfile.value?.name
            ?: VpnShortcutController.getCachedLastServerName(applicationContext)

        val tileState = resolveTileState(state)
        tile.state = tileState
        tile.icon = Icon.createWithResource(this, R.drawable.ic_baldr_tile)
        tile.label = getString(R.string.tile_vpn_label)

        val statusDesc = when (state) {
            ConnectionState.CONNECTED -> activeName ?: getString(R.string.widget_status_connected)
            ConnectionState.CONNECTING -> getString(R.string.widget_status_connecting)
            ConnectionState.RECONNECTING -> getString(R.string.widget_status_reconnecting)
            ConnectionState.FIREWALL_LOCKED -> getString(R.string.widget_status_firewall)
            else -> activeName ?: getString(R.string.widget_status_disconnected)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = statusDesc
        }
        tile.contentDescription = "${tile.label}: $statusDesc"
        tile.updateTile()
    }

    companion object {
        internal fun resolveTileState(state: ConnectionState): Int {
            return if (VpnShortcutController.isTunnelActiveOrBusy(state)) {
                Tile.STATE_ACTIVE
            } else {
                Tile.STATE_INACTIVE
            }
        }

        fun requestSync(context: Context) {
            runCatching {
                requestListeningState(
                    context.applicationContext,
                    ComponentName(context.applicationContext, BaldrQuickTileService::class.java)
                )
            }
        }

        fun canRequestAddTile(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        fun requestAddTileToPanel(context: Context, onResult: (Int) -> Unit) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                onResult(-1)
                return
            }
            val sbm = context.getSystemService(StatusBarManager::class.java)
            if (sbm == null) {
                onResult(-1)
                return
            }
            sbm.requestAddTileService(
                ComponentName(context.applicationContext, BaldrQuickTileService::class.java),
                context.getString(R.string.tile_vpn_label),
                Icon.createWithResource(context.applicationContext, R.drawable.ic_baldr_tile),
                ContextCompat.getMainExecutor(context.applicationContext)
            ) { resultCode ->
                onResult(resultCode)
            }
        }
    }
}
