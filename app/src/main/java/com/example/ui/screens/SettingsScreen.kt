package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppSettings
import com.example.model.AppLanguage
import com.example.model.AppThemeMode
import com.example.model.BatteryMode
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun SettingsScreen(
    strings: AppStrings,
    settings: AppSettings,
    logs: List<String>,
    fcmToken: String?,
    installationId: String?,
    onSetBatteryMode: (BatteryMode) -> Unit,
    onSetDns: (String, String) -> Unit,
    onSetMtu: (Int) -> Unit,
    onSetAutoCheckIp: (Boolean) -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetThemeMode: (AppThemeMode) -> Unit,
    onSetCrashlyticsEnabled: (Boolean) -> Unit,
    onCheckRemoteUpdates: (onDone: (Boolean) -> Unit) -> Unit,
    onTestAnalytics: (onDone: (Boolean) -> Unit) -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var dns1 by remember(settings.dnsPrimary) { mutableStateOf(settings.dnsPrimary) }
    var dns2 by remember(settings.dnsSecondary) { mutableStateOf(settings.dnsSecondary) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = strings.settingsTitle,
                style = MaterialTheme.typography.headlineMedium
            )

            // Battery Optimization Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.48f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.BatterySaver, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(strings.batterySectionTitle, style = MaterialTheme.typography.titleMedium)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSetBatteryMode(BatteryMode.ULTRA_LOW_POWER) }
                            .testTag("battery_ultra_low_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.batteryMode == BatteryMode.ULTRA_LOW_POWER,
                            onClick = { onSetBatteryMode(BatteryMode.ULTRA_LOW_POWER) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.batteryUltraLowTitle, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(strings.batteryUltraLowDesc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSetBatteryMode(BatteryMode.BALANCED) }
                            .testTag("battery_balanced_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.batteryMode == BatteryMode.BALANCED,
                            onClick = { onSetBatteryMode(BatteryMode.BALANCED) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.batteryBalancedTitle, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(strings.batteryBalancedDesc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("battery_settings_shortcut")
                    ) {
                        Text(strings.systemBatterySettings)
                    }
                }
            }

            // Network & DNS Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.48f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.NetworkCheck, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Text(strings.networkSectionTitle, style = MaterialTheme.typography.titleMedium)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = dns1,
                            onValueChange = {
                                dns1 = it
                                onSetDns(it, dns2)
                            },
                            label = { Text(strings.dnsPrimaryLabel) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = dns2,
                            onValueChange = {
                                dns2 = it
                                onSetDns(dns1, it)
                            },
                            label = { Text(strings.dnsSecondaryLabel) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(strings.mtuLabel, style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1500, 1420, 1280).forEach { m ->
                            FilterChip(
                                selected = settings.mtu == m,
                                onClick = { onSetMtu(m) },
                                label = { Text("$m") }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.autoCheckIpLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = settings.autoCheckExitIp,
                            onCheckedChange = onSetAutoCheckIp,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        )
                    }
                }
            }

            // Language Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.48f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Language, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Text(strings.languageSectionTitle, style = MaterialTheme.typography.titleMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = settings.language == AppLanguage.FARSI,
                            onClick = { onSetLanguage(AppLanguage.FARSI) },
                            label = { Text("فارسی (RTL)") },
                            modifier = Modifier.testTag("lang_farsi_chip")
                        )
                        FilterChip(
                            selected = settings.language == AppLanguage.ENGLISH,
                            onClick = { onSetLanguage(AppLanguage.ENGLISH) },
                            label = { Text("English (LTR)") },
                            modifier = Modifier.testTag("lang_english_chip")
                        )
                    }
                }
            }

            // Theme & Background Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.48f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.BrightnessMedium, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Text(strings.themeSectionTitle, style = MaterialTheme.typography.titleMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.themeMode == AppThemeMode.DARK,
                            onClick = { onSetThemeMode(AppThemeMode.DARK) },
                            label = { Text(strings.themeDark) },
                            modifier = Modifier.testTag("theme_dark_chip")
                        )
                        FilterChip(
                            selected = settings.themeMode == AppThemeMode.LIGHT,
                            onClick = { onSetThemeMode(AppThemeMode.LIGHT) },
                            label = { Text(strings.themeLight) },
                            modifier = Modifier.testTag("theme_light_chip")
                        )
                        FilterChip(
                            selected = settings.themeMode == AppThemeMode.SYSTEM,
                            onClick = { onSetThemeMode(AppThemeMode.SYSTEM) },
                            label = { Text(strings.themeSystem) },
                            modifier = Modifier.testTag("theme_system_chip")
                        )
                    }
                }
            }

            // Firebase Crashlytics Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.48f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(strings.crashlyticsSectionTitle, style = MaterialTheme.typography.titleMedium)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSetCrashlyticsEnabled(!settings.crashlyticsEnabled) }
                            .testTag("crashlytics_toggle_row"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.crashlyticsEnabledLabel,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = strings.crashlyticsEnabledDesc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.crashlyticsEnabled,
                            onCheckedChange = onSetCrashlyticsEnabled,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("crashlytics_toggle")
                        )
                    }
                }
            }

            // Firebase Services & Remote Update Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.48f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Text(strings.firebaseServicesSectionTitle, style = MaterialTheme.typography.titleMedium)
                    }

                    Button(
                        onClick = {
                            onCheckRemoteUpdates { hasUpdate ->
                                if (!hasUpdate) {
                                    Toast.makeText(context, strings.noUpdateAvailableToast, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("check_updates_button")
                    ) {
                        Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.checkUpdatesButton)
                    }

                    OutlinedButton(
                        onClick = {
                            onTestAnalytics { ok ->
                                Toast.makeText(
                                    context,
                                    if (ok) strings.analyticsTestSentToast else strings.analyticsTestFailedToast,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("test_analytics_button")
                    ) {
                        Icon(Icons.Filled.Analytics, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.testAnalyticsButton)
                    }

                    OutlinedButton(
                        onClick = {
                            if (installationId.isNullOrBlank() && fcmToken.isNullOrBlank()) {
                                Toast.makeText(context, strings.firebaseIdsUnavailableToast, Toast.LENGTH_SHORT).show()
                            } else {
                                val payload = buildString {
                                    if (!installationId.isNullOrBlank()) appendLine("FID: $installationId")
                                    if (!fcmToken.isNullOrBlank()) appendLine("FCM Token: $fcmToken")
                                }.trim()
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Baldr Firebase IDs", payload))
                                Toast.makeText(context, strings.firebaseIdsCopiedToast, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("copy_firebase_ids_button")
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.copyFirebaseIdsButton)
                    }
                }
            }

            // Diagnostics Log Viewer
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.48f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            Icon(Icons.Filled.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(strings.logsTitle, style = MaterialTheme.typography.titleMedium)
                        }
                        TextButton(onClick = onClearLogs) {
                            Text(strings.clearLogs)
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (logs.isEmpty()) {
                                Text(
                                    text = strings.noLogsYet,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                logs.takeLast(20).forEach { line ->
                                    Text(
                                        text = line,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
