package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppLanguage
import com.example.model.AppThemeMode
import com.example.model.BatteryMode
import com.example.model.TunnelMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "netshield_settings")

data class AppSettings(
    val activeProfileId: Long = -1L,
    val tunnelMode: TunnelMode = TunnelMode.FULL_SYSTEM,
    val selectedPackages: Set<String> = emptySet(),
    val batteryMode: BatteryMode = BatteryMode.ULTRA_LOW_POWER,
    val dnsPrimary: String = "1.1.1.1",
    val dnsSecondary: String = "8.8.8.8",
    val mtu: Int = 1500,
    val autoCheckExitIp: Boolean = true,
    val killSwitchEnabled: Boolean = false,
    val autoReconnectEnabled: Boolean = false,
    val systemAlwaysOnShortcut: Boolean = false,
    val language: AppLanguage = AppLanguage.FARSI,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val crashlyticsEnabled: Boolean = true
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val ACTIVE_PROFILE_ID = longPreferencesKey("active_profile_id")
        val TUNNEL_MODE = stringPreferencesKey("tunnel_mode")
        val SELECTED_PACKAGES = stringSetPreferencesKey("selected_packages")
        val BATTERY_MODE = stringPreferencesKey("battery_mode")
        val DNS_PRIMARY = stringPreferencesKey("dns_primary")
        val DNS_SECONDARY = stringPreferencesKey("dns_secondary")
        val MTU = intPreferencesKey("mtu")
        val AUTO_CHECK_EXIT_IP = booleanPreferencesKey("auto_check_exit_ip")
        val KILL_SWITCH_ENABLED = booleanPreferencesKey("kill_switch_enabled")
        val AUTO_RECONNECT_ENABLED = booleanPreferencesKey("auto_reconnect_enabled")
        val SYSTEM_ALWAYS_ON_SHORTCUT = booleanPreferencesKey("system_always_on_shortcut")
        val LANGUAGE = stringPreferencesKey("language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CRASHLYTICS_ENABLED = booleanPreferencesKey("crashlytics_enabled")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            activeProfileId = prefs[Keys.ACTIVE_PROFILE_ID] ?: -1L,
            tunnelMode = prefs[Keys.TUNNEL_MODE]?.let { name ->
                TunnelMode.entries.firstOrNull { it.name == name }
            } ?: TunnelMode.FULL_SYSTEM,
            selectedPackages = prefs[Keys.SELECTED_PACKAGES] ?: emptySet(),
            batteryMode = prefs[Keys.BATTERY_MODE]?.let { name ->
                BatteryMode.entries.firstOrNull { it.name == name }
            } ?: BatteryMode.ULTRA_LOW_POWER,
            dnsPrimary = prefs[Keys.DNS_PRIMARY] ?: "1.1.1.1",
            dnsSecondary = prefs[Keys.DNS_SECONDARY] ?: "8.8.8.8",
            mtu = prefs[Keys.MTU] ?: 1500,
            autoCheckExitIp = prefs[Keys.AUTO_CHECK_EXIT_IP] ?: true,
            killSwitchEnabled = prefs[Keys.KILL_SWITCH_ENABLED] ?: false,
            autoReconnectEnabled = prefs[Keys.AUTO_RECONNECT_ENABLED] ?: false,
            systemAlwaysOnShortcut = prefs[Keys.SYSTEM_ALWAYS_ON_SHORTCUT] ?: false,
            language = prefs[Keys.LANGUAGE]?.let { name ->
                AppLanguage.entries.firstOrNull { it.name == name }
            } ?: AppLanguage.FARSI,
            themeMode = prefs[Keys.THEME_MODE]?.let { name ->
                AppThemeMode.entries.firstOrNull { it.name == name }
            } ?: AppThemeMode.DARK,
            crashlyticsEnabled = prefs[Keys.CRASHLYTICS_ENABLED] ?: true
        )
    }

    suspend fun setActiveProfileId(id: Long) {
        context.dataStore.edit { it[Keys.ACTIVE_PROFILE_ID] = id }
    }

    suspend fun setTunnelMode(mode: TunnelMode) {
        context.dataStore.edit { it[Keys.TUNNEL_MODE] = mode.name }
    }

    suspend fun setSelectedPackages(packages: Set<String>) {
        context.dataStore.edit { it[Keys.SELECTED_PACKAGES] = packages }
    }

    suspend fun togglePackage(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.SELECTED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
            if (!current.add(packageName)) {
                current.remove(packageName)
            }
            prefs[Keys.SELECTED_PACKAGES] = current
        }
    }

    suspend fun setBatteryMode(mode: BatteryMode) {
        context.dataStore.edit { it[Keys.BATTERY_MODE] = mode.name }
    }

    suspend fun setDnsServers(primary: String, secondary: String) {
        context.dataStore.edit {
            it[Keys.DNS_PRIMARY] = primary.trim().ifEmpty { "1.1.1.1" }
            it[Keys.DNS_SECONDARY] = secondary.trim().ifEmpty { "8.8.8.8" }
        }
    }

    suspend fun setMtu(mtu: Int) {
        context.dataStore.edit { it[Keys.MTU] = mtu.coerceIn(1200, 1500) }
    }

    suspend fun setAutoCheckExitIp(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_CHECK_EXIT_IP] = enabled }
    }

    suspend fun setKillSwitchEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.KILL_SWITCH_ENABLED] = enabled }
    }

    suspend fun setAutoReconnectEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_RECONNECT_ENABLED] = enabled }
    }

    suspend fun setSystemAlwaysOnShortcut(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SYSTEM_ALWAYS_ON_SHORTCUT] = enabled }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun setThemeMode(themeMode: AppThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = themeMode.name }
    }

    suspend fun setCrashlyticsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CRASHLYTICS_ENABLED] = enabled }
    }
}
