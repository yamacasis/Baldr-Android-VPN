package com.example.vpn.protocols

import android.annotation.SuppressLint
import android.os.Build
import com.example.model.TransportType
import com.example.model.VpnProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.InetAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.SocketFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Transport helper supporting raw TCP, TLS, and WebSocket (ws/wss) over VpnService.protect().
 */
object V2RayTransport {

    fun openRawStream(profile: VpnProfile, protector: (Socket) -> Boolean): TunnelStream {
        return when (profile.transport) {
            TransportType.WS -> openWebSocketStream(profile, protector)
            TransportType.TCP -> {
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
                SocketTunnelStream(socket)
            }
        }
    }

    @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    private fun openWebSocketStream(profile: VpnProfile, protector: (Socket) -> Boolean): TunnelStream {
        val scheme = if (profile.tlsEnabled) "wss" else "ws"
        val cleanPath = if (profile.wsPath.startsWith("/")) profile.wsPath else "/${profile.wsPath}"
        val url = "$scheme://${profile.host}:${profile.port}$cleanPath"

        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .socketFactory(object : SocketFactory() {
                override fun createSocket(): Socket = Socket().also { protector(it) }
                override fun createSocket(host: String, port: Int): Socket =
                    ProtectedSocketFactory.openTcp(host, port, 10_000, protector)
                override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
                    ProtectedSocketFactory.openTcp(host, port, 10_000, protector)
                override fun createSocket(host: InetAddress, port: Int): Socket =
                    ProtectedSocketFactory.openTcp(host.hostAddress ?: "", port, 10_000, protector)
                override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
                    ProtectedSocketFactory.openTcp(address.hostAddress ?: "", port, 10_000, protector)
            })

        if (profile.allowInsecure && profile.tlsEnabled) {
            val trustAll = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val ctx = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
            }
            builder.sslSocketFactory(ctx.socketFactory, trustAll)
            builder.hostnameVerifier { _, _ -> true }
        }

        val client = builder.build()
        val reqBuilder = Request.Builder().url(url)
        val wsHostHeader = profile.wsHost.ifBlank { profile.sni }
        if (wsHostHeader.isNotBlank()) {
            reqBuilder.header("Host", wsHostHeader)
        }

        val pipedIn = PipedInputStream(65_536)
        val pipedOut = PipedOutputStream(pipedIn)
        val openLatch = CountDownLatch(1)
        var openError: Throwable? = null

        val ws = client.newWebSocket(reqBuilder.build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                openLatch.countDown()
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                runCatching {
                    pipedOut.write(bytes.toByteArray())
                    pipedOut.flush()
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                runCatching { pipedOut.close() }
                webSocket.close(1000, null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                openError = t
                openLatch.countDown()
                runCatching { pipedOut.close() }
            }
        })

        if (!openLatch.await(10, TimeUnit.SECONDS)) {
            ws.cancel()
            throw IOException("WebSocket handshake timeout")
        }
        openError?.let {
            ws.cancel()
            throw IOException("WebSocket failed: ${it.message}", it)
        }

        return object : TunnelStream {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                pipedIn.read(buffer, offset, length)

            override fun write(data: ByteArray, offset: Int, length: Int) {
                if (!ws.send(data.toByteString(offset, length))) {
                    throw IOException("WebSocket write failed")
                }
            }

            override fun inputStream(): InputStream = pipedIn

            override fun close() {
                runCatching { ws.close(1000, "closed") }
                runCatching { pipedIn.close() }
                runCatching { pipedOut.close() }
                client.dispatcher.executorService.shutdown()
            }
        }
    }
}

/**
 * VLESS protocol v0 connector (TCP, TLS, WS, WSS).
 */
