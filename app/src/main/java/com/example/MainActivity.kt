package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.model.AppLanguage
import com.example.model.AppThemeMode
import com.example.model.ConnectionState
import com.example.model.VpnProfile
import com.example.ui.MainViewModel
import com.example.ui.i18n.EnglishStrings
import com.example.ui.i18n.FarsiStrings
import com.example.ui.navigation.DashboardRoute
import com.example.ui.navigation.ServersRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.SplitTunnelRoute
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ServersScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplitTunnelScreen
import com.example.ui.theme.BaldrGold
import com.example.ui.theme.ConnectedGreenDarkRing
import com.example.ui.theme.ConnectedGreenDeep
import com.example.ui.theme.ConnectedGreenLight
import com.example.ui.theme.LapisEmblemBg
import com.example.ui.theme.NetShieldTheme
import com.example.vpn.AppAnalytics
import com.example.vpn.CrashReporter
import com.example.vpn.RemoteUpdateManager
import com.example.vpn.RemoteUpdateState
import com.example.vpn.VpnShortcutController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {

    private val incomingIntentState = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingIntentState.value = intent
        enableEdgeToEdge()
        setContent {
            val latestIntent by incomingIntentState.collectAsStateWithLifecycle()
            NetShieldApp(
                incomingIntent = latestIntent,
                onConsumeIncomingIntent = { incomingIntentState.value = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingIntentState.value = intent
    }
}

private data class NavItem(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun NetShieldApp(
    viewModel: MainViewModel = viewModel(),
    incomingIntent: Intent? = null,
    onConsumeIncomingIntent: () -> Unit = {}
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val updatingSubIds by viewModel.updatingSubIds.collectAsStateWithLifecycle()
    val selectedProfile by viewModel.selectedProfile.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeTunnelProfile.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val trafficStats by viewModel.trafficStats.collectAsStateWithLifecycle()
    val multiTunnelStatus by viewModel.multiTunnelStatus.collectAsStateWithLifecycle()
    val appTraffic by viewModel.appTraffic.collectAsStateWithLifecycle()
    val exitInfo by viewModel.exitInfo.collectAsStateWithLifecycle()
    val lastError by viewModel.lastError.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val appsLoading by viewModel.appsLoading.collectAsStateWithLifecycle()
    val pingingIds by viewModel.pingingIds.collectAsStateWithLifecycle()
    val benchmarkState by viewModel.benchmarkState.collectAsStateWithLifecycle()
    val pingAllState by viewModel.pingAllState.collectAsStateWithLifecycle()
    val freeProvidersState by viewModel.freeProvidersState.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val blockedAdsCount by viewModel.blockedAdsCount.collectAsStateWithLifecycle()
    val bypassedConnectionsCount by viewModel.bypassedConnectionsCount.collectAsStateWithLifecycle()
    val dataUsageSummary by viewModel.dataUsageSummary.collectAsStateWithLifecycle()
    val isSmartConnecting by viewModel.isSmartConnecting.collectAsStateWithLifecycle()
    val remoteUpdateState by viewModel.remoteUpdateState.collectAsStateWithLifecycle()
    val fcmToken by viewModel.fcmToken.collectAsStateWithLifecycle()
    val installationId by viewModel.installationId.collectAsStateWithLifecycle()
    val lowBatteryState by viewModel.lowBatteryState.collectAsStateWithLifecycle()

    val isFarsi = settings.language == AppLanguage.FARSI
    val strings = if (isFarsi) FarsiStrings else EnglishStrings
    val layoutDirection = if (isFarsi) LayoutDirection.Rtl else LayoutDirection.Ltr
    val systemDark = isSystemInDarkTheme()
    val isDarkTheme = when (settings.themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> systemDark
    }
    var showSplash by rememberSaveable { mutableStateOf(true) }
    var askedStartupNotif by rememberSaveable { mutableStateOf(false) }

    val startupNotificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (showSplash) {
            delay(950L)
            showSplash = false
        }
        if (!askedStartupNotif && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askedStartupNotif = true
            startupNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(settings.crashlyticsEnabled) {
        CrashReporter.init(context.applicationContext, settings.crashlyticsEnabled)
    }

    val activity = context as? ComponentActivity
    LaunchedEffect(isDarkTheme) {
        activity?.enableEdgeToEdge(
            statusBarStyle = if (isDarkTheme) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            },
            navigationBarStyle = if (isDarkTheme) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            }
        )
    }

    var pendingConnectProfile by remember { mutableStateOf<VpnProfile?>(null) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingConnectProfile?.let { viewModel.startVpn(it) }
        }
        pendingConnectProfile = null
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        pendingConnectProfile?.let { profile ->
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent != null) {
                vpnPermissionLauncher.launch(prepareIntent)
            } else {
                viewModel.startVpn(profile)
                pendingConnectProfile = null
            }
        }
    }

    fun requestVpnStart(profile: VpnProfile) {
        pendingConnectProfile = profile
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            viewModel.startVpn(profile)
            pendingConnectProfile = null
        }
    }

    LaunchedEffect(incomingIntent, profiles, selectedProfile, showSplash) {
        val intent = incomingIntent ?: return@LaunchedEffect
        val autoConnectLast = intent.getBooleanExtra(VpnShortcutController.EXTRA_AUTO_CONNECT_LAST, false)
        val fcmApkUrl = intent.getStringExtra("apk_url")?.trim()
            ?.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
            ?: intent.getStringExtra("url")?.trim()
                ?.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
        val syncRemote = intent.getStringExtra("sync_remote_config").equals("true", ignoreCase = true) ||
            !intent.getStringExtra("forced_update").isNullOrBlank()

        if (syncRemote) {
            viewModel.checkRemoteUpdateNow()
        }
        if (fcmApkUrl != null) {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fcmApkUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
            onConsumeIncomingIntent()
            return@LaunchedEffect
        }
        if (autoConnectLast && !showSplash) {
            val target = selectedProfile ?: profiles.firstOrNull()
            if (target != null) {
                requestVpnStart(target)
                onConsumeIncomingIntent()
            } else if (profiles.isEmpty()) {
                // Keep waiting briefly until Room emits initial profiles or let user add one
                onConsumeIncomingIntent()
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        NetShieldTheme(darkTheme = isDarkTheme, isFarsi = isFarsi) {
            val navController = rememberNavController()
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDest = backStackEntry?.destination

            LaunchedEffect(currentDest) {
                val screenName = when {
                    currentDest?.hasRoute(DashboardRoute::class) == true -> "dashboard"
                    currentDest?.hasRoute(ServersRoute::class) == true -> "servers"
                    currentDest?.hasRoute(SplitTunnelRoute::class) == true -> "traffic"
                    currentDest?.hasRoute(SettingsRoute::class) == true -> "settings"
                    else -> null
                }
                if (screenName != null) {
                    viewModel.logScreen(screenName)
                }
            }

            val navItems = listOf(
                NavItem(DashboardRoute, strings.navDashboard, Icons.Filled.Shield, Icons.Outlined.Shield, "nav_dashboard"),
                NavItem(ServersRoute, strings.navServers, Icons.Filled.Dns, Icons.Outlined.Dns, "nav_servers"),
                NavItem(SplitTunnelRoute, strings.navSplitTunnel, Icons.Filled.SwapVert, Icons.Outlined.SwapVert, "nav_split"),
                NavItem(SettingsRoute, strings.navSettings, Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
            )

            fun navigateTo(route: Any) {
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }

            val bgDrawableRes = if (isDarkTheme) {
                R.drawable.bg_lapis_dark_1795506348696
            } else {
                R.drawable.bg_lapis_light_1795506391560
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(id = bgDrawableRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                val isVpnConnected = connectionState == ConnectionState.CONNECTED
                val navSelectedColor by animateColorAsState(
                    targetValue = if (isVpnConnected) {
                        if (isDarkTheme) ConnectedGreenLight else ConnectedGreenDeep
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
                    animationSpec = spring(),
                    label = "nav_selected_color"
                )
                val navIndicatorColor by animateColorAsState(
                    targetValue = if (isVpnConnected) {
                        ConnectedGreenDarkRing.copy(alpha = if (isDarkTheme) 0.78f else 0.22f)
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    animationSpec = spring(),
                    label = "nav_indicator_color"
                )

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val useRail = maxWidth >= 600.dp

                    Scaffold(
                        containerColor = Color.Transparent,
                        contentWindowInsets = WindowInsets.safeDrawing,
                        bottomBar = {
                            if (!useRail) {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ) {
                                    val navItemColors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = navSelectedColor,
                                        selectedTextColor = navSelectedColor,
                                        indicatorColor = navIndicatorColor,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    navItems.forEach { item ->
                                        val selected = currentDest?.hasRoute(item.route::class) == true
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = { navigateTo(item.route) },
                                            colors = navItemColors,
                                            icon = {
                                                Icon(
                                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                    contentDescription = item.label
                                                )
                                            },
                                            label = { Text(item.label) },
                                            modifier = Modifier.testTag(item.testTag)
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            if (useRail) {
                                NavigationRail(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ) {
                                    val railItemColors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = navSelectedColor,
                                        selectedTextColor = navSelectedColor,
                                        indicatorColor = navIndicatorColor,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    navItems.forEach { item ->
                                        val selected = currentDest?.hasRoute(item.route::class) == true
                                        NavigationRailItem(
                                            selected = selected,
                                            onClick = { navigateTo(item.route) },
                                            colors = railItemColors,
                                            icon = {
                                                Icon(
                                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                    contentDescription = item.label
                                                )
                                            },
                                            label = { Text(item.label) },
                                            modifier = Modifier.testTag(item.testTag)
                                        )
                                    }
                                }
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                NavHost(
                                    navController = navController,
                                    startDestination = DashboardRoute
                                ) {
                                    composable<DashboardRoute> {
                                        DashboardScreen(
                                            strings = strings,
                                            connectionState = connectionState,
                                            selectedProfile = selectedProfile,
                                            activeProfile = activeProfile,
                                            trafficStats = trafficStats,
                                            multiTunnelStatus = multiTunnelStatus,
                                            exitInfo = exitInfo,
                                            settings = settings,
                                            lowBatteryState = lowBatteryState,
                                            isDarkTheme = isDarkTheme,
                                            lastError = lastError,
                                            blockedAdsCount = blockedAdsCount,
                                            bypassedConnectionsCount = bypassedConnectionsCount,
                                            isSmartConnecting = isSmartConnecting,
                                            onToggleConnection = {
                                                if (connectionState == ConnectionState.CONNECTED ||
                                                    connectionState == ConnectionState.CONNECTING ||
                                                    connectionState == ConnectionState.RECONNECTING ||
                                                    connectionState == ConnectionState.FIREWALL_LOCKED
                                                ) {
                                                    viewModel.stopVpn()
                                                } else {
                                                    val target = selectedProfile
                                                    if (target != null) {
                                                        requestVpnStart(target)
                                                    } else {
                                                        navigateTo(ServersRoute)
                                                    }
                                                }
                                            },
                                            onSmartAutoConnect = {
                                                viewModel.smartAutoConnect(
                                                    onReadyToConnect = { winner ->
                                                        Toast.makeText(
                                                            context,
                                                            strings.smartAutoConnectedToast(winner.name, winner.latencyMs ?: -1),
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        requestVpnStart(winner)
                                                    },
                                                    onNoServerFound = {
                                                        Toast.makeText(
                                                            context,
                                                            strings.smartAutoConnectNoServerToast,
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                        navigateTo(ServersRoute)
                                                    }
                                                )
                                            },
                                            onToggleTheme = {
                                                viewModel.setThemeMode(
                                                    if (isDarkTheme) AppThemeMode.LIGHT else AppThemeMode.DARK
                                                )
                                            },
                                            onToggleKillSwitch = { viewModel.setKillSwitchEnabled(it) },
                                            onToggleAutoReconnect = { viewModel.setAutoReconnectEnabled(it) },
                                            onToggleSmartFailover = { viewModel.setSmartFailoverEnabled(it) },
                                            onToggleSystemAlwaysOn = { viewModel.setSystemAlwaysOnShortcut(it) },
                                            onSwitchMultiNode = { viewModel.switchMultiVpnNode() },
                                            onRefreshExitIp = { viewModel.refreshExitIp() },
                                            onNavigateToServers = { navigateTo(ServersRoute) },
                                            onNavigateToSplitTunnel = { navigateTo(SplitTunnelRoute) },
                                            onDismissError = { viewModel.dismissError() }
                                        )
                                    }

                                    composable<ServersRoute> {
                                        ServersScreen(
                                            strings = strings,
                                            profiles = profiles,
                                            subscriptions = subscriptions,
                                            selectedProfileId = selectedProfile?.id ?: -1L,
                                            pingingIds = pingingIds,
                                            updatingSubscriptionIds = updatingSubIds,
                                            benchmarkState = benchmarkState,
                                            pingAllState = pingAllState,
                                            freeProvidersState = freeProvidersState,
                                            isFarsi = isFarsi,
                                            lowBatteryState = lowBatteryState,
                                            onSelectProfile = { id -> viewModel.selectProfile(id) },
                                            onSaveProfile = { profile -> viewModel.saveProfile(profile) },
                                            onDeleteProfile = { id -> viewModel.deleteProfile(id) },
                                            onToggleFavorite = { id, fav -> viewModel.toggleFavorite(id, fav) },
                                            onComputeCleanStats = { list -> viewModel.findDeadAndDuplicateStats(list) },
                                            onCleanDeadAndDuplicates = { cb -> viewModel.cleanDeadAndDuplicateServers(cb) },
                                            onPasteFromClipboard = { raw, cb ->
                                                viewModel.importFromClipboardText(raw, cb)
                                            },
                                            onAddOrUpdateSubscription = { existingId, name, url, cb ->
                                                viewModel.addOrUpdateSubscription(existingId, name, url, cb)
                                            },
                                            onRefreshSubscription = { sub, cb ->
                                                viewModel.refreshSubscription(sub, cb)
                                            },
                                            onRefreshAllSubscriptions = { cb ->
                                                viewModel.refreshAllSubscriptions(cb)
                                            },
                                            onDeleteSubscription = { subId ->
                                                viewModel.deleteSubscription(subId)
                                            },
                                            onExportConfigs = { passphrase -> viewModel.exportConfigs(passphrase) },
                                            onImportConfigsOrUris = { raw, passphrase, cb ->
                                                viewModel.importConfigsOrUris(raw, passphrase, cb)
                                            },
                                            onPingProfile = { profile -> viewModel.pingProfile(profile) },
                                            onPingAllServers = { list -> viewModel.pingAllServers(list) },
                                            onRefreshStaleLatencies = { list -> viewModel.refreshStaleLatenciesInBackground(list) },
                                            onStopPingAll = { viewModel.stopPingAll() },
                                            onBenchmarkProfile = { profile -> viewModel.benchmarkProfile(profile) },
                                            onTestAllServers = { list, autoConnect, cb ->
                                                viewModel.testAllServers(list, autoConnect, cb)
                                            },
                                            onStopBenchmarking = { viewModel.stopBenchmarking() },
                                            onAutoConnectFastest = { fastest ->
                                                requestVpnStart(fastest)
                                                navigateTo(DashboardRoute)
                                            },
                                            onConnectFreeProvider = { provider, noServerMsg, onReady ->
                                                viewModel.connectBestFromFreeProvider(provider, noServerMsg, onReady)
                                            },
                                            onCancelFreeProvider = { viewModel.cancelFreeProviderConnect() }
                                        )
                                    }

                                    composable<SplitTunnelRoute> {
                                        SplitTunnelScreen(
                                            strings = strings,
                                            tunnelMode = settings.tunnelMode,
                                            selectedPackages = settings.selectedPackages,
                                            installedApps = installedApps,
                                            appsLoading = appsLoading,
                                            appTraffic = appTraffic,
                                            isConnected = connectionState == ConnectionState.CONNECTED,
                                            onLoadApps = { viewModel.loadInstalledAppsIfNeeded() },
                                            onSelectTunnelMode = { viewModel.setTunnelMode(it) },
                                            onTogglePackage = { viewModel.toggleAppPackage(it) },
                                            onSetSelectedPackages = { viewModel.setSelectedPackages(it) },
                                            getAppIcon = { pkg -> viewModel.getAppIcon(pkg) },
                                            getAppLabel = { pkg -> viewModel.getAppLabel(pkg) }
                                        )
                                    }

                                    composable<SettingsRoute> {
                                        SettingsScreen(
                                            strings = strings,
                                            settings = settings,
                                            lowBatteryState = lowBatteryState,
                                            logs = logs,
                                            dataUsageSummary = dataUsageSummary,
                                            blockedAdsCount = blockedAdsCount,
                                            fcmToken = fcmToken,
                                            installationId = installationId,
                                            onSetBatteryMode = { viewModel.setBatteryMode(it) },
                                            onSetLowBatteryAutoMode = { viewModel.setLowBatteryAutoModeEnabled(it) },
                                            onSetDns = { p, s -> viewModel.setDnsServers(p, s) },
                                            onSetDnsPreset = { viewModel.setDnsPreset(it) },
                                            onSetAdBlockDnsEnabled = { viewModel.setAdBlockDnsEnabled(it) },
                                            onSetCustomDohUrl = { viewModel.setCustomDohUrl(it) },
                                            onSetBypassIranEnabled = { viewModel.setBypassIranEnabled(it) },
                                            onSetBypassLanEnabled = { viewModel.setBypassLanEnabled(it) },
                                            onSetCustomBypassDomains = { viewModel.setCustomBypassDomains(it) },
                                            onClearDataUsageHistory = { viewModel.clearDataUsageHistory() },
                                            onSetMtu = { viewModel.setMtu(it) },
                                            onSetAutoCheckIp = { viewModel.setAutoCheckExitIp(it) },
                                            onSetLanguage = { viewModel.setLanguage(it) },
                                            onSetThemeMode = { viewModel.setThemeMode(it) },
                                            onSetCrashlyticsEnabled = { viewModel.setCrashlyticsEnabled(it) },
                                            onCheckRemoteUpdates = { onDone ->
                                                viewModel.checkRemoteUpdateNow { state ->
                                                    onDone(state)
                                                }
                                            },
                                            onTestAnalytics = { onDone ->
                                                viewModel.testFirebaseAnalytics(onDone)
                                            },
                                            onClearLogs = { viewModel.clearLogs() }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Remote Config Force / Optional Update Prompt
                if (!showSplash && remoteUpdateState.hasUpdate) {
                    RemoteUpdatePromptDialog(
                        state = remoteUpdateState,
                        strings = strings,
                        onDownloadClick = {
                            AppAnalytics.logUpdateClicked(remoteUpdateState.forced)
                            val opened = runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(remoteUpdateState.apkUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                                true
                            }.getOrDefault(false)
                            if (!opened) {
                                Toast.makeText(context, strings.updateInvalidUrlMsg, Toast.LENGTH_SHORT).show()
                            } else if (!remoteUpdateState.forced) {
                                viewModel.dismissOptionalUpdate()
                            }
                        },
                        onDismissOptional = {
                            if (!remoteUpdateState.forced) {
                                viewModel.dismissOptionalUpdate()
                            }
                        }
                    )
                }

                // Baldr Branded Splash Overlay
                AnimatedVisibility(
                    visible = showSplash,
                    exit = fadeOut(animationSpec = tween(320))
                ) {
                    BaldrSplashOverlay(
                        bgDrawableRes = bgDrawableRes,
                        title = strings.appTitle,
                        subtitle = strings.splashSubtitle
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoteUpdatePromptDialog(
    state: RemoteUpdateState,
    strings: com.example.ui.i18n.AppStrings,
    onDownloadClick: () -> Unit,
    onDismissOptional: () -> Unit
) {
    val bodyText = state.updateMessage.ifBlank {
        if (state.forced) strings.updateForceDesc else strings.updateOptionalDesc
    }
    AlertDialog(
        onDismissRequest = {
            if (!state.forced) onDismissOptional()
        },
        properties = DialogProperties(
            dismissOnBackPress = !state.forced,
            dismissOnClickOutside = !state.forced
        ),
        icon = {
            Icon(
                imageVector = Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = if (state.forced) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = if (state.forced) strings.updateRequiredTitle else strings.updateAvailableTitle,
                modifier = Modifier.testTag("remote_update_dialog_title")
            )
        },
        text = {
            Text(text = bodyText)
        },
        confirmButton = {
            Button(
                onClick = onDownloadClick,
                modifier = Modifier.testTag("remote_update_download_button")
            ) {
                Text(strings.updateDownloadButton)
            }
        },
        dismissButton = if (!state.forced) {
            {
                TextButton(
                    onClick = onDismissOptional,
                    modifier = Modifier.testTag("remote_update_later_button")
                ) {
                    Text(strings.updateLaterButton)
                }
            }
        } else {
            null
        }
    )
}

@Composable
private fun BaldrSplashOverlay(
    bgDrawableRes: Int,
    title: String,
    subtitle: String
) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val logoScale by animateFloatAsState(
        targetValue = if (started) 1f else 0.88f,
        animationSpec = tween(420),
        label = "splash_logo_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("baldr_splash_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = bgDrawableRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(136.dp)
                    .scale(logoScale)
                    .clip(RoundedCornerShape(32.dp))
                    .background(LapisEmblemBg)
                    .border(
                        width = 1.5.dp,
                        color = BaldrGold.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(32.dp)
                    )
                    .padding(6.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_baldr_logo),
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
