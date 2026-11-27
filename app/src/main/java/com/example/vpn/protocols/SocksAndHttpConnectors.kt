package com.example.vpn.protocols

import android.util.Base64
import com.example.model.VpnProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.Socket

class Socks5Connector(
    private val profile: VpnProfile,
    private val protector: (Socket) -> Boolean
) : UpstreamConnector {

    override suspend fun probeHealth(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            ProtectedSocketFactory.openTcp(
                host = profile.host,
                port = profile.port,
                timeoutMs = 4_500,
                protector = protector,
                soTimeoutMs = 4_500,
                retryOnTransient = false
            ).use { s -> performHandshake(s) }
            true
        }.getOrDefault(false)
    }

    override suspend fun connect() = withContext(Dispatchers.IO) {
        // Verify handshake works with proxy server on connect
        val probe = ProtectedSocketFactory.openTcp(profile.host, profile.port, 8000, protector)
        probe.use { s ->
            performHandshake(s)
        }
    }

    override suspend fun openTcpStream(dstHost: String, dstPort: Int): TunnelStream =
        withContext(Dispatchers.IO) {
            val socket = ProtectedSocketFactory.openTcp(profile.host, profile.port, 10_000, protector)
            try {
                performHandshake(socket)
                val out = socket.getOutputStream()
                val inp = socket.getInputStream()

                val addrBytes = encodeSocksAddress(dstHost, dstPort)
                val req = ByteArray(3 + addrBytes.size)
                req[0] = 0x05 // VER
                req[1] = 0x01 // CMD CONNECT
                req[2] = 0x00 // RSV
                System.arraycopy(addrBytes, 0, req, 3, addrBytes.size)
                out.write(req)
                out.flush()

                val header = readExact(inp, 4)
                if (header[0] != 0x05.toByte() || header[1] != 0x00.toByte()) {
                    throw IOException("SOCKS5 connect rejected with code ${header[1].toInt() and 0xFF}")
                }
                when (header[3].toInt() and 0xFF) {
                    0x01 -> readExact(inp, 6) // IPv4 + port
                    0x03 -> {
                        val len = readExact(inp, 1)[0].toInt() and 0xFF
                        readExact(inp, len + 2)
                    }
                    0x04 -> readExact(inp, 18) // IPv6 + port
                }
                SocketTunnelStream(socket, inp, out)
            } catch (e: Exception) {
                runCatching { socket.close() }
                throw e
            }
        }

    private fun performHandshake(socket: Socket) {
        val out = socket.getOutputStream()
        val inp = socket.getInputStream()
        val hasAuth = profile.usernameOrUuid.isNotBlank()
        if (hasAuth) {
            out.write(byteArrayOf(0x05, 0x02, 0x00, 0x02))
        } else {
            out.write(byteArrayOf(0x05, 0x01, 0x00))
        }
        out.flush()

        val choice = readExact(inp, 2)
        if (choice[0] != 0x05.toByte()) throw IOException("Invalid SOCKS5 version")
        when (choice[1].toInt() and 0xFF) {
            0x00 -> return
            0x02 -> {
                val u = profile.usernameOrUuid.toByteArray(Charsets.UTF_8)
                val p = profile.passwordOrSecret.toByteArray(Charsets.UTF_8)
                val auth = ByteArrayOutputStream().apply {
                    write(0x01)
                    write(u.size)
                    write(u)
                    write(p.size)
                    write(p)
                }
                out.write(auth.toByteArray())
                out.flush()
                val authResp = readExact(inp, 2)
                if (authResp[1] != 0x00.toByte()) {
                    throw IOException("SOCKS5 authentication failed")
                }
            }
            else -> throw IOException("SOCKS5 no acceptable authentication method")
        }
    }

    override fun close() = Unit
}

