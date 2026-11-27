package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Serializable
data class DailyUsageEntry(
    val dateKey: String, // YYYY-MM-DD
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
    val sessions: Int = 0
)

@Serializable
private data class DataUsageStorePayload(
    val entries: List<DailyUsageEntry> = emptyList()
)

data class DataUsageSummary(
    val todayRxBytes: Long = 0L,
    val todayTxBytes: Long = 0L,
    val weekRxBytes: Long = 0L,
    val weekTxBytes: Long = 0L,
    val monthRxBytes: Long = 0L,
    val monthTxBytes: Long = 0L,
    val last7Days: List<DailyUsageEntry> = emptyList()
) {
    val todayTotalBytes: Long get() = todayRxBytes + todayTxBytes
    val weekTotalBytes: Long get() = weekRxBytes + weekTxBytes
    val monthTotalBytes: Long get() = monthRxBytes + monthTxBytes
}

/**
 * Lightweight persistent daily & monthly VPN data usage history tracker (last 30 days).
 */
object DataUsageTracker {

    private const val FILE_NAME = "vpn_data_usage_history.json"
    private const val RETENTION_DAYS = 30

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val mutex = Mutex()
    private val _summary = MutableStateFlow(DataUsageSummary())
    val summary: StateFlow<DataUsageSummary> = _summary.asStateFlow()

    @Volatile
    private var loaded = false

    suspend fun load(context: Context): DataUsageSummary = mutex.withLock {
        val entries = withContext(Dispatchers.IO) { readEntries(context) }
        val computed = computeSummary(entries, currentDateKey())
        _summary.value = computed
        loaded = true
        computed
    }

    suspend fun recordDelta(
        context: Context,
        deltaRxBytes: Long,
        deltaTxBytes: Long,
        incrementSession: Boolean = false
    ) {
        if (deltaRxBytes <= 0L && deltaTxBytes <= 0L && !incrementSession) return
        mutex.withLock {
            val today = currentDateKey()
            val existing = withContext(Dispatchers.IO) { readEntries(context) }.toMutableList()
            val idx = existing.indexOfFirst { it.dateKey == today }
            if (idx >= 0) {
                val cur = existing[idx]
                existing[idx] = cur.copy(
                    rxBytes = (cur.rxBytes + deltaRxBytes.coerceAtLeast(0L)).coerceAtLeast(0L),
                    txBytes = (cur.txBytes + deltaTxBytes.coerceAtLeast(0L)).coerceAtLeast(0L),
                    sessions = cur.sessions + if (incrementSession) 1 else 0
                )
            } else {
                existing.add(
                    DailyUsageEntry(
                        dateKey = today,
                        rxBytes = deltaRxBytes.coerceAtLeast(0L),
                        txBytes = deltaTxBytes.coerceAtLeast(0L),
                        sessions = if (incrementSession) 1 else 0
                    )
                )
            }
            val pruned = existing
                .sortedBy { it.dateKey }
                .takeLast(RETENTION_DAYS)
            withContext(Dispatchers.IO) { writeEntries(context, pruned) }
            _summary.value = computeSummary(pruned, today)
            loaded = true
        }
    }

    suspend fun clearHistory(context: Context) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching { File(context.filesDir, FILE_NAME).delete() }
            }
            _summary.value = computeSummary(emptyList(), currentDateKey())
            loaded = true
        }
    }

    fun currentDateKey(nowMillis: Long = System.currentTimeMillis()): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMillis))
    }

    /**
     * Pure computation of today, 7-day, and 30-day totals plus a padded 7-day chart series.
     */
    fun computeSummary(
        entries: List<DailyUsageEntry>,
        todayKey: String,
        referenceMillis: Long = System.currentTimeMillis()
    ): DataUsageSummary {
        val byKey = entries.associateBy { it.dateKey }
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        val last7Keys = (6 downTo 0).map { offset ->
            Calendar.getInstance().apply {
                timeInMillis = referenceMillis
                add(Calendar.DAY_OF_YEAR, -offset)
            }.let { fmt.format(it.time) }
        }
        val last30Keys = (29 downTo 0).map { offset ->
            Calendar.getInstance().apply {
                timeInMillis = referenceMillis
                add(Calendar.DAY_OF_YEAR, -offset)
            }.let { fmt.format(it.time) }
        }.toSet()

        val todayEntry = byKey[todayKey]
        val series7 = last7Keys.map { key -> byKey[key] ?: DailyUsageEntry(dateKey = key) }
        val monthEntries = entries.filter { it.dateKey in last30Keys || it.dateKey == todayKey }

        return DataUsageSummary(
            todayRxBytes = todayEntry?.rxBytes ?: 0L,
            todayTxBytes = todayEntry?.txBytes ?: 0L,
            weekRxBytes = series7.sumOf { it.rxBytes },
            weekTxBytes = series7.sumOf { it.txBytes },
            monthRxBytes = monthEntries.sumOf { it.rxBytes },
            monthTxBytes = monthEntries.sumOf { it.txBytes },
            last7Days = series7
        )
    }

    private fun readEntries(context: Context): List<DailyUsageEntry> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<DataUsageStorePayload>(file.readText()).entries
        }.getOrDefault(emptyList())
    }

    private fun writeEntries(context: Context, entries: List<DailyUsageEntry>) {
        runCatching {
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(json.encodeToString(DataUsageStorePayload(entries)))
        }
    }
}
