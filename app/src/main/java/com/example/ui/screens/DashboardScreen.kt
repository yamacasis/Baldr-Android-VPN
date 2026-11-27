package com.example.ui.screens

import android.app.StatusBarManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AppSettings
import com.example.data.ExitIpChecker
import com.example.data.LatencyTier
import com.example.model.BatteryMode
import com.example.model.ConnectionState
import com.example.model.ExitNetworkInfo
import com.example.model.MultiTunnelStatus
import com.example.model.TrafficStatsSnapshot
import com.example.model.TunnelMode
import com.example.model.VpnProfile
import com.example.model.countryCodeToFlag
import com.example.ui.components.ConnectPowerButton
import com.example.ui.components.SpeedSparklineChart
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.AmberStatus
import com.example.ui.theme.BaldrGold
import com.example.ui.theme.ConnectedGreen
import com.example.ui.theme.ConnectingOrange
import com.example.ui.theme.CoralError
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LapisEmblemBg
import com.example.ui.theme.NavyShadow
import com.example.vpn.BaldrQuickTileService
import com.example.vpn.LowBatteryState
import com.example.vpn.formatBytes
import com.example.vpn.formatRate
import com.example.widget.BaldrVpnWidgetProvider
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(
    strings: AppStrings,
    connectionState: ConnectionState,
    selectedProfile: VpnProfile?,
    activeProfile: VpnProfile?,
    trafficStats: TrafficStatsSnapshot,
    multiTunnelStatus: MultiTunnelStatus = MultiTunnelStatus(),
    exitInfo: ExitNetworkInfo,
    settings: AppSettings,
    lowBatteryState: LowBatteryState = LowBatteryState(),
    isDarkTheme: Boolean,
    lastError: String?,
    blockedAdsCount: Int = 0,
    bypassedConnectionsCount: Int = 0,
    isSmartConnecting: Boolean = false,
    onToggleConnection: () -> Unit,
    onSmartAutoConnect: () -> Unit = {},
    onToggleTheme: () -> Unit,
    onToggleKillSwitch: (Boolean) -> Unit,
    onToggleAutoReconnect: (Boolean) -> Unit,
    onToggleSmartFailover: (Boolean) -> Unit = {},
    onToggleSystemAlwaysOn: (Boolean) -> Unit,
    onSwitchMultiNode: () -> Unit = {},
    onRefreshExitIp: () -> Unit,
    onNavigateToServers: () -> Unit,
    onNavigateToSplitTunnel: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val displayProfile = activeProfile ?: selectedProfile
    val isLowBatteryActive = lowBatteryState.isLowBatteryActive
    val isConnected = connectionState == ConnectionState.CONNECTED
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isServerAndSpeedExpanded by rememberSaveable { mutableStateOf(false) }
    var isProtectionExpanded by rememberSaveable { mutableStateOf(!isConnected) }

    LaunchedEffect(isConnected) {
        isProtectionExpanded = !isConnected
    }

    LaunchedEffect(connectionState, isLowBatteryActive) {
        if (connectionState == ConnectionState.CONNECTED) {
            val tickMs = if (isLowBatteryActive) 5_000L else 1_000L
            while (true) {
                nowMillis = System.currentTimeMillis()
                delay(tickMs)
            }
        }
    }

    val durationText = remember(connectionState, trafficStats.connectedSinceMillis, nowMillis) {
        if (connectionState == ConnectionState.CONNECTED && trafficStats.connectedSinceMillis > 0L) {
            val sec = ((nowMillis - trafficStats.connectedSinceMillis) / 1000L).coerceAtLeast(0L)
            val h = sec / 3600
            val m = (sec % 3600) / 60
            val s = sec % 60
            "%02d:%02d:%02d".format(h, m, s)
        } else {
            "00:00:00"
        }
    }

    val statusColor by animateColorAsState(
        targetValue = when (connectionState) {
            ConnectionState.CONNECTED -> ConnectedGreen
            ConnectionState.CONNECTING, ConnectionState.RECONNECTING, ConnectionState.DISCONNECTING -> ConnectingOrange
            ConnectionState.FIREWALL_LOCKED, ConnectionState.ERROR -> CoralError
            ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.primary
        },
        animationSpec = spring(),
        label = "status_color"
    )

    val statusText = when (connectionState) {
        ConnectionState.DISCONNECTED -> strings.statusDisconnected
        ConnectionState.CONNECTING -> strings.statusConnecting
        ConnectionState.CONNECTED -> strings.statusConnected
        ConnectionState.RECONNECTING -> strings.statusReconnecting
        ConnectionState.FIREWALL_LOCKED -> strings.statusFirewallLocked
        ConnectionState.DISCONNECTING -> strings.statusDisconnecting
        ConnectionState.ERROR -> strings.statusError
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Low-Battery Operation Banner (<20%)
            if (isLowBatteryActive) {
                Surface(
                    color = AmberStatus.copy(alpha = 0.16f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, AmberStatus.copy(alpha = 0.50f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("low_battery_active_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BatterySaver,
                            contentDescription = null,
                            tint = AmberStatus
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.lowBatteryBannerTitle(lowBatteryState.batteryPercent),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = AmberStatus
                            )
                            Text(
                                text = strings.lowBatteryBannerSubtitle(lowBatteryState.pausedBackgroundOperations),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Firewall Lock Banner
            if (connectionState == ConnectionState.FIREWALL_LOCKED) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("firewall_locked_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = strings.firewallLockedBanner,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Error banner if present
            if (!lastError.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = lastError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = onDismissError,
                            modifier = Modifier.testTag("dismiss_error_button")
                        ) {
                            Text("OK")
                        }
                    }
                }
            }

            // Hero Connection Card
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (isDarkTheme) 0.78f else 0.92f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isDarkTheme) 0.48f else 0.55f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = NavyShadow,
                        spotColor = NavyShadow
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Brand & Quick Theme Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(LapisEmblemBg)
                                    .border(
                                        width = 1.dp,
                                        color = BaldrGold.copy(alpha = 0.50f),
                                        shape = RoundedCornerShape(11.dp)
                                    )
                                    .padding(2.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_baldr_logo),
                                    contentDescription = strings.appTitle,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Text(
                                text = strings.appTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val pillAccent = if (isLowBatteryActive) AmberStatus else MaterialTheme.colorScheme.primary
                            Surface(
                                color = if (isLowBatteryActive) {
                                    AmberStatus.copy(alpha = 0.16f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                },
                                contentColor = if (isLowBatteryActive) AmberStatus else MaterialTheme.colorScheme.onSurfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (isLowBatteryActive) AmberStatus.copy(alpha = 0.45f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                ),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.testTag("dashboard_battery_badge")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isLowBatteryActive) Icons.Filled.BatterySaver else Icons.Filled.BatteryChargingFull,
                                        contentDescription = null,
                                        tint = pillAccent,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = when {
                                            isLowBatteryActive -> strings.lowBatteryActiveBadge(lowBatteryState.batteryPercent)
                                            settings.batteryMode == BatteryMode.ULTRA_LOW_POWER -> strings.batteryLowPowerBadge
                                            else -> strings.batteryBalancedBadge
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isLowBatteryActive) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isLowBatteryActive) AmberStatus else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = onToggleTheme,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("quick_theme_toggle")
                            ) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                    contentDescription = strings.switchThemeDesc,
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }

                    // Status Pill
                    Surface(
                        color = statusColor.copy(alpha = 0.14f),
                        contentColor = statusColor,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.48f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelMedium,
                                color = statusColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Connect Power Button:
                    // - Before connecting: full-size centered orb, NO speed chart.
                    // - When connected: compact green orb beside the live speed chart.
                    val buttonContentDesc = when (connectionState) {
                        ConnectionState.CONNECTED -> strings.tapToDisconnect
                        ConnectionState.FIREWALL_LOCKED -> strings.tapToUnlockFirewall
                        else -> strings.tapToConnect
                    }

                    if (isConnected) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ConnectPowerButton(
                                connectionState = connectionState,
                                isLowBatteryActive = isLowBatteryActive,
                                contentDescription = buttonContentDesc,
                                onToggleConnection = onToggleConnection,
                                compact = true
                            )

                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                border = BorderStroke(1.dp, ConnectedGreen.copy(alpha = 0.36f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("dashboard_speed_chart")
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.ArrowDownward,
                                                    contentDescription = strings.downloadSpeed,
                                                    tint = ConnectedGreen,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Text(
                                                    text = formatRate(trafficStats.downloadSpeedBps),
                                                    fontFamily = JetBrainsMonoFamily,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = ConnectedGreen,
                                                    maxLines = 1
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.ArrowUpward,
                                                    contentDescription = strings.uploadSpeed,
                                                    tint = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Text(
                                                    text = formatRate(trafficStats.uploadSpeedBps),
                                                    fontFamily = JetBrainsMonoFamily,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                        Text(
                                            text = durationText,
                                            fontFamily = JetBrainsMonoFamily,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }

                                    SpeedSparklineChart(
                                        samples = trafficStats.speedHistory,
                                        modifier = Modifier.height(64.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        ConnectPowerButton(
                            connectionState = connectionState,
                            isLowBatteryActive = isLowBatteryActive,
                            contentDescription = buttonContentDesc,
                            onToggleConnection = onToggleConnection,
                            compact = false
                        )
                    }

                    Text(
                        text = when {
                            connectionState == ConnectionState.FIREWALL_LOCKED -> strings.tapToUnlockFirewall
                            displayProfile == null -> strings.selectServerPrompt
                            connectionState == ConnectionState.CONNECTED ||
                                connectionState == ConnectionState.RECONNECTING -> strings.tapToDisconnect
                            else -> strings.tapToConnect
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 1-Tap Smart Auto-Connect CTA when disconnected
                    if (!isConnected && connectionState != ConnectionState.CONNECTING && connectionState != ConnectionState.RECONNECTING) {
                        FilledTonalButton(
                            onClick = onSmartAutoConnect,
                            enabled = !isSmartConnecting,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("smart_auto_connect_button")
                        ) {
                            if (isSmartConnecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(strings.smartAutoConnectingLabel, maxLines = 1)
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(strings.smartAutoConnectButton, maxLines = 1)
                            }
                        }
                    }

                    // Live AdBlock DNS & Smart Routing Bypass status badges when connected
                    if (isConnected && (settings.adBlockDnsEnabled || settings.bypassIranEnabled || settings.bypassLanEnabled)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (settings.adBlockDnsEnabled) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.testTag("dashboard_adblock_badge")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Block,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = strings.adsBlockedLiveBadge(blockedAdsCount),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                            if (settings.bypassIranEnabled || settings.bypassLanEnabled) {
                                Surface(
                                    color = ConnectedGreen.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, ConnectedGreen.copy(alpha = 0.35f)),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.testTag("dashboard_bypass_badge")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.AltRoute,
                                            contentDescription = null,
                                            tint = ConnectedGreen,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = strings.bypassedLiveBadge(bypassedConnectionsCount),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = ConnectedGreen
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Collapsible Server Info & Download/Upload Speed Details (collapsed by default; tap to expand)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        border = BorderStroke(
                            1.dp,
                            if (isConnected) ConnectedGreen.copy(alpha = 0.34f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.40f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { isServerAndSpeedExpanded = !isServerAndSpeedExpanded }
                            .minimumInteractiveComponentSize()
                            .testTag("server_speed_collapse_header")
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 11.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Dns,
                                    contentDescription = null,
                                    tint = if (isConnected) ConnectedGreen else MaterialTheme.colorScheme.primary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = displayProfile?.name ?: strings.noProfileSelected,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isConnected) {
                                            "↓ ${formatRate(trafficStats.downloadSpeedBps)} · ↑ ${formatRate(trafficStats.uploadSpeedBps)}"
                                        } else {
                                            strings.serverSpeedDetailsTitle
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (displayProfile != null) {
                                    Surface(
                                        color = if (isConnected) {
                                            ConnectedGreen.copy(alpha = 0.16f)
                                        } else {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = displayProfile.protocol.shortBadge,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isConnected) ConnectedGreen else MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = if (isServerAndSpeedExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = strings.serverSpeedDetailsTitle,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = isServerAndSpeedExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Live Speed & Traffic tiles
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("traffic_stats_card"),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    StatTile(
                                        label = strings.downloadSpeed,
                                        value = formatRate(trafficStats.downloadSpeedBps),
                                        icon = Icons.Filled.ArrowDownward,
                                        accent = if (isConnected) ConnectedGreen else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatTile(
                                        label = strings.uploadSpeed,
                                        value = formatRate(trafficStats.uploadSpeedBps),
                                        icon = Icons.Filled.ArrowUpward,
                                        accent = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    StatTile(
                                        label = strings.totalDownloaded,
                                        value = formatBytes(trafficStats.totalDownloadedBytes),
                                        icon = Icons.Filled.Shield,
                                        accent = if (isConnected) ConnectedGreen else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatTile(
                                        label = strings.totalUploaded,
                                        value = formatBytes(trafficStats.totalUploadedBytes),
                                        icon = Icons.Filled.Lock,
                                        accent = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Active Server Selector Chip (tap to switch server)
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.40f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(onClick = onNavigateToServers)
                                    .testTag("active_server_selector")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Dns,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Column {
                                            Text(
                                                text = displayProfile?.name ?: strings.noProfileSelected,
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (displayProfile != null) {
                                                Text(
                                                    text = "${displayProfile.host}:${displayProfile.port}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    if (displayProfile != null) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            val ms = displayProfile.latencyMs
                                            if (ms != null) {
                                                val tier = ExitIpChecker.classifyLatency(ms)
                                                val pingColor = when (tier) {
                                                    LatencyTier.EXCELLENT, LatencyTier.GOOD -> ConnectedGreen
                                                    LatencyTier.FAIR -> AmberStatus
                                                    else -> CoralError
                                                }
                                                Surface(
                                                    color = pingColor.copy(alpha = 0.14f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.testTag("dashboard_server_latency")
                                                ) {
                                                    Text(
                                                        text = if (ms > 0) strings.latencyMs(ms) else strings.pingTimeoutLabel,
                                                        fontFamily = JetBrainsMonoFamily,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = pingColor,
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                            Surface(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = displayProfile.protocol.shortBadge,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Dual-VPN Active + Standby Live Control Strip (when connected via Multi-Mode)
                            if (isConnected && multiTunnelStatus.isMultiMode) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.28f),
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.42f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("dual_vpn_status_card")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Hub,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.tertiary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = strings.dualVpnTitle,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (multiTunnelStatus.switchCount > 0) {
                                                    Text(
                                                        text = "• ${strings.multiTunnelSwitchesCount(multiTunnelStatus.switchCount)}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                            if (!multiTunnelStatus.standbyNodeName.isNullOrBlank()) {
                                                FilledTonalButton(
                                                    onClick = onSwitchMultiNode,
                                                    modifier = Modifier
                                                        .minimumInteractiveComponentSize()
                                                        .testTag("switch_multi_vpn_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.SwapHoriz,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.size(4.dp))
                                                    Text(
                                                        text = strings.switchVpnNowButton,
                                                        style = MaterialTheme.typography.labelMedium
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            DualVpnNodePill(
                                                roleLabel = strings.dualVpnActiveLabel,
                                                nodeName = multiTunnelStatus.activeNodeName.ifBlank { "-" },
                                                badge = multiTunnelStatus.activeNodeBadge,
                                                latencyMs = multiTunnelStatus.activeNodeLatencyMs,
                                                statusAccent = ConnectedGreen,
                                                statusBadgeText = "${multiTunnelStatus.healthyCount}/${multiTunnelStatus.totalCount}",
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (!multiTunnelStatus.standbyNodeName.isNullOrBlank()) {
                                                DualVpnNodePill(
                                                    roleLabel = strings.dualVpnStandbyLabel,
                                                    nodeName = multiTunnelStatus.standbyNodeName,
                                                    badge = multiTunnelStatus.standbyNodeBadge.orEmpty(),
                                                    latencyMs = multiTunnelStatus.standbyNodeLatencyMs,
                                                    statusAccent = if (multiTunnelStatus.standbyReady) MaterialTheme.colorScheme.secondary else AmberStatus,
                                                    statusBadgeText = if (multiTunnelStatus.standbyReady) strings.dualVpnReadyLabel else strings.dualVpnWarmingLabel,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Connection Guard: auto-collapses after VPN connects, tap header to expand/collapse
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (isDarkTheme) 0.78f else 0.92f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isDarkTheme) 0.45f else 0.52f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(22.dp),
                        ambientColor = NavyShadow,
                        spotColor = NavyShadow
                    )
                    .testTag("protection_options_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isProtectionExpanded) 12.dp else 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { isProtectionExpanded = !isProtectionExpanded }
                            .minimumInteractiveComponentSize()
                            .testTag("protection_card_header"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GppGood,
                                contentDescription = null,
                                tint = if (isConnected) ConnectedGreen else MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = strings.protectionCardTitle,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = if (isProtectionExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = strings.protectionCardTitle,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(
                        visible = isProtectionExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Option 1: Firewall (Kill Switch)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onToggleKillSwitch(!settings.killSwitchEnabled) }
                                    .padding(vertical = 4.dp)
                                    .testTag("kill_switch_row"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Security,
                                    contentDescription = null,
                                    tint = if (settings.killSwitchEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = strings.killSwitchTitle,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = strings.killSwitchDesc,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = settings.killSwitchEnabled,
                                    onCheckedChange = onToggleKillSwitch,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("kill_switch_toggle")
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))

                            // Option 2: Auto-Reconnect
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onToggleAutoReconnect(!settings.autoReconnectEnabled) }
                                    .padding(vertical = 4.dp)
                                    .testTag("auto_reconnect_row"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Autorenew,
                                    contentDescription = null,
                                    tint = if (settings.autoReconnectEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = strings.autoReconnectTitle,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = strings.autoReconnectDesc,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = settings.autoReconnectEnabled,
                                    onCheckedChange = onToggleAutoReconnect,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("auto_reconnect_toggle")
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))

                            // Option 3: Smart Auto-Failover
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onToggleSmartFailover(!settings.smartFailoverEnabled) }
                                    .padding(vertical = 4.dp)
                                    .testTag("smart_failover_row"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (settings.smartFailoverEnabled) ConnectedGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = strings.smartFailoverTitle,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = strings.smartFailoverDesc,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = settings.smartFailoverEnabled,
                                    onCheckedChange = onToggleSmartFailover,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("smart_failover_toggle")
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))

                            // Clearly labeled shortcut for Android OS Always-On VPN settings
                            OutlinedButton(
                                onClick = {
                                    onToggleSystemAlwaysOn(true)
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .semantics { contentDescription = strings.systemAlwaysOnVpnDesc }
                                    .testTag("system_vpn_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = strings.systemAlwaysOnVpn,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }

            // Exit IP & Country Flag Card
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (isDarkTheme) 0.78f else 0.92f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isDarkTheme) 0.45f else 0.52f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(22.dp),
                        ambientColor = NavyShadow,
                        spotColor = NavyShadow
                    )
                    .testTag("exit_ip_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = strings.exitLocationTitle,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        IconButton(
                            onClick = onRefreshExitIp,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("refresh_exit_ip_button")
                        ) {
                            if (exitInfo.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = strings.refreshIp
                                )
                            }
                        }
                    }

                    when {
                        exitInfo.isLoading -> {
                            Text(
                                text = strings.exitIpLoading,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        exitInfo.ipAddress.isNotEmpty() -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(60.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = exitInfo.flagEmoji,
                                            fontSize = 32.sp
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = exitInfo.ipAddress,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    val locationLine = listOf(exitInfo.countryName, exitInfo.city)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" • ")
                                    if (locationLine.isNotBlank()) {
                                        Text(
                                            text = locationLine,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    if (exitInfo.isp.isNotBlank()) {
                                        Text(
                                            text = "${strings.ispLabel}: ${exitInfo.isp}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            val hintCountry = displayProfile?.lastCountryCode?.takeIf { it.isNotBlank() }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = if (hintCountry != null) countryCodeToFlag(hintCountry) else "\uD83C\uDF10",
                                    fontSize = 24.sp
                                )
                                Text(
                                    text = exitInfo.errorMessage ?: strings.exitIpPlaceholder,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Quick Split Tunneling Summary Strip
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = if (isDarkTheme) 0.78f else 0.92f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isDarkTheme) 0.45f else 0.52f)),
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 6.dp,
                        shape = RoundedCornerShape(18.dp),
                        ambientColor = NavyShadow,
                        spotColor = NavyShadow
                    )
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onNavigateToSplitTunnel)
                    .testTag("split_tunnel_summary_card")
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SwapHoriz,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Column {
                            Text(
                                text = strings.splitTunnelTitle,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = when (settings.tunnelMode) {
                                    TunnelMode.FULL_SYSTEM -> strings.tunnelSummaryFull
                                    TunnelMode.SPLIT_INCLUDE -> strings.tunnelSummaryInclude(settings.selectedPackages.size)
                                    TunnelMode.SPLIT_EXCLUDE -> strings.tunnelSummaryExclude(settings.selectedPackages.size)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    AssistChip(
                        onClick = onNavigateToSplitTunnel,
                        label = { Text("${trafficStats.activeStreams} ${strings.activeStreams}") }
                    )
                }
            }

            // Quick Access Shortcuts: 1-Tap Home Screen Widget & Control Center (Quick Settings) Tile
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_quick_shortcuts_row"),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = if (isDarkTheme) 0.78f else 0.92f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isDarkTheme) 0.45f else 0.52f)),
                    modifier = Modifier
                        .weight(1f)
                        .shadow(
                            elevation = 6.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = NavyShadow,
                            spotColor = NavyShadow
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            val requested = BaldrVpnWidgetProvider.requestPinWidget(context)
                            if (!requested) {
                                Toast.makeText(context, strings.pinWidgetNotSupportedToast, Toast.LENGTH_LONG).show()
                            }
                        }
                        .minimumInteractiveComponentSize()
                        .testTag("dashboard_pin_widget_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddToHomeScreen,
                            contentDescription = strings.quickWidgetShortButton,
                            tint = if (isConnected) ConnectedGreen else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(
                            text = strings.quickWidgetShortButton,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = if (isDarkTheme) 0.78f else 0.92f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isDarkTheme) 0.45f else 0.52f)),
                    modifier = Modifier
                        .weight(1f)
                        .shadow(
                            elevation = 6.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = NavyShadow,
                            spotColor = NavyShadow
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            if (BaldrQuickTileService.canRequestAddTile()) {
                                BaldrQuickTileService.requestAddTileToPanel(context) { resultCode ->
                                    val msg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        (resultCode == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                                            resultCode == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED)
                                    ) {
                                        strings.quickTileAddedToast
                                    } else {
                                        strings.quickTileManualHintToast
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(context, strings.quickTileManualHintToast, Toast.LENGTH_LONG).show()
                            }
                        }
                        .minimumInteractiveComponentSize()
                        .testTag("dashboard_add_tile_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = strings.controlCenterShortButton,
                            tint = if (isConnected) ConnectedGreen else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(
                            text = strings.controlCenterShortButton,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.36f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DualVpnNodePill(
    roleLabel: String,
    nodeName: String,
    badge: String,
    latencyMs: Int?,
    statusAccent: Color,
    statusBadgeText: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.56f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, statusAccent.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(statusAccent)
                    )
                    Text(
                        text = roleLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = statusAccent
                    )
                }
                Text(
                    text = statusBadgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = nodeName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (badge.isNotBlank()) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (latencyMs != null && latencyMs > 0) {
                    Text(
                        text = "${latencyMs}ms",
                        fontFamily = JetBrainsMonoFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusAccent
                    )
                }
            }
        }
    }
}
