package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ConfigBackupManager
import com.example.data.UriConfigParser
import com.example.model.LoadBalanceStrategy
import com.example.model.ProtocolGroup
import com.example.model.TransportType
import com.example.model.VpnProfile
import com.example.model.VpnProtocol
import com.example.model.countryCodeToFlag
import com.example.ui.BackupImportStatus
import com.example.ui.BenchmarkUiState
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.AmberStatus
import com.example.ui.theme.CoralError
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.vpn.formatRate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(
    strings: AppStrings,
    profiles: List<VpnProfile>,
    selectedProfileId: Long,
    pingingIds: Set<Long>,
    benchmarkState: BenchmarkUiState,
    onSelectProfile: (Long) -> Unit,
    onSaveProfile: (VpnProfile) -> Unit,
    onDeleteProfile: (Long) -> Unit,
    onExportConfigs: (String) -> String,
    onImportConfigsOrUris: (String, String, (BackupImportStatus) -> Unit) -> Unit,
    onBenchmarkProfile: (VpnProfile) -> Unit,
    onTestAllServers: (List<VpnProfile>, Boolean, (VpnProfile) -> Unit) -> Unit,
    onStopBenchmarking: () -> Unit,
    onAutoConnectFastest: (VpnProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf<ProtocolGroup?>(null) }
    var editingProfile by remember { mutableStateOf<VpnProfile?>(null) }
    var defaultNewProtocol by remember { mutableStateOf(VpnProtocol.VLESS) }
    var showEditorSheet by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showTestDialog by remember { mutableStateOf(false) }
    var autoConnectFastestChecked by remember { mutableStateOf(true) }
    var qrProfile by remember { mutableStateOf<VpnProfile?>(null) }

    val filteredProfiles = remember(profiles, selectedFilter) {
        if (selectedFilter == null) profiles
        else profiles.filter { it.protocol.group == selectedFilter }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        editingProfile = null
                        defaultNewProtocol = VpnProtocol.LOAD_BALANCE
                        showEditorSheet = true
                    },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.testTag("add_load_balance_fab")
                ) {
                    Icon(Icons.Filled.Hub, contentDescription = strings.createLoadBalance)
                }
                FloatingActionButton(
                    onClick = { showImportDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.testTag("import_uri_fab")
                ) {
                    Icon(Icons.Filled.Link, contentDescription = strings.importFromLink)
                }
                FloatingActionButton(
                    onClick = {
                        editingProfile = null
                        defaultNewProtocol = VpnProtocol.VLESS
                        showEditorSheet = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_server_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = strings.addServer)
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize()
            ) {
                // Header + Test All Connections Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.serversTitle,
                        style = MaterialTheme.typography.headlineMedium
                    )

                    if (filteredProfiles.isNotEmpty()) {
                        if (benchmarkState.isTestingAll) {
                            FilledTonalButton(
                                onClick = onStopBenchmarking,
                                modifier = Modifier.testTag("stop_test_all_button")
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.stopTest)
                            }
                        } else {
                            FilledTonalButton(
                                onClick = { showTestDialog = true },
                                modifier = Modifier.testTag("test_all_servers_button")
                            ) {
                                Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.testAllConnections)
                            }
                        }
                    }
                }

                // Export & Import Config Backup Action Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (profiles.isEmpty()) {
                                Toast.makeText(context, strings.noServersToExportMsg, Toast.LENGTH_SHORT).show()
                            } else {
                                showExportDialog = true
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_configs_button")
                    ) {
                        Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.exportConfigs, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = { showImportDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_configs_button")
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.importConfigs, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                // Live progress card when running Test All Servers
                if (benchmarkState.isTestingAll) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .testTag("benchmark_progress_banner")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = strings.testingProgress(benchmarkState.completedCount, benchmarkState.totalCount),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            val progress = if (benchmarkState.totalCount > 0) {
                                benchmarkState.completedCount.toFloat() / benchmarkState.totalCount.toFloat()
                            } else 0f
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Protocol filter chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == null,
                        onClick = { selectedFilter = null },
                        label = { Text(strings.filterAll) },
                        modifier = Modifier.testTag("filter_all_chip")
                    )
                    FilterChip(
                        selected = selectedFilter == ProtocolGroup.V2RAY,
                        onClick = { selectedFilter = ProtocolGroup.V2RAY },
                        label = { Text("V2Ray / Xray") },
                        modifier = Modifier.testTag("filter_v2ray_chip")
                    )
                    FilterChip(
                        selected = selectedFilter == ProtocolGroup.SSH,
                        onClick = { selectedFilter = ProtocolGroup.SSH },
                        label = { Text("SSH") },
                        modifier = Modifier.testTag("filter_ssh_chip")
                    )
                    FilterChip(
                        selected = selectedFilter == ProtocolGroup.PROXY,
                        onClick = { selectedFilter = ProtocolGroup.PROXY },
                        label = { Text("SOCKS / HTTP") },
                        modifier = Modifier.testTag("filter_proxy_chip")
                    )
                    FilterChip(
                        selected = selectedFilter == ProtocolGroup.MULTI,
                        onClick = { selectedFilter = ProtocolGroup.MULTI },
                        label = { Text(strings.filterMulti) },
                        modifier = Modifier.testTag("filter_multi_chip")
                    )
                }

                if (filteredProfiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Dns,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                text = strings.noServersTitle,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = strings.noServersSubtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { showImportDialog = true },
                                    modifier = Modifier.testTag("empty_import_button")
                                ) {
                                    Text(strings.importFromLink)
                                }
                                Button(
                                    onClick = {
                                        editingProfile = null
                                        defaultNewProtocol = VpnProtocol.VLESS
                                        showEditorSheet = true
                                    },
                                    modifier = Modifier.testTag("empty_add_button")
                                ) {
                                    Text(strings.addServer)
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 172.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredProfiles, key = { it.id }) { profile ->
                            val isSelected = profile.id == selectedProfileId
                            val isTesting = pingingIds.contains(profile.id)
                            ServerProfileCard(
                                strings = strings,
                                profile = profile,
                                isSelected = isSelected,
                                isTesting = isTesting,
                                onSelect = { onSelectProfile(profile.id) },
                                onBenchmark = { onBenchmarkProfile(profile) },
                                onShareQr = { qrProfile = profile },
                                onCopyUri = {
                                    val uri = UriConfigParser.exportToUri(profile)
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("VPN Config", uri))
                                    Toast.makeText(context, strings.copiedToast, Toast.LENGTH_SHORT).show()
                                },
                                onEdit = {
                                    editingProfile = profile
                                    showEditorSheet = true
                                },
                                onDelete = { onDeleteProfile(profile.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Test All Servers Dialog with Auto-Connect to Fastest checkbox
    if (showTestDialog) {
        AlertDialog(
            onDismissRequest = { showTestDialog = false },
            title = { Text(strings.testDialogTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = strings.testDialogMessage,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { autoConnectFastestChecked = !autoConnectFastestChecked }
                            .padding(vertical = 4.dp)
                            .testTag("auto_connect_fastest_row"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = autoConnectFastestChecked,
                            onCheckedChange = { autoConnectFastestChecked = it },
                            modifier = Modifier.testTag("auto_connect_fastest_checkbox")
                        )
                        Text(
                            text = strings.autoConnectFastestCheck,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTestDialog = false
                        onTestAllServers(filteredProfiles, autoConnectFastestChecked) { fastest ->
                            Toast.makeText(
                                context,
                                strings.autoConnectedFastestMsg(fastest.name),
                                Toast.LENGTH_SHORT
                            ).show()
                            onAutoConnectFastest(fastest)
                        }
                    },
                    modifier = Modifier.testTag("confirm_start_test_button")
                ) {
                    Text(strings.startTestButton)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTestDialog = false }) {
                    Text(strings.cancelButton)
                }
            }
        )
    }

    if (showImportDialog) {
        ImportConfigOrUriDialog(
            strings = strings,
            onDismiss = { showImportDialog = false },
            onImport = { text, passphrase, onDone ->
                onImportConfigsOrUris(text, passphrase) { status ->
                    when (status) {
                        is BackupImportStatus.Success -> {
                            Toast.makeText(context, strings.importedCountMsg(status.count), Toast.LENGTH_SHORT).show()
                            onDone(null)
                            showImportDialog = false
                        }
                        BackupImportStatus.WrongPassword -> onDone(strings.wrongDecryptionPasswordMsg)
                        BackupImportStatus.InvalidFormat -> onDone(strings.invalidBackupFileMsg)
                    }
                }
            }
        )
    }

    if (showExportDialog) {
        ExportBackupDialog(
            strings = strings,
            profileCount = profiles.size,
            onBuildExportJson = onExportConfigs,
            onDismiss = { showExportDialog = false }
        )
    }

    if (showEditorSheet) {
        ServerEditorBottomSheet(
            strings = strings,
            initialProfile = editingProfile,
            defaultProtocol = defaultNewProtocol,
            existingProfiles = profiles.filter { it.protocol != VpnProtocol.LOAD_BALANCE },
            onDismiss = { showEditorSheet = false },
            onSave = { saved ->
                onSaveProfile(saved)
                showEditorSheet = false
            }
        )
    }

    qrProfile?.let { profile ->
        val uri = remember(profile) { UriConfigParser.exportToUri(profile) }
        val bmp = remember(uri) { UriConfigParser.generateQrBitmap(uri) }
        AlertDialog(
            onDismissRequest = { qrProfile = null },
            title = { Text(profile.name) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (bmp != null) {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.padding(4.dp)
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = strings.shareQr,
                                modifier = Modifier
                                    .size(210.dp)
                                    .padding(12.dp)
                            )
                        }
                    }
                    Text(
                        text = uri,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { qrProfile = null }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun ServerProfileCard(
    strings: AppStrings,
    profile: VpnProfile,
    isSelected: Boolean,
    isTesting: Boolean,
    onSelect: () -> Unit,
    onBenchmark: () -> Unit,
    onShareQr: () -> Unit,
    onCopyUri: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)
            else
                MaterialTheme.colorScheme.surface.copy(alpha = 0.48f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onSelect)
            .testTag("server_card_${profile.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (profile.protocol == VpnProtocol.LOAD_BALANCE) {
                        Icon(
                            imageVector = Icons.Filled.Hub,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else if (!profile.lastCountryCode.isNullOrBlank()) {
                        Text(
                            text = countryCodeToFlag(profile.lastCountryCode),
                            fontSize = 22.sp
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtitleText = if (profile.protocol == VpnProtocol.LOAD_BALANCE) {
                            val memberCount = profile.parsedMemberIds().size
                            "${strings.membersCountBadge(memberCount)} • ${profile.loadBalanceStrategy.name}"
                        } else {
                            "${profile.host}:${profile.port}"
                        }
                        Text(
                            text = subtitleText,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 12.sp,
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
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Surface(
                        color = if (profile.protocol == VpnProtocol.LOAD_BALANCE)
                            MaterialTheme.colorScheme.tertiaryContainer
                        else
                            MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = profile.protocol.shortBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (profile.protocol == VpnProtocol.LOAD_BALANCE)
                                MaterialTheme.colorScheme.onTertiaryContainer
                            else
                                MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Bottom action row: Ping + Download Speed badge + Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(enabled = !isTesting, onClick = onBenchmark)
                            .testTag("ping_button_${profile.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Text(
                                    text = strings.testingNow,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.NetworkPing,
                                    contentDescription = strings.testLatency,
                                    modifier = Modifier.size(14.dp),
                                    tint = when (val ms = profile.latencyMs) {
                                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                                        in 1..250 -> MaterialTheme.colorScheme.primary
                                        in 251..700 -> AmberStatus
                                        else -> CoralError
                                    }
                                )
                                Text(
                                    text = when (val ms = profile.latencyMs) {
                                        null -> strings.testLatency
                                        -1 -> strings.testFailed
                                        else -> strings.latencyMs(ms)
                                    },
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    profile.lastSpeedBps?.let { bps ->
                        if (!isTesting) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (bps > 0L)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
                                else
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                                contentColor = if (bps > 0L)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onErrorContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Speed,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = if (bps > 0L) MaterialTheme.colorScheme.primary else CoralError
                                    )
                                    Text(
                                        text = if (bps > 0L) formatRate(bps) else strings.testFailed,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 11.sp,
                                        color = if (bps > 0L) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onShareQr,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.QrCode2, contentDescription = strings.shareQr, modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = onCopyUri,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = strings.copyLink, modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = strings.editServer, modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = strings.deleteServer,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportBackupDialog(
    strings: AppStrings,
    profileCount: Int,
    onBuildExportJson: (String) -> String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var passphrase by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var pendingJson by remember { mutableStateOf<String?>(null) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingJson
        pendingJson = null
        if (uri != null && json != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
            }.isSuccess
            if (ok) {
                Toast.makeText(context, strings.exportSavedToast, Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        }
    }

    val isPassValid = passphrase.length >= 4

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
        title = { Text(strings.exportDialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = strings.exportDialogDesc(profileCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(strings.encryptionPasswordLabel) },
                    placeholder = { Text(strings.encryptionPasswordHint) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (passwordVisible) strings.hidePasswordDesc else strings.showPasswordDesc
                            )
                        }
                    },
                    isError = passphrase.isNotEmpty() && !isPassValid,
                    supportingText = if (passphrase.isNotEmpty() && !isPassValid) {
                        { Text(strings.passwordTooShortError) }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_passphrase_input")
                )

                OutlinedButton(
                    onClick = {
                        val json = onBuildExportJson(passphrase)
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("NetShield Backup", json))
                        Toast.makeText(context, strings.exportCopiedToast, Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    enabled = isPassValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_copy_json_button")
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(strings.copyJsonButton)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    pendingJson = onBuildExportJson(passphrase)
                    createDocumentLauncher.launch("netshield-configs.json")
                },
                enabled = isPassValid,
                modifier = Modifier.testTag("export_save_file_button")
            ) {
                Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.saveToFileButton)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelButton)
            }
        }
    )
}

@Composable
private fun ImportConfigOrUriDialog(
    strings: AppStrings,
    onDismiss: () -> Unit,
    onImport: (String, String, (String?) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isEncrypted = remember(text) { ConfigBackupManager.isEncryptedBackup(text) }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val loaded = runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                }
            }.getOrNull()
            if (!loaded.isNullOrBlank()) {
                text = loaded
                errorMessage = null
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.importConfigs) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = strings.importDialogSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            openDocumentLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("choose_backup_file_button")
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.chooseFileButton, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                            if (clip.isNotBlank()) {
                                text = clip
                                errorMessage = null
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("paste_clipboard_button")
                    ) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.importFromClipboard, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        errorMessage = null
                    },
                    placeholder = { Text(strings.pasteConfigHint) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_uri_input")
                )

                if (isEncrypted) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = strings.decryptionPasswordRequired,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = {
                            passphrase = it
                            errorMessage = null
                        },
                        label = { Text(strings.decryptionPasswordLabel) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (passwordVisible) strings.hidePasswordDesc else strings.showPasswordDesc
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_passphrase_input")
                    )
                }

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("import_error_text")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onImport(text, passphrase) { err ->
                        errorMessage = err
                    }
                },
                enabled = text.isNotBlank() && (!isEncrypted || passphrase.isNotEmpty()),
                modifier = Modifier.testTag("confirm_import_button")
            ) {
                Text(strings.importButton)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelButton)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerEditorBottomSheet(
    strings: AppStrings,
    initialProfile: VpnProfile?,
    defaultProtocol: VpnProtocol,
    existingProfiles: List<VpnProfile>,
    onDismiss: () -> Unit,
    onSave: (VpnProfile) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var protocol by remember { mutableStateOf(initialProfile?.protocol ?: defaultProtocol) }
    var name by remember { mutableStateOf(initialProfile?.name ?: "") }
    var host by remember { mutableStateOf(initialProfile?.host ?: "") }
    var port by remember { mutableStateOf((initialProfile?.port ?: protocol.defaultPort).toString()) }
    var usernameOrUuid by remember { mutableStateOf(initialProfile?.usernameOrUuid ?: "") }
    var passwordOrSecret by remember { mutableStateOf(initialProfile?.passwordOrSecret ?: "") }
    var privateKeyPem by remember { mutableStateOf(initialProfile?.privateKeyPem ?: "") }
    var transport by remember { mutableStateOf(initialProfile?.transport ?: TransportType.TCP) }
    var tlsEnabled by remember { mutableStateOf(initialProfile?.tlsEnabled ?: (protocol == VpnProtocol.TROJAN || protocol == VpnProtocol.VLESS)) }
    var sni by remember { mutableStateOf(initialProfile?.sni ?: "") }
    var wsHost by remember { mutableStateOf(initialProfile?.wsHost ?: "") }
    var wsPath by remember { mutableStateOf(initialProfile?.wsPath ?: "/") }
    var cipher by remember { mutableStateOf(initialProfile?.cipher ?: "aes-256-gcm") }
    var allowInsecure by remember { mutableStateOf(initialProfile?.allowInsecure ?: false) }

    var selectedMemberIds by remember {
        mutableStateOf(initialProfile?.parsedMemberIds()?.toSet() ?: emptySet())
    }
    var strategy by remember {
        mutableStateOf(initialProfile?.loadBalanceStrategy ?: LoadBalanceStrategy.ROUND_ROBIN)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = when {
                    initialProfile != null -> strings.editServer
                    protocol == VpnProtocol.LOAD_BALANCE -> strings.createLoadBalance
                    else -> strings.addServer
                },
                style = MaterialTheme.typography.titleLarge
            )

            Text(text = strings.protocolLabel, style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VpnProtocol.entries.forEach { p ->
                    FilterChip(
                        selected = protocol == p,
                        onClick = {
                            protocol = p
                            if (initialProfile == null) {
                                port = p.defaultPort.toString()
                                tlsEnabled = p == VpnProtocol.TROJAN || p == VpnProtocol.VLESS
                            }
                        },
                        label = { Text(p.shortBadge) },
                        modifier = Modifier.testTag("proto_chip_${p.name}")
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(strings.serverNameLabel) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_name_input")
            )

            if (protocol == VpnProtocol.LOAD_BALANCE) {
                Text(text = strings.loadBalanceStrategyLabel, style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        LoadBalanceStrategy.ROUND_ROBIN to strings.strategyRoundRobin,
                        LoadBalanceStrategy.LEAST_LATENCY to strings.strategyLeastLatency,
                        LoadBalanceStrategy.RANDOM to strings.strategyRandom
                    ).forEach { (st, label) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (strategy == st)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { strategy = st }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = label, style = MaterialTheme.typography.bodyLarge)
                                if (strategy == st) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Text(text = strings.selectMembersHint, style = MaterialTheme.typography.labelLarge)
                if (existingProfiles.size < 2) {
                    Text(
                        text = strings.needTwoMembersMsg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        existingProfiles.forEach { member ->
                            val checked = selectedMemberIds.contains(member.id)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (checked)
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        selectedMemberIds = if (checked) {
                                            selectedMemberIds - member.id
                                        } else {
                                            selectedMemberIds + member.id
                                        }
                                    }
                                    .testTag("lb_member_${member.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { isChecked ->
                                            selectedMemberIds = if (isChecked) {
                                                selectedMemberIds + member.id
                                            } else {
                                                selectedMemberIds - member.id
                                            }
                                        }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = member.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${member.protocol.shortBadge} • ${member.host}:${member.port}",
                                            fontFamily = JetBrainsMonoFamily,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text(strings.hostLabel) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(2f)
                            .testTag("editor_host_input")
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter { ch -> ch.isDigit() } },
                        label = { Text(strings.portLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("editor_port_input")
                    )
                }

                if (protocol != VpnProtocol.TROJAN && protocol != VpnProtocol.SHADOWSOCKS) {
                    OutlinedTextField(
                        value = usernameOrUuid,
                        onValueChange = { usernameOrUuid = it },
                        label = { Text(strings.uuidOrUserLabel) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_user_input")
                    )
                }

                if (protocol != VpnProtocol.VLESS && protocol != VpnProtocol.VMESS && protocol != VpnProtocol.SOCKS4) {
                    OutlinedTextField(
                        value = passwordOrSecret,
                        onValueChange = { passwordOrSecret = it },
                        label = { Text(strings.passwordOrSecretLabel) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_password_input")
                    )
                }

                if (protocol == VpnProtocol.SSH) {
                    OutlinedTextField(
                        value = privateKeyPem,
                        onValueChange = { privateKeyPem = it },
                        label = { Text(strings.privateKeyLabel) },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (protocol == VpnProtocol.SHADOWSOCKS || protocol == VpnProtocol.VMESS) {
                    Text(text = strings.cipherLabel, style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("aes-256-gcm", "aes-128-gcm", "chacha20-ietf-poly1305").forEach { c ->
                            FilterChip(
                                selected = cipher.equals(c, ignoreCase = true),
                                onClick = { cipher = c },
                                label = { Text(c) }
                            )
                        }
                    }
                }

                if (protocol.group == ProtocolGroup.V2RAY && protocol != VpnProtocol.SHADOWSOCKS) {
                    Text(text = strings.transportLabel, style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TransportType.entries.forEach { t ->
                            FilterChip(
                                selected = transport == t,
                                onClick = { transport = t },
                                label = { Text(t.wireName.uppercase()) }
                            )
                        }
                    }
                    if (transport == TransportType.WS) {
                        OutlinedTextField(
                            value = wsHost,
                            onValueChange = { wsHost = it },
                            label = { Text(strings.wsHostLabel) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = wsPath,
                            onValueChange = { wsPath = it },
                            label = { Text(strings.wsPathLabel) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (protocol == VpnProtocol.VLESS || protocol == VpnProtocol.VMESS ||
                    protocol == VpnProtocol.TROJAN || protocol == VpnProtocol.HTTP_PROXY
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.tlsEnabledLabel, style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = tlsEnabled,
                            onCheckedChange = { tlsEnabled = it }
                        )
                    }
                    if (tlsEnabled) {
                        OutlinedTextField(
                            value = sni,
                            onValueChange = { sni = it },
                            label = { Text(strings.sniLabel) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(strings.allowInsecureLabel, style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = allowInsecure,
                                onCheckedChange = { allowInsecure = it }
                            )
                        }
                    }
                }
            }

            val canSave = if (protocol == VpnProtocol.LOAD_BALANCE) {
                selectedMemberIds.size >= 2
            } else {
                host.isNotBlank()
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(strings.cancelButton)
                }
                Button(
                    onClick = {
                        if (protocol == VpnProtocol.LOAD_BALANCE) {
                            val memberCsv = selectedMemberIds.sorted().joinToString(",")
                            val finalName = name.trim().ifBlank {
                                "MULTI (${selectedMemberIds.size})"
                            }
                            onSave(
                                VpnProfile(
                                    id = initialProfile?.id ?: 0L,
                                    name = finalName,
                                    protocol = VpnProtocol.LOAD_BALANCE,
                                    host = "${selectedMemberIds.size} servers",
                                    port = selectedMemberIds.size,
                                    memberProfileIds = memberCsv,
                                    loadBalanceStrategy = strategy,
                                    latencyMs = initialProfile?.latencyMs,
                                    lastSpeedBps = initialProfile?.lastSpeedBps,
                                    lastCountryCode = initialProfile?.lastCountryCode
                                )
                            )
                        } else {
                            val parsedPort = port.toIntOrNull()?.coerceIn(1, 65535) ?: protocol.defaultPort
                            val finalName = name.trim().ifBlank { "${protocol.shortBadge} ${host.trim()}" }
                            onSave(
                                VpnProfile(
                                    id = initialProfile?.id ?: 0L,
                                    name = finalName,
                                    protocol = protocol,
                                    host = host.trim(),
                                    port = parsedPort,
                                    usernameOrUuid = usernameOrUuid.trim(),
                                    passwordOrSecret = passwordOrSecret,
                                    privateKeyPem = privateKeyPem,
                                    transport = transport,
                                    tlsEnabled = tlsEnabled,
                                    sni = sni.trim(),
                                    wsPath = wsPath.trim().ifBlank { "/" },
                                    wsHost = wsHost.trim(),
                                    allowInsecure = allowInsecure,
                                    cipher = cipher,
                                    latencyMs = initialProfile?.latencyMs,
                                    lastSpeedBps = initialProfile?.lastSpeedBps,
                                    lastCountryCode = initialProfile?.lastCountryCode
                                )
                            )
                        }
                    },
                    enabled = canSave,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_server_button")
                ) {
                    Text(strings.saveButton)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
