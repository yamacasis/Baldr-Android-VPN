package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.example.R
import com.example.model.ConnectionState
import com.example.vpn.TunnelTelemetryBus
import com.example.vpn.VpnShortcutController

class BaldrVpnWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        VpnShortcutController.refreshCachedServerAsync(context)
        for (appWidgetId in appWidgetIds) {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val views = buildRemoteViews(context, options)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        appWidgetManager.updateAppWidget(appWidgetId, buildRemoteViews(context, newOptions))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_VPN) {
            VpnShortcutController.toggleFromExternalTrigger(context) { activityIntent ->
                runCatching { context.startActivity(activityIntent) }
            }
            updateAll(context)
        }
    }

    companion object {
        const val ACTION_TOGGLE_VPN = "com.example.widget.ACTION_TOGGLE_VPN"

        fun updateAll(context: Context) {
            val appCtx = context.applicationContext
            val manager = AppWidgetManager.getInstance(appCtx) ?: return
            val component = ComponentName(appCtx, BaldrVpnWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            for (id in ids) {
                val options = manager.getAppWidgetOptions(id)
                manager.updateAppWidget(id, buildRemoteViews(appCtx, options))
            }
        }

        internal fun buildRemoteViews(context: Context, options: Bundle? = null): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_baldr_compact)
            val state = TunnelTelemetryBus.connectionState.value
            val activeProfile = TunnelTelemetryBus.activeProfile.value
            val serverName = activeProfile?.name
                ?: VpnShortcutController.getCachedLastServerName(context)
                ?: context.getString(R.string.widget_default_server_name)

            val minWidthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 140) ?: 140
            val isUltraCompactIconOnly = minWidthDp in 1 until 95
            views.setViewVisibility(
                R.id.widget_text_container,
                if (isUltraCompactIconOnly) View.GONE else View.VISIBLE
            )

            val (bgRes, ringRes, statusText, statusColor) = when (state) {
                ConnectionState.CONNECTED -> Quad(
                    R.drawable.widget_bg_connected,
                    R.drawable.widget_icon_ring_connected,
                    context.getString(R.string.widget_status_connected),
                    0xFF34D399.toInt()
                )
                ConnectionState.CONNECTING -> Quad(
                    R.drawable.widget_bg_connecting,
                    R.drawable.widget_icon_ring_connecting,
                    context.getString(R.string.widget_status_connecting),
                    0xFFFBBF24.toInt()
                )
                ConnectionState.RECONNECTING -> Quad(
                    R.drawable.widget_bg_connecting,
                    R.drawable.widget_icon_ring_connecting,
                    context.getString(R.string.widget_status_reconnecting),
                    0xFFFBBF24.toInt()
                )
                ConnectionState.FIREWALL_LOCKED -> Quad(
                    R.drawable.widget_bg_connecting,
                    R.drawable.widget_icon_ring_connecting,
                    context.getString(R.string.widget_status_firewall),
                    0xFFF87171.toInt()
                )
                else -> Quad(
                    R.drawable.widget_bg_disconnected,
                    R.drawable.widget_icon_ring_disconnected,
                    context.getString(R.string.widget_status_disconnected),
                    0xFF94A3B8.toInt()
                )
            }

            views.setInt(R.id.widget_root, "setBackgroundResource", bgRes)
            views.setInt(R.id.widget_icon_ring, "setBackgroundResource", ringRes)
            views.setTextViewText(R.id.widget_server_name, serverName)
            views.setTextViewText(R.id.widget_status_text, statusText)
            views.setTextColor(R.id.widget_status_text, statusColor)

            val toggleIntent = Intent(context, BaldrVpnWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_VPN
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                4102,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            return views
        }

        fun canRequestPin(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            val mgr = AppWidgetManager.getInstance(context) ?: return false
            return runCatching { mgr.isRequestPinAppWidgetSupported }.getOrDefault(false)
        }

        fun requestPinWidget(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            val mgr = AppWidgetManager.getInstance(context) ?: return false
            if (!runCatching { mgr.isRequestPinAppWidgetSupported }.getOrDefault(false)) return false
            val component = ComponentName(context, BaldrVpnWidgetProvider::class.java)
            return runCatching { mgr.requestPinAppWidget(component, null, null) }.getOrDefault(false)
        }
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
