package com.example.ui

import android.app.Application
import android.content.Intent
import android.graphics.drawable.Drawable
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
import com.example.data.UriConfigParser
import com.example.model.AppLanguage
import com.example.model.AppThemeMode
import com.example.model.BatteryMode
import com.example.model.ConnectionState
import com.example.model.ExitNetworkInfo
import com.example.model.InstalledAppInfo
import com.example.model.TrafficStatsSnapshot
import com.example.model.TunnelMode
import com.example.model.VpnProfile
import com.example.model.VpnProtocol
import com.example.vpn.NetShieldVpnService
import com.example.vpn.TunnelTelemetryBus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val profileRepo = ProfileRepository(db.profileDao())
    private val settingsRepo = SettingsRepository(application)
    private val appsRepo = InstalledAppsRepository(application)

    val profiles: StateFlow<List<VpnProfile>> = profileRepo.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val connectionState: StateFlow<ConnectionState> = TunnelTelemetryBus.connectionState
    val activeTunnelProfile: StateFlow<VpnProfile?> = TunnelTelemetryBus.activeProfile
    val trafficStats: StateFlow<TrafficStatsSnapshot> = TunnelTelemetryBus.trafficStats
    val exitInfo: StateFlow<ExitNetworkInfo> = TunnelTelemetryBus.exitInfo
    val lastError: StateFlow<String?> = TunnelTelemetryBus.lastError
    val logs: StateFlow<List<String>> = TunnelTelemetryBus.logs

    val selectedProfile: StateFlow<VpnProfile?> = combine(profiles, settings) { list, prefs ->
        list.firstOrNull { it.id == prefs.activeProfileId } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _appsLoading = MutableStateFlow(false)
    val appsLoading: StateFlow<Boolean> = _appsLoading.asStateFlow()

    private val _pingingIds = MutableStateFlow<Set<Long>>(emptySet())
    val pingingIds: StateFlow<Set<Long>> = _pingingIds.asStateFlow()

    private val _benchmarkState = MutableStateFlow(BenchmarkUiState())
    val benchmarkState: StateFlow<BenchmarkUiState> = _benchmarkState.asStateFlow()

    private var benchmarkAllJob: Job? = null

    fun loadInstalledAppsIfNeeded() {
        if (_installedApps.value.isNotEmpty() || _appsLoading.value) return
        viewModelScope.launch {
            _appsLoading.value = true
            _installedApps.value = appsRepo.loadLaunchableApps()
            _appsLoading.value = false
        }
    }

    fun getAppIcon(packageName: String): Drawable? = appsRepo.loadAppIcon(packageName)

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
            onResult(parsed.size)
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
                        onResult(BackupImportStatus.Success(singleNodes.size + lbNodes.size))
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

    fun clearLogs() = TunnelTelemetryBus.clearLogs()

    fun dismissError() = TunnelTelemetryBus.setError(null)
}
