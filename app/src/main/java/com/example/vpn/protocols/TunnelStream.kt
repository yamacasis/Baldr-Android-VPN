package com.example.vpn.protocols

import android.annotation.SuppressLint
import com.example.model.VpnProfile
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

interface TunnelStream : Closeable {
    fun read(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size): Int
    fun write(data: ByteArray, offset: Int = 0, length: Int = data.size)
    fun inputStream(): InputStream
}

class SocketTunnelStream(
    private val socket: Socket,
    private val input: InputStream = socket.getInputStream(),
    private val output: OutputStream = socket.getOutputStream()
) : TunnelStream {
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return input.read(buffer, offset, length)
    }

    override fun write(data: ByteArray, offset: Int, length: Int) {
        output.write(data, offset, length)
        output.flush()
    }

    override fun inputStream(): InputStream = input

    override fun close() {
        runCatching { socket.close() }
    }
}

interface UpstreamConnector : Closeable {
    val supportsStatefulHealthCheck: Boolean
        get() = false

    fun isHealthy(): Boolean = true

    /**
     * Performs an active lightweight reachability/liveness check against the upstream tunnel.
     */
    suspend fun probeHealth(): Boolean = isHealthy()

    fun setLowBatteryMode(enabled: Boolean) {}

    val preferDnsOverHttps: Boolean
        get() = false

    suspend fun connect()
    suspend fun openTcpStream(dstHost: String, dstPort: Int): TunnelStream

    /**
     * Forwards a raw DNS query packet (UDP payload) through the active tunnel.
     * Supports both DNS-over-TCP (port 53) and automatic DNS-over-HTTPS (port 443, RFC 8484)
     * fallback so free HTTP/SOCKS/V2Ray nodes that block port 53 still resolve domains reliably.
     */
    suspend fun forwardDnsQuery(dnsQuery: ByteArray, dnsServer: String = "1.1.1.1"): ByteArray? {
        if (preferDnsOverHttps) {
            forwardDnsOverHttps(dnsQuery)?.let { return it }
            return runCatching { forwardDnsOverTcp(dnsQuery, dnsServer) }.getOrNull()
        }
        val tcpResp = runCatching { forwardDnsOverTcp(dnsQuery, dnsServer) }.getOrNull()
        if (tcpResp != null) return tcpResp
        return forwardDnsOverHttps(dnsQuery)
    }

    suspend fun forwardDnsOverTcp(dnsQuery: ByteArray, dnsServer: String = "1.1.1.1"): ByteArray? {
        val stream = openTcpStream(dnsServer, 53)
        return try {
            val framed = ByteArray(2 + dnsQuery.size)
            framed[0] = ((dnsQuery.size ushr 8) and 0xFF).toByte()
            framed[1] = (dnsQuery.size and 0xFF).toByte()
            System.arraycopy(dnsQuery, 0, framed, 2, dnsQuery.size)
            stream.write(framed)

            val inp = stream.inputStream()
            val lenBytes = readExact(inp, 2)
            val respLen = ((lenBytes[0].toInt() and 0xFF) shl 8) or (lenBytes[1].toInt() and 0xFF)
            if (respLen <= 0 || respLen > 8192) return null
            readExact(inp, respLen)
        } finally {
            runCatching { stream.close() }
        }
    }

    suspend fun forwardDnsOverHttps(dnsQuery: ByteArray): ByteArray? {
        val dohEndpoints = listOf(
            "1.1.1.1" to "cloudflare-dns.com",
            "8.8.8.8" to "dns.google"
        )
        for ((ip, sni) in dohEndpoints) {
            val resp = runCatching {
                DnsOverHttpsTunnel.query(this, dnsQuery, ip, sni)
            }.getOrNull()
            if (resp != null && resp.size >= 12) return resp
        }
        return null
    }
}

/**
 * Pure SSLEngine-based RFC 8484 DNS-over-HTTPS client operating over any [TunnelStream]
 * without needing a native FileDescriptor (compatible with WebSocket, VMess, VLESS, Trojan,
 * Shadowsocks, SOCKS4/5, HTTP CONNECT, and SSH).
 */
internal object DnsOverHttpsTunnel {

    suspend fun query(
        connector: UpstreamConnector,
        dnsQuery: ByteArray,
        targetIp: String,
        sniHost: String
    ): ByteArray? {
        val stream = connector.openTcpStream(targetIp, 443)
        return try {
            queryOverStream(stream, dnsQuery, sniHost)
        } finally {
            runCatching { stream.close() }
        }
    }

