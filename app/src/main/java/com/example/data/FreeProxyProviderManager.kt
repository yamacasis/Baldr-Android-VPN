package com.example.data

import com.example.model.VpnProfile
import com.example.model.VpnProtocol
import com.example.vpn.protocols.ProtocolConnectorFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

enum class FreeProxyFeedFormat {
    PLAIN_IP_PORT,
    GEONODE_JSON,
    V2RAY_SUB
}

data class FreeProxyFeedSource(
    val url: String,
    val defaultProtocol: VpnProtocol,
    val format: FreeProxyFeedFormat
)

data class FreeProxyProvider(
    val id: String,
    val name: String,
    val protocolSummary: String,
    val isV2Ray: Boolean,
    val iranRecommended: Boolean = false,
    val descriptionEn: String,
    val descriptionFa: String,
    val sources: List<FreeProxyFeedSource>
)

data class FreeProviderPingProgress(
    val pingedCount: Int,
    val totalCount: Int,
    val reachableCount: Int,
    val bestLatencyMs: Int?
)

sealed class FreeProviderFetchResult {
    data class Success(val candidates: List<VpnProfile>) : FreeProviderFetchResult()
    data class Failure(val message: String) : FreeProviderFetchResult()
}

data class FreeProviderBestResult(
    val bestProfile: VpnProfile?,
    val reachableCount: Int,
    val testedCount: Int,
    val verifiedHandshake: Boolean
)

