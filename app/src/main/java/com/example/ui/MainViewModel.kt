package com.example.ui

import android.app.Application
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AppSettings
import com.example.data.ConfigBackupManager
import com.example.data.ExitIpChecker
import com.example.data.InstalledAppsRepository
import com.example.data.ProfileRepository
import com.example.data.SettingsRepository
import com.example.data.SubscriptionFetchResult
import com.example.data.SubscriptionManager
import com.example.data.UriConfigParser
import com.example.model.AppLanguage
import com.example.model.AppThemeMode
import com.example.model.AppTrafficInfo
import com.example.model.BatteryMode
import com.example.model.ConnectionState
import com.example.model.ExitNetworkInfo
import com.example.model.InstalledAppInfo
import com.example.model.TrafficStatsSnapshot
import com.example.model.TunnelMode
import com.example.model.VpnProfile
import com.example.model.VpnProtocol
import com.example.model.VpnSubscription
import com.example.vpn.AppAnalytics
import com.example.vpn.CrashReporter
import com.example.vpn.NetShieldVpnService
import com.example.vpn.RemoteUpdateManager
import com.example.vpn.RemoteUpdateState
import com.example.vpn.TunnelTelemetryBus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BenchmarkUiState(
    val isTestingAll: Boolean = false,
    val completedCount: Int = 0,
    val totalCount: Int = 0
)

sealed class BackupImportStatus {
    data class Success(val count: Int) : BackupImportStatus()
    data object WrongPassword : BackupImportStatus()
    data object InvalidFormat : BackupImportStatus()
}

sealed class ClipboardPasteOutcome {
    data class ImportedProfiles(val count: Int, val protocolBadges: String) : ClipboardPasteOutcome()
    data class EncryptedBackupDetected(val rawText: String) : ClipboardPasteOutcome()
    data class SubscriptionUrlDetected(val url: String) : ClipboardPasteOutcome()
    data object EmptyOrInvalid : ClipboardPasteOutcome()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val profileRepo = ProfileRepository(db.profileDao(), db.subscriptionDao())
    private val settingsRepo = SettingsRepository(application)
    private val appsRepo = InstalledAppsRepository(application)

