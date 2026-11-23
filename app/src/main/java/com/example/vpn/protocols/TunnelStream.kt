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
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
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

    suspend fun connect()
    suspend fun openTcpStream(dstHost: String, dstPort: Int): TunnelStream

    /**
     * Forwards a raw DNS query packet (UDP payload) through the active tunnel using DNS-over-TCP
     * (RFC 1035 2-byte length prefix) to the configured DNS resolver.
     */
    suspend fun forwardDnsQuery(dnsQuery: ByteArray, dnsServer: String = "1.1.1.1"): ByteArray? {
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