object FreeProxyProviderManager {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * 15 verified live public providers.
     * Iran-recommended V2Ray/Xray sources are listed first and prioritize jsDelivr CDN mirrors
     * before raw.githubusercontent.com for improved reachability from restricted networks.
     */
    val providers: List<FreeProxyProvider> = listOf(
        // --- Iran-Recommended V2Ray / Xray Providers ---
        FreeProxyProvider(
            id = "matin_ghanbari",
            name = "MatinGhanbari V2Ray",
            protocolSummary = "VLESS · VMess · Trojan · SS",
            isV2Ray = true,
            iranRecommended = true,
            descriptionEn = "Iran-focused V2Ray/Xray aggregator updated every 15 minutes via CDN mirror",
            descriptionFa = "کانفیگ‌های V2Ray/Xray ویژه کاربران ایران با بروزرسانی هر ۱۵ دقیقه از طریق CDN",
            sources = listOf(
                FreeProxyFeedSource(
                    url = "https://cdn.jsdelivr.net/gh/MatinGhanbari/v2ray-configs@main/subscriptions/v2ray/all_sub.txt",
                    defaultProtocol = VpnProtocol.VLESS,
                    format = FreeProxyFeedFormat.V2RAY_SUB
                ),
                FreeProxyFeedSource(
                    url = "https://raw.githubusercontent.com/MatinGhanbari/v2ray-configs/main/subscriptions/v2ray/all_sub.txt",
                    defaultProtocol = VpnProtocol.VLESS,
                    format = FreeProxyFeedFormat.V2RAY_SUB
                )
            )
        ),
        FreeProxyProvider(
            id = "barry_vless",
            name = "Barry-Far VLESS",
            protocolSummary = "VLESS (TLS · WS)",
            isV2Ray = true,
            iranRecommended = true,
            descriptionEn = "Dedicated VLESS TLS/WebSocket nodes optimized for restricted networks",
            descriptionFa = "سرورهای اختصاصی VLESS با TLS و WebSocket مناسب اینترنت ایران",
            sources = listOf(
                FreeProxyFeedSource(
                    url = "https://cdn.jsdelivr.net/gh/barry-far/V2ray-config@main/Splitted-By-Protocol/vless.txt",
                    defaultProtocol = VpnProtocol.VLESS,
                    format = FreeProxyFeedFormat.V2RAY_SUB
                ),
                FreeProxyFeedSource(
                    url = "https://raw.githubusercontent.com/barry-far/V2ray-config/main/Splitted-By-Protocol/vless.txt",
                    defaultProtocol = VpnProtocol.VLESS,
                    format = FreeProxyFeedFormat.V2RAY_SUB
                ),
                FreeProxyFeedSource(
                    url = "https://cdn.jsdelivr.net/gh/barry-far/V2ray-config@main/Sub1.txt",
                    defaultProtocol = VpnProtocol.VLESS,
                    format = FreeProxyFeedFormat.V2RAY_SUB
                )
            )
        ),
        FreeProxyProvider(
            id = "barry_trojan",
            name = "Barry-Far Trojan",
            protocolSummary = "Trojan TLS",
            isV2Ray = true,
            iranRecommended = true,
            descriptionEn = "Dedicated Trojan-over-TLS servers with strong DPI resilience",
            descriptionFa = "سرورهای اختصاصی Trojan روی TLS با پایداری بالا در برابر فیلترینگ",
            sources = listOf(
                FreeProxyFeedSource(
                    url = "https://cdn.jsdelivr.net/gh/barry-far/V2ray-config@main/Splitted-By-Protocol/trojan.txt",
                    defaultProtocol = VpnProtocol.TROJAN,
                    format = FreeProxyFeedFormat.V2RAY_SUB
                ),
                FreeProxyFeedSource(
                    url = "https://raw.githubusercontent.com/barry-far/V2ray-config/main/Splitted-By-Protocol/trojan.txt",
                    defaultProtocol = VpnProtocol.TROJAN,
                    format = FreeProxyFeedFormat.V2RAY_SUB
                )
            )
        ),
        FreeProxyProvider(
            id = "solispirit",
            name = "SoliSpirit V2Ray",
            protocolSummary = "VLESS · VMess · Trojan · SS",
            isV2Ray = true,
            iranRecommended = true,
            descriptionEn = "Curated multi-protocol V2Ray pool tested frequently for Iran operators",
            descriptionFa = "مجموعه منتخب کانفیگ‌های چندپروتکله مناسب اپراتورهای همراه و ثابت ایران",
            sources = listOf(
                FreeProxyFeedSource(
                    url = "https://cdn.jsdelivr.net/gh/SoliSpirit/v2ray-configs@main/all_configs.txt",
                    defaultProtocol = VpnProtocol.VLESS,
                    format = FreeProxyFeedFormat.V2RAY_SUB
                ),
                FreeProxyFeedSource(
                    url = "https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/all_configs.txt",
                    defaultProtocol = VpnProtocol.VLESS,
                    format = FreeProxyFeedFormat.V2RAY_SUB
                )
            )
        ),
        FreeProxyProvider(
            id = "mahdibland",
            name = "Mahdibland V2Ray",
            protocolSummary = "VLESS · VMess · Trojan · SS",
            isV2Ray = true,
            iranRecommended = true,
            descriptionEn = "Merged multi-source V2RayAggregator public nodes via jsDelivr CDN",
            descriptionFa = "سرورهای تجمیعی و فیلترشده مخزن V2RayAggregator از طریق CDN",
            sources = listOf(
                FreeProxyFeedSource(
                    url = "https://cdn.jsdelivr.net/gh/mahdibland/V2RayAggregator@master/sub/sub_merge.txt",
                    defaultProtocol = VpnProtocol.VLESS,
                    format = FreeProxyFeedFormat.V2RAY_SUB
                ),
                FreeProxyFeedSource(
                    url = "https://raw.githubusercontent.com/mahdibland/V2RayAggregator/master/sub/sub_merge.txt",
                    defaultProtocol = VpnProtocol.VLESS,
                    format = FreeProxyFeedFormat.V2RAY_SUB
                )
            )
        ),
        FreeProxyProvider(
            id = "surfboard_tg",
            name = "Surfboard TGParse",
            protocolSummary = "VLESS · VMess",
            isV2Ray = true,
            iranRecommended = true,
            descriptionEn = "Auto-parsed Telegram channel VLESS & VMess configs refreshed hourly",
            descriptionFa = "کانفیگ‌های VLESS و VMess استخراج‌شده خودکار از کانال‌های عمومی تلگرام",
            sources = listOf(
                FreeProxyFeedSource(
                    url = "https://cdn.jsdelivr.net/gh/Surfboardv2ray/TGParse@main/splitted/vless",
                    defaultProtocol = VpnProtocol.VLESS,
                    format = FreeProxyFeedFormat.V2RAY_SUB
                ),
                FreeProxyFeedSource(
                    url = "https://cdn.jsdelivr.net/gh/Surfboardv2ray/TGParse@main/splitted/vmess",
                    defaultProtocol = VpnProtocol.VMESS,
                    format = FreeProxyFeedFormat.V2RAY_SUB
                ),
                FreeProxyFeedSource(
                    url = "https://raw.githubusercontent.com/Surfboardv2ray/TGParse/main/splitted/vless",
                    defaultProtocol = VpnProtocol.VLESS,
                    format = FreeProxyFeedFormat.V2RAY_SUB
                )
            )
        ),
        FreeProxyProvider(
            id = "nirevil",
            name = "NiREvil V2Ray",
            protocolSummary = "VLESS · Trojan · SS",
            isV2Ray = true,
            iranRecommended = true,
            descriptionEn = "Compact curated VLESS & Shadowsocks feed tuned for high stability",
            descriptionFa = "فید سبک و گلچین‌شده VLESS و Shadowsocks با تمرکز بر پایداری اتصال",
            sources = listOf(
                FreeProxyFeedSource(
                    url = "https://cdn.jsdelivr.net/gh/NiREvil/vless@main/sub/SSTime",
                    defaultProtocol = VpnProtocol.VLESS,
                    format = FreeProxyFeedFormat.V2RAY_SUB
                ),
                FreeProxyFeedSource(
                    url = "https://raw.githubusercontent.com/NiREvil/vless/main/sub/SSTime",
                    defaultProtocol = VpnProtocol.VLESS,
                    format = FreeProxyFeedFormat.V2RAY_SUB
                )
            )
        ),
        FreeProxyProvider(
            id = "nomorewalls",
            name = "NoMoreWalls",
            protocolSummary = "VLESS · VMess · Trojan · SS",
            isV2Ray = true,
            iranRecommended = true,
            descriptionEn = "Open-source censorship-circumvention node pool updated continuously",
            descriptionFa = "مجموعه آزاد سرورهای عبور از محدودیت اینترنت با بروزرسانی پیوسته",
            sources = listOf(
                FreeProxyFeedSource(
                    url = "https://cdn.jsdelivr.net/gh/peasoft/NoMoreWalls@master/list_raw.txt",
                    defaultProtocol = VpnProtocol.VLESS,
                    format = FreeProxyFeedFormat.V2RAY_SUB
                ),
                FreeProxyFeedSource(
                    url = "https://raw.githubusercontent.com/peasoft/NoMoreWalls/master/list_raw.txt",
                    defaultProtocol = VpnProtocol.VLESS,
                    format = FreeProxyFeedFormat.V2RAY_SUB
                )
            )
        ),

        // --- Global SOCKS5 / SOCKS4 / HTTP Proxy Providers ---
        FreeProxyProvider(
            id = "proxifly",
            name = "Proxifly",
            protocolSummary = "SOCKS5 · HTTP",
            isV2Ray = false,
            iranRecommended = false,
            descriptionEn = "High-volume global SOCKS5 & HTTP proxies checked every 5 minutes",
            descriptionFa = "فهرست پرتعداد پروکسی‌های SOCKS5 و HTTP با بررسی هر ۵ دقیقه",
            sources = listOf(
                FreeProxyFeedSource(
                    url = "https://cdn.jsdelivr.net/gh/proxifly/free-proxy-list@main/proxies/protocols/socks5/data.txt",
                    defaultProtocol = VpnProtocol.SOCKS5,
                    format = FreeProxyFeedFormat.PLAIN_IP_PORT
                ),
                FreeProxyFeedSource(
                    url = "https://raw.githubusercontent.com/proxifly/free-proxy-list/main/proxies/protocols/socks5/data.txt",
                    defaultProtocol = VpnProtocol.SOCKS5,
                    format = FreeProxyFeedFormat.PLAIN_IP_PORT
                ),
                FreeProxyFeedSource(
                    url = "https://cdn.jsdelivr.net/gh/proxifly/free-proxy-list@main/proxies/protocols/http/data.txt",
                    defaultProtocol = VpnProtocol.HTTP_PROXY,
                    format = FreeProxyFeedFormat.PLAIN_IP_PORT
                )
            )
        ),
        FreeProxyProvider(
            id = "vakhov",
            name = "Vakhov Fresh",
            protocolSummary = "SOCKS5 · HTTP",
            isV2Ray = false,
            iranRecommended = false,
            descriptionEn = "Fast GitHub Pages CDN feed of freshly verified SOCKS5 & HTTP proxies",
            descriptionFa = "پروکسی‌های تازه تست‌شده SOCKS5 و HTTP روی سرور سریع GitHub Pages",
            sources = listOf(
                FreeProxyFeedSource(
                    url = "https://vakhov.github.io/fresh-proxy-list/socks5.txt",
                    defaultProtocol = VpnProtocol.SOCKS5,
                    format = FreeProxyFeedFormat.PLAIN_IP_PORT
                ),
                FreeProxyFeedSource(
                    url = "https://cdn.jsdelivr.net/gh/vakhov/fresh-proxy-list@master/socks5.txt",
                    defaultProtocol = VpnProtocol.SOCKS5,
                    format = FreeProxyFeedFormat.PLAIN_IP_PORT
                ),
                FreeProxyFeedSource(
                    url = "https://vakhov.github.io/fresh-proxy-list/http.txt",
                    defaultProtocol = VpnProtocol.HTTP_PROXY,
                    format = FreeProxyFeedFormat.PLAIN_IP_PORT
                )
            )
        ),
        FreeProxyProvider(
            id = "proxyscrape",
            name = "ProxyScrape",
            protocolSummary = "SOCKS5 · HTTP",
            isV2Ray = false,
            iranRecommended = false,
            descriptionEn = "Live free SOCKS5 & HTTP proxy API refreshed every 5 minutes",
            descriptionFa = "سرویس آنلاین پروکسی رایگان SOCKS5 و HTTP با بروزرسانی هر ۵ دقیقه",
            sources = listOf(
                FreeProxyFeedSource(
                    url = "https://api.proxyscrape.com/v4/free-proxy-list/get?request=displayproxies&protocol=socks5&timeout=5000&country=all&simplified=true",
                    defaultProtocol = VpnProtocol.SOCKS5,
                    format = FreeProxyFeedFormat.PLAIN_IP_PORT
                ),
                FreeProxyFeedSource(
                    url = "https://api.proxyscrape.com/v4/free-proxy-list/get?request=displayproxies&protocol=http&timeout=5000&country=all&simplified=true",
                    defaultProtocol = VpnProtocol.HTTP_PROXY,
                    format = FreeProxyFeedFormat.PLAIN_IP_PORT
                )
            )
        ),
        FreeProxyProvider(
            id = "geonode",
            name = "GeoNode",
            protocolSummary = "SOCKS5 · HTTP",
            isV2Ray = false,
            iranRecommended = false,
            descriptionEn = "Global checked free proxies with country flags and latency sorting",
            descriptionFa = "پروکسی‌های بررسی‌شده جهانی با نمایش پرچم کشور و مرتب‌سازی پینگ",
            sources = listOf(
                FreeProxyFeedSource(
                    url = "https://proxylist.geonode.com/api/proxy-list?limit=50&page=1&sort_by=lastChecked&sort_type=desc&protocols=socks5%2Chttp",
                    defaultProtocol = VpnProtocol.SOCKS5,
                    format = FreeProxyFeedFormat.GEONODE_JSON
                )
            )
        ),
        FreeProxyProvider(
            id = "monosans",
            name = "Monosans",
            protocolSummary = "SOCKS5 · HTTP",
            isV2Ray = false,
            iranRecommended = false,
            descriptionEn = "Frequently re-checked public proxy lists sorted by connection speed",
            descriptionFa = "فهرست پروکسی عمومی تست‌شده و مرتب‌شده بر اساس سرعت اتصال",
            sources = listOf(
                FreeProxyFeedSource(
                    url = "https://cdn.jsdelivr.net/gh/monosans/proxy-list@main/proxies/socks5.txt",
                    defaultProtocol = VpnProtocol.SOCKS5,
                    format = FreeProxyFeedFormat.PLAIN_IP_PORT
                ),
                FreeProxyFeedSource(
                    url = "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/socks5.txt",
                    defaultProtocol = VpnProtocol.SOCKS5,
                    format = FreeProxyFeedFormat.PLAIN_IP_PORT
                ),
                FreeProxyFeedSource(
                    url = "https://cdn.jsdelivr.net/gh/monosans/proxy-list@main/proxies/http.txt",
                    defaultProtocol = VpnProtocol.HTTP_PROXY,
                    format = FreeProxyFeedFormat.PLAIN_IP_PORT
                )
            )
        ),
        FreeProxyProvider(
            id = "roosterkid",
            name = "Roosterkid",
            protocolSummary = "SOCKS5 · HTTPS",
            isV2Ray = false,
            iranRecommended = false,
            descriptionEn = "Hourly refreshed open SOCKS5 & HTTPS proxy repository via CDN",
            descriptionFa = "مخزن پروکسی باز SOCKS5 و HTTPS با بروزرسانی ساعتی از طریق CDN",
            sources = listOf(
                FreeProxyFeedSource(
                    url = "https://cdn.jsdelivr.net/gh/roosterkid/openproxylist@main/SOCKS5_RAW.txt",
                    defaultProtocol = VpnProtocol.SOCKS5,
                    format = FreeProxyFeedFormat.PLAIN_IP_PORT
                ),
                FreeProxyFeedSource(
                    url = "https://cdn.jsdelivr.net/gh/roosterkid/openproxylist@main/HTTPS_RAW.txt",
                    defaultProtocol = VpnProtocol.HTTP_PROXY,
                    format = FreeProxyFeedFormat.PLAIN_IP_PORT
                )
            )
        ),
        FreeProxyProvider(
            id = "thespeedx",
            name = "TheSpeedX",
            protocolSummary = "SOCKS5 · SOCKS4 · HTTP",
            isV2Ray = false,
            iranRecommended = false,
            descriptionEn = "Community-maintained open SOCKS5, SOCKS4 & HTTP proxy pool",
            descriptionFa = "مجموعه عمومی و گسترده پروکسی‌های SOCKS5، SOCKS4 و HTTP",
            sources = listOf(
                FreeProxyFeedSource(
                    url = "https://cdn.jsdelivr.net/gh/TheSpeedX/PROXY-List@master/socks5.txt",
                    defaultProtocol = VpnProtocol.SOCKS5,
                    format = FreeProxyFeedFormat.PLAIN_IP_PORT
                ),
                FreeProxyFeedSource(
                    url = "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/socks5.txt",
                    defaultProtocol = VpnProtocol.SOCKS5,
                    format = FreeProxyFeedFormat.PLAIN_IP_PORT
                ),
                FreeProxyFeedSource(
                    url = "https://cdn.jsdelivr.net/gh/TheSpeedX/PROXY-List@master/http.txt",
                    defaultProtocol = VpnProtocol.HTTP_PROXY,
                    format = FreeProxyFeedFormat.PLAIN_IP_PORT
                )
            )
        )
    )