    val profiles: StateFlow<List<VpnProfile>> = profileRepo.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subscriptions: StateFlow<List<VpnSubscription>> =
        (profileRepo.subscriptions ?: flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val connectionState: StateFlow<ConnectionState> = TunnelTelemetryBus.connectionState
    val activeTunnelProfile: StateFlow<VpnProfile?> = TunnelTelemetryBus.activeProfile
    val trafficStats: StateFlow<TrafficStatsSnapshot> = TunnelTelemetryBus.trafficStats
    val appTraffic: StateFlow<List<AppTrafficInfo>> = TunnelTelemetryBus.appTraffic
    val exitInfo: StateFlow<ExitNetworkInfo> = TunnelTelemetryBus.exitInfo
    val lastError: StateFlow<String?> = TunnelTelemetryBus.lastError
    val logs: StateFlow<List<String>> = TunnelTelemetryBus.logs

    val remoteUpdateState: StateFlow<RemoteUpdateState> = RemoteUpdateManager.updateState
    val fcmToken: StateFlow<String?> = AppAnalytics.fcmToken
    val installationId: StateFlow<String?> = AppAnalytics.installationId

    val selectedProfile: StateFlow<VpnProfile?> = combine(profiles, settings) { list, prefs ->
        list.firstOrNull { it.id == prefs.activeProfileId } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _appsLoading = MutableStateFlow(false)
    val appsLoading: StateFlow<Boolean> = _appsLoading.asStateFlow()

    private val _pingingIds = MutableStateFlow<Set<Long>>(emptySet())
    val pingingIds: StateFlow<Set<Long>> = _pingingIds.asStateFlow()

    private val _updatingSubIds = MutableStateFlow<Set<Long>>(emptySet())
    val updatingSubIds: StateFlow<Set<Long>> = _updatingSubIds.asStateFlow()

    private val _benchmarkState = MutableStateFlow(BenchmarkUiState())
    val benchmarkState: StateFlow<BenchmarkUiState> = _benchmarkState.asStateFlow()

    private var benchmarkAllJob: Job? = null

    init {
        val app = getApplication<Application>()
        AppAnalytics.init(app, analyticsEnabled = true)
        viewModelScope.launch {
            RemoteUpdateManager.refresh(app, forceFetch = false)
        }
    }

    fun loadInstalledAppsIfNeeded() {
        if (_installedApps.value.isNotEmpty() || _appsLoading.value) return
        viewModelScope.launch {
            _appsLoading.value = true
            _installedApps.value = appsRepo.loadLaunchableApps()
            _appsLoading.value = false
        }
    }

    fun getAppIcon(packageName: String): Drawable? = appsRepo.loadAppIcon(packageName)

    fun getAppLabel(packageName: String): String = appsRepo.resolveAppLabel(packageName)

    fun selectProfile(profileId: Long) {
        viewModelScope.launch {
            settingsRepo.setActiveProfileId(profileId)
        }
    }

    fun saveProfile(profile: VpnProfile) {
        viewModelScope.launch {
            val savedId = profileRepo.save(profile)
            if (settings.value.activeProfileId <= 0L) {
                settingsRepo.setActiveProfileId(savedId)
            }
        }
    }

    fun deleteProfile(profileId: Long) {
        viewModelScope.launch {
            profileRepo.delete(profileId)
        }
    }

    fun importUris(rawText: String, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val parsed = UriConfigParser.parseBatch(rawText)
            var firstId = -1L
            for (profile in parsed) {
                val id = profileRepo.save(profile)
                if (firstId < 0) firstId = id
            }
            if (firstId > 0 && settings.value.activeProfileId <= 0L) {
                settingsRepo.setActiveProfileId(firstId)
            }
            if (parsed.isNotEmpty()) {
                AppAnalytics.logConfigImported("uri", parsed.size)
            }
            onResult(parsed.size)
        }
    }

    fun importFromClipboardText(
        rawText: String,
        onResult: (ClipboardPasteOutcome) -> Unit
    ) {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            onResult(ClipboardPasteOutcome.EmptyOrInvalid)
            return
        }
        if (ConfigBackupManager.isEncryptedBackup(trimmed)) {
            onResult(ClipboardPasteOutcome.EncryptedBackupDetected(trimmed))
            return
        }
        if (UriConfigParser.looksLikeSubscriptionUrl(trimmed)) {
            onResult(ClipboardPasteOutcome.SubscriptionUrlDetected(trimmed))
            return
        }
        viewModelScope.launch {
            val parsed = UriConfigParser.parseBatch(trimmed)
            if (parsed.isEmpty()) {
                onResult(ClipboardPasteOutcome.EmptyOrInvalid)
                return@launch
            }
            var firstId = -1L
            for (profile in parsed) {
                val id = profileRepo.save(profile)
                if (firstId < 0L) firstId = id
            }
            if (firstId > 0L) {
                settingsRepo.setActiveProfileId(firstId)
            }
            AppAnalytics.logConfigImported("clipboard", parsed.size)
            val summary = parsed.map { it.protocol.shortBadge }.distinct().joinToString(", ")
            onResult(ClipboardPasteOutcome.ImportedProfiles(parsed.size, summary))
        }
    }

    fun addOrUpdateSubscription(
        existingId: Long = 0L,
        name: String,
        url: String,
        onDone: (Boolean, String, Int) -> Unit
    ) {
        viewModelScope.launch {
            val cleanUrl = url.trim()
            val fallbackName = name.trim().ifBlank {
                runCatching { Uri.parse(cleanUrl).host.orEmpty() }.getOrDefault("").ifBlank { "Subscription" }
            }
            val initialSub = if (existingId > 0L) {
                subscriptions.value.firstOrNull { it.id == existingId }?.copy(
                    name = fallbackName,
                    url = cleanUrl,
                    lastError = null
                ) ?: VpnSubscription(id = existingId, name = fallbackName, url = cleanUrl)
            } else {
                VpnSubscription(name = fallbackName, url = cleanUrl)
            }
            val subId = profileRepo.saveSubscription(initialSub)
            syncSubscriptionInternal(
                sub = initialSub.copy(id = subId),
                preferHeaderName = name.trim().isEmpty(),
                onDone = onDone
            )
        }
    }

