package com.example.ui.screens

import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.model.AppTrafficInfo
import com.example.model.InstalledAppInfo
import com.example.model.TunnelMode
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.CoralError
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.vpn.formatBytes

private enum class TrafficSubTab { LIVE, RULES }
private enum class TrafficStatusFilter { ALL, PASSED, FAILED }

@Composable
fun SplitTunnelScreen(
    strings: AppStrings,
    tunnelMode: TunnelMode,
    selectedPackages: Set<String>,
    installedApps: List<InstalledAppInfo>,
    appsLoading: Boolean,
    appTraffic: List<AppTrafficInfo>,
    isConnected: Boolean,
    onLoadApps: () -> Unit,
    onSelectTunnelMode: (TunnelMode) -> Unit,
    onTogglePackage: (String) -> Unit,
    onSetSelectedPackages: (Set<String>) -> Unit,
    getAppIcon: (String) -> Drawable?,
    getAppLabel: (String) -> String,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        onLoadApps()
    }

    var activeTab by rememberSaveable { mutableStateOf(TrafficSubTab.LIVE) }
    var trafficFilter by remember { mutableStateOf(TrafficStatusFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }

    val filteredApps = remember(installedApps, searchQuery, showSystemApps) {
        installedApps.filter { app ->
            (showSystemApps || !app.isSystemApp) &&
                (searchQuery.isBlank() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true))
        }
    }

    val filteredTraffic = remember(appTraffic, trafficFilter) {
        when (trafficFilter) {
            TrafficStatusFilter.ALL -> appTraffic
            TrafficStatusFilter.PASSED -> appTraffic.filter { it.passedConnections > 0 || it.bytesReceived + it.bytesSent > 0L }
            TrafficStatusFilter.FAILED -> appTraffic.filter { it.failedConnections > 0 }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
        ) {
            Text(
                text = strings.splitTunnelTitle,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )

            // Segmented Switcher: Live Traffic | Tunnel Rules
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SubTabPill(
                        label = strings.trafficTabLive,
                        icon = Icons.Filled.SwapVert,
                        selected = activeTab == TrafficSubTab.LIVE,
                        onClick = { activeTab = TrafficSubTab.LIVE },
                        testTag = "traffic_subtab_live",
                        modifier = Modifier.weight(1f)
                    )
                    SubTabPill(
                        label = strings.trafficTabRules,
                        icon = Icons.Filled.Tune,
                        selected = activeTab == TrafficSubTab.RULES,
                        onClick = { activeTab = TrafficSubTab.RULES },
                        testTag = "traffic_subtab_rules",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (activeTab == TrafficSubTab.LIVE) {
                // Live Per-App VPN Traffic Tab
                val failedAppsCount = remember(appTraffic) {
                    appTraffic.count { it.failedConnections > 0 }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = trafficFilter == TrafficStatusFilter.ALL,
                            onClick = { trafficFilter = TrafficStatusFilter.ALL },
                            label = { Text(strings.trafficFilterAll) },
                            modifier = Modifier.testTag("traffic_filter_all")
                        )
                        FilterChip(
                            selected = trafficFilter == TrafficStatusFilter.PASSED,
                            onClick = { trafficFilter = TrafficStatusFilter.PASSED },
                            label = { Text(strings.trafficFilterPassed) },
                            modifier = Modifier.testTag("traffic_filter_passed")
                        )
                        FilterChip(
                            selected = trafficFilter == TrafficStatusFilter.FAILED,
                            onClick = { trafficFilter = TrafficStatusFilter.FAILED },
                            label = { Text(strings.trafficFilterFailed) },
                            modifier = Modifier.testTag("traffic_filter_failed")
                        )
                    }
                }

                if (appTraffic.isNotEmpty()) {
                    Text(
                        text = strings.trafficSummaryCounts(appTraffic.size, failedAppsCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
                    )
                }

                if (filteredTraffic.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SwapVert,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = if (isConnected) strings.trafficEmptyWaiting else strings.trafficEmptyDisconnected,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("live_app_traffic_list"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(filteredTraffic, key = { it.packageName }) { item ->
                            AppTrafficMiniRow(
                                item = item,
                                strings = strings,
                                getAppIcon = getAppIcon,
                                getAppLabel = getAppLabel
                            )
                        }
                    }
                }
            } else {
                // Split Tunnel Rules Sub-Tab
                if (isConnected) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.52f),
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = strings.reconnectToApply,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                // Tunnel Mode Cards
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TunnelModeOptionCard(
                        title = strings.splitModeFull,
                        description = strings.splitModeFullDesc,
                        selected = tunnelMode == TunnelMode.FULL_SYSTEM,
                        onClick = { onSelectTunnelMode(TunnelMode.FULL_SYSTEM) },
                        testTag = "tunnel_mode_full"
                    )
                    TunnelModeOptionCard(
                        title = strings.splitModeInclude,
                        description = strings.splitModeIncludeDesc,
                        selected = tunnelMode == TunnelMode.SPLIT_INCLUDE,
                        onClick = { onSelectTunnelMode(TunnelMode.SPLIT_INCLUDE) },
                        testTag = "tunnel_mode_include"
                    )
                    TunnelModeOptionCard(
                        title = strings.splitModeExclude,
                        description = strings.splitModeExcludeDesc,
                        selected = tunnelMode == TunnelMode.SPLIT_EXCLUDE,
                        onClick = { onSelectTunnelMode(TunnelMode.SPLIT_EXCLUDE) },
                        testTag = "tunnel_mode_exclude"
                    )
                }

                if (tunnelMode != TunnelMode.FULL_SYSTEM) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(strings.searchAppsHint) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("search_apps_input")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = showSystemApps,
                            onClick = { showSystemApps = !showSystemApps },
                            label = { Text(strings.showSystemApps) }
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = strings.appsSelectedCount(selectedPackages.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(
                                onClick = {
                                    onSetSelectedPackages(filteredApps.map { it.packageName }.toSet())
                                },
                                modifier = Modifier.testTag("select_all_apps_button")
                            ) {
                                Text(strings.selectAll)
                            }
                            TextButton(
                                onClick = { onSetSelectedPackages(emptySet()) },
                                modifier = Modifier.testTag("clear_all_apps_button")
                            ) {
                                Text(strings.clearAll)
                            }
                        }
                    }

                    if (appsLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredApps, key = { it.packageName }) { app ->
                                val isChecked = selectedPackages.contains(app.packageName)
                                AppSplitRow(
                                    app = app,
                                    isChecked = isChecked,
                                    getAppIcon = getAppIcon,
                                    onToggle = { onTogglePackage(app.packageName) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubTabPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f) else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AppTrafficMiniRow(
    item: AppTrafficInfo,
    strings: AppStrings,
    getAppIcon: (String) -> Drawable?,
    getAppLabel: (String) -> String
) {
    val isSystemPkg = item.packageName.isBlank() || item.packageName == "system.network"
    val appName = remember(item.packageName) {
        if (isSystemPkg) strings.trafficSystemAppLabel
        else getAppLabel(item.packageName).ifBlank { item.packageName }
    }
    val iconBitmap = remember(item.packageName) {
        if (isSystemPkg) null
        else runCatching { getAppIcon(item.packageName)?.toBitmap(64, 64)?.asImageBitmap() }.getOrNull()
    }

    val hasFailed = item.failedConnections > 0
    val hasPassed = item.passedConnections > 0 || (item.bytesReceived + item.bytesSent) > 0L

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.44f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            1.dp,
            if (hasFailed && !hasPassed) CoralError.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("traffic_row_${item.packageName}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = appName,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(7.dp))
                )
            } else {
                Icon(
                    imageVector = if (isSystemPkg) Icons.Filled.Dns else Icons.Filled.Apps,
                    contentDescription = appName,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "↓ ${formatBytes(item.bytesReceived)}  ↑ ${formatBytes(item.bytesSent)}",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (hasPassed) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = strings.trafficStatusPassed,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "${strings.trafficStatusPassed} ${item.passedConnections}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                if (hasFailed) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f),
                        contentColor = CoralError
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = strings.trafficStatusFailed,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "${strings.trafficStatusFailed} ${item.failedConnections}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TunnelModeOptionCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
            else
                MaterialTheme.colorScheme.surface.copy(alpha = 0.48f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(if (selected) 1.5.dp else 1.dp, border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                modifier = Modifier.minimumInteractiveComponentSize()
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AppSplitRow(
    app: InstalledAppInfo,
    isChecked: Boolean,
    getAppIcon: (String) -> Drawable?,
    onToggle: () -> Unit
) {
    val iconBitmap = remember(app.packageName) {
        runCatching { getAppIcon(app.packageName)?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.46f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.26f)),
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .testTag("app_row_${app.packageName}")
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = app.appName,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Apps,
                    contentDescription = app.appName,
                    modifier = Modifier.size(40.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    fontFamily = JetBrainsMonoFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = isChecked,
                onCheckedChange = { onToggle() },
                modifier = Modifier.minimumInteractiveComponentSize()
            )
        }
    }
}
