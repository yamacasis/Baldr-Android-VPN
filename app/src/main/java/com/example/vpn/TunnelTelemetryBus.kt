package com.example.vpn

import com.example.model.ConnectionState
import com.example.model.ExitNetworkInfo
import com.example.model.SpeedSample
import com.example.model.TrafficStatsSnapshot
import com.example.model.VpnProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object TunnelTelemetryBus {
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _activeProfile = MutableStateFlow<VpnProfile?>(null)
    val activeProfile: StateFlow<VpnProfile?> = _activeProfile.asStateFlow()

    private val _trafficStats = MutableStateFlow(TrafficStatsSnapshot())
    val trafficStats: StateFlow<TrafficStatsSnapshot> = _trafficStats.asStateFlow()

    private val _exitInfo = MutableStateFlow(ExitNetworkInfo())
    val exitInfo: StateFlow<ExitNetworkInfo> = _exitInfo.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    fun setConnectionState(state: ConnectionState, profile: VpnProfile? = _activeProfile.value) {
        _connectionState.value = state
        if (profile != null || state == ConnectionState.DISCONNECTED) {
            _activeProfile.value = if (state == ConnectionState.DISCONNECTED) null else profile
        }
        if (state == ConnectionState.DISCONNECTED) {
            _trafficStats.value = TrafficStatsSnapshot()
            _exitInfo.value = ExitNetworkInfo()
        }
    }

    fun updateTraffic(
        downloadSpeedBps: Long,
        uploadSpeedBps: Long,
        totalDownloadedBytes: Long,
        totalUploadedBytes: Long,
        connectedSinceMillis: Long,
        activeStreams: Int
    ) {
        _trafficStats.update { prev ->
            val updatedHistory = (prev.speedHistory + SpeedSample(
                timestampMillis = System.currentTimeMillis(),
                downloadBps = downloadSpeedBps,
                uploadBps = uploadSpeedBps
            )).takeLast(36)
            TrafficStatsSnapshot(
                downloadSpeedBps = downloadSpeedBps,
                uploadSpeedBps = uploadSpeedBps,
                totalDownloadedBytes = totalDownloadedBytes,
                totalUploadedBytes = totalUploadedBytes,
                connectedSinceMillis = connectedSinceMillis,
                activeStreams = activeStreams,
                speedHistory = updatedHistory
            )
        }
    }

    fun setExitInfo(info: ExitNetworkInfo) {
        _exitInfo.value = info
    }

    fun setError(message: String?) {
        _lastError.value = message
        if (!message.isNullOrBlank()) {
            appendLog("ERROR: $message")
        }
    }

    fun appendLog(message: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        _logs.update { current ->
            (current + "[$ts] $message").takeLast(80)
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