    fun refreshSubscription(
        subscription: VpnSubscription,
        onDone: (Boolean, String, Int) -> Unit
    ) {
        viewModelScope.launch {
            syncSubscriptionInternal(subscription, preferHeaderName = false, onDone = onDone)
        }
    }

    fun refreshAllSubscriptions(onDone: (Int, Int) -> Unit) {
        val currentSubs = subscriptions.value
        if (currentSubs.isEmpty()) {
            onDone(0, 0)
            return
        }
        viewModelScope.launch {
            var successCount = 0
            for (sub in currentSubs) {
                var ok = false
                syncSubscriptionInternal(sub, preferHeaderName = false) { s, _, _ -> ok = s }
                if (ok) successCount++
            }
            onDone(successCount, currentSubs.size)
        }
    }

    fun deleteSubscription(subscriptionId: Long) {
        viewModelScope.launch {
            profileRepo.deleteSubscription(subscriptionId)
        }
    }

    private suspend fun syncSubscriptionInternal(
        sub: VpnSubscription,
        preferHeaderName: Boolean,
        onDone: (Boolean, String, Int) -> Unit
    ) {
        _updatingSubIds.value = _updatingSubIds.value + sub.id
        try {
            when (val res = SubscriptionManager.fetchSubscription(sub.url)) {
                is SubscriptionFetchResult.Failure -> {
                    profileRepo.saveSubscription(sub.copy(lastError = res.message))
                    onDone(false, res.message, 0)
                }
                is SubscriptionFetchResult.Success -> {
                    val activeWasInSub = profiles.value.any {
                        it.id == settings.value.activeProfileId && it.subscriptionId == sub.id
                    }
                    val insertedIds = profileRepo.replaceSubscriptionProfiles(sub.id, res.profiles)
                    if (insertedIds.isNotEmpty() && (settings.value.activeProfileId <= 0L || activeWasInSub)) {
                        settingsRepo.setActiveProfileId(insertedIds.first())
                    }
                    val resolvedName = if (preferHeaderName && !res.titleFromHeader.isNullOrBlank()) {
                        res.titleFromHeader
                    } else {
                        sub.name
                    }
                    profileRepo.saveSubscription(
                        sub.copy(
                            name = resolvedName,
                            lastUpdatedAt = System.currentTimeMillis(),
                            serverCount = insertedIds.size,
                            uploadBytes = res.userInfo.uploadBytes,
                            downloadBytes = res.userInfo.downloadBytes,
                            totalBytes = res.userInfo.totalBytes,
                            expireAtSeconds = res.userInfo.expireAtSeconds,
                            lastError = null
                        )
                    )
                    AppAnalytics.logSubscriptionSynced(insertedIds.size)
                    onDone(true, resolvedName, insertedIds.size)
                }
            }
        } finally {
            _updatingSubIds.value = _updatingSubIds.value - sub.id
        }
    }

    fun exportConfigs(passphrase: String): String {
        return ConfigBackupManager.exportProfiles(profiles.value, passphrase)
    }

