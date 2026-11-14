package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VpnSubscription
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.vpn.formatBytes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsBottomSheet(
    strings: AppStrings,
    subscriptions: List<VpnSubscription>,
    updatingIds: Set<Long>,
    prefillUrl: String = "",
    onAddOrUpdate: (Long, String, String, (Boolean, String, Int) -> Unit) -> Unit,
    onRefresh: (VpnSubscription, (Boolean, String, Int) -> Unit) -> Unit,
    onRefreshAll: ((Int, Int) -> Unit) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editingId by remember { mutableStateOf(0L) }
    var nameInput by remember { mutableStateOf("") }
    var urlInput by remember(prefillUrl) { mutableStateOf(prefillUrl) }
    var isSubmitting by remember { mutableStateOf(false) }

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
                        imageVector = Icons.Filled.RssFeed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = strings.subscriptionsTitle,
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                if (subscriptions.isNotEmpty()) {
                    FilledTonalButton(
                        enabled = updatingIds.isEmpty() && !isSubmitting,
                        onClick = {
                            onRefreshAll { ok, total ->
                                Toast.makeText(
                                    context,
                                    strings.subscriptionUpdateAllResult(ok, total),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier.testTag("update_all_subscriptions_button")
                    ) {
                        if (updatingIds.isNotEmpty()) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(strings.updateAllSubscriptions)
                    }
                }
            }

            // Add / Edit subscription form
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (editingId > 0L) strings.editSubscriptionTitle else strings.addSubscriptionTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text(strings.subscriptionNameLabel) },
                        placeholder = { Text(strings.subscriptionNameHint) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sub_name_input")
                    )

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text(strings.subscriptionUrlLabel) },
                        placeholder = { Text(strings.subscriptionUrlHint) },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
                                    if (clip.isNotEmpty()) {
                                        urlInput = clip
                                    } else {
                                        Toast.makeText(context, strings.clipboardEmptyMsg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.testTag("sub_paste_url_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentPaste,
                                    contentDescription = strings.importFromClipboard
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sub_url_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (editingId > 0L) {
                            TextButton(
                                onClick = {
                                    editingId = 0L
                                    nameInput = ""
                                    urlInput = ""
                                }
                            ) {
                                Text(strings.cancelButton)
                            }
                        }
                        Button(
                            onClick = {
                                isSubmitting = true
                                val currentEditId = editingId
                                onAddOrUpdate(currentEditId, nameInput, urlInput) { ok, info, count ->
                                    isSubmitting = false
                                    if (ok) {
                                        Toast.makeText(
                                            context,
                                            strings.subscriptionUpdatedSuccess(info, count),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        editingId = 0L
                                        nameInput = ""
                                        urlInput = ""
                                    } else {
                                        Toast.makeText(context, info, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = urlInput.trim().isNotBlank() && !isSubmitting,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sub_save_sync_button")
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (editingId > 0L) strings.saveAndSyncButton else strings.addAndSyncButton)
                        }
                    }
                }
            }

            if (subscriptions.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = strings.noSubscriptionsTitle,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = strings.noSubscriptionsSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                subscriptions.forEach { sub ->
                    val isUpdating = updatingIds.contains(sub.id)
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("subscription_card_${sub.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = sub.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = sub.url,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = strings.subscriptionServersCount(sub.serverCount),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            val metaParts = buildList {
                                if (sub.lastUpdatedAt > 0L) {
                                    val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                                    add(fmt.format(Date(sub.lastUpdatedAt)))
                                }
                                val used = (sub.uploadBytes ?: 0L) + (sub.downloadBytes ?: 0L)
                                val total = sub.totalBytes
                                if (total != null && total > 0L) {
                                    add("${formatBytes(used)} / ${formatBytes(total)}")
                                }
                            }
                            if (metaParts.isNotEmpty()) {
                                Text(
                                    text = metaParts.joinToString("  •  "),
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (!sub.lastError.isNullOrBlank()) {
                                Text(
                                    text = sub.lastError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    enabled = !isUpdating && !isSubmitting,
                                    onClick = {
                                        onRefresh(sub) { ok, info, count ->
                                            val msg = if (ok) {
                                                strings.subscriptionUpdatedSuccess(info, count)
                                            } else {
                                                info
                                            }
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.testTag("refresh_sub_${sub.id}")
                                ) {
                                    if (isUpdating) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.updateSubscription)
                                }
                                IconButton(
                                    onClick = {
                                        editingId = sub.id
                                        nameInput = sub.name
                                        urlInput = sub.url
                                    },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = strings.editSubscriptionTitle, modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { onDelete(sub.id) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("delete_sub_${sub.id}")
                                ) {
                                    Icon(
                                        Icons.Filled.DeleteOutline,
                                        contentDescription = strings.deleteSubscription,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