    internal fun queryOverStream(
        stream: TunnelStream,
        dnsQuery: ByteArray,
        sniHost: String,
        sslContext: SSLContext = SSLContext.getDefault()
    ): ByteArray? {
        val engine = sslContext.createSSLEngine(sniHost, 443).apply {
            useClientMode = true
            val params = sslParameters
            if (sniHost.isNotBlank()) {
                params.serverNames = listOf(SNIHostName(sniHost))
            }
            sslParameters = params
            beginHandshake()
        }

        val session = engine.session
        var myNetBuf = ByteBuffer.allocate(session.packetBufferSize.coerceAtLeast(16_384))
        var peerNetBuf = ByteBuffer.allocate(session.packetBufferSize.coerceAtLeast(16_384))
        var peerAppBuf = ByteBuffer.allocate(session.applicationBufferSize.coerceAtLeast(16_384))
        val emptyAppBuf = ByteBuffer.allocate(0)
        val readTmp = ByteArray(8_192)

        // 1) TLS Handshake
        var hsStatus = engine.handshakeStatus
        var hsSteps = 0
        while (hsStatus != SSLEngineResult.HandshakeStatus.FINISHED &&
            hsStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING
        ) {
            if (++hsSteps > 200) throw IOException("TLS handshake step limit exceeded")
            when (hsStatus) {
                SSLEngineResult.HandshakeStatus.NEED_TASK -> {
                    var task = engine.delegatedTask
                    while (task != null) {
                        task.run()
                        task = engine.delegatedTask
                    }
                    hsStatus = engine.handshakeStatus
                }
                SSLEngineResult.HandshakeStatus.NEED_WRAP -> {
                    myNetBuf.clear()
                    val res = engine.wrap(emptyAppBuf, myNetBuf)
                    when (res.status) {
                        SSLEngineResult.Status.OK -> {
                            myNetBuf.flip()
                            if (myNetBuf.hasRemaining()) {
                                val out = ByteArray(myNetBuf.remaining())
                                myNetBuf.get(out)
                                stream.write(out)
                            }
                            hsStatus = res.handshakeStatus
                        }
                        SSLEngineResult.Status.BUFFER_OVERFLOW -> {
                            myNetBuf = ByteBuffer.allocate(myNetBuf.capacity() * 2)
                        }
                        SSLEngineResult.Status.CLOSED -> throw IOException("TLS closed during wrap")
                        else -> hsStatus = res.handshakeStatus
                    }
                }
                SSLEngineResult.HandshakeStatus.NEED_UNWRAP -> {
                    peerNetBuf.flip()
                    val res = engine.unwrap(peerNetBuf, peerAppBuf)
                    peerNetBuf.compact()
                    when (res.status) {
                        SSLEngineResult.Status.OK -> {
                            hsStatus = res.handshakeStatus
                        }
                        SSLEngineResult.Status.BUFFER_UNDERFLOW -> {
                            if (peerNetBuf.remaining() == 0) {
                                peerNetBuf = growByteBuffer(peerNetBuf)
                            }
                            val n = stream.read(readTmp, 0, minOf(readTmp.size, peerNetBuf.remaining()))
                            if (n < 0) throw EOFException("EOF during DoH TLS handshake")
                            if (n > 0) peerNetBuf.put(readTmp, 0, n)
                        }
                        SSLEngineResult.Status.BUFFER_OVERFLOW -> {
                            peerAppBuf = ByteBuffer.allocate(peerAppBuf.capacity() * 2)
                        }
                        SSLEngineResult.Status.CLOSED -> throw IOException("TLS closed during unwrap")
                        else -> hsStatus = res.handshakeStatus
                    }
                }
                else -> break
            }
        }

        // 2) Send RFC 8484 HTTP/1.1 POST /dns-query request
        val reqHeader = buildString {
            append("POST /dns-query HTTP/1.1\r\n")
            append("Host: ").append(sniHost).append("\r\n")
            append("Content-Type: application/dns-message\r\n")
            append("Accept: application/dns-message\r\n")
            append("Content-Length: ").append(dnsQuery.size).append("\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray(Charsets.US_ASCII)
        val reqBytes = ByteArray(reqHeader.size + dnsQuery.size)
        System.arraycopy(reqHeader, 0, reqBytes, 0, reqHeader.size)
        System.arraycopy(dnsQuery, 0, reqBytes, reqHeader.size, dnsQuery.size)

        val appOutBuf = ByteBuffer.wrap(reqBytes)
        while (appOutBuf.hasRemaining()) {
            myNetBuf.clear()
            val res = engine.wrap(appOutBuf, myNetBuf)
            when (res.status) {
                SSLEngineResult.Status.OK -> {
                    myNetBuf.flip()
                    if (myNetBuf.hasRemaining()) {
                        val out = ByteArray(myNetBuf.remaining())
                        myNetBuf.get(out)
                        stream.write(out)
                    }
                }
                SSLEngineResult.Status.BUFFER_OVERFLOW -> {
                    myNetBuf = ByteBuffer.allocate(myNetBuf.capacity() * 2)
                }
                else -> throw IOException("DoH TLS wrap failed: ${res.status}")
            }
        }

        // 3) Read and decrypt HTTP response until complete body is received
        val plainOut = ByteArrayOutputStream()
        var done = false
        while (!done && plainOut.size() < 32_768) {
            peerNetBuf.flip()
            var needMoreNet = false
            while (peerNetBuf.hasRemaining()) {
                peerAppBuf.clear()
                val res = engine.unwrap(peerNetBuf, peerAppBuf)
                when (res.status) {
                    SSLEngineResult.Status.OK -> {
                        peerAppBuf.flip()
                        if (peerAppBuf.hasRemaining()) {
                            val chunk = ByteArray(peerAppBuf.remaining())
                            peerAppBuf.get(chunk)
                            plainOut.write(chunk)
                        }
                        if (isHttpResponseComplete(plainOut.toByteArray())) {
                            done = true
                            break
                        }
                    }
                    SSLEngineResult.Status.BUFFER_UNDERFLOW -> {
                        needMoreNet = true
                        break
                    }
                    SSLEngineResult.Status.BUFFER_OVERFLOW -> {
                        peerAppBuf = ByteBuffer.allocate(peerAppBuf.capacity() * 2)
                    }
                    SSLEngineResult.Status.CLOSED -> {
                        done = true
                        break
                    }
                    else -> break
                }
            }
            peerNetBuf.compact()
            if (done) break

            if (needMoreNet || peerNetBuf.position() == 0) {
                if (peerNetBuf.remaining() == 0) {
                    peerNetBuf = growByteBuffer(peerNetBuf)
                }
                val n = stream.read(readTmp, 0, minOf(readTmp.size, peerNetBuf.remaining()))
                if (n < 0) break
                if (n > 0) peerNetBuf.put(readTmp, 0, n)
            }
        }

        return extractHttpBody(plainOut.toByteArray())
    }

    private fun growByteBuffer(buf: ByteBuffer): ByteBuffer {
        val next = ByteBuffer.allocate(buf.capacity() * 2)
        buf.flip()
        next.put(buf)
        return next
    }

    private fun findHeaderEnd(data: ByteArray): Int {
        for (i in 0 until data.size - 3) {
            if (data[i] == 0x0D.toByte() &&
                data[i + 1] == 0x0A.toByte() &&
                data[i + 2] == 0x0D.toByte() &&
                data[i + 3] == 0x0A.toByte()
            ) {
                return i + 4
            }
        }
        return -1
    }

    private fun isHttpResponseComplete(raw: ByteArray): Boolean {
        val bodyStart = findHeaderEnd(raw)
        if (bodyStart < 0) return false
        val headers = String(raw, 0, bodyStart, Charsets.ISO_8859_1)
        val contentLength = parseContentLength(headers)
        if (contentLength != null) {
            return raw.size - bodyStart >= contentLength
        }
        if (headers.contains("transfer-encoding:", ignoreCase = true) &&
            headers.contains("chunked", ignoreCase = true)
        ) {
            return decodeChunkedBody(raw, bodyStart) != null
        }
        return false
    }

    internal fun extractHttpBody(raw: ByteArray): ByteArray? {
        val bodyStart = findHeaderEnd(raw)
        if (bodyStart < 0) return null
        val headers = String(raw, 0, bodyStart, Charsets.ISO_8859_1)
        val statusLine = headers.lineSequence().firstOrNull().orEmpty()
        if (!statusLine.contains(" 200")) return null

        val contentLength = parseContentLength(headers)
        if (contentLength != null) {
            if (contentLength <= 0 || raw.size - bodyStart < contentLength) return null
            return raw.copyOfRange(bodyStart, bodyStart + contentLength)
        }
        if (headers.contains("transfer-encoding:", ignoreCase = true) &&
            headers.contains("chunked", ignoreCase = true)
        ) {
            return decodeChunkedBody(raw, bodyStart)
        }
        val bodyLen = raw.size - bodyStart
        return if (bodyLen >= 12) raw.copyOfRange(bodyStart, raw.size) else null
    }

    private fun parseContentLength(headers: String): Int? {
        for (line in headers.lineSequence()) {
            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                return line.substringAfter(":").trim().toIntOrNull()
            }
        }
        return null
    }

    private fun decodeChunkedBody(raw: ByteArray, bodyStart: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        var pos = bodyStart
        while (pos < raw.size) {
            var crlf = -1
            for (i in pos until raw.size - 1) {
                if (raw[i] == 0x0D.toByte() && raw[i + 1] == 0x0A.toByte()) {
                    crlf = i
                    break
                }
            }
            if (crlf < 0) return null
            val sizeHex = String(raw, pos, crlf - pos, Charsets.US_ASCII).substringBefore(';').trim()
            val chunkSize = sizeHex.toIntOrNull(16) ?: return null
            pos = crlf + 2
            if (chunkSize == 0) {
                return out.toByteArray()
            }
            if (pos + chunkSize + 2 > raw.size) return null
            out.write(raw, pos, chunkSize)
            pos += chunkSize + 2
        }
        return null
    }
}

fun readExact(input: InputStream, count: Int): ByteArray {
    val buf = ByteArray(count)
    var read = 0
    while (read < count) {
        val n = input.read(buf, read, count - read)
        if (n < 0) throw EOFException("Unexpected EOF while reading $count bytes")
        read += n
    }
    return buf
}

object ProtectedSocketFactory {