    fun importConfigsOrUris(
        rawText: String,
        passphrase: String,
        onResult: (BackupImportStatus) -> Unit
    ) {
        viewModelScope.launch {
            if (ConfigBackupManager.isEncryptedBackup(rawText)) {
                when (val outcome = ConfigBackupManager.importBackup(rawText, passphrase)) {
                    is ConfigBackupManager.ImportOutcome.WrongPasswordOrCorrupted -> {
                        onResult(BackupImportStatus.WrongPassword)
                        return@launch
                    }
                    is ConfigBackupManager.ImportOutcome.InvalidFormat -> {
                        onResult(BackupImportStatus.InvalidFormat)
                        return@launch
                    }
                    is ConfigBackupManager.ImportOutcome.Success -> {
                        val imported = outcome.profiles
                        if (imported.isEmpty()) {
                            onResult(BackupImportStatus.InvalidFormat)
                            return@launch
                        }
                        val idMap = mutableMapOf<Long, Long>()
                        var firstId = -1L
                        val singleNodes = imported.filter { it.protocol != VpnProtocol.LOAD_BALANCE }
                        val lbNodes = imported.filter { it.protocol == VpnProtocol.LOAD_BALANCE }

                        for (profile in singleNodes) {
                            val oldId = profile.id
                            val newId = profileRepo.save(profile.copy(id = 0L))
                            if (oldId > 0L) idMap[oldId] = newId
                            if (firstId < 0L) firstId = newId
                        }
                        for (lb in lbNodes) {
                            val remappedMembers = lb.parsedMemberIds()
                                .mapNotNull { oldMemberId -> idMap[oldMemberId] }
                                .joinToString(",")
                            val newId = profileRepo.save(
                                lb.copy(id = 0L, memberProfileIds = remappedMembers)
                            )
                            if (firstId < 0L) firstId = newId
                        }
                        if (firstId > 0L && settings.value.activeProfileId <= 0L) {
                            settingsRepo.setActiveProfileId(firstId)
                        }
                        val importedCount = singleNodes.size + lbNodes.size
                        AppAnalytics.logConfigImported("backup", importedCount)
                        onResult(BackupImportStatus.Success(importedCount))
                        return@launch
                    }
                }
            } else {
                val parsed = UriConfigParser.parseBatch(rawText)
                if (parsed.isEmpty()) {
                    onResult(BackupImportStatus.InvalidFormat)
                    return@launch
                }
                var firstId = -1L
                for (profile in parsed) {
                    val id = profileRepo.save(profile)
                    if (firstId < 0L) firstId = id
                }
                if (firstId > 0L && settings.value.activeProfileId <= 0L) {
                    settingsRepo.setActiveProfileId(firstId)
                }
                AppAnalytics.logConfigImported("uri", parsed.size)
                onResult(BackupImportStatus.Success(parsed.size))
            }
        }
    }

    fun benchmarkProfile(profile: VpnProfile) {
        viewModelScope.launch {
            _pingingIds.value = _pingingIds.value + profile.id
            val all = profiles.value
            val result = ExitIpChecker.benchmarkProfile(profile, all)
            profileRepo.updateBenchmark(profile.id, result.latencyMs, result.downloadSpeedBps)
            _pingingIds.value = _pingingIds.value - profile.id
        }
    }

    fun testAllServers(
        targetProfiles: List<VpnProfile>,
        autoConnectFastest: Boolean,
        onAutoConnect: (VpnProfile) -> Unit
    ) {
        if (targetProfiles.isEmpty()) return
        benchmarkAllJob?.cancel()
        benchmarkAllJob = viewModelScope.launch {
            _benchmarkState.value = BenchmarkUiState(
                isTestingAll = true,
                completedCount = 0,
                totalCount = targetProfiles.size
            )
            var fastestProfile: VpnProfile? = null
            var fastestSpeed = -1L
            var lowestLatency = Int.MAX_VALUE

            try {
                val all = profiles.value
                targetProfiles.forEachIndexed { index, profile ->
                    _pingingIds.value = _pingingIds.value + profile.id
                    val result = ExitIpChecker.benchmarkProfile(profile, all)
                    profileRepo.updateBenchmark(profile.id, result.latencyMs, result.downloadSpeedBps)
                    _pingingIds.value = _pingingIds.value - profile.id

                    val speed = result.downloadSpeedBps ?: -1L
                    val lat = result.latencyMs ?: -1
                    if (speed > 0L) {
                        if (speed > fastestSpeed || (speed == fastestSpeed && lat in 1 until lowestLatency)) {
                            fastestSpeed = speed
                            lowestLatency = if (lat > 0) lat else Int.MAX_VALUE
                            fastestProfile = profile.copy(latencyMs = result.latencyMs, lastSpeedBps = speed)
                        }
                    }

                    _benchmarkState.value = BenchmarkUiState(
                        isTestingAll = true,
                        completedCount = index + 1,
                        totalCount = targetProfiles.size
                    )
                }

                if (autoConnectFastest && fastestProfile != null) {
                    val winner = fastestProfile!!
                    settingsRepo.setActiveProfileId(winner.id)
                    onAutoConnect(winner)
                }
            } finally {
                _pingingIds.value = emptySet()
                _benchmarkState.value = BenchmarkUiState()
            }
        }
    }

