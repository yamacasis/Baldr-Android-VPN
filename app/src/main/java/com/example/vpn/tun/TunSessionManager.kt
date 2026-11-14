package com.example.vpn.tun

import com.example.vpn.protocols.TunnelStream
import com.example.vpn.protocols.UpstreamConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

class TunSessionManager(
    private val tunInput: FileInputStream,
    private val tunOutput: FileOutputStream,
    @Volatile private var connector: UpstreamConnector?,
    private val dnsPrimary: String,
    private val mtu: Int,
    private val scope: CoroutineScope,
    private val onLog: (String) -> Unit,
    private val onConnectionLost: () -> Unit = {}
) {
    val totalRxBytes = AtomicLong(0L)
    val totalTxBytes = AtomicLong(0L)
    val activeStreamCount = AtomicInteger(0)

    private val writeLock = Any()
    private val tcpSessions = ConcurrentHashMap<FlowKey, TcpFlowSession>()
    private val running = AtomicBoolean(true)
    private val dropAllMode = AtomicBoolean(false)
    private val consecutiveFailures = AtomicInteger(0)
    private val maxTcpPayload = (mtu - 40).coerceIn(512, 1440)

    private var readerJob: Job? = null
    private var reaperJob: Job? = null

    fun start() {
        readerJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(mtu.coerceAtLeast(2048) + 64)
            while (isActive && running.get()) {
                val readLen = try {
                    tunInput.read(buffer)
                } catch (_: Exception) {
                    break
                }
                if (readLen <= 0) {
                    if (readLen < 0) break
                    continue
                }
                if (dropAllMode.get() || connector == null) {
                    // Firewall Kill Switch mode: consume and drop all packets so zero traffic leaks
                    continue
                }
                handlePacket(buffer, readLen)
            }
        }

        // Low-battery idle session reaper: sleeps 30s and skips work when no sessions exist
        reaperJob = scope.launch(Dispatchers.IO) {
            while (isActive && running.get()) {
                delay(30_000L)
                if (tcpSessions.isEmpty()) continue
                val now = System.currentTimeMillis()
                val expired = tcpSessions.entries.filter { now - it.value.lastActivityMillis > 90_000L }
                for (entry in expired) {
                    entry.value.closeSilently()
                    if (tcpSessions.remove(entry.key) != null) {
                        activeStreamCount.set(tcpSessions.size)
                    }
                }
            }
        }
    }

    /**
     * Switches between normal tunneling and Firewall Kill Switch blackhole mode without closing TUN.
     */
    fun setFirewallDropAll(enabled: Boolean) {
        dropAllMode.set(enabled)
        if (enabled) {
            tcpSessions.values.forEach { it.closeSilently() }
            tcpSessions.clear()
            activeStreamCount.set(0)
        }
    }

    fun swapConnector(newConnector: UpstreamConnector) {
        connector = newConnector
        consecutiveFailures.set(0)
        dropAllMode.set(false)
    }

    fun stop() {
        running.set(false)
        readerJob?.cancel()
        reaperJob?.cancel()
        tcpSessions.values.forEach { it.closeSilently() }
        tcpSessions.clear()
        activeStreamCount.set(0)
    }

    private fun handlePacket(packet: ByteArray, length: Int) {
        val ip = PacketUtils.parseIpv4Header(packet, length) ?: return
        when (ip.protocol) {
            PacketUtils.PROTO_TCP -> handleTcpPacket(packet, ip)
            PacketUtils.PROTO_UDP -> handleUdpPacket(packet, ip)
        }
    }

    private fun handleUdpPacket(packet: ByteArray, ip: PacketUtils.Ipv4Header) {
        val activeConnector = connector ?: return
        val udp = PacketUtils.parseUdpHeader(packet, ip.headerLength, ip.totalLength) ?: return
        val payloadOffset = ip.headerLength + 8
        val payloadLen = (ip.totalLength - payloadOffset).coerceAtLeast(0)
        if (payloadLen <= 0) return

        // DNS queries (UDP port 53) are relayed cleanly via DNS-over-TCP over the active tunnel
        if (udp.dstPort == 53) {
            val dnsQuery = packet.copyOfRange(payloadOffset, payloadOffset + payloadLen)
            totalTxBytes.addAndGet(payloadLen.toLong())
            scope.launch(Dispatchers.IO) {
                val dnsResp = runCatching {
                    activeConnector.forwardDnsQuery(dnsQuery, dnsPrimary)
                }.getOrNull() ?: return@launch

                if (dropAllMode.get()) return@launch
                val respPacket = PacketUtils.buildIpv4UdpPacket(
                    srcIp = ip.dstIp,
                    dstIp = ip.srcIp,
                    srcPort = udp.dstPort,
                    dstPort = udp.srcPort,
                    payload = dnsResp
                )
                writeToTun(respPacket)
                totalRxBytes.addAndGet(dnsResp.size.toLong())
            }
        }
    }

    private fun handleTcpPacket(packet: ByteArray, ip: PacketUtils.Ipv4Header) {
        val tcp = PacketUtils.parseTcpHeader(packet, ip.headerLength, ip.totalLength) ?: return
        val key = FlowKey(ip.srcIpStr, tcp.srcPort, ip.dstIpStr, tcp.dstPort)

        val isSyn = (tcp.flags and PacketUtils.TCP_SYN) != 0
        val isAck = (tcp.flags and PacketUtils.TCP_ACK) != 0
        val isFin = (tcp.flags and PacketUtils.TCP_FIN) != 0
        val isRst = (tcp.flags and PacketUtils.TCP_RST) != 0

        if (isRst) {
            tcpSessions.remove(key)?.closeSilently()
            activeStreamCount.set(tcpSessions.size)
            return
        }

        if (isSyn && !isAck) {
            if (tcpSessions.containsKey(key)) return
            val session = TcpFlowSession(
                key = key,
                clientIp = ip.srcIp,
                serverIp = ip.dstIp,
                clientPort = tcp.srcPort,
                serverPort = tcp.dstPort,
                initialClientSeq = tcp.seqNum
            )
            tcpSessions[key] = session
            activeStreamCount.set(tcpSessions.size)
            session.startUpstream()
            return
        }

        val session = tcpSessions[key] ?: return
        session.lastActivityMillis = System.currentTimeMillis()

        val payloadOffset = ip.headerLength + tcp.headerLength
        val payloadLen = (ip.totalLength - payloadOffset).coerceAtLeast(0)
        if (payloadLen > 0) {
            val data = packet.copyOfRange(payloadOffset, payloadOffset + payloadLen)
            session.onClientPayload(tcp.seqNum, data)
        }

        if (isFin) {
            session.onClientFin(tcp.seqNum + payloadLen)
        }
    }

    private fun writeToTun(packet: ByteArray) {
        if (!running.get() || dropAllMode.get()) return
        synchronized(writeLock) {
            runCatching {
                tunOutput.write(packet)
            }
        }
    }

    private data class FlowKey(
        val srcIp: String,
        val srcPort: Int,
        val dstIp: String,
        val dstPort: Int
    )

    private inner class TcpFlowSession(
        private val key: FlowKey,
        private val clientIp: ByteArray,
        private val serverIp: ByteArray,
        private val clientPort: Int,
        private val serverPort: Int,
        initialClientSeq: Long
    ) {
        @Volatile var lastActivityMillis = System.currentTimeMillis()
        @Volatile private var closed = false
        private val established = AtomicBoolean(false)
        private val clientNextSeq = AtomicLong((initialClientSeq + 1L) and 0xFFFFFFFFL)
        private val serverSeq = AtomicLong(Random.nextLong(100_000L, 2_000_000_000L))
        private var upstreamStream: TunnelStream? = null
        private var upstreamJob: Job? = null

        fun startUpstream() {
            upstreamJob = scope.launch(Dispatchers.IO) {
                val activeConnector = connector
                if (activeConnector == null || dropAllMode.get()) {
                    sendRst()
                    closeSilently()
                    tcpSessions.remove(key)
                    activeStreamCount.set(tcpSessions.size)
                    return@launch
                }

                try {
                    val stream = activeConnector.openTcpStream(key.dstIp, key.dstPort)
                    consecutiveFailures.set(0)
                    if (closed || dropAllMode.get()) {
                        stream.close()
                        return@launch
                    }
                    upstreamStream = stream
                    established.set(true)

                    val synAckSeq = serverSeq.getAndIncrement() and 0xFFFFFFFFL
                    val synAck = PacketUtils.buildIpv4TcpPacket(
                        srcIp = serverIp,
                        dstIp = clientIp,
                        srcPort = serverPort,
                        dstPort = clientPort,
                        seqNum = synAckSeq,
                        ackNum = clientNextSeq.get(),
                        flags = PacketUtils.TCP_SYN or PacketUtils.TCP_ACK
                    )
                    writeToTun(synAck)

                    val readBuf = ByteArray(maxTcpPayload)
                    while (isActive && !closed && !dropAllMode.get()) {
                        val n = stream.read(readBuf, 0, readBuf.size)
                        if (n < 0) break
                        if (n == 0) continue
                        lastActivityMillis = System.currentTimeMillis()
                        totalRxBytes.addAndGet(n.toLong())

                        val curSeq = serverSeq.getAndAdd(n.toLong()) and 0xFFFFFFFFL
                        val dataPkt = PacketUtils.buildIpv4TcpPacket(
                            srcIp = serverIp,
                            dstIp = clientIp,
                            srcPort = serverPort,
                            dstPort = clientPort,
                            seqNum = curSeq,
                            ackNum = clientNextSeq.get(),
                            flags = PacketUtils.TCP_PSH or PacketUtils.TCP_ACK,
                            payload = readBuf,
                            payloadOffset = 0,
                            payloadLength = n
                        )
                        writeToTun(dataPkt)
                    }

                    if (!closed && !dropAllMode.get()) {
                        sendFinAck()
                    }
                } catch (e: Exception) {
                    if (!closed && !dropAllMode.get()) {
                        onLog("TCP ${key.dstIp}:${key.dstPort} failed: ${e.message}")
                        sendRst()
                        if (consecutiveFailures.incrementAndGet() >= 5) {
                            consecutiveFailures.set(0)
                            onConnectionLost()
                        }
                    }
                } finally {
                    closeSilently()
                    if (tcpSessions.remove(key) != null) {
                        activeStreamCount.set(tcpSessions.size)
                    }
                }
            }
        }

        fun onClientPayload(seqNum: Long, data: ByteArray) {
            if (closed || !established.get() || dropAllMode.get()) return
            val expected = clientNextSeq.get()
            if (seqNum != expected) {
                sendAckOnly()
                return
            }
            clientNextSeq.set((expected + data.size) and 0xFFFFFFFFL)
            totalTxBytes.addAndGet(data.size.toLong())
            sendAckOnly()

            scope.launch(Dispatchers.IO) {
                runCatching {
                    upstreamStream?.write(data, 0, data.size)
                }.onFailure {
                    sendRst()
                    closeSilently()
                    tcpSessions.remove(key)
                    activeStreamCount.set(tcpSessions.size)
                }
            }
        }

        fun onClientFin(finSeq: Long) {
            if (closed) return
            clientNextSeq.set((finSeq + 1L) and 0xFFFFFFFFL)
            sendFinAck()
            closeSilently()
            if (tcpSessions.remove(key) != null) {
                activeStreamCount.set(tcpSessions.size)
            }
        }

        private fun sendAckOnly() {
            val pkt = PacketUtils.buildIpv4TcpPacket(
                srcIp = serverIp,
                dstIp = clientIp,
                srcPort = serverPort,
                dstPort = clientPort,
                seqNum = serverSeq.get() and 0xFFFFFFFFL,
                ackNum = clientNextSeq.get(),
                flags = PacketUtils.TCP_ACK
            )
            writeToTun(pkt)
        }

        private fun sendFinAck() {
            val curSeq = serverSeq.getAndIncrement() and 0xFFFFFFFFL
            val pkt = PacketUtils.buildIpv4TcpPacket(
                srcIp = serverIp,
                dstIp = clientIp,
                srcPort = serverPort,
                dstPort = clientPort,
                seqNum = curSeq,
                ackNum = clientNextSeq.get(),
                flags = PacketUtils.TCP_FIN or PacketUtils.TCP_ACK
            )
            writeToTun(pkt)
        }

        private fun sendRst() {
            val pkt = PacketUtils.buildIpv4TcpPacket(
                srcIp = serverIp,
                dstIp = clientIp,
                srcPort = serverPort,
                dstPort = clientPort,
                seqNum = serverSeq.get() and 0xFFFFFFFFL,
                ackNum = clientNextSeq.get(),
                flags = PacketUtils.TCP_RST or PacketUtils.TCP_ACK
            )
            writeToTun(pkt)
        }

        fun closeSilently() {
            closed = true
            upstreamJob?.cancel()
            runCatching { upstreamStream?.close() }
        }
    }
}
