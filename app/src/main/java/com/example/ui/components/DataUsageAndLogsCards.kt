package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DataUsageSummary
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.AmberStatus
import com.example.ui.theme.ConnectedGreen
import com.example.ui.theme.CoralError
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.vpn.formatBytes

@Composable
fun DataUsageHistoryCard(
    strings: AppStrings,
    summary: DataUsageSummary,
    onResetHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rxColor = ConnectedGreen
    val txColor = MaterialTheme.colorScheme.secondary
    val emptyBarColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.48f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("data_usage_history_card")
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.BarChart,
                        contentDescription = null,
                        tint = ConnectedGreen
                    )
                    Text(
                        text = strings.dataUsageSectionTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    onClick = onResetHistory,
                    modifier = Modifier.testTag("reset_data_usage_button")
                ) {
                    Text(strings.dataUsageResetButton)
                }
            }

            // 3 Summary Tiles: Today / 7 Days / 30 Days
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UsageSummaryTile(
                    title = strings.dataUsageToday,
                    totalBytes = summary.todayTotalBytes,
                    rxBytes = summary.todayRxBytes,
                    txBytes = summary.todayTxBytes,
                    accent = ConnectedGreen,
                    modifier = Modifier.weight(1f)
                )
                UsageSummaryTile(
                    title = strings.dataUsageWeek,
                    totalBytes = summary.weekTotalBytes,
                    rxBytes = summary.weekRxBytes,
                    txBytes = summary.weekTxBytes,
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                UsageSummaryTile(
                    title = strings.dataUsageMonth,
                    totalBytes = summary.monthTotalBytes,
                    rxBytes = summary.monthRxBytes,
                    txBytes = summary.monthTxBytes,
                    accent = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }

            // 7-Day Stacked Bar Chart
            if (summary.last7Days.isNotEmpty()) {
                Text(
                    text = strings.dataUsageLast7Days,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val days = summary.last7Days
                val maxTotal = remember(days) {
                    days.maxOfOrNull { it.rxBytes + it.txBytes }?.coerceAtLeast(1L) ?: 1L
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(88.dp)
                                .testTag("data_usage_7day_chart")
                        ) {
                            val count = days.size.coerceAtLeast(1)
                            val slotW = size.width / count
                            val barW = (slotW * 0.46f).coerceIn(10f, 34f)
                            val maxH = size.height - 6f

                            days.forEachIndexed { idx, entry ->
                                val x = idx * slotW + (slotW - barW) / 2f
                                val total = entry.rxBytes + entry.txBytes
                                if (total <= 0L) {
                                    drawRoundRect(
                                        color = emptyBarColor,
                                        topLeft = Offset(x, size.height - 8f),
                                        size = Size(barW, 8f),
                                        cornerRadius = CornerRadius(4f, 4f)
                                    )
                                } else {
                                    val totalH = ((total.toFloat() / maxTotal.toFloat()) * maxH).coerceAtLeast(10f)
                                    val rxFrac = entry.rxBytes.toFloat() / total.toFloat()
                                    val rxH = (totalH * rxFrac).coerceAtLeast(4f)
                                    val txH = (totalH - rxH).coerceAtLeast(0f)

                                    // TX top segment
                                    if (txH > 1f) {
                                        drawRoundRect(
                                            color = txColor,
                                            topLeft = Offset(x, size.height - totalH),
                                            size = Size(barW, txH + 3f),
                                            cornerRadius = CornerRadius(6f, 6f)
                                        )
                                    }
                                    // RX bottom segment
                                    drawRoundRect(
                                        color = rxColor,
                                        topLeft = Offset(x, size.height - rxH),
                                        size = Size(barW, rxH),
                                        cornerRadius = CornerRadius(6f, 6f)
                                    )
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth()) {
                            days.forEach { entry ->
                                val shortDay = entry.dateKey.takeLast(5) // MM-DD
                                Text(
                                    text = shortDay,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
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

@Composable
private fun UsageSummaryTile(
    title: String,
    totalBytes: Long,
    rxBytes: Long,
    txBytes: Long,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatBytes(totalBytes),
                fontFamily = JetBrainsMonoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1
            )
            Text(
                text = "↓${formatBytes(rxBytes)} ↑${formatBytes(txBytes)}",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun EnhancedDiagnosticsLogsCard(
    strings: AppStrings,
    logs: List<String>,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // 0 = All, 1 = Errors, 2 = Routing, 3 = Failover, 4 = AdBlock
    var selectedFilter by remember { mutableIntStateOf(0) }

    val filteredLogs = remember(logs, selectedFilter) {
        when (selectedFilter) {
            1 -> logs.filter {
                it.contains("[ERR]", ignoreCase = true) ||
                    it.contains("error", ignoreCase = true) ||
                    it.contains("failed", ignoreCase = true) ||
                    it.contains("drop", ignoreCase = true)
            }
            2 -> logs.filter {
                it.contains("[ROUTE]", ignoreCase = true) ||
                    it.contains("bypass", ignoreCase = true) ||
                    it.contains("DNS", ignoreCase = true)
            }
            3 -> logs.filter {
                it.contains("[FAILOVER]", ignoreCase = true) ||
                    it.contains("reconnect", ignoreCase = true) ||
                    it.contains("switch", ignoreCase = true)
            }
            4 -> logs.filter {
                it.contains("[ADBLOCK]", ignoreCase = true) ||
                    it.contains("blocked", ignoreCase = true)
            }
            else -> logs
        }.takeLast(45)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.48f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("enhanced_logs_card")
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = strings.logsTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            if (filteredLogs.isNotEmpty()) {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Baldr VPN Logs", filteredLogs.joinToString("\n")))
                                Toast.makeText(context, strings.logsCopiedToast, Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = filteredLogs.isNotEmpty(),
                        modifier = Modifier.testTag("copy_logs_button")
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.copyLogsButton)
                    }
                    TextButton(
                        onClick = onClearLogs,
                        modifier = Modifier.testTag("clear_logs_button")
                    ) {
                        Text(strings.clearLogs)
                    }
                }
            }

            // Category filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val chips = listOf(
                    0 to strings.logFilterAll,
                    1 to strings.logFilterErrors,
                    2 to strings.logFilterRouting,
                    3 to strings.logFilterFailover,
                    4 to strings.logFilterAdBlock
                )
                chips.forEach { (idx, label) ->
                    FilterChip(
                        selected = selectedFilter == idx,
                        onClick = { selectedFilter = idx },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("log_filter_chip_$idx")
                    )
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
                    if (filteredLogs.isEmpty()) {
                        Text(
                            text = strings.noLogsYet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        filteredLogs.forEach { line ->
                            val lineColor = when {
                                line.contains("[ERR]", ignoreCase = true) ||
                                    line.contains("failed", ignoreCase = true) ||
                                    line.contains("error", ignoreCase = true) -> CoralError
                                line.contains("[FAILOVER]", ignoreCase = true) -> AmberStatus
                                line.contains("[ADBLOCK]", ignoreCase = true) -> MaterialTheme.colorScheme.tertiary
                                line.contains("[ROUTE]", ignoreCase = true) ||
                                    line.contains("connected", ignoreCase = true) -> ConnectedGreen
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            Text(
                                text = line,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = lineColor
                            )
                        }
                    }
                }
            }
        }
    }
}
