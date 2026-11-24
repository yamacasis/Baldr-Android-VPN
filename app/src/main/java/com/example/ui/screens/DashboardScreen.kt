package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.ui.components.SpeedSparklineChart
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.AmberStatus
import com.example.ui.theme.BaldrGold
import com.example.ui.theme.CoralError
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LapisEmblemBg
import com.example.vpn.LowBatteryState
import com.example.vpn.formatBytes
import com.example.vpn.formatRate
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
    onToggleConnection: () -> Unit,
    onToggleTheme: () -> Unit,
    onToggleKillSwitch: (Boolean) -> Unit,
    onToggleAutoReconnect: (Boolean) -> Unit,
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
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

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
            ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
            ConnectionState.CONNECTING, ConnectionState.RECONNECTING, ConnectionState.DISCONNECTING -> AmberStatus
            ConnectionState.FIREWALL_LOCKED, ConnectionState.ERROR -> CoralError
            ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
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
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.48f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
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

                    // Connect Power Button with Lightweight Connected Effect
                    val isConnected = connectionState == ConnectionState.CONNECTED
                    val isActiveState = isConnected ||
                        connectionState == ConnectionState.CONNECTING ||
                        connectionState == ConnectionState.RECONNECTING

                    val effectStrength by animateFloatAsState(
                        targetValue = if (isConnected) 1f else 0f,
                        animationSpec = tween(420),
                        label = "connected_effect_strength"
                    )

                    val connectedSweep: Float
                    val connectedWave1: Float
                    val connectedWave2: Float
                    val pulseScale: Float

                    if (isActiveState && !isLowBatteryActive) {
                        val infiniteTransition = rememberInfiniteTransition(label = "vpn_active_fx")
                        val pScale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.07f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1600, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulse_scale"
                        )
                        val sweep by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(3600, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "ring_sweep"
                        )
                        val wave1 by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(2200, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "wave1"
                        )
                        pulseScale = pScale
                        connectedSweep = sweep
                        connectedWave1 = wave1
                        connectedWave2 = (wave1 + 0.5f) % 1f
                    } else {
                        pulseScale = 1f
                        connectedSweep = 0f
                        connectedWave1 = 0f
                        connectedWave2 = 0f
                    }

                    val primaryColor = MaterialTheme.colorScheme.primary
                    val secondaryLapis = MaterialTheme.colorScheme.secondary
                    val goldColor = BaldrGold

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        // Ultra-lightweight Canvas halo + staggered ripples + rotating orbital arc when connected
                        if (effectStrength > 0.01f || isActiveState) {
                            Canvas(
                                modifier = Modifier
                                    .size(182.dp)
                                    .testTag("connected_visual_effect")
                            ) {
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val baseRadius = 58.dp.toPx()
                                val maxRadius = size.minDimension / 2f

                                if (effectStrength > 0.01f) {
                                    // Soft radial aura blending Jade + Lapis Lazuli + Gold
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                primaryColor.copy(alpha = 0.24f * effectStrength),
                                                secondaryLapis.copy(alpha = 0.18f * effectStrength),
                                                goldColor.copy(alpha = 0.08f * effectStrength),
                                                Color.Transparent
                                            ),
                                            center = center,
                                            radius = maxRadius
                                        ),
                                        radius = maxRadius,
                                        center = center
                                    )

                                    // Two lightweight expanding shield rings (Jade + Lapis Lazuli)
                                    val r1 = baseRadius + (maxRadius - baseRadius) * connectedWave1
                                    val a1 = (1f - connectedWave1) * 0.42f * effectStrength
                                    drawCircle(
                                        color = primaryColor.copy(alpha = a1),
                                        radius = r1,
                                        center = center,
                                        style = Stroke(width = 2.dp.toPx())
                                    )

                                    val r2 = baseRadius + (maxRadius - baseRadius) * connectedWave2
                                    val a2 = (1f - connectedWave2) * 0.38f * effectStrength
                                    drawCircle(
                                        color = secondaryLapis.copy(alpha = a2),
                                        radius = r2,
                                        center = center,
                                        style = Stroke(width = 1.8.dp.toPx())
                                    )

                                    // Subtle rotating orbital arcs (Baldr Gold + Lapis Lazuli)
                                    val arcDiameter = baseRadius * 2.26f
                                    val topLeft = Offset(
                                        center.x - arcDiameter / 2f,
                                        center.y - arcDiameter / 2f
                                    )
                                    drawArc(
                                        color = goldColor.copy(alpha = 0.68f * effectStrength),
                                        startAngle = connectedSweep,
                                        sweepAngle = 64f,
                                        useCenter = false,
                                        topLeft = topLeft,
                                        size = Size(arcDiameter, arcDiameter),
                                        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                    drawArc(
                                        color = secondaryLapis.copy(alpha = 0.52f * effectStrength),
                                        startAngle = connectedSweep + 180f,
                                        sweepAngle = 48f,
                                        useCenter = false,
                                        topLeft = topLeft,
                                        size = Size(arcDiameter, arcDiameter),
                                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(142.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(statusColor.copy(alpha = 0.14f))
                        )
                        Surface(
                            shape = CircleShape,
                            color = when (connectionState) {
                                ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
                                ConnectionState.CONNECTING, ConnectionState.RECONNECTING, ConnectionState.DISCONNECTING -> AmberStatus
                                ConnectionState.FIREWALL_LOCKED -> CoralError
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                            },
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            tonalElevation = 0.dp,
                            shadowElevation = if (isConnected) 6.dp else 0.dp,
                            modifier = Modifier
                                .size(116.dp)
                                .clip(CircleShape)
                                .border(
                                    width = if (isConnected) 2.5.dp else 2.dp,
                                    color = if (isConnected) BaldrGold.copy(alpha = 0.85f) else statusColor.copy(alpha = 0.7f),
                                    shape = CircleShape
                                )
                                .clickable(onClick = onToggleConnection)
                                .testTag("vpn_connect_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (connectionState == ConnectionState.CONNECTING ||
                                    connectionState == ConnectionState.RECONNECTING ||
                                    connectionState == ConnectionState.DISCONNECTING
                                ) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(44.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.PowerSettingsNew,
                                        contentDescription = when (connectionState) {
                                            ConnectionState.CONNECTED -> strings.tapToDisconnect
                                            ConnectionState.FIREWALL_LOCKED -> strings.tapToUnlockFirewall
                                            else -> strings.tapToConnect
                                        },
                                        tint = if (connectionState == ConnectionState.CONNECTED ||
                                            connectionState == ConnectionState.FIREWALL_LOCKED
                                        ) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }
                        }
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

                        // Live Speed Chart directly below the Connect button
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dashboard_speed_chart")
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.ArrowDownward,
                                                contentDescription = strings.downloadSpeed,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = formatRate(trafficStats.downloadSpeedBps),
                                                fontFamily = JetBrainsMonoFamily,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.ArrowUpward,
                                                contentDescription = strings.uploadSpeed,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = formatRate(trafficStats.uploadSpeedBps),
                                                fontFamily = JetBrainsMonoFamily,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                    Text(
                                        text = durationText,
                                        fontFamily = JetBrainsMonoFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                SpeedSparklineChart(
                                    samples = trafficStats.speedHistory,
                                    modifier = Modifier.height(78.dp)
                                )
                            }
                        }

                        // Live Speed & Traffic right after the Speed Chart
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
                                    accent = MaterialTheme.colorScheme.primary,
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
                                    accent = MaterialTheme.colorScheme.primary,
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

                        // Active Server Selector Chip
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.26f)),
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
                                                LatencyTier.EXCELLENT, LatencyTier.GOOD -> MaterialTheme.colorScheme.primary
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
                        if (connectionState == ConnectionState.CONNECTED && multiTunnelStatus.isMultiMode) {
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
                                            statusAccent = MaterialTheme.colorScheme.primary,
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

            // Connection Guard: Firewall (Kill Switch), Auto-Reconnect & System Always-On VPN
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.48f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("protection_options_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GppGood,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = strings.protectionCardTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

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

            // Exit IP & Country Flag Card
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.48f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
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
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.48f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
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
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)),
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