class VlessConnector(
    private val profile: VpnProfile,
    private val protector: (Socket) -> Boolean
) : UpstreamConnector {

    private val uuidBytes: ByteArray by lazy {
        val uuid = UUID.fromString(profile.usernameOrUuid.trim())
        ByteBuffer.allocate(16)
            .putLong(uuid.mostSignificantBits)
            .putLong(uuid.leastSignificantBits)
            .array()
    }

    override suspend fun connect() = withContext(Dispatchers.IO) {
        // Validate UUID format and transport reachability
        uuidBytes
        val probe = V2RayTransport.openRawStream(profile, protector)
        probe.close()
    }

    override suspend fun openTcpStream(dstHost: String, dstPort: Int): TunnelStream =
        withContext(Dispatchers.IO) {
            val raw = V2RayTransport.openRawStream(profile, protector)
            try {
                val out = ByteArrayOutputStream()
                out.write(0x00) // Version 0
                out.write(uuidBytes)
                out.write(0x00) // Addons length 0
                out.write(0x01) // Command TCP
                out.write((dstPort ushr 8) and 0xFF)
                out.write(dstPort and 0xFF)

                val ipv4 = dstHost.split(".")
                if (ipv4.size == 4 && ipv4.all { it.toIntOrNull() in 0..255 }) {
                    out.write(0x01)
                    ipv4.forEach { out.write(it.toInt()) }
                } else {
                    val domainBytes = dstHost.toByteArray(Charsets.UTF_8)
                    out.write(0x02)
                    out.write(domainBytes.size)
                    out.write(domainBytes)
                }
                raw.write(out.toByteArray())
                VlessResponseStrippingStream(raw)
            } catch (e: Exception) {
                runCatching { raw.close() }
                throw e
            }
        }

    override fun close() = Unit

    private class VlessResponseStrippingStream(private val delegate: TunnelStream) : TunnelStream {
        private var headerStripped = false

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (!headerStripped) {
                val inp = delegate.inputStream()
                val header = readExact(inp, 2)
                val addonsLen = header[1].toInt() and 0xFF
                if (addonsLen > 0) readExact(inp, addonsLen)
                headerStripped = true
            }
            return delegate.read(buffer, offset, length)
        }

        override fun write(data: ByteArray, offset: Int, length: Int) =
            delegate.write(data, offset, length)

        override fun inputStream(): InputStream = object : InputStream() {
            override fun read(): Int {
                val b = ByteArray(1)
                val n = read(b, 0, 1)
                return if (n < 0) -1 else b[0].toInt() and 0xFF
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int = this@VlessResponseStrippingStream.read(b, off, len)
        }

        override fun close() = delegate.close()
    }
}

/**
 * Trojan protocol connector (TLS / WebSocket+TLS).
 */
class TrojanConnector(
    private val profile: VpnProfile,
    private val protector: (Socket) -> Boolean
) : UpstreamConnector {

    private val hashedPasswordHex: ByteArray by lazy {
        val digest = MessageDigest.getInstance("SHA-224")
            .digest(profile.passwordOrSecret.toByteArray(Charsets.UTF_8))
        digest.joinToString("") { "%02x".format(it) }.toByteArray(Charsets.US_ASCII)
    }

    override suspend fun connect() = withContext(Dispatchers.IO) {
        val tlsProfile = profile.copy(tlsEnabled = true)
        V2RayTransport.openRawStream(tlsProfile, protector).close()
    }

    override suspend fun openTcpStream(dstHost: String, dstPort: Int): TunnelStream =
        withContext(Dispatchers.IO) {
            val tlsProfile = profile.copy(tlsEnabled = true)
            val raw = V2RayTransport.openRawStream(tlsProfile, protector)
            try {
                val out = ByteArrayOutputStream()
                out.write(hashedPasswordHex)
                out.write(byteArrayOf('\r'.code.toByte(), '\n'.code.toByte()))
                out.write(0x01) // CMD CONNECT
                out.write(encodeSocksAddress(dstHost, dstPort))
                out.write(byteArrayOf('\r'.code.toByte(), '\n'.code.toByte()))
                raw.write(out.toByteArray())
                raw
            } catch (e: Exception) {
                runCatching { raw.close() }
                throw e
            }
        }

    override fun close() = Unit
}

/**
 * Shadowsocks AEAD connector (aes-256-gcm, aes-128-gcm, chacha20-ietf-poly1305).
 */