    suspend fun fetchCandidates(
        provider: FreeProxyProvider,
        maxCandidates: Int = 36
    ): FreeProviderFetchResult = withContext(Dispatchers.IO) {
        val collected = LinkedHashMap<String, VpnProfile>()
        var lastError: String? = null

        for (source in provider.sources) {
            if (collected.size >= maxCandidates) break
            try {
                val request = Request.Builder()
                    .url(source.url)
                    .header("User-Agent", "BaldrVPN/8.0")
                    .header("Accept", "*/*")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        lastError = "HTTP ${response.code}"
                        return@use
                    }
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) {
                        lastError = "Empty feed"
                        return@use
                    }

                    val parsed = when (source.format) {
                        FreeProxyFeedFormat.PLAIN_IP_PORT -> parsePlainIpPort(
                            rawText = body,
                            providerName = provider.name,
                            protocol = source.defaultProtocol,
                            limit = maxCandidates
                        )
                        FreeProxyFeedFormat.GEONODE_JSON -> parseGeoNodeJson(
                            rawJson = body,
                            providerName = provider.name,
                            limit = maxCandidates
                        )
                        FreeProxyFeedFormat.V2RAY_SUB -> parseV2RaySubscription(
                            rawText = body,
                            providerName = provider.name,
                            limit = maxCandidates
                        )
                    }

                    for (profile in parsed) {
                        val dedupKey = "${profile.protocol.name}:${profile.host}:${profile.port}"
                        if (!collected.containsKey(dedupKey)) {
                            collected[dedupKey] = profile
                            if (collected.size >= maxCandidates) break
                        }
                    }
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Network error"
            }
        }

        val list = collected.values.toList()
        if (list.isEmpty()) {
            FreeProviderFetchResult.Failure(lastError ?: "No proxy servers returned by ${provider.name}")
        } else {
            FreeProviderFetchResult.Success(list)
        }
    }

    /**
     * Concurrently pings all candidate nodes, tracks live progress,
     * validates the top lowest-latency candidates with a quick protocol handshake,
     * and returns the best working server.
     */
    suspend fun pingAndPickBest(
        candidates: List<VpnProfile>,
        maxConcurrency: Int = 8,
        pacingDelayMs: Long = 0L,
        pingMeasure: suspend (VpnProfile) -> Int? = { profile ->
            ExitIpChecker.measureLatency(profile, timeoutMs = 2200)
        },
        handshakeVerifier: suspend (VpnProfile) -> Boolean = { profile ->
            verifyConnectorHandshake(profile)
        },
        onProgress: (FreeProviderPingProgress) -> Unit = {}
    ): FreeProviderBestResult {
        if (candidates.isEmpty()) {
            return FreeProviderBestResult(
                bestProfile = null,
                reachableCount = 0,
                testedCount = 0,
                verifiedHandshake = false
            )
        }

        val total = candidates.size
        val doneCounter = AtomicInteger(0)
        val reachableCounter = AtomicInteger(0)
        val bestLatencyAtomic = AtomicInteger(Int.MAX_VALUE)
        val semaphore = Semaphore(maxConcurrency.coerceIn(1, 12))

        val pingedProfiles: List<VpnProfile> = coroutineScope {
            candidates.mapIndexed { index, candidate ->
                async {
                    semaphore.withPermit {
                        if (pacingDelayMs > 0L && index > 0) {
                            delay(pacingDelayMs)
                        }
                        val ms = runCatching { pingMeasure(candidate) }.getOrNull()
                        val isReachable = ms != null && ms > 0
                        if (isReachable) {
                            reachableCounter.incrementAndGet()
                            bestLatencyAtomic.updateAndGet { current -> minOf(current, ms!!) }
                        }
                        val done = doneCounter.incrementAndGet()
                        val bestSoFar = bestLatencyAtomic.get().takeIf { it < Int.MAX_VALUE }
                        onProgress(
                            FreeProviderPingProgress(
                                pingedCount = done,
                                totalCount = total,
                                reachableCount = reachableCounter.get(),
                                bestLatencyMs = bestSoFar
                            )
                        )
                        candidate.copy(latencyMs = if (isReachable) ms else -1)
                    }
                }
            }.awaitAll()
        }

        val reachableSorted = pingedProfiles
            .filter { (it.latencyMs ?: -1) > 0 }
            .sortedBy { it.latencyMs ?: Int.MAX_VALUE }

        if (reachableSorted.isEmpty()) {
            return FreeProviderBestResult(
                bestProfile = null,
                reachableCount = 0,
                testedCount = total,
                verifiedHandshake = false
            )
        }

        // Verify top 3 lowest-latency candidates with a quick handshake check
        val finalists = reachableSorted.take(3)
        for (finalist in finalists) {
            val ok = runCatching { handshakeVerifier(finalist) }.getOrDefault(false)
            if (ok) {
                return FreeProviderBestResult(
                    bestProfile = finalist,
                    reachableCount = reachableSorted.size,
                    testedCount = total,
                    verifiedHandshake = true
                )
            }
        }

        // Fallback to lowest-latency TCP-reachable candidate if quick handshake timed out
        return FreeProviderBestResult(
            bestProfile = reachableSorted.first(),
            reachableCount = reachableSorted.size,
            testedCount = total,
            verifiedHandshake = false
        )
    }

    private suspend fun verifyConnectorHandshake(profile: VpnProfile): Boolean =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(3500L) {
                val connector = ProtocolConnectorFactory.create(profile, emptyList()) { true }
                try {
                    connector.connect()
                    connector.isHealthy()
                } catch (_: Exception) {
                    false
                } finally {
                    runCatching { connector.close() }
                }
            } ?: false
        }

    internal fun parsePlainIpPort(
        rawText: String,
        providerName: String,
        protocol: VpnProtocol,
        limit: Int = 36
    ): List<VpnProfile> {
        val result = ArrayList<VpnProfile>()
        val seen = HashSet<String>()
        for (rawLine in rawText.lineSequence()) {
            val cleaned = rawLine
                .trim()
                .removePrefix("socks5://")
                .removePrefix("socks4://")
                .removePrefix("http://")
                .removePrefix("https://")
                .substringBefore(" ")
                .substringBefore("#")
                .trim()
            if (cleaned.isEmpty() || !cleaned.contains(":")) continue
            val host = cleaned.substringBeforeLast(":").trim().removePrefix("[").removeSuffix("]")
            val port = cleaned.substringAfterLast(":").trim().toIntOrNull() ?: continue
            if (!isValidHost(host) || port !in 1..65535) continue
            val key = "$host:$port"
            if (!seen.add(key)) continue
            result.add(
                VpnProfile(
                    name = "$providerName · $host:$port",
                    protocol = protocol,
                    host = host,
                    port = port
                )
            )
            if (result.size >= limit) break
        }
        return result
    }

    internal fun parseGeoNodeJson(
        rawJson: String,
        providerName: String,
        limit: Int = 36
    ): List<VpnProfile> {
        val root = runCatching { Json.parseToJsonElement(rawJson).jsonObject }.getOrNull() ?: return emptyList()
        val data = runCatching { root["data"]?.jsonArray }.getOrNull() ?: return emptyList()
        val result = ArrayList<VpnProfile>()
        val seen = HashSet<String>()

        for (element in data) {
            val item = runCatching { element.jsonObject }.getOrNull() ?: continue
            val ip = item["ip"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val port = item["port"]?.jsonPrimitive?.contentOrNull?.trim()?.toIntOrNull() ?: -1
            if (!isValidHost(ip) || port !in 1..65535) continue

            val protoNames = runCatching {
                item["protocols"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull?.lowercase() }
            }.getOrNull().orEmpty()
            val protocol = when {
                protoNames.any { it.contains("socks5") } -> VpnProtocol.SOCKS5
                protoNames.any { it.contains("socks4") } -> VpnProtocol.SOCKS4
                protoNames.any { it.contains("http") } -> VpnProtocol.HTTP_PROXY
                else -> VpnProtocol.SOCKS5
            }

            val country = item["country"]?.jsonPrimitive?.contentOrNull?.trim()?.uppercase()?.take(2).orEmpty()
            val key = "${protocol.name}:$ip:$port"
            if (!seen.add(key)) continue
            val labelSuffix = if (country.length == 2) "$country · $ip" else "$ip:$port"
            result.add(
                VpnProfile(
                    name = "$providerName · $labelSuffix",
                    protocol = protocol,
                    host = ip,
                    port = port,
                    lastCountryCode = if (country.length == 2) country else null
                )
            )
            if (result.size >= limit) break
        }
        return result
    }

    internal fun parseV2RaySubscription(
        rawText: String,
        providerName: String,
        limit: Int = 36
    ): List<VpnProfile> {
        val allParsed = UriConfigParser.parseBatch(rawText)
        if (allParsed.isEmpty()) return emptyList()
        val supported = allParsed.filter { profile ->
            profile.protocol in setOf(
                VpnProtocol.VLESS,
                VpnProtocol.VMESS,
                VpnProtocol.TROJAN,
                VpnProtocol.SHADOWSOCKS
            ) && isValidHost(profile.host) && profile.port in 1..65535
        }
        if (supported.isEmpty()) return emptyList()

        // Spread sample evenly across the list for diverse endpoints
        val sampled = if (supported.size <= limit) {
            supported
        } else {
            val step = supported.size.toDouble() / limit.toDouble()
            (0 until limit).map { idx -> supported[(idx * step).toInt().coerceIn(0, supported.lastIndex)] }
        }

        val seen = HashSet<String>()
        return sampled.mapNotNull { profile ->
            val key = "${profile.protocol.name}:${profile.host}:${profile.port}"
            if (!seen.add(key)) return@mapNotNull null
            val cleanBase = profile.name.trim().ifBlank { "${profile.host}:${profile.port}" }
            val labeledName = if (cleanBase.startsWith(providerName, ignoreCase = true)) {
                cleanBase
            } else {
                "$providerName · $cleanBase"
            }
            profile.copy(name = labeledName.take(64))
        }
    }

    private fun isValidHost(host: String): Boolean {
        if (host.length < 3 || host.length > 253) return false
        if (host.any { it.isWhitespace() || it == '/' || it == '@' }) return false
        return host.contains('.')
    }
}
