package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.Process
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.AppSettings
import com.example.data.ExitIpChecker
import com.example.data.ProfileRepository
import com.example.data.SettingsRepository
import com.example.model.BatteryMode
import com.example.model.ConnectionState
import com.example.model.ExitNetworkInfo
import com.example.model.TunnelMode
import com.example.model.VpnProfile
import com.example.vpn.protocols.LoadBalanceConnector
import com.example.vpn.protocols.ProtocolConnectorFactory
import com.example.vpn.protocols.UpstreamConnector
import com.example.vpn.tun.TunSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class NetShieldVpnService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunDescriptor: ParcelFileDescriptor? = null
    private var connector: UpstreamConnector? = null
    private var sessionManager: TunSessionManager? = null
    private var telemetryJob: Job? = null
    private var connectJob: Job? = null
    private var reconnectJob: Job? = null
    private var healthWatchdogJob: Job? = null
    private var batteryObserverJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val manualDisconnect = AtomicBoolean(false)
    private val networkAvailable = AtomicBoolean(true)
    private val healingInProgress = AtomicBoolean(false)
    private val activeNetworks = ConcurrentHashMap.newKeySet<Network>()
    @Volatile private var primaryNetwork: Network? = null

    private fun isDeviceInteractive(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return runCatching { pm.isInteractive }.getOrDefault(true)
    }

    private fun ensureBatteryObserverStarted() {
        LowBatteryController.startMonitoring(applicationContext)
        if (batteryObserverJob?.isActive == true) return
        batteryObserverJob = serviceScope.launch {
            LowBatteryController.state.collect { batteryState ->
                val lowBat = batteryState.isLowBatteryActive
                sessionManager?.setLowBatteryMode(lowBat)
                connector?.setLowBatteryMode(lowBat)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureBatteryObserverStarted()
        serviceScope.launch {
            val prefs = runCatching { SettingsRepository(applicationContext).settingsFlow.first() }.getOrNull()
            CrashReporter.init(applicationContext, prefs?.crashlyticsEnabled ?: true)
            if (prefs != null) {
                LowBatteryController.setFeatureEnabled(prefs.lowBatteryAutoModeEnabled)
            }
        }
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                manualDisconnect.set(true)
                disconnectTunnel(forceCloseFirewall = true)
                return START_NOT_STICKY
            }
            ACTION_SWITCH_MULTI_NODE -> {
                serviceScope.launch {
                    val lb = connector as? LoadBalanceConnector ?: return@launch
                    val switched = lb.switchToStandbyNow()
                    if (switched) {
                        val status = lb.currentStatusSnapshot()
                        val profile = TunnelTelemetryBus.activeProfile.value
                        if (profile != null) {
                            updateNotification(
                                "${profile.name} • ${status.activeNodeName}",
                                "Dual-VPN switched • Standby: ${status.standbyNodeName ?: "None"}"
                            )
                        }
                    }
                }
                return START_STICKY
            }
            ACTION_CONNECT -> {
                manualDisconnect.set(false)
                val profileId = intent.getLongExtra(EXTRA_PROFILE_ID, -1L)
                startForegroundSafe(buildNotification("Connecting...", ""))
                connectJob?.cancel()
                reconnectJob?.cancel()
                connectJob = serviceScope.launch {
                    connectTunnel(profileId)
                }
            }
            else -> {
                manualDisconnect.set(false)
                startForegroundSafe(buildNotification("Starting VPN...", ""))
                connectJob?.cancel()
                reconnectJob?.cancel()
                connectJob = serviceScope.launch {
                    val settings = SettingsRepository(applicationContext).settingsFlow.first()
                    connectTunnel(settings.activeProfileId)
                }
            }
        }
        return START_STICKY
    }

    private suspend fun connectTunnel(
        profileId: Long,
        preconnectedConnector: UpstreamConnector? = null
    ) {
        TunnelTelemetryBus.setError(null)
        val isReconnecting = TunnelTelemetryBus.connectionState.value == ConnectionState.RECONNECTING
        if (!isReconnecting) {
            TunnelTelemetryBus.setConnectionState(ConnectionState.CONNECTING)
        }

        val db = AppDatabase.getInstance(applicationContext)
        val profileRepo = ProfileRepository(db.profileDao())
        val settingsRepo = SettingsRepository(applicationContext)
        val settings = settingsRepo.settingsFlow.first()

        val profile = VpnShortcutController.resolveLastOrFirstProfile(applicationContext, profileId)
        if (profile == null) {
            runCatching { preconnectedConnector?.close() }
            TunnelTelemetryBus.setError("No VPN profile selected")
            TunnelTelemetryBus.setConnectionState(ConnectionState.DISCONNECTED)
            VpnShortcutController.syncWidgetAndTile(applicationContext)
            stopSelfCleanly()
            return
        }

        if (VpnService.prepare(applicationContext) != null) {
            runCatching { preconnectedConnector?.close() }
            TunnelTelemetryBus.setError("VPN permission required — tap to grant in app")
            TunnelTelemetryBus.setConnectionState(ConnectionState.DISCONNECTED)
            VpnShortcutController.syncWidgetAndTile(applicationContext)
            stopSelfCleanly()
            return
        }

        TunnelTelemetryBus.setConnectionState(
            if (isReconnecting) ConnectionState.RECONNECTING else ConnectionState.CONNECTING,
            profile
        )
        VpnShortcutController.syncWidgetAndTile(applicationContext)
        CrashReporter.setTunnelContext(
            protocol = profile.protocol.name,
            tunnelMode = settings.tunnelMode.name,
            batteryMode = settings.batteryMode.name,
            state = if (isReconnecting) "RECONNECTING" else "CONNECTING"
        )
        TunnelTelemetryBus.appendLog("Connecting via ${profile.protocol.shortBadge} -> ${profile.host}:${profile.port}")

        val allProfiles = profileRepo.getAllOnce()
        val newConnector = preconnectedConnector ?: ProtocolConnectorFactory.create(profile, allProfiles) { socket ->
            protect(socket)
        }
        if (preconnectedConnector == null) {
            try {
                newConnector.connect()
            } catch (e: Exception) {
                newConnector.close()
                CrashReporter.recordNonFatal("upstream_handshake_${profile.protocol.name}", e)
                TunnelTelemetryBus.setError(e.message ?: "Upstream handshake failed")
                handleUnexpectedDisconnect(profile, settings)
                return
            }
        }

        // Reuse existing Kill-Switch TUN descriptor if already locked, otherwise establish new TUN
        val existingTun = tunDescriptor
        val tunFd = existingTun ?: buildTunInterface(
            profile = profile,
            tunnelMode = settings.tunnelMode,
            selectedPackages = settings.selectedPackages,
            dnsPrimary = settings.dnsPrimary,
            dnsSecondary = settings.dnsSecondary,
            mtu = settings.mtu
        )
        if (tunFd == null) {
            newConnector.close()
            TunnelTelemetryBus.setError("Failed to establish Android TUN interface")
            TunnelTelemetryBus.setConnectionState(ConnectionState.ERROR, profile)
            stopSelfCleanly()
            return
        }

        val lowBatteryActive = LowBatteryController.state.value.isLowBatteryActive
        newConnector.setLowBatteryMode(lowBatteryActive)

        runCatching { connector?.close() }
        connector = newConnector
        tunDescriptor = tunFd

        val existingMgr = sessionManager
        if (existingMgr != null && existingTun != null) {
            existingMgr.swapConnector(newConnector)
            existingMgr.setLowBatteryMode(lowBatteryActive)
        } else {
            val tunIn = FileInputStream(tunFd.fileDescriptor)
            val tunOut = FileOutputStream(tunFd.fileDescriptor)
            val manager = TunSessionManager(
                tunInput = tunIn,
                tunOutput = tunOut,
                connector = newConnector,
                dnsPrimary = settings.dnsPrimary,
                mtu = settings.mtu,
                scope = serviceScope,
                onLog = { msg -> TunnelTelemetryBus.appendLog(msg) },
                onConnectionLost = {
                    if (!manualDisconnect.get()) {
                        serviceScope.launch {
                            val latest = SettingsRepository(applicationContext).settingsFlow.first()
                            handleUnexpectedDisconnect(profile, latest)
                        }
                    }
                },
                uidResolver = { proto, srcIp, srcPort, dstIp, dstPort ->
                    resolveOwnerPackage(proto, srcIp, srcPort, dstIp, dstPort)
                },
                dnsSecondary = settings.dnsSecondary,
                isDeviceInteractiveProvider = { isDeviceInteractive() },
                onPausedCountChanged = { count ->
                    LowBatteryController.updatePausedBackgroundOperations(count)
                }
            )
            manager.setLowBatteryMode(lowBatteryActive)
            sessionManager = manager
            manager.start()
        }

        registerNetworkObserver(profile)

        val connectedAt = System.currentTimeMillis()
        TunnelTelemetryBus.setConnectionState(ConnectionState.CONNECTED, profile)
        VpnShortcutController.syncWidgetAndTile(applicationContext)
        AppAnalytics.logVpnConnected(profile.protocol.name)
        CrashReporter.setTunnelContext(
            protocol = profile.protocol.name,
            tunnelMode = settings.tunnelMode.name,
            batteryMode = settings.batteryMode.name,
            state = "CONNECTED"
        )
        TunnelTelemetryBus.appendLog("Tunnel established (${settings.tunnelMode.name}, MTU ${settings.mtu})")
        val initialStatus = if (lowBatteryActive) {
            "Connected • Low-Battery Operation (${LowBatteryController.state.value.batteryPercent}%)"
        } else {
            "Connected • Protected"
        }
        updateNotification("${profile.name} • ${profile.protocol.shortBadge}", initialStatus)

        startTelemetryLoop(profile, connectedAt, settings.batteryMode)
        startHealthWatchdog(profile, settings.batteryMode)

        val currentPolicy = LowBatteryController.resolvePolicy(LowBatteryController.state.value, settings.batteryMode)
        if (settings.autoCheckExitIp && currentPolicy.allowAutoExitIpCheck) {
            TunnelTelemetryBus.setExitInfo(ExitNetworkInfo(isLoading = true))
            val info = ExitIpChecker.fetchExitInfo(profile, allProfiles)
            TunnelTelemetryBus.setExitInfo(info)
            if (info.countryCode.isNotBlank()) {
                profileRepo.updateCountryCode(profile.id, info.countryCode)
            }
            if (info.ipAddress.isNotBlank()) {
                TunnelTelemetryBus.appendLog("Exit IP: ${info.ipAddress} (${info.countryCode} ${info.flagEmoji})")
            }
        } else if (settings.autoCheckExitIp && !currentPolicy.allowAutoExitIpCheck) {
            TunnelTelemetryBus.appendLog("Low-Battery Mode: paused automatic background Exit IP check")
        }
    }

    private fun handleUnexpectedDisconnect(profile: VpnProfile, settings: AppSettings) {
        if (manualDisconnect.get()) return
        if (reconnectJob?.isActive == true) return

        telemetryJob?.cancel()
        healthWatchdogJob?.cancel()
        runCatching { connector?.close() }
        connector = null

        val keepFirewall = settings.killSwitchEnabled
        val shouldReconnect = settings.autoReconnectEnabled

        if (keepFirewall) {
            // Keep or establish protective blackhole TUN so unencrypted traffic cannot leak
            if (tunDescriptor == null) {
                val blackholeTun = buildTunInterface(
                    profile = profile,
                    tunnelMode = settings.tunnelMode,
                    selectedPackages = settings.selectedPackages,
                    dnsPrimary = settings.dnsPrimary,
                    dnsSecondary = settings.dnsSecondary,
                    mtu = settings.mtu
                )
                if (blackholeTun != null) {
                    tunDescriptor = blackholeTun
                    val mgr = TunSessionManager(
                        tunInput = FileInputStream(blackholeTun.fileDescriptor),
                        tunOutput = FileOutputStream(blackholeTun.fileDescriptor),
                        connector = null,
                        dnsPrimary = settings.dnsPrimary,
                        mtu = settings.mtu,
                        scope = serviceScope,
                        onLog = { msg -> TunnelTelemetryBus.appendLog(msg) }
                    )
                    mgr.setFirewallDropAll(true)
                    sessionManager = mgr
                    mgr.start()
                }
            } else {
                sessionManager?.setFirewallDropAll(true)
            }
            TunnelTelemetryBus.appendLog("Kill Switch Firewall active — blocking non-VPN traffic")
        }

        if (shouldReconnect) {
            TunnelTelemetryBus.setConnectionState(ConnectionState.RECONNECTING, profile)
            VpnShortcutController.syncWidgetAndTile(applicationContext)
            updateNotification(
                "${profile.name} • Reconnecting",
                if (keepFirewall) "Firewall active • Blocking traffic until reconnected" else "Waiting to reconnect..."
            )
            scheduleAutoReconnect(profile.id)
        } else if (keepFirewall) {
            TunnelTelemetryBus.setConnectionState(ConnectionState.FIREWALL_LOCKED, profile)
            VpnShortcutController.syncWidgetAndTile(applicationContext)
            updateNotification(
                "Firewall Lock Active",
                "VPN disconnected • Traffic blocked to prevent leaks",
                includeReconnect = true,
                reconnectProfileId = profile.id
            )
        } else {
            TunnelTelemetryBus.setConnectionState(ConnectionState.ERROR, profile)
            VpnShortcutController.syncWidgetAndTile(applicationContext)
            disconnectTunnel(forceCloseFirewall = true)
        }
    }

    private fun scheduleAutoReconnect(profileId: Long) {
        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            val initialPolicy = LowBatteryController.resolvePolicy()
            var backoffMs = initialPolicy.autoReconnectInitialBackoffMs
            while (isActive && !manualDisconnect.get()) {
                val currentSettings = SettingsRepository(applicationContext).settingsFlow.first()
                if (!currentSettings.autoReconnectEnabled) {
                    if (currentSettings.killSwitchEnabled) {
                        TunnelTelemetryBus.setConnectionState(ConnectionState.FIREWALL_LOCKED)
                    } else {
                        disconnectTunnel(forceCloseFirewall = true)
                    }
                    break
                }

                val policy = LowBatteryController.resolvePolicy(
                    state = LowBatteryController.state.value,
                    batteryMode = currentSettings.batteryMode
                )
                if (backoffMs < policy.autoReconnectInitialBackoffMs) {
                    backoffMs = policy.autoReconnectInitialBackoffMs
                }

                if (networkAvailable.get()) {
                    TunnelTelemetryBus.appendLog("Auto-reconnect attempt...")
                    val dao = AppDatabase.getInstance(applicationContext).profileDao()
                    val allProfiles = dao.getAllOnce()
                    val profile = allProfiles.firstOrNull { it.id == profileId }
                    if (profile == null) {
                        disconnectTunnel(forceCloseFirewall = true)
                        break
                    }
                    val trial = ProtocolConnectorFactory.create(profile, allProfiles) { socket -> protect(socket) }
                    val connected = runCatching { trial.connect() }.isSuccess
                    if (connected) {
                        connectTunnel(profileId, preconnectedConnector = trial)
                        break
                    } else {
                        runCatching { trial.close() }
                    }
                }
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(policy.autoReconnectMaxBackoffMs)
            }
        }
    }

    private fun startHealthWatchdog(profile: VpnProfile, batteryMode: BatteryMode) {
        healthWatchdogJob?.cancel()
        healthWatchdogJob = serviceScope.launch {
            while (isActive && !manualDisconnect.get() &&
                TunnelTelemetryBus.connectionState.value == ConnectionState.CONNECTED
            ) {
                val batteryState = LowBatteryController.state.value
                val lowBat = batteryState.isLowBatteryActive || batteryMode == BatteryMode.ULTRA_LOW_POWER && batteryState.batteryPercent in 0..25
                val intervalMs = if (lowBat) WATCHDOG_INTERVAL_LOW_BATTERY_MS else WATCHDOG_INTERVAL_NORMAL_MS
                delay(intervalMs)
                if (manualDisconnect.get() || TunnelTelemetryBus.connectionState.value != ConnectionState.CONNECTED) {
                    break
                }
                if (!networkAvailable.get()) continue
                val currentConnector = connector ?: continue
                val alive = runCatching { currentConnector.probeHealth() }.getOrDefault(false)
                if (!alive) {
                    attemptSeamlessConnectorHeal(profile, reason = "Tunnel health probe failed", probeFirst = false)
                }
            }
        }
    }

    /**
     * Attempts to hot-swap the upstream connector while keeping the Android TUN descriptor open
     * so apps experience minimal disruption across Wi-Fi/cellular handoffs or brief server drops.
     */
    private suspend fun attemptSeamlessConnectorHeal(
        profile: VpnProfile,
        reason: String,
        probeFirst: Boolean
    ) {
        if (manualDisconnect.get()) return
        if (TunnelTelemetryBus.connectionState.value != ConnectionState.CONNECTED) return
        if (!healingInProgress.compareAndSet(false, true)) return
        try {
            if (probeFirst) {
                val existing = connector
                if (existing != null && runCatching { existing.probeHealth() }.getOrDefault(false)) {
                    return
                }
            }
            TunnelTelemetryBus.appendLog("$reason — attempting seamless tunnel recovery")
            val db = AppDatabase.getInstance(applicationContext)
            val allProfiles = db.profileDao().getAllOnce()
            val latestProfile = allProfiles.firstOrNull { it.id == profile.id } ?: profile
            val freshConnector = ProtocolConnectorFactory.create(latestProfile, allProfiles) { socket ->
                protect(socket)
            }
            val connected = runCatching { freshConnector.connect() }.isSuccess
            if (connected && !manualDisconnect.get() &&
                TunnelTelemetryBus.connectionState.value == ConnectionState.CONNECTED
            ) {
                freshConnector.setLowBatteryMode(LowBatteryController.state.value.isLowBatteryActive)
                val old = connector
                connector = freshConnector
                sessionManager?.swapConnector(freshConnector)
                runCatching { old?.close() }
                TunnelTelemetryBus.appendLog("Seamless tunnel recovery succeeded")
            } else {
                runCatching { freshConnector.close() }
                val latestSettings = SettingsRepository(applicationContext).settingsFlow.first()
                handleUnexpectedDisconnect(latestProfile, latestSettings)
            }
        } finally {
            healingInProgress.set(false)
        }
    }

    private fun registerNetworkObserver(profile: VpnProfile) {
        unregisterNetworkObserver()
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        activeNetworks.clear()
        val initialNet = cm.activeNetwork
        primaryNetwork = initialNet
        initialNet?.let { activeNetworks.add(it) }
        networkAvailable.set(initialNet != null)

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                activeNetworks.add(network)
                networkAvailable.set(true)
                val prev = primaryNetwork
                primaryNetwork = network
                if (prev != null && prev != network &&
                    !manualDisconnect.get() &&
                    TunnelTelemetryBus.connectionState.value == ConnectionState.CONNECTED
                ) {
                    serviceScope.launch {
                        // Brief settle delay after Wi-Fi <-> cellular handoff before probing/healing
                        delay(600L)
                        attemptSeamlessConnectorHeal(
                            profile = profile,
                            reason = "Network handoff detected",
                            probeFirst = true
                        )
                    }
                }
            }

            override fun onLost(network: Network) {
                activeNetworks.remove(network)
                val stillOnline = activeNetworks.isNotEmpty() || cm.activeNetwork != null
                networkAvailable.set(stillOnline)
                if (!stillOnline &&
                    !manualDisconnect.get() &&
                    TunnelTelemetryBus.connectionState.value == ConnectionState.CONNECTED
                ) {
                    serviceScope.launch {
                        val latest = SettingsRepository(applicationContext).settingsFlow.first()
                        if (latest.killSwitchEnabled || latest.autoReconnectEnabled) {
                            TunnelTelemetryBus.appendLog("Underlying network lost")
                            handleUnexpectedDisconnect(profile, latest)
                        }
                    }
                }
            }
        }
        networkCallback = cb
        runCatching { cm.registerDefaultNetworkCallback(cb) }
    }

    private fun unregisterNetworkObserver() {
        val cb = networkCallback ?: return
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        runCatching { cm.unregisterNetworkCallback(cb) }
        networkCallback = null
        primaryNetwork = null
        activeNetworks.clear()
    }

    private fun buildTunInterface(
        profile: VpnProfile,
        tunnelMode: TunnelMode,
        selectedPackages: Set<String>,
        dnsPrimary: String,
        dnsSecondary: String,
        mtu: Int
    ): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession("NetShield (${profile.name})")
            .setMtu(mtu)
            .addAddress("10.8.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(dnsPrimary)
            .addDnsServer(dnsSecondary)
            .setBlocking(true)

        when (tunnelMode) {
            TunnelMode.FULL_SYSTEM -> {
                runCatching { builder.addDisallowedApplication(packageName) }
            }
            TunnelMode.SPLIT_INCLUDE -> {
                var added = 0
                for (pkg in selectedPackages) {
                    if (pkg == packageName) continue
                    runCatching {
                        packageManager.getPackageInfo(pkg, 0)
                        builder.addAllowedApplication(pkg)
                        added++
                    }
                }
                if (added == 0) {
                    runCatching { builder.addDisallowedApplication(packageName) }
                }
            }
            TunnelMode.SPLIT_EXCLUDE -> {
                runCatching { builder.addDisallowedApplication(packageName) }
                for (pkg in selectedPackages) {
                    if (pkg == packageName) continue
                    runCatching {
                        packageManager.getPackageInfo(pkg, 0)
                        builder.addDisallowedApplication(pkg)
                    }
                }
            }
        }
        return builder.establish()
    }

    private fun startTelemetryLoop(profile: VpnProfile, connectedAt: Long, batteryMode: BatteryMode) {
        telemetryJob?.cancel()
        telemetryJob = serviceScope.launch {
            var prevRx = sessionManager?.totalRxBytes?.get() ?: 0L
            var prevTx = sessionManager?.totalTxBytes?.get() ?: 0L
            var notifTick = 0

            while (isActive && TunnelTelemetryBus.connectionState.value == ConnectionState.CONNECTED) {
                val batteryState = LowBatteryController.state.value
                val policy = LowBatteryController.resolvePolicy(batteryState, batteryMode)
                val sampleIntervalMs = policy.telemetrySampleIntervalMs
                val notifBatchTicks = policy.notificationBatchTicks

                delay(sampleIntervalMs)
                val mgr = sessionManager ?: break
                val rx = mgr.totalRxBytes.get()
                val tx = mgr.totalTxBytes.get()
                val elapsedSeconds = (sampleIntervalMs / 1000L).coerceAtLeast(1L)
                val dlSpeed = ((rx - prevRx).coerceAtLeast(0L)) / elapsedSeconds
                val ulSpeed = ((tx - prevTx).coerceAtLeast(0L)) / elapsedSeconds
                prevRx = rx
                prevTx = tx

                TunnelTelemetryBus.updateTraffic(
                    downloadSpeedBps = dlSpeed,
                    uploadSpeedBps = ulSpeed,
                    totalDownloadedBytes = rx,
                    totalUploadedBytes = tx,
                    connectedSinceMillis = connectedAt,
                    activeStreams = mgr.activeStreamCount.get()
                )
                // Pause heavy per-app traffic table snapshots while Low-Battery Operation is active
                if (!policy.pauseHighResourceBackgroundTraffic) {
                    TunnelTelemetryBus.updateAppTraffic(mgr.snapshotAppTraffic())
                }

                notifTick++
                if (notifTick >= notifBatchTicks) {
                    notifTick = 0
                    val title = if (policy.isLowBatteryActive) {
                        "${profile.name} • Low-Battery (${batteryState.batteryPercent}%)"
                    } else {
                        "${profile.name} • ${profile.protocol.shortBadge}"
                    }
                    val body = if (policy.isLowBatteryActive) {
                        "Power saver active • ↓ ${formatRate(dlSpeed)}  ↑ ${formatRate(ulSpeed)}"
                    } else {
                        "↓ ${formatRate(dlSpeed)}  ↑ ${formatRate(ulSpeed)}  |  Σ ↓${formatBytes(rx)} ↑${formatBytes(tx)}"
                    }
                    updateNotification(title, body)
                }
            }
        }
    }

    private fun resolveOwnerPackage(
        protocol: Int,
        srcIp: String,
        srcPort: Int,
        dstIp: String,
        dstPort: Int
    ): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val cm = getSystemService(ConnectivityManager::class.java) ?: return null
        val uid = runCatching {
            cm.getConnectionOwnerUid(
                protocol,
                InetSocketAddress(srcIp, srcPort),
                InetSocketAddress(dstIp, dstPort)
            )
        }.getOrDefault(Process.INVALID_UID)
        return when {
            uid == Process.INVALID_UID -> null
            uid == Process.myUid() -> packageName
            uid == 0 || uid == 1000 -> "android"
            else -> runCatching {
                packageManager.getPackagesForUid(uid)?.firstOrNull()
            }.getOrNull()
        }
    }

    private fun disconnectTunnel(forceCloseFirewall: Boolean) {
        TunnelTelemetryBus.setConnectionState(ConnectionState.DISCONNECTING)
        TunnelTelemetryBus.appendLog("Disconnecting tunnel...")
        connectJob?.cancel()
        reconnectJob?.cancel()
        telemetryJob?.cancel()
        healthWatchdogJob?.cancel()
        unregisterNetworkObserver()
        LowBatteryController.resetPausedBackgroundOperations()
        runCatching { sessionManager?.stop() }
        sessionManager = null
        runCatching { connector?.close() }
        connector = null
        if (forceCloseFirewall) {
            runCatching { tunDescriptor?.close() }
            tunDescriptor = null
        }
        TunnelTelemetryBus.setConnectionState(ConnectionState.DISCONNECTED)
        VpnShortcutController.syncWidgetAndTile(applicationContext)
        stopSelfCleanly()
    }

    private fun stopSelfCleanly() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        manualDisconnect.set(true)
        disconnectTunnel(forceCloseFirewall = true)
        super.onRevoke()
    }

    override fun onDestroy() {
        manualDisconnect.set(true)
        disconnectTunnel(forceCloseFirewall = true)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.vpn_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.vpn_channel_description)
                    setShowBadge(false)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun buildNotification(
        title: String,
        body: String,
        includeReconnect: Boolean = false,
        reconnectProfileId: Long = -1L
    ): Notification {
        ensureChannel()
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val disconnectIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, NetShieldVpnService::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_baldr_tile)
            .setContentTitle(title)
            .setContentText(body.ifBlank { "Tunnel active • Low battery mode" })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.notif_action_disconnect), disconnectIntent)

        if (includeReconnect) {
            val reconnectIntent = PendingIntent.getService(
                this,
                2,
                Intent(this, NetShieldVpnService::class.java).apply {
                    action = ACTION_CONNECT
                    if (reconnectProfileId > 0L) {
                        putExtra(EXTRA_PROFILE_ID, reconnectProfileId)
                    }
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(0, getString(R.string.notif_action_reconnect), reconnectIntent)
        }
        return builder.build()
    }

    private fun startForegroundSafe(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun updateNotification(
        title: String,
        body: String,
        includeReconnect: Boolean = false,
        reconnectProfileId: Long = -1L
    ) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(title, body, includeReconnect, reconnectProfileId))
    }

    companion object {
        const val ACTION_CONNECT = "com.example.vpn.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.example.vpn.ACTION_DISCONNECT"
        const val ACTION_SWITCH_MULTI_NODE = "com.example.vpn.ACTION_SWITCH_MULTI_NODE"
        const val EXTRA_PROFILE_ID = "extra_profile_id"
        private const val CHANNEL_ID = "netshield_vpn_status"
        private const val NOTIF_ID = 1001
        private const val WATCHDOG_INTERVAL_NORMAL_MS = 20_000L
        private const val WATCHDOG_INTERVAL_LOW_BATTERY_MS = 60_000L
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.2f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}

fun formatRate(bytesPerSec: Long): String = "${formatBytes(bytesPerSec)}/s"
