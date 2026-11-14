package com.example.vpn.protocols

import com.example.model.VpnProfile
import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

class SshConnector(
    private val profile: VpnProfile,
    private val protector: (Socket) -> Boolean
) : UpstreamConnector {

    private val mutex = Mutex()
    private var session: Session? = null

    override suspend fun connect() = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureSessionLocked()
            Unit
        }
    }

    override suspend fun openTcpStream(dstHost: String, dstPort: Int): TunnelStream =
        withContext(Dispatchers.IO) {
            val activeSession = mutex.withLock { ensureSessionLocked() }
            val channel = activeSession.openChannel("direct-tcpip") as ChannelDirectTCPIP
            channel.setHost(dstHost)
            channel.setPort(dstPort)
            channel.setOrgIPAddress("127.0.0.1")
            channel.setOrgPort(0)
            val inp = channel.inputStream
            val out = channel.outputStream
            channel.connect(10_000)
            SshChannelTunnelStream(channel, inp, out)
        }

    private fun ensureSessionLocked(): Session {
        val current = session
        if (current != null && current.isConnected) return current

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
            setConfig("PreferredAuthentications", "publickey,password,keyboard-interactive")
            setConfig("compression.s2c", "zlib@openssh.com,zlib,none")
            setConfig("compression.c2s", "zlib@openssh.com,zlib,none")
            // Low-battery keepalive: 30s interval avoids radio wakeups while keeping NAT mapping alive
            serverAliveInterval = 30_000
            serverAliveCountMax = 3
            setSocketFactory(object : SocketFactory {
                override fun createSocket(host: String, port: Int): Socket {
                    return ProtectedSocketFactory.openTcp(host, port, 12_000, protector)
                }

                override fun getInputStream(socket: Socket): InputStream = socket.getInputStream()
                override fun getOutputStream(socket: Socket): OutputStream = socket.getOutputStream()
            })
        }
        newSession.connect(12_000)
        session = newSession
        return newSession
    }

    override fun close() {
        runCatching { session?.disconnect() }
        session = null
    }

    private class SshChannelTunnelStream(
        private val channel: ChannelDirectTCPIP,
        private val input: InputStream,
        private val output: OutputStream
    ) : TunnelStream {
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            return input.read(buffer, offset, length)
        }

        override fun write(data: ByteArray, offset: Int, length: Int) {
            if (!channel.isConnected) throw IOException("SSH channel closed")
            output.write(data, offset, length)
            output.flush()
        }

        override fun inputStream(): InputStream = input

        override fun close() {
            runCatching { channel.disconnect() }
        }
    }
}