class Socks4Connector(
    private val profile: VpnProfile,
    private val protector: (Socket) -> Boolean
) : UpstreamConnector {

    override suspend fun probeHealth(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            ProtectedSocketFactory.openTcp(
                host = profile.host,
                port = profile.port,
                timeoutMs = 4_500,
                protector = protector,
                soTimeoutMs = 4_500,
                retryOnTransient = false
            ).close()
            true
        }.getOrDefault(false)
    }

    override suspend fun connect() = withContext(Dispatchers.IO) {
        val probe = ProtectedSocketFactory.openTcp(profile.host, profile.port, 8000, protector)
        probe.close()
    }

    override suspend fun openTcpStream(dstHost: String, dstPort: Int): TunnelStream =
        withContext(Dispatchers.IO) {
            val socket = ProtectedSocketFactory.openTcp(profile.host, profile.port, 10_000, protector)
            try {
                val out = socket.getOutputStream()
                val inp = socket.getInputStream()
                val userIdBytes = profile.usernameOrUuid.toByteArray(Charsets.UTF_8)
                val ipv4Parts = dstHost.split(".")
                val isIpv4 = ipv4Parts.size == 4 && ipv4Parts.all { it.toIntOrNull() in 0..255 }

                val req = ByteArrayOutputStream()
                req.write(0x04) // VN = 4
                req.write(0x01) // CD = CONNECT
                req.write((dstPort ushr 8) and 0xFF)
                req.write(dstPort and 0xFF)
                if (isIpv4) {
                    ipv4Parts.forEach { req.write(it.toInt()) }
                    req.write(userIdBytes)
                    req.write(0x00)
                } else {
                    // SOCKS4a extension: 0.0.0.1 + userId + 0 + domain + 0
                    req.write(byteArrayOf(0x00, 0x00, 0x00, 0x01))
                    req.write(userIdBytes)
                    req.write(0x00)
                    req.write(dstHost.toByteArray(Charsets.UTF_8))
                    req.write(0x00)
                }
                out.write(req.toByteArray())
                out.flush()

                val resp = readExact(inp, 8)
                if (resp[1] != 0x5A.toByte()) {
                    throw IOException("SOCKS4 connect rejected with code ${resp[1].toInt() and 0xFF}")
                }
                SocketTunnelStream(socket, inp, out)
            } catch (e: Exception) {
                runCatching { socket.close() }
                throw e
            }
        }

    override fun close() = Unit
}

class HttpProxyConnector(
    private val profile: VpnProfile,
    private val protector: (Socket) -> Boolean
) : UpstreamConnector {

    override val preferDnsOverHttps: Boolean = true

    override suspend fun probeHealth(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val probe = if (profile.tlsEnabled) {
                ProtectedSocketFactory.openTls(
                    host = profile.host,
                    port = profile.port,
                    sni = profile.sni.ifBlank { profile.host },
                    allowInsecure = profile.allowInsecure,
                    timeoutMs = 4_500,
                    protector = protector,
                    soTimeoutMs = 4_500,
                    retryOnTransient = false
                )
            } else {
                ProtectedSocketFactory.openTcp(
                    host = profile.host,
                    port = profile.port,
                    timeoutMs = 4_500,
                    protector = protector,
                    soTimeoutMs = 4_500,
                    retryOnTransient = false
                )
            }
            probe.close()
            true
        }.getOrDefault(false)
    }

    override suspend fun connect() = withContext(Dispatchers.IO) {
        val probe = if (profile.tlsEnabled) {
            ProtectedSocketFactory.openTls(
                host = profile.host,
                port = profile.port,
                sni = profile.sni.ifBlank { profile.host },
                allowInsecure = profile.allowInsecure,
                timeoutMs = 8000,
                protector = protector
            )
        } else {
            ProtectedSocketFactory.openTcp(profile.host, profile.port, 8000, protector)
        }
        probe.close()
    }

    override suspend fun openTcpStream(dstHost: String, dstPort: Int): TunnelStream =
        withContext(Dispatchers.IO) {
            val socket = if (profile.tlsEnabled) {
                ProtectedSocketFactory.openTls(
                    host = profile.host,
                    port = profile.port,
                    sni = profile.sni.ifBlank { profile.host },
                    allowInsecure = profile.allowInsecure,
                    timeoutMs = 10_000,
                    protector = protector
                )
            } else {
                ProtectedSocketFactory.openTcp(profile.host, profile.port, 10_000, protector)
            }
            try {
                val out = socket.getOutputStream()
                val inp = socket.getInputStream()
                val target = "$dstHost:$dstPort"
                val headers = buildString {
                    append("CONNECT $target HTTP/1.1\r\n")
                    append("Host: $target\r\n")
                    append("Proxy-Connection: Keep-Alive\r\n")
                    if (profile.usernameOrUuid.isNotBlank()) {
                        val creds = "${profile.usernameOrUuid}:${profile.passwordOrSecret}"
                        val encoded = Base64.encodeToString(creds.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                        append("Proxy-Authorization: Basic $encoded\r\n")
                    }
                    append("\r\n")
                }
                out.write(headers.toByteArray(Charsets.US_ASCII))
                out.flush()

                val statusAndHeaders = readHttpHeaderBlock(inp)
                val statusLine = statusAndHeaders.lineSequence().firstOrNull().orEmpty()
                if (!statusLine.contains(" 200")) {
                    throw IOException("HTTP Proxy rejected CONNECT: $statusLine")
                }
                SocketTunnelStream(socket, inp, out)
            } catch (e: Exception) {
                runCatching { socket.close() }
                throw e
            }
        }

    private fun readHttpHeaderBlock(inp: java.io.InputStream): String {
        val sb = StringBuilder()
        var tail = 0
        while (sb.length < 16_384) {
            val b = inp.read()
            if (b < 0) throw IOException("EOF reading HTTP proxy response")
            sb.append(b.toChar())
            tail = ((tail shl 8) or (b and 0xFF))
            if (tail == 0x0D0A0D0A) break
        }
        return sb.toString()
    }

    override fun close() = Unit
}