    private data class CachedHost(
        val address: InetAddress,
        val cachedAtMillis: Long
    )

    private const val DNS_CACHE_TTL_MS = 10 * 60 * 1000L
    private const val MAX_DNS_CACHE_SIZE = 128
    private const val SOCKET_BUFFER_BYTES = 65_536
    private const val TRANSIENT_RETRY_DELAY_MS = 200L

    private val hostCache = ConcurrentHashMap<String, CachedHost>()

    internal fun clearHostCache() {
        hostCache.clear()
    }

    internal fun seedHostCacheForTest(host: String, address: InetAddress, cachedAtMillis: Long = System.currentTimeMillis()) {
        hostCache[host.trim().lowercase()] = CachedHost(address, cachedAtMillis)
    }

    /**
     * Resolves host preferring IPv4 and falls back to the last known good IP (up to 10 minutes)
     * during transient mobile DNS blips or Wi-Fi/cellular handoffs.
     */
    internal fun resolveWithFallback(host: String): InetAddress {
        val cleanHost = host.trim()
        val cacheKey = cleanHost.lowercase()
        val now = System.currentTimeMillis()
        if (isIpLiteral(cleanHost)) {
            return InetAddress.getByName(cleanHost)
        }
        return try {
            val all = InetAddress.getAllByName(cleanHost)
            val chosen = all.firstOrNull { it is Inet4Address } ?: all.first()
            if (hostCache.size >= MAX_DNS_CACHE_SIZE) {
                hostCache.entries.removeIf { now - it.value.cachedAtMillis > DNS_CACHE_TTL_MS }
            }
            hostCache[cacheKey] = CachedHost(chosen, now)
            chosen
        } catch (err: Exception) {
            val cached = hostCache[cacheKey]
            if (cached != null && now - cached.cachedAtMillis <= DNS_CACHE_TTL_MS) {
                cached.address
            } else {
                throw err
            }
        }
    }

