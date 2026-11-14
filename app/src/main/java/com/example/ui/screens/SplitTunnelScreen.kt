package com.example.ui.screens

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.model.InstalledAppInfo
import com.example.model.TunnelMode
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun SplitTunnelScreen(
    strings: AppStrings,
    tunnelMode: TunnelMode,
    selectedPackages: Set<String>,
    installedApps: List<InstalledAppInfo>,
    appsLoading: Boolean,
    isConnected: Boolean,
    onLoadApps: () -> Unit,
    onSelectTunnelMode: (TunnelMode) -> Unit,
    onTogglePackage: (String) -> Unit,
    onSetSelectedPackages: (Set<String>) -> Unit,
    getAppIcon: (String) -> Drawable?,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        onLoadApps()
    }

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

            if (isConnected) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
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

@Composable
private fun TunnelModeOptionCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            else
                MaterialTheme.colorScheme.surface
        ),
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
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
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
