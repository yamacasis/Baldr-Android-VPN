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
import com.example.data.CachedProviderEntry
import com.example.data.ConfigBackupManager
import com.example.data.DataUsageSummary
import com.example.data.DataUsageTracker
import com.example.data.ExitIpChecker
import com.example.data.FreeProviderFetchResult
import com.example.data.FreeProxyProvider
import com.example.data.FreeProxyProviderCache
import com.example.data.FreeProxyProviderManager
import com.example.data.InstalledAppsRepository
import com.example.data.ProfileRepository
import com.example.data.ServerLatencyTester
import com.example.data.SettingsRepository
import com.example.data.SubscriptionFetchResult
import com.example.data.SubscriptionManager
import com.example.data.UriConfigParser
import com.example.model.AppLanguage
import com.example.model.AppThemeMode
import com.example.model.AppTrafficInfo
import com.example.model.BatteryMode
import com.example.model.ConnectionState
import com.example.model.DnsPreset
import com.example.model.ExitNetworkInfo
import com.example.model.InstalledAppInfo
import com.example.model.MultiTunnelStatus
import com.example.model.TrafficStatsSnapshot
import com.example.model.TunnelMode
import com.example.model.VpnProfile
import com.example.model.VpnProtocol
import com.example.model.VpnSubscription
import com.example.vpn.AppAnalytics
import com.example.vpn.CrashReporter
import com.example.vpn.LowBatteryController
import com.example.vpn.LowBatteryState
import com.example.vpn.NetShieldVpnService
import com.example.vpn.RemoteUpdateManager
import com.example.vpn.RemoteUpdateState
import com.example.vpn.TunnelTelemetryBus
import com.example.vpn.VpnShortcutController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BenchmarkUiState(
    val isTestingAll: Boolean = false,
    val completedCount: Int = 0,
    val totalCount: Int = 0
)

data class PingAllUiState(
    val isPingingAll: Boolean = false,
    val completedCount: Int = 0,
    val totalCount: Int = 0
)

enum class FreeProviderStage {
    IDLE,
    FETCHING,
    PINGING,
    VERIFYING,
    CONNECTED,
    FAILED
}

data class FreeProviderResultSnapshot(
    val providerId: String,
    val bestServerLabel: String,
    val bestLatencyMs: Int?,
    val reachableCount: Int,
    val countryCode: String = "",
    val testedAtMillis: Long = System.currentTimeMillis()
)