    fun openTcp(
        host: String,
        port: Int,
        timeoutMs: Int = 10_000,
        protector: (Socket) -> Boolean,
        soTimeoutMs: Int = 120_000,
        retryOnTransient: Boolean = true
    ): Socket {
        val attempts = if (retryOnTransient) 2 else 1
        var lastError: Exception? = null
        for (attempt in 1..attempts) {
            val socket = Socket()
            try {
                protector(socket)
                socket.tcpNoDelay = true
                socket.keepAlive = true
                runCatching { socket.sendBufferSize = SOCKET_BUFFER_BYTES }
                runCatching { socket.receiveBufferSize = SOCKET_BUFFER_BYTES }
                val targetAddr = resolveWithFallback(host)
                socket.connect(InetSocketAddress(targetAddr, port), timeoutMs)
                socket.soTimeout = soTimeoutMs.coerceAtLeast(0)
                return socket
            } catch (e: Exception) {
                runCatching { socket.close() }
                lastError = e
                val transient = e is SocketTimeoutException || e is ConnectException
                if (attempt < attempts && transient) {
                    runCatching { Thread.sleep(TRANSIENT_RETRY_DELAY_MS) }
                    continue
                }
                throw e
            }
        }
        throw lastError ?: IOException("Unable to connect to $host:$port")
    }