class ShadowsocksConnector(
    private val profile: VpnProfile,
    private val protector: (Socket) -> Boolean
) : UpstreamConnector {

    private val cipherMode = profile.cipher.lowercase()
    private val keySize = when {
        cipherMode.contains("128") -> 16
        else -> 32
    }
    private val isChaCha = cipherMode.contains("chacha20")
    private val masterKey: ByteArray by lazy {
        evpBytesToKey(profile.passwordOrSecret.toByteArray(Charsets.UTF_8), keySize)
    }

    override suspend fun connect() = withContext(Dispatchers.IO) {
        if (isChaCha && Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            throw IOException("ChaCha20-Poly1305 requires Android 9+ (API 28+)")
        }
        ProtectedSocketFactory.openTcp(profile.host, profile.port, 8000, protector).close()
    }

    override suspend fun openTcpStream(dstHost: String, dstPort: Int): TunnelStream =
        withContext(Dispatchers.IO) {
            val socket = ProtectedSocketFactory.openTcp(profile.host, profile.port, 10_000, protector)
            try {
                val ssStream = ShadowsocksAeadStream(socket, masterKey, keySize, isChaCha)
                val targetHeader = encodeSocksAddress(dstHost, dstPort)
                ssStream.write(targetHeader)
                ssStream
            } catch (e: Exception) {
                runCatching { socket.close() }
                throw e
            }
        }

    override fun close() = Unit

    private fun evpBytesToKey(password: ByteArray, keyLen: Int): ByteArray {
        val md5 = MessageDigest.getInstance("MD5")
        val result = ByteArrayOutputStream()
        var prev = ByteArray(0)
        while (result.size() < keyLen) {
            md5.reset()
            md5.update(prev)
            md5.update(password)
            prev = md5.digest()
            result.write(prev)
        }
        return result.toByteArray().copyOf(keyLen)
    }
}

internal class ShadowsocksAeadStream(
    private val socket: Socket,
    private val masterKey: ByteArray,
    private val keySize: Int,
    private val isChaCha: Boolean
) : TunnelStream {

    private val rawIn = socket.getInputStream()
    private val rawOut = socket.getOutputStream()
    private val random = SecureRandom()

    private val txSalt = ByteArray(keySize).also { random.nextBytes(it) }
    private val txSubKey = hkdfSha1(masterKey, txSalt, "ss-subkey".toByteArray(Charsets.US_ASCII), keySize)
    private var txNonce = ByteArray(12)
    private var txSaltSent = false

    private var rxSubKey: ByteArray? = null
    private var rxNonce = ByteArray(12)
    private var pendingPlain = ByteArray(0)
    private var pendingOffset = 0

    @Synchronized
    override fun write(data: ByteArray, offset: Int, length: Int) {
        if (!txSaltSent) {
            rawOut.write(txSalt)
            txSaltSent = true
        }
        var pos = offset
        val end = offset + length
        while (pos < end) {
            val chunkLen = minOf(0x3FFF, end - pos)
            val lenBytes = byteArrayOf(((chunkLen ushr 8) and 0xFF).toByte(), (chunkLen and 0xFF).toByte())
            val encLen = aeadEncrypt(txSubKey, txNonce, lenBytes, isChaCha)
            incrementNonce(txNonce)
            val payload = data.copyOfRange(pos, pos + chunkLen)
            val encPayload = aeadEncrypt(txSubKey, txNonce, payload, isChaCha)
            incrementNonce(txNonce)
            rawOut.write(encLen)
            rawOut.write(encPayload)
            pos += chunkLen
        }
        rawOut.flush()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (pendingOffset < pendingPlain.size) {
            val n = minOf(length, pendingPlain.size - pendingOffset)
            System.arraycopy(pendingPlain, pendingOffset, buffer, offset, n)
            pendingOffset += n
            return n
        }
        if (rxSubKey == null) {
            val salt = readExact(rawIn, keySize)
            rxSubKey = hkdfSha1(masterKey, salt, "ss-subkey".toByteArray(Charsets.US_ASCII), keySize)
        }
        val key = rxSubKey!!
        val encLen = readExact(rawIn, 2 + 16)
        val lenBytes = aeadDecrypt(key, rxNonce, encLen, isChaCha)
        incrementNonce(rxNonce)
        val chunkLen = ((lenBytes[0].toInt() and 0xFF) shl 8) or (lenBytes[1].toInt() and 0xFF)
        if (chunkLen <= 0 || chunkLen > 0x3FFF) throw IOException("Invalid Shadowsocks chunk length: $chunkLen")
        val encPayload = readExact(rawIn, chunkLen + 16)
        pendingPlain = aeadDecrypt(key, rxNonce, encPayload, isChaCha)
        incrementNonce(rxNonce)
        pendingOffset = 0
        val n = minOf(length, pendingPlain.size)
        System.arraycopy(pendingPlain, 0, buffer, offset, n)
        pendingOffset = n
        return n
    }

    override fun inputStream(): InputStream = object : InputStream() {
        override fun read(): Int {
            val b = ByteArray(1)
            val n = read(b, 0, 1)
            return if (n < 0) -1 else b[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            runCatching { this@ShadowsocksAeadStream.read(b, off, len) }
                .getOrElse { if (it is EOFException) -1 else throw it }
    }

    override fun close() {
        runCatching { socket.close() }
    }

    private fun aeadEncrypt(key: ByteArray, nonce: ByteArray, plain: ByteArray, chacha: Boolean): ByteArray {
        val cipher = Cipher.getInstance(if (chacha) "ChaCha20-Poly1305" else "AES/GCM/NoPadding")
        val spec = if (chacha) IvParameterSpec(nonce) else GCMParameterSpec(128, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, if (chacha) "ChaCha20" else "AES"), spec)
        return cipher.doFinal(plain)
    }

    private fun aeadDecrypt(key: ByteArray, nonce: ByteArray, cipherText: ByteArray, chacha: Boolean): ByteArray {
        val cipher = Cipher.getInstance(if (chacha) "ChaCha20-Poly1305" else "AES/GCM/NoPadding")
        val spec = if (chacha) IvParameterSpec(nonce) else GCMParameterSpec(128, nonce)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, if (chacha) "ChaCha20" else "AES"), spec)
        return cipher.doFinal(cipherText)
    }

    private fun incrementNonce(nonce: ByteArray) {
        for (i in nonce.indices) {
            val v = (nonce[i].toInt() and 0xFF) + 1
            nonce[i] = v.toByte()
            if (v <= 0xFF) break
        }
    }

    private fun hkdfSha1(ikm: ByteArray, salt: ByteArray, info: ByteArray, outLen: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(salt, "HmacSHA1"))
        val prk = mac.doFinal(ikm)
        val out = ByteArrayOutputStream()
        var t = ByteArray(0)
        var counter = 1
        while (out.size() < outLen) {
            mac.init(SecretKeySpec(prk, "HmacSHA1"))
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            out.write(t)
            counter++
        }
        return out.toByteArray().copyOf(outLen)
    }
}