    fun stopBenchmarking() {
        benchmarkAllJob?.cancel()
        benchmarkAllJob = null
        _pingingIds.value = emptySet()
        _benchmarkState.value = BenchmarkUiState()
    }

    fun refreshExitIp() {
        viewModelScope.launch {
            TunnelTelemetryBus.setExitInfo(
                TunnelTelemetryBus.exitInfo.value.copy(isLoading = true, errorMessage = null)
            )
            val profile = activeTunnelProfile.value ?: selectedProfile.value
            val info = ExitIpChecker.fetchExitInfo(
                activeProfile = if (connectionState.value == ConnectionState.CONNECTED) profile else null,
                allProfiles = profiles.value
            )
            TunnelTelemetryBus.setExitInfo(info)
            if (profile != null && info.countryCode.isNotBlank()) {
                profileRepo.updateCountryCode(profile.id, info.countryCode)
            }
        }
    }

    fun startVpn(profile: VpnProfile) {
        viewModelScope.launch {
            settingsRepo.setActiveProfileId(profile.id)
            val currentSettings = settings.value
            AppAnalytics.logVpnConnectAttempt(
                protocol = profile.protocol.name,
                tunnelMode = currentSettings.tunnelMode.name,
                batteryMode = currentSettings.batteryMode.name
            )
            val ctx = getApplication<Application>()
            val intent = Intent(ctx, NetShieldVpnService::class.java).apply {
                action = NetShieldVpnService.ACTION_CONNECT
                putExtra(NetShieldVpnService.EXTRA_PROFILE_ID, profile.id)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        }
    }

    fun stopVpn() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, NetShieldVpnService::class.java).apply {
            action = NetShieldVpnService.ACTION_DISCONNECT
        }
        ctx.startService(intent)
    }

    fun setKillSwitchEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setKillSwitchEnabled(enabled) }
    }

    fun setAutoReconnectEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setAutoReconnectEnabled(enabled) }
    }

    fun setSystemAlwaysOnShortcut(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setSystemAlwaysOnShortcut(enabled) }
    }

    fun setTunnelMode(mode: TunnelMode) {
        viewModelScope.launch { settingsRepo.setTunnelMode(mode) }
    }

    fun toggleAppPackage(packageName: String) {
        viewModelScope.launch { settingsRepo.togglePackage(packageName) }
    }

    fun setSelectedPackages(packages: Set<String>) {
        viewModelScope.launch { settingsRepo.setSelectedPackages(packages) }
    }

    fun setBatteryMode(mode: BatteryMode) {
        viewModelScope.launch { settingsRepo.setBatteryMode(mode) }
    }

    fun setDnsServers(primary: String, secondary: String) {
        viewModelScope.launch { settingsRepo.setDnsServers(primary, secondary) }
    }

    fun setMtu(mtu: Int) {
        viewModelScope.launch { settingsRepo.setMtu(mtu) }
    }

    fun setAutoCheckExitIp(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setAutoCheckExitIp(enabled) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { settingsRepo.setLanguage(language) }
    }

    fun setThemeMode(themeMode: AppThemeMode) {
        viewModelScope.launch { settingsRepo.setThemeMode(themeMode) }
    }

    fun setCrashlyticsEnabled(enabled: Boolean) {
        CrashReporter.setCollectionEnabled(getApplication(), enabled)
        viewModelScope.launch { settingsRepo.setCrashlyticsEnabled(enabled) }
    }

    fun checkRemoteUpdateNow(onResult: (RemoteUpdateState) -> Unit = {}) {
        viewModelScope.launch {
            val state = RemoteUpdateManager.refresh(getApplication(), forceFetch = true)
            onResult(state)
        }
    }

    fun dismissOptionalUpdate() {
        RemoteUpdateManager.dismissOptionalUpdate()
    }

    fun testFirebaseAnalytics(onResult: (Boolean) -> Unit) {
        val ok = AppAnalytics.sendDiagnosticTestEvent(getApplication())
        onResult(ok)
    }

    fun logScreen(screenName: String) {
        AppAnalytics.logScreen(screenName)
    }

    fun clearLogs() = TunnelTelemetryBus.clearLogs()

    fun dismissError() = TunnelTelemetryBus.setError(null)
}