    @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    fun openTls(
        host: String,
        port: Int,
        sni: String = host,
        allowInsecure: Boolean = false,
        timeoutMs: Int = 10_000,
        protector: (Socket) -> Boolean,
        soTimeoutMs: Int = 120_000,
        retryOnTransient: Boolean = true
    ): SSLSocket {
        val rawSocket = openTcp(
            host = host,
            port = port,
            timeoutMs = timeoutMs,
            protector = protector,
            soTimeoutMs = timeoutMs,
            retryOnTransient = retryOnTransient
        )
        return try {
            val sslContext = if (allowInsecure) {
                val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                })
                SSLContext.getInstance("TLS").apply { init(null, trustAll, SecureRandom()) }
            } else {
                SSLContext.getDefault()
            }
            val factory = sslContext.socketFactory
            val effectiveSni = sni.ifBlank { host }
            val sslSocket = factory.createSocket(rawSocket, effectiveSni, port, true) as SSLSocket
            val params = sslSocket.sslParameters
            if (effectiveSni.isNotBlank() && !isIpLiteral(effectiveSni)) {
                params.serverNames = listOf(SNIHostName(effectiveSni))
            }
            sslSocket.sslParameters = params
            sslSocket.startHandshake()
            sslSocket.soTimeout = soTimeoutMs.coerceAtLeast(0)
            sslSocket
        } catch (e: Exception) {
            runCatching { rawSocket.close() }
            throw e
        }
    }

    private fun isIpLiteral(host: String): Boolean {
        return host.matches(Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$")) || host.contains(":")
    }
}

fun encodeSocksAddress(host: String, port: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val ipv4 = host.split(".")
    if (ipv4.size == 4 && ipv4.all { it.toIntOrNull() in 0..255 }) {
        out.write(0x01)
        ipv4.forEach { out.write(it.toInt()) }
    } else {
        val hostBytes = host.toByteArray(Charsets.UTF_8)
        if (hostBytes.size > 255) throw IOException("Host too long: $host")
        out.write(0x03)
        out.write(hostBytes.size)
        out.write(hostBytes)
    }
    out.write((port ushr 8) and 0xFF)
    out.write(port and 0xFF)
    return out.toByteArray()
}

object ProtocolConnectorFactory {
    fun create(
        profile: VpnProfile,
        allProfiles: List<VpnProfile> = emptyList(),
        protector: (Socket) -> Boolean
    ): UpstreamConnector {
        return when (profile.protocol) {
            com.example.model.VpnProtocol.SOCKS5 -> Socks5Connector(profile, protector)
            com.example.model.VpnProtocol.SOCKS4 -> Socks4Connector(profile, protector)
            com.example.model.VpnProtocol.HTTP_PROXY -> HttpProxyConnector(profile, protector)
            com.example.model.VpnProtocol.SSH -> SshConnector(profile, protector)
            com.example.model.VpnProtocol.VLESS -> VlessConnector(profile, protector)
            com.example.model.VpnProtocol.TROJAN -> TrojanConnector(profile, protector)
            com.example.model.VpnProtocol.SHADOWSOCKS -> ShadowsocksConnector(profile, protector)
            com.example.model.VpnProtocol.VMESS -> VmessConnector(profile, protector)
            com.example.model.VpnProtocol.LOAD_BALANCE -> {
                val ids = profile.parsedMemberIds().toSet()
                val members = allProfiles.filter { ids.contains(it.id) }
                LoadBalanceConnector(profile, members, protector)
            }
        }
    }
}