/**
 * VMess AEAD connector (AES-128-GCM / ChaCha20-Poly1305 over TCP, TLS, or WebSocket).
 */
class VmessConnector(
    private val profile: VpnProfile,
    private val protector: (Socket) -> Boolean
) : UpstreamConnector {

    private val uuidBytes: ByteArray by lazy {
        val uuid = UUID.fromString(profile.usernameOrUuid.trim())
        ByteBuffer.allocate(16)
            .putLong(uuid.mostSignificantBits)
            .putLong(uuid.leastSignificantBits)
            .array()
    }

    private val cmdKey: ByteArray by lazy {
        val md5 = MessageDigest.getInstance("MD5")
        md5.update(uuidBytes)
        md5.update("c48619fe-8f02-49e0-b9e9-edf763e17e21".toByteArray(Charsets.US_ASCII))
        md5.digest()
    }

    override suspend fun connect() = withContext(Dispatchers.IO) {
        uuidBytes
        V2RayTransport.openRawStream(profile, protector).close()
    }

    override suspend fun openTcpStream(dstHost: String, dstPort: Int): TunnelStream =
        withContext(Dispatchers.IO) {
            val raw = V2RayTransport.openRawStream(profile, protector)
            try {
                val rnd = SecureRandom()
                val reqIv = ByteArray(16).also { rnd.nextBytes(it) }
                val reqKey = ByteArray(16).also { rnd.nextBytes(it) }
                val respV = ByteArray(1).also { rnd.nextBytes(it) }[0]
                val paddingLen = rnd.nextInt(16)
                val padding = ByteArray(paddingLen).also { rnd.nextBytes(it) }

                val useChaCha = profile.cipher.lowercase().contains("chacha") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                val secByte = if (useChaCha) 0x04 else 0x03 // 0x03 = AES-128-GCM, 0x04 = ChaCha20-Poly1305

                val reqBody = ByteArrayOutputStream().apply {
                    write(0x01) // Ver
                    write(reqIv)
                    write(reqKey)
                    write(respV.toInt() and 0xFF)
                    write(0x01) // Opt: standard chunk masking / global padding
                    write((paddingLen shl 4) or secByte)
                    write(0x00) // Reserved
                    write(0x01) // Cmd TCP
                    write((dstPort ushr 8) and 0xFF)
                    write(dstPort and 0xFF)

                    val ipv4 = dstHost.split(".")
                    if (ipv4.size == 4 && ipv4.all { it.toIntOrNull() in 0..255 }) {
                        write(0x01)
                        ipv4.forEach { write(it.toInt()) }
                    } else {
                        val hostBytes = dstHost.toByteArray(Charsets.UTF_8)
                        write(0x02)
                        write(hostBytes.size)
                        write(hostBytes)
                    }
                    write(padding)
                }.toByteArray()

                val fnv = fnv1a32(reqBody)
                val fullPlainHeader = reqBody + byteArrayOf(
                    ((fnv ushr 24) and 0xFF).toByte(),
                    ((fnv ushr 16) and 0xFF).toByte(),
                    ((fnv ushr 8) and 0xFF).toByte(),
                    (fnv and 0xFF).toByte()
                )

                val authId = createAuthId(cmdKey, rnd)
                val connNonce = ByteArray(8).also { rnd.nextBytes(it) }
                val lenBytes = byteArrayOf(
                    ((fullPlainHeader.size ushr 8) and 0xFF).toByte(),
                    (fullPlainHeader.size and 0xFF).toByte()
                )

                val lenKey = kdf16(cmdKey, "VMess Header AEAD Key_Length", authId, connNonce)
                val lenNonce = kdf(cmdKey, "VMess Header AEAD Nonce_Length", authId, connNonce).copyOf(12)
                val encLen = aesGcmEncrypt(lenKey, lenNonce, lenBytes, authId)

                val hdrKey = kdf16(cmdKey, "VMess Header AEAD Key", authId, connNonce)
                val hdrNonce = kdf(cmdKey, "VMess Header AEAD Nonce", authId, connNonce).copyOf(12)
                val encHdr = aesGcmEncrypt(hdrKey, hdrNonce, fullPlainHeader, authId)

                raw.write(authId + encLen + connNonce + encHdr)
                VmessAeadStream(raw, reqKey, reqIv, respV, useChaCha)
            } catch (e: Exception) {
                runCatching { raw.close() }
                throw e
            }
        }

    override fun close() = Unit

    private fun createAuthId(cmdKey: ByteArray, rnd: SecureRandom): ByteArray {
        val ts = System.currentTimeMillis() / 1000L
        val rand = ByteArray(4).also { rnd.nextBytes(it) }
        val payload = ByteBuffer.allocate(12).putLong(ts).put(rand).array()
        val crc = CRC32().apply { update(payload) }.value.toInt()
        val plain16 = ByteBuffer.allocate(16).put(payload).putInt(crc).array()
        val authKey = kdf16(cmdKey, "AES Auth ID Encryption")
        val ecb = Cipher.getInstance("AES/ECB/NoPadding")
        ecb.init(Cipher.ENCRYPT_MODE, SecretKeySpec(authKey, "AES"))
        return ecb.doFinal(plain16)
    }

    private fun fnv1a32(data: ByteArray): Int {
        var hash = -0x7ee3623b // 0x811c9dc5
        for (b in data) {
            hash = hash xor (b.toInt() and 0xFF)
            hash *= 0x01000193
        }
        return hash
    }

    companion object {
        internal fun kdf(key: ByteArray, vararg paths: Any): ByteArray {
            var currentKey = "VMess AEAD KDF".toByteArray(Charsets.US_ASCII)
            for (path in paths) {
                val label = when (path) {
                    is String -> path.toByteArray(Charsets.US_ASCII)
                    is ByteArray -> path
                    else -> ByteArray(0)
                }
                val mac = Mac.getInstance("HmacSHA256")
                mac.init(SecretKeySpec(currentKey, "HmacSHA256"))
                currentKey = mac.doFinal(label)
            }
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(currentKey, "HmacSHA256"))
            return mac.doFinal(key)
        }

        internal fun kdf16(key: ByteArray, vararg paths: Any): ByteArray =
            kdf(key, *paths).copyOf(16)

        internal fun aesGcmEncrypt(
            key: ByteArray,
            nonce: ByteArray,
            plain: ByteArray,
            aad: ByteArray = ByteArray(0)
        ): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            if (aad.isNotEmpty()) cipher.updateAAD(aad)
            return cipher.doFinal(plain)
        }

        internal fun aesGcmDecrypt(
            key: ByteArray,
            nonce: ByteArray,
            cipherText: ByteArray,
            aad: ByteArray = ByteArray(0)
        ): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            if (aad.isNotEmpty()) cipher.updateAAD(aad)
            return cipher.doFinal(cipherText)
        }
    }
}

