package com.example.data

import com.example.model.ExitNetworkInfo
import com.example.model.VpnProfile
import com.example.vpn.protocols.ProtocolConnectorFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

data class ProfileBenchmarkResult(
    val latencyMs: Int?,
    val downloadSpeedBps: Long?,
    val bytesDownloaded: Long = 0L,
    val error: String? = null
)

object ExitIpChecker {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun measureLatency(profile: VpnProfile): Int? = withContext(Dispatchers.IO) {
        val start = System.nanoTime()
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(profile.host, profile.port), 4000)
            }
            ((System.nanoTime() - start) / 1_000_000L).toInt().coerceAtLeast(1)
        }.getOrNull()
    }

    /**
     * Benchmarks a single server profile end-to-end:
     * 1) Measures TCP connect ping to host:port
     * 2) Establishes a real protocol session (VLESS/VMess/Trojan/SS/SSH/SOCKS4/5/HTTP)
     * 3) Downloads a test payload from the internet through that tunnel to measure real speed.
     */
    suspend fun benchmarkProfile(
        profile: VpnProfile,
        allProfiles: List<VpnProfile> = emptyList()
    ): ProfileBenchmarkResult =
        withContext(Dispatchers.IO) {
            val latency = if (profile.protocol == com.example.model.VpnProtocol.LOAD_BALANCE) {
                val memberIds = profile.parsedMemberIds().toSet()
                val members = allProfiles.filter { memberIds.contains(it.id) }
                members.mapNotNull { measureLatency(it) }.minOrNull()
            } else {
                measureLatency(profile)
            }
            if (latency == null) {
                return@withContext ProfileBenchmarkResult(
                    latencyMs = -1,
                    downloadSpeedBps = -1L,
                    error = "Ping timeout"
                )
            }

            val connector = ProtocolConnectorFactory.create(profile, allProfiles) { true }
            try {
                connector.connect()

                val targets = listOf(
                    Triple("speed.cloudflare.com", 80, "/__down?bytes=262144"),
                    Triple("proof.ovh.net", 80, "/files/1Mb.dat")
                )

                var measuredSpeed: Long? = null
                var totalRead = 0L
                var lastErr: String? = null

                for ((host, port, path) in targets) {
                    val attempt = runCatching {
                        val stream = connector.openTcpStream(host, port)
                        stream.use { s ->
                            val req = buildString {
                                append("GET $path HTTP/1.1\r\n")
                                append("Host: $host\r\n")
                                append("User-Agent: NetShieldBenchmark/1.0\r\n")
                                append("Connection: close\r\n\r\n")
                            }
                            s.write(req.toByteArray(Charsets.US_ASCII))

                            val buf = ByteArray(16_384)
                            val startNs = System.nanoTime()
                            var headerSkipped = false
                            val headerBuf = StringBuilder()
                            var downloaded = 0L
                            val maxBytes = 262_144L
                            val maxDurationNs = 6_000_000_000L

                            while (downloaded < maxBytes && (System.nanoTime() - startNs) < maxDurationNs) {
                                val n = s.read(buf, 0, buf.size)
                                if (n <= 0) break
                                if (!headerSkipped) {
                                    val chunkStr = String(buf, 0, n, Charsets.ISO_8859_1)
                                    val prevLen = headerBuf.length
                                    headerBuf.append(chunkStr)
                                    val idx = headerBuf.indexOf("\r\n\r\n")
                                    if (idx >= 0) {
                                        headerSkipped = true
                                        val bodyStartInChunk = (idx + 4) - prevLen
                                        if (bodyStartInChunk in 0 until n) {
                                            downloaded += (n - bodyStartInChunk).toLong()
                                        }
                                    }
                                } else {
                                    downloaded += n.toLong()
                                }
                            }
                            val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                            if (downloaded > 0L) {
                                totalRead = downloaded
                                (downloaded * 1000L) / elapsedMs
                            } else {
                                null
                            }
                        }
                    }
                    val speed = attempt.getOrNull()
                    if (speed != null && speed > 0L) {
                        measuredSpeed = speed
                        break
                    } else {
                        lastErr = attempt.exceptionOrNull()?.message ?: "Empty response"
                    }
                }

                if (measuredSpeed != null) {
                    ProfileBenchmarkResult(
                        latencyMs = latency,
                        downloadSpeedBps = measuredSpeed,
                        bytesDownloaded = totalRead
                    )
                } else {
                    ProfileBenchmarkResult(
                        latencyMs = latency,
                        downloadSpeedBps = -1L,
                        error = lastErr ?: "Download test failed"
                    )
                }
            } catch (e: Exception) {
                ProfileBenchmarkResult(
                    latencyMs = latency,
                    downloadSpeedBps = -1L,
                    error = e.message ?: "Connection failed"
                )
            } finally {
                runCatching { connector.close() }
            }
        }

    /**
     * Queries exit IP and Geo-IP metadata.
     * When [activeProfile] is provided, opens the HTTP request directly through the active protocol
     * connector so that even in Per-App Split Tunneling mode, the reported IP and flag reflect
     * the VPN server's exit IP.
     */
    suspend fun fetchExitInfo(
        activeProfile: VpnProfile? = null,
        allProfiles: List<VpnProfile> = emptyList()
    ): ExitNetworkInfo =
        withContext(Dispatchers.IO) {
            val directAttempt = runCatching { queryViaOkHttp() }.getOrNull()
            if (directAttempt != null && directAttempt.ipAddress.isNotEmpty() && activeProfile == null) {
                return@withContext directAttempt
            }

            if (activeProfile != null) {
                val tunneledAttempt = runCatching { queryThroughConnector(activeProfile, allProfiles) }.getOrNull()
                if (tunneledAttempt != null && tunneledAttempt.ipAddress.isNotEmpty()) {
                    return@withContext tunneledAttempt
                }
            }

            if (directAttempt != null && directAttempt.ipAddress.isNotEmpty()) {
                return@withContext directAttempt
            }

            ExitNetworkInfo(
                isLoading = false,
                errorMessage = "Unable to resolve exit IP",
                checkedAtMillis = System.currentTimeMillis()
            )
        }

    private fun queryViaOkHttp(): ExitNetworkInfo? {
        val endpoints = listOf(
            "https://ipwho.is/",
            "https://ipapi.co/json/",
            "http://ip-api.com/json/"
        )
        for (url in endpoints) {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "NetShieldVPN/1.0")
                .build()
            runCatching {
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body?.string() ?: return@use null
                    parseGeoJson(body)
                }
            }.getOrNull()?.let { return it }
        }
        return null
    }

    private suspend fun queryThroughConnector(
        profile: VpnProfile,
        allProfiles: List<VpnProfile>
    ): ExitNetworkInfo? {
        val connector = ProtocolConnectorFactory.create(profile, allProfiles) { true }
        return try {
            val stream = connector.openTcpStream("ip-api.com", 80)
            stream.use { s ->
                val httpReq = buildString {
                    append("GET /json/ HTTP/1.1\r\n")
                    append("Host: ip-api.com\r\n")
                    append("User-Agent: NetShieldVPN/1.0\r\n")
                    append("Connection: close\r\n\r\n")
                }
                s.write(httpReq.toByteArray(Charsets.US_ASCII))
                val reader = BufferedReader(InputStreamReader(s.inputStream(), Charsets.UTF_8))
                val raw = reader.readText()
                val jsonBody = raw.substringAfter("\r\n\r\n", "").trim()
                if (jsonBody.startsWith("{")) parseGeoJson(jsonBody) else null
            }
        } finally {
            runCatching { connector.close() }
        }
    }

    private fun parseGeoJson(raw: String): ExitNetworkInfo? {
        val json = JSONObject(raw)
        val ip = json.optString("ip").ifBlank { json.optString("query") }.trim()
        if (ip.isEmpty()) return null
        val countryCode = json.optString("country_code").ifBlank { json.optString("countryCode") }.trim()
        val countryName = json.optString("country_name").ifBlank { json.optString("country") }.trim()
        val city = json.optString("city").trim()
        val isp = json.optJSONObject("connection")?.optString("isp")
            ?: json.optString("org").ifBlank { json.optString("isp") }
        return ExitNetworkInfo(
            ipAddress = ip,
            countryCode = countryCode,
            countryName = countryName,
            city = city,
            isp = isp.trim(),
            isLoading = false,
            errorMessage = null,
            checkedAtMillis = System.currentTimeMillis()
        )
    }
}
