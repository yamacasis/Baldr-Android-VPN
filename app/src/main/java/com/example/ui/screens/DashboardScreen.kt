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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
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
import com.example.model.BatteryMode
import com.example.model.ConnectionState
import com.example.model.ExitNetworkInfo
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
    exitInfo: ExitNetworkInfo,
    settings: AppSettings,
    isDarkTheme: Boolean,
    lastError: String?,
    onToggleConnection: () -> Unit,
    onToggleTheme: () -> Unit,
    onToggleKillSwitch: (Boolean) -> Unit,
    onToggleAutoReconnect: (Boolean) -> Unit,
    onToggleSystemAlwaysOn: (Boolean) -> Unit,
    onRefreshExitIp: () -> Unit,
    onNavigateToServers: () -> Unit,
    onNavigateToSplitTunnel: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val displayProfile = activeProfile ?: selectedProfile
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.CONNECTED) {
            while (true) {
                nowMillis = System.currentTimeMillis()
                delay(1000L)
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
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
                            Image(
                                painter = painterResource(id = R.drawable.ic_baldr_logo),
                                contentDescription = strings.appTitle,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
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
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(50)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.BatteryChargingFull,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = if (settings.batteryMode == BatteryMode.ULTRA_LOW_POWER)
                                            strings.batteryLowPowerBadge
                                        else
                                            strings.batteryBalancedBadge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        color = statusColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(50),
                        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
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

                    if (isActiveState) {
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
                    val goldColor = BaldrGold

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        // Ultra-lightweight Canvas halo + staggered ripples + rotating accent arc when connected
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
                                    // Soft radial aura
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                primaryColor.copy(alpha = 0.28f * effectStrength),
                                                goldColor.copy(alpha = 0.14f * effectStrength),
                                                Color.Transparent
                                            ),
                                            center = center,
                                            radius = maxRadius
                                        ),
                                        radius = maxRadius,
                                        center = center
                                    )

                                    // Two lightweight expanding shield rings
                                    val r1 = baseRadius + (maxRadius - baseRadius) * connectedWave1
                                    val a1 = (1f - connectedWave1) * 0.42f * effectStrength
                                    drawCircle(
                                        color = primaryColor.copy(alpha = a1),
                                        radius = r1,
                                        center = center,
                                        style = Stroke(width = 2.dp.toPx())
                                    )

                                    val r2 = baseRadius + (maxRadius - baseRadius) * connectedWave2
                                    val a2 = (1f - connectedWave2) * 0.34f * effectStrength
                                    drawCircle(
                                        color = goldColor.copy(alpha = a2),
                                        radius = r2,
                                        center = center,
                                        style = Stroke(width = 1.6.dp.toPx())
                                    )

                                    // Subtle rotating orbital arc
                                    val arcDiameter = baseRadius * 2.26f
                                    val topLeft = Offset(
                                        center.x - arcDiameter / 2f,
                                        center.y - arcDiameter / 2f
                                    )
                                    drawArc(
                                        color = goldColor.copy(alpha = 0.65f * effectStrength),
                                        startAngle = connectedSweep,
                                        sweepAngle = 68f,
                                        useCenter = false,
                                        topLeft = topLeft,
                                        size = Size(arcDiameter, arcDiameter),
                                        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
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
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            tonalElevation = 8.dp,
                            shadowElevation = 12.dp,
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
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
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

                        // Active Server Selector Chip
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
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
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = displayProfile.protocol.shortBadge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

            // Two Home Options: Firewall (Kill Switch) & Auto-Reconnect
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                ),
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
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
                        Switch(
                            checked = settings.systemAlwaysOnShortcut,
                            onCheckedChange = { enabled ->
                                onToggleSystemAlwaysOn(enabled)
                                if (enabled) {
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
                                    }
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .semantics { contentDescription = strings.systemAlwaysOnVpn }
                                .testTag("system_vpn_settings_switch")
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
                }
            }

            // Exit IP & Country Flag Card
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                ),
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
                                    color = MaterialTheme.colorScheme.surfaceVariant,
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

            // Live Speed & Cumulative Data Card
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("traffic_stats_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
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
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = strings.liveSpeedTitle,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            text = durationText,
                            fontFamily = JetBrainsMonoFamily,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
            }

            // Quick Split Tunneling Summary Strip
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
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
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
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
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
