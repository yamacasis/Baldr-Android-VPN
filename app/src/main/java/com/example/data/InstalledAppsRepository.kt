package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import com.example.model.InstalledAppInfo
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstalledAppsRepository(private val context: Context) {
    private val labelCache = ConcurrentHashMap<String, String>()
    private val iconCache = ConcurrentHashMap<String, Drawable>()

    suspend fun loadLaunchableApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }
        val selfPkg = context.packageName
        resolved
            .asSequence()
            .mapNotNull { info -> info.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != selfPkg }
            .map { appInfo ->
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                val label = pm.getApplicationLabel(appInfo).toString()
                labelCache[appInfo.packageName] = label
                InstalledAppInfo(
                    packageName = appInfo.packageName,
                    appName = label,
                    isSystemApp = isSystem
                )
            }
            .sortedBy { it.appName.lowercase() }
            .toList()
    }

    fun resolveAppLabel(packageName: String): String {
        if (packageName.isBlank() || packageName == "system.network") return ""
        labelCache[packageName]?.let { return it }
        val resolved = runCatching {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(info).toString()
        }.getOrElse { packageName }
        labelCache[packageName] = resolved
        return resolved
    }

    fun loadAppIcon(packageName: String): Drawable? {
        if (packageName.isBlank() || packageName == "system.network") return null
        iconCache[packageName]?.let { return it }
        return try {
            val icon = context.packageManager.getApplicationIcon(packageName)
            iconCache[packageName] = icon
            icon
        } catch (_: Exception) {
            null
        }
    }
}
