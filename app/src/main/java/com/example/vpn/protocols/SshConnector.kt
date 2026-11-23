package com.example.vpn.protocols

import com.example.model.VpnProfile
import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Resilient SSH tunnel connector modeled after KPN Tunnel:
 * - Disables zlib compression (no jzlib dependency, avoids packet crashes)
 * - Uses 12s keepalives with 5 retries to survive carrier NAT & radio blips
 * - Supports SSH over direct TCP as well as SSH over TLS/SSL (Stunnel)
 * - Pools up to 2 SSH sessions lazily under load to avoid OpenSSH MaxSessions saturation
 * - Auto-heals dead sessions transparently on transient channel/transport errors
 */
class SshConnector(
    private val profile: VpnProfile,
    private val protector: (Socket) -> Boolean
) : UpstreamConnector {

    private class SessionSlot(val index: Int) {
        val lock = Mutex()
        @Volatile var session: Session? = null
        val openChannels = AtomicInteger(0)

        fun isConnected(): Boolean = session?.isConnected == true
    }

    private val closed = AtomicBoolean(false)
    private val lowBatteryMode = AtomicBoolean(false)
    private val slots = Array(POOL_SIZE) { SessionSlot(it) }
    private val consecutiveReconnectFailures = AtomicInteger(0)

    @Volatile private var cachedHostIp: String? = null
    @Volatile private var cachedHostIpAt: Long = 0L

    override val supportsStatefulHealthCheck: Boolean = true

    override fun isHealthy(): Boolean {
        if (closed.get()) return false
        return slots.any { it.isConnected() } || consecutiveReconnectFailures.get() == 0
    }

    override suspend fun probeHealth(): Boolean = withContext(Dispatchers.IO) {
        if (closed.get()) return@withContext false
        val alive = slots.firstOrNull { it.isConnected() }
        if (alive != null) {
            val keepAliveOk = runCatching {
                alive.session?.sendKeepAliveMsg()
                alive.isConnected()
            }.getOrDefault(false)
            if (keepAliveOk) return@withContext true
        }
        // Session went silent — attempt silent slot heal before reporting down
        runCatching {
            slots[0].lock.withLock {
                ensureSlotConnectedLocked(slots[0], forceReconnect = true)
            }
            slots[0].isConnected()
        }.getOrDefault(false)
    }

    override fun setLowBatteryMode(enabled: Boolean) {
        if (lowBatteryMode.getAndSet(enabled) == enabled) return
        val intervalMs = effectiveKeepAliveIntervalMs()
        slots.forEach { slot ->
            slot.session?.let { activeSession ->
                if (activeSession.isConnected) {
                    runCatching { activeSession.serverAliveInterval = intervalMs }
                }
            }
        }
    }

    internal fun effectiveKeepAliveIntervalMs(): Int =
        if (lowBatteryMode.get()) KEEPALIVE_INTERVAL_LOW_BATTERY_MS else KEEPALIVE_INTERVAL_MS

    override suspend fun connect() = withContext(Dispatchers.IO) {
        if (closed.get()) throw IOException("SSH connector is closed")
        slots[0].lock.withLock {
            ensureSlotConnectedLocked(slots[0], forceReconnect = false)
        }
        Unit
    }

    override suspend fun openTcpStream(dstHost: String, dstPort: Int): TunnelStream =
        withContext(Dispatchers.IO) {
            if (closed.get()) throw IOException("SSH connector is closed")

            var chosenSlot = selectOrWarmSlot()
            try {
                openChannelOnSlot(chosenSlot, dstHost, dstPort)
            } catch (firstErr: Exception) {
                if (closed.get()) throw firstErr
                val sessionDied = !chosenSlot.isConnected() || isSessionLevelFailure(firstErr)
                if (!sessionDied && isDestinationRefused(firstErr)) {
                    throw firstErr
                }

                // Transparently heal the failed slot (or fail over to the secondary slot) and retry once
                if (sessionDied) {
                    chosenSlot.lock.withLock {
                        invalidateSlotLocked(chosenSlot)
                        runCatching { ensureSlotConnectedLocked(chosenSlot, forceReconnect = true) }
                    }
                    if (!chosenSlot.isConnected()) {
                        val alt = slots.firstOrNull { it.index != chosenSlot.index } ?: chosenSlot
                        alt.lock.withLock {
                            ensureSlotConnectedLocked(alt, forceReconnect = !alt.isConnected())
                        }
                        chosenSlot = alt
                    }
                } else {
                    // Channel limit/saturation on primary slot: try warming/using secondary slot
                    val alt = slots.firstOrNull { it.index != chosenSlot.index }
                    if (alt != null) {
                        alt.lock.withLock {
                            ensureSlotConnectedLocked(alt, forceReconnect = false)
                        }
                        chosenSlot = alt
                    } else {
                        throw firstErr
                    }
                }

                openChannelOnSlot(chosenSlot, dstHost, dstPort)
            }
        }

    private suspend fun selectOrWarmSlot(): SessionSlot {
        val connectedSlots = slots.filter { it.isConnected() }
        if (connectedSlots.isNotEmpty()) {
            val leastLoaded = connectedSlots.minBy { it.openChannels.get() }
            if (!lowBatteryMode.get() &&
                leastLoaded.openChannels.get() >= SECONDARY_WARM_THRESHOLD &&
                connectedSlots.size < slots.size
            ) {
                val coldSlot = slots.firstOrNull { !it.isConnected() }
                if (coldSlot != null) {
                    val warmed = runCatching {
                        coldSlot.lock.withLock {
                            ensureSlotConnectedLocked(coldSlot, forceReconnect = false)
                        }
                        coldSlot
                    }.getOrNull()
                    if (warmed != null && warmed.isConnected()) {
                        return warmed
                    }
                }
            }
            return leastLoaded
        }

        val primary = slots[0]
        primary.lock.withLock {
            ensureSlotConnectedLocked(primary, forceReconnect = false)
        }
        return primary
    }

    private fun openChannelOnSlot(slot: SessionSlot, dstHost: String, dstPort: Int): TunnelStream {
        val activeSession = slot.session
            ?.takeIf { it.isConnected }
            ?: throw IOException("SSH session is down")

        val channel = activeSession.openChannel("direct-tcpip") as ChannelDirectTCPIP
        channel.setHost(dstHost)
        channel.setPort(dstPort)
        channel.setOrgIPAddress("127.0.0.1")
        channel.setOrgPort(0)

        var counted = false
        return try {
            val inp = channel.inputStream
            val out = channel.outputStream
            channel.connect(CHANNEL_CONNECT_TIMEOUT_MS)
            slot.openChannels.incrementAndGet()
            counted = true
            SshChannelTunnelStream(channel, inp, out) {
                slot.openChannels.updateAndGet { cur -> (cur - 1).coerceAtLeast(0) }
            }
        } catch (e: Exception) {
            if (counted) {
                slot.openChannels.updateAndGet { cur -> (cur - 1).coerceAtLeast(0) }
            }
            runCatching { channel.disconnect() }
            throw e
        }
    }

    private fun ensureSlotConnectedLocked(slot: SessionSlot, forceReconnect: Boolean): Session {
        val current = slot.session
        if (!forceReconnect && current != null && current.isConnected) {
            return current
        }
        invalidateSlotLocked(slot)
        if (closed.get()) throw IOException("SSH connector is closed")

        return try {
            val newSession = createAndConnectSession()
            slot.session = newSession
            consecutiveReconnectFailures.set(0)
            newSession
        } catch (e: Exception) {
            consecutiveReconnectFailures.incrementAndGet()
            throw e
        }
    }

    private fun invalidateSlotLocked(slot: SessionSlot) {
        val old = slot.session
        slot.session = null
        if (old != null) {
            runCatching { old.disconnect() }
        }
    }

    private fun resolveTargetHost(): String {
        val now = System.currentTimeMillis()
        val cached = cachedHostIp
        return runCatching {
            val addr = InetAddress.getByName(profile.host).hostAddress
            if (!addr.isNullOrBlank()) {
                cachedHostIp = addr
                cachedHostIpAt = now
                addr
            } else {
                profile.host
            }
        }.getOrElse {
            if (cached != null && now - cachedHostIpAt < DNS_CACHE_TTL_MS) cached else profile.host
        }
    }

    private fun createAndConnectSession(): Session {
        val jsch = JSch()
        if (profile.privateKeyPem.isNotBlank()) {
            val keyBytes = profile.privateKeyPem.trim().toByteArray(Charsets.UTF_8)
            val passphrase = profile.passwordOrSecret.takeIf { it.isNotBlank() }?.toByteArray(Charsets.UTF_8)
            jsch.addIdentity("netshield-key", keyBytes, null, passphrase)
        }

        val user = profile.usernameOrUuid.ifBlank { "root" }
        val newSession = jsch.getSession(user, profile.host, profile.port).apply {
            if (profile.privateKeyPem.isBlank() && profile.passwordOrSecret.isNotBlank()) {
                setPassword(profile.passwordOrSecret)
            }
            setConfig("StrictHostKeyChecking", "no")
            setConfig("HashKnownHosts", "no")
            setConfig("PreferredAuthentications", "publickey,password,keyboard-interactive")
            setConfig("MaxAuthTries", "3")
            // Explicitly disable zlib compression so JSch never requests missing jzlib
            setConfig("compression.s2c", "none")
            setConfig("compression.c2s", "none")

            // Mobile NAT keepalive: 12s normal, reduced to 60s in Low-Battery Operation mode
            timeout = SESSION_SOCKET_TIMEOUT_MS
            serverAliveInterval = effectiveKeepAliveIntervalMs()
            serverAliveCountMax = KEEPALIVE_MAX_COUNT

            setSocketFactory(object : SocketFactory {
                override fun createSocket(host: String, port: Int): Socket {
                    val connectHost = resolveTargetHost()
                    return if (profile.tlsEnabled) {
                        ProtectedSocketFactory.openTls(
                            host = connectHost,
                            port = port,
                            sni = profile.sni.ifBlank { profile.host },
                            allowInsecure = profile.allowInsecure,
                            timeoutMs = SESSION_CONNECT_TIMEOUT_MS,
                            protector = protector,
                            soTimeoutMs = 0
                        )
                    } else {
                        ProtectedSocketFactory.openTcp(
                            host = connectHost,
                            port = port,
                            timeoutMs = SESSION_CONNECT_TIMEOUT_MS,
                            protector = protector,
                            soTimeoutMs = 0
                        )
                    }
                }

                override fun getInputStream(socket: Socket): InputStream = socket.getInputStream()
                override fun getOutputStream(socket: Socket): OutputStream = socket.getOutputStream()
            })
        }

        newSession.connect(SESSION_CONNECT_TIMEOUT_MS)
        return newSession
    }

    private fun isSessionLevelFailure(err: Throwable): Boolean {
        val msg = err.message?.lowercase().orEmpty()
        return msg.contains("session is down") ||
            msg.contains("socket closed") ||
            msg.contains("socket is closed") ||
            msg.contains("broken pipe") ||
            msg.contains("connection reset") ||
            msg.contains("packet corrupt") ||
            msg.contains("ssh_msg_disconnect") ||
            msg.contains("eof") ||
            msg.contains("channel is not opened") ||
            (err is JSchException && err.cause is IOException)
    }

    private fun isDestinationRefused(err: Throwable): Boolean {
        val msg = err.message?.lowercase().orEmpty()
        return msg.contains("open failed") ||
            msg.contains("connect failed") ||
            msg.contains("connection refused") ||
            msg.contains("administratively prohibited") ||
            msg.contains("unreachable")
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        slots.forEach { slot ->
            runCatching { slot.session?.disconnect() }
            slot.session = null
            slot.openChannels.set(0)
        }
    }

    private class SshChannelTunnelStream(
        private val channel: ChannelDirectTCPIP,
        private val input: InputStream,
        private val output: OutputStream,
        private val onCloseCallback: () -> Unit
    ) : TunnelStream {
        private val streamClosed = AtomicBoolean(false)

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            return input.read(buffer, offset, length)
        }

        override fun write(data: ByteArray, offset: Int, length: Int) {
            if (streamClosed.get() || !channel.isConnected) {
                throw IOException("SSH channel closed")
            }
            output.write(data, offset, length)
            output.flush()
        }

        override fun inputStream(): InputStream = input

        override fun close() {
            if (streamClosed.compareAndSet(false, true)) {
                runCatching { channel.disconnect() }
                onCloseCallback()
            }
        }
    }

    companion object {
        private const val POOL_SIZE = 2
        private const val SECONDARY_WARM_THRESHOLD = 6
        private const val SESSION_CONNECT_TIMEOUT_MS = 12_000
        private const val CHANNEL_CONNECT_TIMEOUT_MS = 10_000
        private const val SESSION_SOCKET_TIMEOUT_MS = 20_000
        private const val KEEPALIVE_INTERVAL_MS = 12_000
        private const val KEEPALIVE_INTERVAL_LOW_BATTERY_MS = 60_000
        private const val KEEPALIVE_MAX_COUNT = 5
        private const val DNS_CACHE_TTL_MS = 10 * 60 * 1000L

        internal fun createDefaultConfigMap(tlsEnabled: Boolean = false): Map<String, String> = mapOf(
            "StrictHostKeyChecking" to "no",
            "HashKnownHosts" to "no",
            "PreferredAuthentications" to "publickey,password,keyboard-interactive",
            "MaxAuthTries" to "3",
            "compression.s2c" to "none",
            "compression.c2s" to "none",
            "tlsEnabled" to tlsEnabled.toString()
        )
    }
}
