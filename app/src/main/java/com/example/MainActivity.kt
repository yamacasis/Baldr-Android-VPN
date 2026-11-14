package com.example

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.example.ui.theme.NetShieldTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NetShieldApp()
        }
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
fun NetShieldApp(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val selectedProfile by viewModel.selectedProfile.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeTunnelProfile.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val trafficStats by viewModel.trafficStats.collectAsStateWithLifecycle()
    val exitInfo by viewModel.exitInfo.collectAsStateWithLifecycle()
    val lastError by viewModel.lastError.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val appsLoading by viewModel.appsLoading.collectAsStateWithLifecycle()
    val pingingIds by viewModel.pingingIds.collectAsStateWithLifecycle()
    val benchmarkState by viewModel.benchmarkState.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()

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

    LaunchedEffect(Unit) {
        if (showSplash) {
            delay(950L)
            showSplash = false
        }
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

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        NetShieldTheme(darkTheme = isDarkTheme, isFarsi = isFarsi) {
            val navController = rememberNavController()
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDest = backStackEntry?.destination

            val navItems = listOf(
                NavItem(DashboardRoute, strings.navDashboard, Icons.Filled.Shield, Icons.Outlined.Shield, "nav_dashboard"),
                NavItem(ServersRoute, strings.navServers, Icons.Filled.Dns, Icons.Outlined.Dns, "nav_servers"),
                NavItem(SplitTunnelRoute, strings.navSplitTunnel, Icons.Filled.SwapHoriz, Icons.Outlined.SwapHoriz, "nav_split"),
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

            val bgDrawableRes = if (isDarkTheme) R.drawable.bg_baldr_dark else R.drawable.bg_baldr_light

            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(id = bgDrawableRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val useRail = maxWidth >= 600.dp

                    Scaffold(
                        containerColor = Color.Transparent,
                        contentWindowInsets = WindowInsets.safeDrawing,
                        bottomBar = {
                            if (!useRail) {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                                ) {
                                    navItems.forEach { item ->
                                        val selected = currentDest?.hasRoute(item.route::class) == true
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = { navigateTo(item.route) },
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
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                                ) {
                                    navItems.forEach { item ->
                                        val selected = currentDest?.hasRoute(item.route::class) == true
                                        NavigationRailItem(
                                            selected = selected,
                                            onClick = { navigateTo(item.route) },
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
                                            exitInfo = exitInfo,
                                            settings = settings,
                                            isDarkTheme = isDarkTheme,
                                            lastError = lastError,
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
                                            onToggleTheme = {
                                                viewModel.setThemeMode(
                                                    if (isDarkTheme) AppThemeMode.LIGHT else AppThemeMode.DARK
                                                )
                                            },
                                            onToggleKillSwitch = { viewModel.setKillSwitchEnabled(it) },
                                            onToggleAutoReconnect = { viewModel.setAutoReconnectEnabled(it) },
                                            onToggleSystemAlwaysOn = { viewModel.setSystemAlwaysOnShortcut(it) },
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
                                            selectedProfileId = selectedProfile?.id ?: -1L,
                                            pingingIds = pingingIds,
                                            benchmarkState = benchmarkState,
                                            onSelectProfile = { id -> viewModel.selectProfile(id) },
                                            onSaveProfile = { profile -> viewModel.saveProfile(profile) },
                                            onDeleteProfile = { id -> viewModel.deleteProfile(id) },
                                            onExportConfigs = { passphrase -> viewModel.exportConfigs(passphrase) },
                                            onImportConfigsOrUris = { raw, passphrase, cb ->
                                                viewModel.importConfigsOrUris(raw, passphrase, cb)
                                            },
                                            onBenchmarkProfile = { profile -> viewModel.benchmarkProfile(profile) },
                                            onTestAllServers = { list, autoConnect, cb ->
                                                viewModel.testAllServers(list, autoConnect, cb)
                                            },
                                            onStopBenchmarking = { viewModel.stopBenchmarking() },
                                            onAutoConnectFastest = { fastest ->
                                                requestVpnStart(fastest)
                                                navigateTo(DashboardRoute)
                                            }
                                        )
                                    }

                                    composable<SplitTunnelRoute> {
                                        SplitTunnelScreen(
                                            strings = strings,
                                            tunnelMode = settings.tunnelMode,
                                            selectedPackages = settings.selectedPackages,
                                            installedApps = installedApps,
                                            appsLoading = appsLoading,
                                            isConnected = connectionState == ConnectionState.CONNECTED,
                                            onLoadApps = { viewModel.loadInstalledAppsIfNeeded() },
                                            onSelectTunnelMode = { viewModel.setTunnelMode(it) },
                                            onTogglePackage = { viewModel.toggleAppPackage(it) },
                                            onSetSelectedPackages = { viewModel.setSelectedPackages(it) },
                                            getAppIcon = { pkg -> viewModel.getAppIcon(pkg) }
                                        )
                                    }

                                    composable<SettingsRoute> {
                                        SettingsScreen(
                                            strings = strings,
                                            settings = settings,
                                            logs = logs,
                                            onSetBatteryMode = { viewModel.setBatteryMode(it) },
                                            onSetDns = { p, s -> viewModel.setDnsServers(p, s) },
                                            onSetMtu = { viewModel.setMtu(it) },
                                            onSetAutoCheckIp = { viewModel.setAutoCheckExitIp(it) },
                                            onSetLanguage = { viewModel.setLanguage(it) },
                                            onSetThemeMode = { viewModel.setThemeMode(it) },
                                            onClearLogs = { viewModel.clearLogs() }
                                        )
                                    }
                                }
                            }
                        }
                    }
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
            Image(
                painter = painterResource(id = R.drawable.ic_baldr_logo),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(128.dp)
                    .scale(logoScale)
                    .clip(RoundedCornerShape(30.dp))
            )
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
