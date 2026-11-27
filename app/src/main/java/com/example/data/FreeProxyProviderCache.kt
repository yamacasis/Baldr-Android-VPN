package com.example.data

import android.content.Context
import com.example.model.VpnProfile
import kotlinx.coroutines.Dispatchers
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
data class CachedProviderEntry(
    val updatedAtMillis: Long = 0L,
    val uris: List<String> = emptyList(),
    val isDefaultBundle: Boolean = true
)

@Serializable
data class FreeProvidersCacheBundle(
    val generatedAtMillis: Long = 0L,
    val providers: Map<String, CachedProviderEntry> = emptyMap()
)

object FreeProxyProviderCache {

    private const val ASSET_FILE_NAME = "free_providers_default.json"
    private const val LOCAL_CACHE_FILE = "free_providers_cache.json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val mutex = Mutex()

    @Volatile
    private var memoryCache: Map<String, CachedProviderEntry>? = null

    suspend fun loadAll(context: Context): Map<String, CachedProviderEntry> = mutex.withLock {
        memoryCache?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            val defaultMap = readDefaultBundle(context)
            val localMap = readLocalCache(context)
            val merged = LinkedHashMap<String, CachedProviderEntry>(defaultMap)
            for ((providerId, localEntry) in localMap) {
                val defaultEntry = merged[providerId]
                if (defaultEntry == null ||
                    (!localEntry.isDefaultBundle && localEntry.uris.isNotEmpty()) ||
                    (localEntry.updatedAtMillis > defaultEntry.updatedAtMillis && localEntry.uris.isNotEmpty())
                ) {
                    merged[providerId] = localEntry
                }
            }
            merged
        }
        memoryCache = loaded
        loaded
    }

    suspend fun getProviderEntry(context: Context, providerId: String): CachedProviderEntry? {
        return loadAll(context)[providerId]
    }

    suspend fun getCachedProfiles(
        context: Context,
        provider: FreeProxyProvider
    ): Pair<List<VpnProfile>, Long> {
        val entry = getProviderEntry(context, provider.id) ?: return emptyList<VpnProfile>() to 0L
        val profiles = parseCachedUris(entry.uris, provider)
        return profiles to entry.updatedAtMillis
    }

    suspend fun saveProviderUpdate(
        context: Context,
        providerId: String,
        profiles: List<VpnProfile>,
        updatedAtMillis: Long = System.currentTimeMillis()
    ): Map<String, CachedProviderEntry> {
        val uris = profiles
            .take(24)
            .mapNotNull { profile ->
                runCatching { UriConfigParser.exportToUri(profile) }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
            }
            .distinct()
        if (uris.isEmpty()) return loadAll(context)

        return mutex.withLock {
            val base = memoryCache ?: withContext(Dispatchers.IO) {
                val defaultMap = readDefaultBundle(context)
                val localMap = readLocalCache(context)
                LinkedHashMap(defaultMap).apply { putAll(localMap) }
            }
            val updated = LinkedHashMap(base)
            updated[providerId] = CachedProviderEntry(
                updatedAtMillis = updatedAtMillis,
                uris = uris,
                isDefaultBundle = false
            )
            memoryCache = updated
            withContext(Dispatchers.IO) {
                writeLocalCache(context, updated, updatedAtMillis)
            }
            updated
        }
    }

    internal fun parseCachedUris(
        uris: List<String>,
        provider: FreeProxyProvider
    ): List<VpnProfile> {
        val result = ArrayList<VpnProfile>()
        val seen = HashSet<String>()
        for (rawUri in uris) {
            val parsed = UriConfigParser.parseSingle(rawUri) ?: continue
            if (!UriConfigParser.isConnectorCompatible(rawUri, parsed)) continue
            val key = "${parsed.protocol.name}:${parsed.host}:${parsed.port}"
            if (!seen.add(key)) continue
            val cleanBase = parsed.name.trim().ifBlank { "${parsed.host}:${parsed.port}" }
            val countryFromFragment = if (cleanBase.length == 2 && cleanBase.all { it.isLetter() }) {
                cleanBase.uppercase()
            } else {
                parsed.lastCountryCode
            }
            val labeled = if (cleanBase.startsWith(provider.name, ignoreCase = true)) {
                cleanBase
            } else {
                "${provider.name} · $cleanBase"
            }
            result.add(
                parsed.copy(
                    name = labeled.take(64),
                    lastCountryCode = countryFromFragment
                )
            )
        }
        return result
    }

    /**
     * Formats an update timestamp for display on provider cards and free server names.
     * Uses Solar Hijri (Jalali) calendar in Farsi mode (e.g. 1405/09/03) and concise date in English.
     */
    fun formatDateTag(timestampMillis: Long, isFarsi: Boolean): String {
        if (timestampMillis <= 0L) return ""
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMillis }
        return if (isFarsi) {
            val (jy, jm, jd) = gregorianToJalali(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
            String.format(Locale.US, "%04d/%02d/%02d", jy, jm, jd)
        } else {
            SimpleDateFormat("MMM dd", Locale.US).format(Date(timestampMillis))
        }
    }

    fun formatDateTimeTag(timestampMillis: Long, isFarsi: Boolean): String {
        if (timestampMillis <= 0L) return ""
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMillis }
        val timePart = String.format(
            Locale.US,
            "%02d:%02d",
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE)
        )
        return "${formatDateTag(timestampMillis, isFarsi)} · $timePart"
    }

    fun formatServerNameWithDate(
        providerName: String,
        baseServerLabel: String,
        updatedAtMillis: Long,
        isFarsi: Boolean
    ): String {
        val dateTag = formatDateTag(updatedAtMillis, isFarsi)
        val cleanBase = baseServerLabel
            .removePrefix("$providerName ·")
            .removePrefix(providerName)
            .trim()
            .ifBlank { baseServerLabel }
        val full = if (dateTag.isNotEmpty()) {
            "$providerName ($dateTag) · $cleanBase"
        } else {
            "$providerName · $cleanBase"
        }
        return full.take(68)
    }

    /**
     * Standard pure-Kotlin Gregorian to Solar Hijri (Jalali) date conversion.
     */
    internal fun gregorianToJalali(gyInput: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        var gy = gyInput
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gDaysInMonth[(gm - 1).coerceIn(0, 11)]
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + (days / 31)
            jd = 1 + (days % 31)
        } else {
            jm = 7 + ((days - 186) / 30)
            jd = 1 + ((days - 186) % 30)
        }
        return Triple(jy, jm, jd)
    }

    private fun readDefaultBundle(context: Context): Map<String, CachedProviderEntry> {
        return runCatching {
            val raw = context.assets.open(ASSET_FILE_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val parsed = json.decodeFromString<FreeProvidersCacheBundle>(raw)
            parsed.providers.mapValues { (_, entry) ->
                entry.copy(
                    updatedAtMillis = if (entry.updatedAtMillis > 0L) entry.updatedAtMillis else parsed.generatedAtMillis,
                    isDefaultBundle = true
                )
            }
        }.getOrDefault(emptyMap())
    }

    private fun readLocalCache(context: Context): Map<String, CachedProviderEntry> {
        return runCatching {
            val file = File(context.filesDir, LOCAL_CACHE_FILE)
            if (!file.exists()) return emptyMap()
            val raw = file.readText(Charsets.UTF_8)
            json.decodeFromString<FreeProvidersCacheBundle>(raw).providers
        }.getOrDefault(emptyMap())
    }

    private fun writeLocalCache(
        context: Context,
        providersMap: Map<String, CachedProviderEntry>,
        generatedAtMillis: Long
    ) {
        runCatching {
            val bundle = FreeProvidersCacheBundle(
                generatedAtMillis = generatedAtMillis,
                providers = providersMap
            )
            val tmpFile = File(context.filesDir, "$LOCAL_CACHE_FILE.tmp")
            val finalFile = File(context.filesDir, LOCAL_CACHE_FILE)
            tmpFile.writeText(json.encodeToString(bundle), Charsets.UTF_8)
            if (!tmpFile.renameTo(finalFile)) {
                finalFile.writeText(json.encodeToString(bundle), Charsets.UTF_8)
                tmpFile.delete()
            }
        }
    }
}