data class FreeProvidersUiState(
    val activeProviderId: String? = null,
    val stage: FreeProviderStage = FreeProviderStage.IDLE,
    val usedOfflineFallback: Boolean = false,
    val pingedCount: Int = 0,
    val totalToPing: Int = 0,
    val reachableCount: Int = 0,
    val bestLatencyMs: Int? = null,
    val errorMessage: String? = null,
    val resultsByProviderId: Map<String, FreeProviderResultSnapshot> = emptyMap(),
    val cachedEntries: Map<String, CachedProviderEntry> = emptyMap(),
    val backgroundSyncProviderId: String? = null,
    val backgroundSyncProviderName: String? = null,
    val backgroundSyncCompleted: Int = 0,
    val backgroundSyncTotal: Int = 0
) {
    val isBusy: Boolean
        get() = stage == FreeProviderStage.FETCHING ||
            stage == FreeProviderStage.PINGING ||
            stage == FreeProviderStage.VERIFYING
}

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
    val multiTunnelStatus: StateFlow<MultiTunnelStatus> = TunnelTelemetryBus.multiTunnelStatus
    val exitInfo: StateFlow<ExitNetworkInfo> = TunnelTelemetryBus.exitInfo
    val lastError: StateFlow<String?> = TunnelTelemetryBus.lastError
    val logs: StateFlow<List<String>> = TunnelTelemetryBus.logs
    val blockedAdsCount: StateFlow<Int> = TunnelTelemetryBus.blockedAdsCount
    val bypassedConnectionsCount: StateFlow<Int> = TunnelTelemetryBus.bypassedConnectionsCount
    val dataUsageSummary: StateFlow<DataUsageSummary> = DataUsageTracker.summary

    val remoteUpdateState: StateFlow<RemoteUpdateState> = RemoteUpdateManager.updateState
    val fcmToken: StateFlow<String?> = AppAnalytics.fcmToken
    val installationId: StateFlow<String?> = AppAnalytics.installationId
    val lowBatteryState: StateFlow<LowBatteryState> = LowBatteryController.state

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

    private val _pingAllState = MutableStateFlow(PingAllUiState())
    val pingAllState: StateFlow<PingAllUiState> = _pingAllState.asStateFlow()

    private val _freeProvidersState = MutableStateFlow(FreeProvidersUiState())
    val freeProvidersState: StateFlow<FreeProvidersUiState> = _freeProvidersState.asStateFlow()

    private val _isSmartConnecting = MutableStateFlow(false)
    val isSmartConnecting: StateFlow<Boolean> = _isSmartConnecting.asStateFlow()

    private var benchmarkAllJob: Job? = null
    private var pingAllJob: Job? = null
    private var backgroundLatencyJob: Job? = null
    private var freeProviderJob: Job? = null
    private var backgroundFreeSyncJob: Job? = null
    private var smartConnectJob: Job? = null
    private val latencyTester = ServerLatencyTester()

    init {
        val app = getApplication<Application>()
        AppAnalytics.init(app, analyticsEnabled = true)
        LowBatteryController.startMonitoring(app)
        viewModelScope.launch {
            DataUsageTracker.load(app)
        }
        viewModelScope.launch {
            val initialCache = FreeProxyProviderCache.loadAll(app)
            _freeProvidersState.update { it.copy(cachedEntries = initialCache) }
        }
        viewModelScope.launch {
            settingsRepo.settingsFlow.collect { prefs ->
                LowBatteryController.setFeatureEnabled(prefs.lowBatteryAutoModeEnabled)
            }
        }
        viewModelScope.launch {
            LowBatteryController.state.collect { batteryState ->
                if (batteryState.isLowBatteryActive) {
                    if (backgroundLatencyJob?.isActive == true) {
                        backgroundLatencyJob?.cancel()
                        backgroundLatencyJob = null
                    }
                    if (benchmarkAllJob?.isActive == true) {
                        stopBenchmarking()
                        TunnelTelemetryBus.appendLog(
                            "Low-Battery Mode (${batteryState.batteryPercent}%): paused background server speed benchmark"
                        )
                    }
                    if (backgroundFreeSyncJob?.isActive == true) {
                        stopBackgroundFreeSync()
                        TunnelTelemetryBus.appendLog(
                            "Low-Battery Mode (${batteryState.batteryPercent}%): paused gradual free proxy list refresh"
                        )
                    }
                }
            }
        }
        // Automatically test newly added / untested servers in the background whenever the server list updates
        viewModelScope.launch {
            profiles.collect { currentList ->
                if (currentList.isEmpty()) return@collect
                val policy = LowBatteryController.resolvePolicy(lowBatteryState.value, settings.value.batteryMode)
                if (!policy.allowAutoPingOnOpen) return@collect
                if (pingAllJob?.isActive == true || benchmarkAllJob?.isActive == true) return@collect
                val untested = currentList.filter { it.latencyMs == null && !_pingingIds.value.contains(it.id) }
                if (untested.isNotEmpty() && backgroundLatencyJob?.isActive != true) {
                    launchBackgroundLatencySweep(targets = untested, allSnapshot = currentList)
                }
            }
        }
        viewModelScope.launch {
            TunnelTelemetryBus.connectionState.collect { connState ->
                if (connState == ConnectionState.CONNECTED) {
                    startGradualFreeProvidersRefreshIfNeeded()
                } else if (connState == ConnectionState.DISCONNECTED || connState == ConnectionState.ERROR) {
                    stopBackgroundFreeSync()
                }
            }
        }
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
            val chosen = profileRepo.getById(profileId)
            VpnShortcutController.cacheLastServer(getApplication(), chosen)
            VpnShortcutController.syncWidgetAndTile(getApplication())
        }
    }

    fun saveProfile(profile: VpnProfile) {
        viewModelScope.launch {
            val savedId = profileRepo.save(profile)
            val resolved = profileRepo.getById(savedId)
            if (settings.value.activeProfileId <= 0L || settings.value.activeProfileId == savedId) {
                settingsRepo.setActiveProfileId(savedId)
                VpnShortcutController.cacheLastServer(getApplication(), resolved)
                VpnShortcutController.syncWidgetAndTile(getApplication())
            }
        }
    }

    fun deleteProfile(profileId: Long) {
        viewModelScope.launch {
            profileRepo.delete(profileId)
            VpnShortcutController.refreshCachedServerAsync(getApplication())
        }
    }

    fun toggleFavorite(profileId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            profileRepo.setFavorite(profileId, isFavorite)
        }
    }

    /**
     * Identifies timed-out (`latencyMs <= 0`) and duplicate (`protocol:host:port`) profiles.
     * Keeps favorites and the lowest-latency entry among duplicates.
     */
    fun findDeadAndDuplicateStats(list: List<VpnProfile> = profiles.value): Triple<Int, Int, List<Long>> {
        val deadIds = mutableSetOf<Long>()
        val dupIds = mutableSetOf<Long>()
        val seenKeys = mutableMapOf<String, VpnProfile>()

        val prioritized = list.sortedWith(
            compareByDescending<VpnProfile> { it.isFavorite }
                .thenBy { val lat = it.latencyMs; if (lat != null && lat > 0) lat else Int.MAX_VALUE }
                .thenBy { it.id }
        )

        for (p in prioritized) {
            if (p.protocol != VpnProtocol.LOAD_BALANCE) {
                val key = "${p.protocol.name}|${p.host.trim().lowercase()}|${p.port}|${p.usernameOrUuid.trim()}"
                val existing = seenKeys[key]
                if (existing != null) {
                    dupIds.add(p.id)
                    continue
                } else {
                    seenKeys[key] = p
                }
            }
            val lat = p.latencyMs
            if (lat != null && lat <= 0) {
                deadIds.add(p.id)
            }
        }
        val allIds = (deadIds + dupIds).toList()
        return Triple(deadIds.size, dupIds.size, allIds)
    }

    fun cleanDeadAndDuplicateServers(onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val (_, _, idsToRemove) = findDeadAndDuplicateStats(profiles.value)
            if (idsToRemove.isNotEmpty()) {
                profileRepo.deleteByIds(idsToRemove)
                VpnShortcutController.refreshCachedServerAsync(getApplication())
            }
            onDone(idsToRemove.size)
        }
    }

    /**
     * One-tap Smart Auto-Connect: prioritizes favorites & lowest-latency servers, tests
     * untested/stale candidates in parallel, and connects to the fastest reachable server.
     */
    fun smartAutoConnect(
        onReadyToConnect: (VpnProfile) -> Unit,
        onNoServerFound: () -> Unit
    ) {
        if (_isSmartConnecting.value) return
        smartConnectJob?.cancel()
        smartConnectJob = viewModelScope.launch {
            _isSmartConnecting.value = true
            try {
                val all = profiles.value
                if (all.isEmpty()) {
                    onNoServerFound()
                    return@launch
                }
                // Check already known fast reachable servers first (prefer favorites with low ping)
                val rankedCandidates = all.sortedWith(
                    compareByDescending<VpnProfile> { it.isFavorite }
                        .thenBy { val lat = it.latencyMs; if (lat != null && lat > 0) lat else Int.MAX_VALUE }
                )
                val testPool = rankedCandidates.take(12)
                var winner: VpnProfile? = null
                var winnerPing = Int.MAX_VALUE

                val results = latencyTester.testBatch(
                    targets = testPool,
                    allProfiles = all,
                    maxConcurrency = 4,
                    onResult = { id, ms -> profileRepo.updateLatency(id, ms) }
                )

                for (candidate in testPool) {
                    val ms = results[candidate.id] ?: continue
                    if (ms > 0) {
                        val effectiveScore = if (candidate.isFavorite) (ms * 0.85).toInt() else ms
                        val winnerScore = if (winner?.isFavorite == true) (winnerPing * 0.85).toInt() else winnerPing
                        if (winner == null || effectiveScore < winnerScore) {
                            winner = candidate.copy(latencyMs = ms)
                            winnerPing = ms
                        }
                    }
                }

                val chosen = winner ?: rankedCandidates.firstOrNull { (it.latencyMs ?: -1) > 0 }
                if (chosen != null) {
                    settingsRepo.setActiveProfileId(chosen.id)
                    VpnShortcutController.cacheLastServer(getApplication(), chosen)
                    VpnShortcutController.syncWidgetAndTile(getApplication())
                    TunnelTelemetryBus.appendLog(
                        "[FAILOVER] Smart Auto-Connect selected ${chosen.name} (${chosen.latencyMs ?: -1} ms)"
                    )
                    onReadyToConnect(chosen)
                } else {
                    onNoServerFound()
                }
            } finally {
                _isSmartConnecting.value = false
            }
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
                VpnShortcutController.cacheLastServer(getApplication(), parsed.first().copy(id = firstId))
                VpnShortcutController.syncWidgetAndTile(getApplication())
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
                        val firstId = insertedIds.first()
                        settingsRepo.setActiveProfileId(firstId)
                        VpnShortcutController.cacheLastServer(getApplication(), profileRepo.getById(firstId))
                        VpnShortcutController.syncWidgetAndTile(getApplication())
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

    fun pingProfile(profile: VpnProfile) {
        val policy = LowBatteryController.resolvePolicy(lowBatteryState.value, settings.value.batteryMode)
        if (latencyTester.isWithinCooldown(profile, policy.minSinglePingCooldownMs)) {
            return
        }
        viewModelScope.launch {
            _pingingIds.update { it + profile.id }
            try {
                val ms = latencyTester.testSingle(profile, profiles.value)
                profileRepo.updateLatency(profile.id, ms)
            } finally {
                _pingingIds.update { it - profile.id }
            }
        }
    }

    /**
     * Silently refreshes any untested or stale servers in the background when the user views
     * the server list, without interrupting an active manual Ping All or Speed Benchmark.
     */
    fun refreshStaleLatenciesInBackground(targetProfiles: List<VpnProfile>) {
        if (targetProfiles.isEmpty()) return
        val policy = LowBatteryController.resolvePolicy(lowBatteryState.value, settings.value.batteryMode)
        if (!policy.allowAutoPingOnOpen) return
        if (pingAllJob?.isActive == true || benchmarkAllJob?.isActive == true || backgroundLatencyJob?.isActive == true) {
            return
        }
        val due = latencyTester.selectDueProfiles(targetProfiles, forceAll = false)
        if (due.isEmpty()) return
        launchBackgroundLatencySweep(targets = due, allSnapshot = profiles.value)
    }

    private fun launchBackgroundLatencySweep(
        targets: List<VpnProfile>,
        allSnapshot: List<VpnProfile>
    ) {
        backgroundLatencyJob?.cancel()
        backgroundLatencyJob = viewModelScope.launch {
            val policy = LowBatteryController.resolvePolicy(lowBatteryState.value, settings.value.batteryMode)
            val targetIdSet = targets.map { it.id }.toSet()
            try {
                latencyTester.testBatch(
                    targets = targets,
                    allProfiles = allSnapshot,
                    maxConcurrency = policy.maxPingConcurrency.coerceAtLeast(1),
                    pacingDelayMs = policy.pingPacingDelayMs,
                    onStart = { id -> _pingingIds.update { it + id } },
                    onResult = { id, ms -> profileRepo.updateLatency(id, ms) },
                    onFinishOne = { id -> _pingingIds.update { it - id } }
                )
            } finally {
                _pingingIds.update { it - targetIdSet }
            }
        }
    }

    fun pingAllServers(targetProfiles: List<VpnProfile>) {
        if (targetProfiles.isEmpty()) return
        backgroundLatencyJob?.cancel()
        backgroundLatencyJob = null
        pingAllJob?.cancel()
        pingAllJob = viewModelScope.launch {
            val orderedTargets = latencyTester.selectDueProfiles(targetProfiles, forceAll = true)
            val total = orderedTargets.size
            _pingAllState.value = PingAllUiState(
                isPingingAll = true,
                completedCount = 0,
                totalCount = total
            )
            val allSnapshot = profiles.value
            val policy = LowBatteryController.resolvePolicy(lowBatteryState.value, settings.value.batteryMode)
            val targetIdSet = orderedTargets.map { it.id }.toSet()
            try {
                latencyTester.testBatch(
                    targets = orderedTargets,
                    allProfiles = allSnapshot,
                    maxConcurrency = policy.maxPingConcurrency.coerceAtLeast(1),
                    pacingDelayMs = policy.pingPacingDelayMs,
                    onStart = { id -> _pingingIds.update { it + id } },
                    onResult = { id, ms -> profileRepo.updateLatency(id, ms) },
                    onFinishOne = { id -> _pingingIds.update { it - id } },
                    onProgress = { done, count ->
                        _pingAllState.value = PingAllUiState(
                            isPingingAll = true,
                            completedCount = done,
                            totalCount = count
                        )
                    }
                )
            } finally {
                _pingingIds.update { it - targetIdSet }
                _pingAllState.value = PingAllUiState()
            }
        }
    }

    fun stopPingAll() {
        backgroundLatencyJob?.cancel()
        backgroundLatencyJob = null
        pingAllJob?.cancel()
        pingAllJob = null
        _pingingIds.value = emptySet()
        _pingAllState.value = PingAllUiState()
    }

    fun benchmarkProfile(profile: VpnProfile) {
        viewModelScope.launch {
            _pingingIds.update { it + profile.id }
            try {
                val all = profiles.value
                val result = ExitIpChecker.benchmarkProfile(profile, all)
                profileRepo.updateBenchmark(profile.id, result.latencyMs, result.downloadSpeedBps)
            } finally {
                _pingingIds.update { it - profile.id }
            }
        }
    }

    fun testAllServers(
        targetProfiles: List<VpnProfile>,
        autoConnectFastest: Boolean,
        onAutoConnect: (VpnProfile) -> Unit
    ) {
        if (targetProfiles.isEmpty()) return
        val policy = LowBatteryController.resolvePolicy(lowBatteryState.value, settings.value.batteryMode)
        if (!policy.allowBulkBenchmark) {
            TunnelTelemetryBus.appendLog(
                "Low-Battery Mode (${lowBatteryState.value.batteryPercent}%): paused bulk speed benchmark"
            )
            return
        }
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

    fun connectBestFromFreeProvider(
        provider: FreeProxyProvider,
        noServerMessage: String,
        onBestServerReady: (VpnProfile) -> Unit
    ) {
        freeProviderJob?.cancel()
        freeProviderJob = viewModelScope.launch {
            val app = getApplication<Application>()
            val isFarsi = settings.value.language == AppLanguage.FARSI
            _freeProvidersState.update { current ->
                current.copy(
                    activeProviderId = provider.id,
                    stage = FreeProviderStage.FETCHING,
                    usedOfflineFallback = false,
                    pingedCount = 0,
                    totalToPing = 0,
                    reachableCount = 0,
                    bestLatencyMs = null,
                    errorMessage = null
                )
            }
            TunnelTelemetryBus.appendLog("Free Providers: fetching server list from ${provider.name}...")

            val fetchResult = FreeProxyProviderManager.fetchCandidates(provider)
            var usedFallback = false
            val listUpdatedAt: Long
            val candidates: List<VpnProfile> = when (fetchResult) {
                is FreeProviderFetchResult.Success -> {
                    val now = System.currentTimeMillis()
                    val updatedCache = FreeProxyProviderCache.saveProviderUpdate(
                        context = app,
                        providerId = provider.id,
                        profiles = fetchResult.candidates,
                        updatedAtMillis = now
                    )
                    _freeProvidersState.update { it.copy(cachedEntries = updatedCache) }
                    listUpdatedAt = now
                    fetchResult.candidates
                }
                is FreeProviderFetchResult.Failure -> {
                    // Seamless fallback to pre-bundled default / locally cached server list
                    val (cachedProfiles, cachedTimestamp) = FreeProxyProviderCache.getCachedProfiles(app, provider)
                    if (cachedProfiles.isNotEmpty()) {
                        usedFallback = true
                        listUpdatedAt = cachedTimestamp
                        TunnelTelemetryBus.appendLog(
                            "Free Providers: online fetch unavailable for ${provider.name} (${fetchResult.message}); using ${cachedProfiles.size} bundled/cached servers"
                        )
                        cachedProfiles
                    } else {
                        _freeProvidersState.update { current ->
                            current.copy(
                                activeProviderId = null,
                                stage = FreeProviderStage.FAILED,
                                errorMessage = "${provider.name}: ${fetchResult.message}"
                            )
                        }
                        TunnelTelemetryBus.appendLog("Free Providers: ${provider.name} fetch failed (${fetchResult.message})")
                        return@launch
                    }
                }
            }

            _freeProvidersState.update { current ->
                current.copy(
                    stage = FreeProviderStage.PINGING,
                    usedOfflineFallback = usedFallback,
                    pingedCount = 0,
                    totalToPing = candidates.size
                )
            }

            val policy = LowBatteryController.resolvePolicy(lowBatteryState.value, settings.value.batteryMode)
            val concurrency = if (lowBatteryState.value.isLowBatteryActive) {
                policy.maxPingConcurrency.coerceAtLeast(1)
            } else {
                8
            }

            val bestResult = FreeProxyProviderManager.pingAndPickBest(
                candidates = candidates,
                maxConcurrency = concurrency,
                pacingDelayMs = policy.pingPacingDelayMs,
                onProgress = { progress ->
                    val nextStage = if (progress.pingedCount >= progress.totalCount && progress.reachableCount > 0) {
                        FreeProviderStage.VERIFYING
                    } else {
                        FreeProviderStage.PINGING
                    }
                    _freeProvidersState.update { current ->
                        current.copy(
                            stage = nextStage,
                            pingedCount = progress.pingedCount,
                            totalToPing = progress.totalCount,
                            reachableCount = progress.reachableCount,
                            bestLatencyMs = progress.bestLatencyMs
                        )
                    }
                }
            )

            val winner = bestResult.bestProfile
            if (winner == null) {
                _freeProvidersState.update { current ->
                    current.copy(
                        activeProviderId = null,
                        stage = FreeProviderStage.FAILED,
                        errorMessage = noServerMessage
                    )
                }
                TunnelTelemetryBus.appendLog("Free Providers: no reachable servers found for ${provider.name}")
                return@launch
            }

            // Include the provider list's update date directly in the saved free server name
            val formattedWinnerName = FreeProxyProviderCache.formatServerNameWithDate(
                providerName = provider.name,
                baseServerLabel = winner.name,
                updatedAtMillis = listUpdatedAt,
                isFarsi = isFarsi
            )

            // Upsert one tidy saved profile per provider so the user's server list stays clean
            val existingProviderProfile = profiles.value.firstOrNull {
                it.subscriptionId == 0L && (
                    it.name.startsWith("${provider.name} ·", ignoreCase = true) ||
                        it.name.startsWith("${provider.name} (", ignoreCase = true)
                    )
            }
            val profileToSave = winner.copy(
                id = existingProviderProfile?.id ?: 0L,
                name = formattedWinnerName
            )
            val savedId = profileRepo.save(profileToSave)
            val savedProfile = profileRepo.getById(savedId) ?: profileToSave.copy(id = savedId)

            settingsRepo.setActiveProfileId(savedId)
            VpnShortcutController.cacheLastServer(app, savedProfile)
            VpnShortcutController.syncWidgetAndTile(app)

            val snapshot = FreeProviderResultSnapshot(
                providerId = provider.id,
                bestServerLabel = "${savedProfile.host}:${savedProfile.port}",
                bestLatencyMs = savedProfile.latencyMs,
                reachableCount = bestResult.reachableCount,
                countryCode = savedProfile.lastCountryCode.orEmpty()
            )
            _freeProvidersState.update { current ->
                current.copy(
                    activeProviderId = null,
                    stage = FreeProviderStage.CONNECTED,
                    usedOfflineFallback = usedFallback,
                    bestLatencyMs = savedProfile.latencyMs,
                    reachableCount = bestResult.reachableCount,
                    errorMessage = null,
                    resultsByProviderId = current.resultsByProviderId + (provider.id to snapshot)
                )
            }
            TunnelTelemetryBus.appendLog(
                "Free Providers: connecting to best ${provider.name} node ${savedProfile.host}:${savedProfile.port} (${savedProfile.latencyMs ?: -1} ms)"
            )
            onBestServerReady(savedProfile)
        }
    }

    fun cancelFreeProviderConnect() {
        freeProviderJob?.cancel()
        freeProviderJob = null
        _freeProvidersState.update { current ->
            current.copy(
                activeProviderId = null,
                stage = FreeProviderStage.IDLE,
                usedOfflineFallback = false,
                errorMessage = null
            )
        }
    }

    /**
     * When VPN is connected, gradually refreshes free proxy provider lists one by one
     * (spaced by 3.5s) so users in restricted networks automatically receive up-to-date
     * lists and fresh update dates without overloading the tunnel or battery.
     */
    private fun startGradualFreeProvidersRefreshIfNeeded() {
        if (backgroundFreeSyncJob?.isActive == true) return
        if (lowBatteryState.value.isLowBatteryActive) return

        backgroundFreeSyncJob = viewModelScope.launch {
            val app = getApplication<Application>()
            // Let the newly established VPN tunnel stabilize first
            delay(4000L)
            if (TunnelTelemetryBus.connectionState.value != ConnectionState.CONNECTED) return@launch
            if (lowBatteryState.value.isLowBatteryActive) return@launch

            val cache = FreeProxyProviderCache.loadAll(app)
            val staleCutoffMs = System.currentTimeMillis() - (3L * 60L * 60L * 1000L) // 3 hours
            val toRefresh = FreeProxyProviderManager.providers.filter { provider ->
                val entry = cache[provider.id]
                entry == null || entry.isDefaultBundle || entry.updatedAtMillis < staleCutoffMs
            }
            if (toRefresh.isEmpty()) return@launch

            TunnelTelemetryBus.appendLog(
                "VPN connected: starting gradual background update for ${toRefresh.size} free proxy providers"
            )
            val total = toRefresh.size
            try {
                toRefresh.forEachIndexed { index, provider ->
                    if (TunnelTelemetryBus.connectionState.value != ConnectionState.CONNECTED) return@launch
                    if (lowBatteryState.value.isLowBatteryActive) return@launch

                    // Yield if the user manually initiated a provider ping/connect
                    while (_freeProvidersState.value.isBusy) {
                        delay(1500L)
                        if (TunnelTelemetryBus.connectionState.value != ConnectionState.CONNECTED) return@launch
                    }

                    _freeProvidersState.update { current ->
                        current.copy(
                            backgroundSyncProviderId = provider.id,
                            backgroundSyncProviderName = provider.name,
                            backgroundSyncCompleted = index + 1,
                            backgroundSyncTotal = total
                        )
                    }

                    val fetched = FreeProxyProviderManager.fetchCandidates(provider, maxCandidates = 24)
                    if (fetched is FreeProviderFetchResult.Success && fetched.candidates.isNotEmpty()) {
                        val now = System.currentTimeMillis()
                        val updatedCache = FreeProxyProviderCache.saveProviderUpdate(
                            context = app,
                            providerId = provider.id,
                            profiles = fetched.candidates,
                            updatedAtMillis = now
                        )
                        _freeProvidersState.update { it.copy(cachedEntries = updatedCache) }
                    }

                    // Gentle pacing between providers so it updates gradually
                    if (index < toRefresh.lastIndex) {
                        delay(3500L)
                    }
                }
                TunnelTelemetryBus.appendLog("Background free proxy provider refresh completed")
            } finally {
                _freeProvidersState.update { current ->
                    current.copy(
                        backgroundSyncProviderId = null,
                        backgroundSyncProviderName = null,
                        backgroundSyncCompleted = 0,
                        backgroundSyncTotal = 0
                    )
                }
            }
        }
    }

    private fun stopBackgroundFreeSync() {
        backgroundFreeSyncJob?.cancel()
        backgroundFreeSyncJob = null
        _freeProvidersState.update { current ->
            current.copy(
                backgroundSyncProviderId = null,
                backgroundSyncProviderName = null,
                backgroundSyncCompleted = 0,
                backgroundSyncTotal = 0
            )
        }
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
            VpnShortcutController.cacheLastServer(getApplication(), profile)
            VpnShortcutController.syncWidgetAndTile(getApplication())
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

    fun switchMultiVpnNode() {
        if (connectionState.value != ConnectionState.CONNECTED) return
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, NetShieldVpnService::class.java).apply {
            action = NetShieldVpnService.ACTION_SWITCH_MULTI_NODE
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

    fun setLowBatteryAutoModeEnabled(enabled: Boolean) {
        LowBatteryController.setFeatureEnabled(enabled)
        viewModelScope.launch { settingsRepo.setLowBatteryAutoModeEnabled(enabled) }
    }

    fun setDnsServers(primary: String, secondary: String) {
        viewModelScope.launch { settingsRepo.setDnsServers(primary, secondary) }
    }

    fun setDnsPreset(preset: DnsPreset) {
        viewModelScope.launch { settingsRepo.setDnsPreset(preset) }
    }

    fun setAdBlockDnsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setAdBlockDnsEnabled(enabled) }
    }

    fun setCustomDohUrl(url: String) {
        viewModelScope.launch { settingsRepo.setCustomDohUrl(url) }
    }

    fun setBypassIranEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setBypassIranEnabled(enabled) }
    }

    fun setBypassLanEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setBypassLanEnabled(enabled) }
    }

    fun setCustomBypassDomains(domains: String) {
        viewModelScope.launch { settingsRepo.setCustomBypassDomains(domains) }
    }

    fun setSmartFailoverEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setSmartFailoverEnabled(enabled) }
    }

    fun clearDataUsageHistory() {
        viewModelScope.launch { DataUsageTracker.clearHistory(getApplication()) }
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
