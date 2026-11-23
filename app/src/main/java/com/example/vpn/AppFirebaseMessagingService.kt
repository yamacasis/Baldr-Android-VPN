package com.example.vpn

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AppFirebaseMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        AppAnalytics.updateFcmToken(token)
        CrashReporter.logBreadcrumb("fcm_new_token")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // Handle Remote Config invalidation even for silent data-only messages (without body)
        if (shouldSyncRemoteConfig(message.data)) {
            RemoteUpdateManager.markForceFetchPending(applicationContext)
            scope.launch {
                RemoteUpdateManager.refresh(applicationContext, forceFetch = true)
            }
        }

        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.app_name)
        val body = message.notification?.body
            ?: message.data["body"]
            ?: message.data["message"]
            ?: return

        val actionUrl = resolveActionUrl(message.data)
        showNotification(title, body, actionUrl)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun showNotification(title: String, body: String, actionUrl: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            nm.getNotificationChannel(FCM_CHANNEL_ID) == null
        ) {
            val channel = NotificationChannel(
                FCM_CHANNEL_ID,
                "Announcements & Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Firebase push notifications and app update alerts"
            }
            nm.createNotificationChannel(channel)
        }

        val targetIntent = if (actionUrl != null) {
            Intent(Intent.ACTION_VIEW, Uri.parse(actionUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            targetIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, FCM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_baldr_tile)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify((System.currentTimeMillis() and 0x7FFFFFFFL).toInt(), notification)
    }

    companion object {
        const val FCM_CHANNEL_ID = "baldr_fcm_notifications"

        internal fun shouldSyncRemoteConfig(data: Map<String, String>): Boolean {
            return data["sync_remote_config"].equals("true", ignoreCase = true) ||
                !data["forced_update"].isNullOrBlank()
        }

        internal fun resolveActionUrl(data: Map<String, String>): String? {
            return data["apk_url"]?.trim()
                ?.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
                ?: data["url"]?.trim()
                    ?.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
        }
    }
}