private class VmessAeadStream(
    private val raw: TunnelStream,
    private val reqKey: ByteArray,
    private val reqIv: ByteArray,
    private val respV: Byte,
    private val useChaCha: Boolean
) : TunnelStream {

    private val respKey = MessageDigest.getInstance("SHA-256").digest(reqKey).copyOf(16)
    private val respIv = MessageDigest.getInstance("SHA-256").digest(reqIv).copyOf(16)
    private var txCount = 0
    private var rxCount = 0
    private var respHeaderRead = false
    private var pendingPlain = ByteArray(0)
    private var pendingOffset = 0

    @Synchronized
    override fun write(data: ByteArray, offset: Int, length: Int) {
        var pos = offset
        val end = offset + length
        while (pos < end) {
            val chunkLen = minOf(8192, end - pos)
            val totalTagLen = 16
            val packetLen = chunkLen + totalTagLen
            val hdr = byteArrayOf(((packetLen ushr 8) and 0xFF).toByte(), (packetLen and 0xFF).toByte())
            val nonce = ByteBuffer.allocate(12)
                .putShort(txCount.toShort())
                .put(reqIv, 2, 10)
                .array()
            val key = if (useChaCha) {
                val md = MessageDigest.getInstance("MD5")
                md.digest(reqKey) + md.digest(md.digest(reqKey))
            } else reqKey
            val cipher = Cipher.getInstance(if (useChaCha) "ChaCha20-Poly1305" else "AES/GCM/NoPadding")
            val spec = if (useChaCha) IvParameterSpec(nonce) else GCMParameterSpec(128, nonce)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, if (useChaCha) "ChaCha20" else "AES"), spec)
            val encrypted = cipher.doFinal(data, pos, chunkLen)
            raw.write(hdr + encrypted)
            txCount++
            pos += chunkLen
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (pendingOffset < pendingPlain.size) {
            val n = minOf(length, pendingPlain.size - pendingOffset)
            System.arraycopy(pendingPlain, pendingOffset, buffer, offset, n)
            pendingOffset += n
            return n
        }
        val inp = raw.inputStream()
        if (!respHeaderRead) {
            val respLenKey = VmessConnector.kdf16(respKey, "AEAD Resp Header Len Key")
            val respLenNonce = VmessConnector.kdf(respIv, "AEAD Resp Header Len IV").copyOf(12)
            val encLen = readExact(inp, 18)
            val lenPlain = VmessConnector.aesGcmDecrypt(respLenKey, respLenNonce, encLen)
            val hdrLen = ((lenPlain[0].toInt() and 0xFF) shl 8) or (lenPlain[1].toInt() and 0xFF)
            val respHdrKey = VmessConnector.kdf16(respKey, "AEAD Resp Header Key")
            val respHdrNonce = VmessConnector.kdf(respIv, "AEAD Resp Header IV").copyOf(12)
            val encHdr = readExact(inp, hdrLen + 16)
            val hdrPlain = VmessConnector.aesGcmDecrypt(respHdrKey, respHdrNonce, encHdr)
            if (hdrPlain.isEmpty() || hdrPlain[0] != respV) {
                throw IOException("VMess response header authentication failed")
            }
            respHeaderRead = true
        }
        val sizeBytes = readExact(inp, 2)
        val encSize = ((sizeBytes[0].toInt() and 0xFF) shl 8) or (sizeBytes[1].toInt() and 0xFF)
        if (encSize <= 16) return -1
        val encChunk = readExact(inp, encSize)
        val nonce = ByteBuffer.allocate(12)
            .putShort(rxCount.toShort())
            .put(respIv, 2, 10)
            .array()
        val key = if (useChaCha) {
            val md = MessageDigest.getInstance("MD5")
            md.digest(respKey) + md.digest(md.digest(respKey))
        } else respKey
        val cipher = Cipher.getInstance(if (useChaCha) "ChaCha20-Poly1305" else "AES/GCM/NoPadding")
        val spec = if (useChaCha) IvParameterSpec(nonce) else GCMParameterSpec(128, nonce)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, if (useChaCha) "ChaCha20" else "AES"), spec)
        pendingPlain = cipher.doFinal(encChunk)
        rxCount++
        pendingOffset = 0
        val n = minOf(length, pendingPlain.size)
        System.arraycopy(pendingPlain, 0, buffer, offset, n)
        pendingOffset = n
        return n
    }

    override fun inputStream(): InputStream = object : InputStream() {
        override fun read(): Int {
            val b = ByteArray(1)
            val n = read(b, 0, 1)
            return if (n < 0) -1 else b[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            runCatching { this@VmessAeadStream.read(b, off, len) }
                .getOrElse { if (it is EOFException) -1 else throw it }
    }

    override fun close() = raw.close()
}
