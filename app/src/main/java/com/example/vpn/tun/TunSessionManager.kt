package com.example.vpn.tun

import android.system.OsConstants
import com.example.model.AppTrafficInfo
import com.example.vpn.SmartRoutingRules
import com.example.vpn.protocols.TunnelStream
import com.example.vpn.protocols.UpstreamConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    private val onConnectionLost: () -> Unit = {},
    private val uidResolver: ((protocol: Int, srcIp: String, srcPort: Int, dstIp: String, dstPort: Int) -> String?)? = null,
    private val dnsSecondary: String = "8.8.8.8",
    private val isDeviceInteractiveProvider: () -> Boolean = { true },
    private val onPausedCountChanged: (Int) -> Unit = {},
    private val adBlockDnsEnabled: Boolean = false,
    private val customDohUrl: String = "",
    private val bypassIranEnabled: Boolean = false,
    private val bypassLanEnabled: Boolean = false,
    private val customBypassDomains: Set<String> = emptySet(),
    private val directSocketOpener: (suspend (host: String, port: Int) -> TunnelStream)? = null,
    private val onAdBlocked: () -> Unit = {},
    private val onConnectionBypassed: () -> Unit = {}
) {
    val totalRxBytes = AtomicLong(0L)
    val totalTxBytes = AtomicLong(0L)
    val activeStreamCount = AtomicInteger(0)
    val pausedBackgroundCount = AtomicInteger(0)
    private val dynamicBypassedIps = ConcurrentHashMap<String, Long>()

    private class MutableAppCounters {
        val bytesSent = AtomicLong(0L)
        val bytesReceived = AtomicLong(0L)
        val passedConnections = AtomicInteger(0)
        val failedConnections = AtomicInteger(0)
        val activeStreams = AtomicInteger(0)
        @Volatile var lastActivityMillis = System.currentTimeMillis()
    }

    private data class CachedDnsEntry(
        val response: ByteArray,
        val cachedAtMillis: Long
    )

    private val writeLock = Any()
    private val tcpSessions = ConcurrentHashMap<FlowKey, TcpFlowSession>()
    private val appCounters = ConcurrentHashMap<String, MutableAppCounters>()
    private val dnsCache = ConcurrentHashMap<String, CachedDnsEntry>()
    private val dnsSemaphore = Semaphore(MAX_CONCURRENT_DNS)
    private val running = AtomicBoolean(true)
    private val dropAllMode = AtomicBoolean(false)
    private val lowBatteryMode = AtomicBoolean(false)
    private val connectionLostNotified = AtomicBoolean(false)
    private val consecutiveFailures = AtomicInteger(0)
    private val lastFailureMillis = AtomicLong(0L)
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

        // Adaptive idle session reaper: 30s normally (5m idle), 45s idle / 15s half-closed in Low-Battery mode
        reaperJob = scope.launch(Dispatchers.IO) {
            while (isActive && running.get()) {
                val lowBat = lowBatteryMode.get()
                delay(if (lowBat) LOW_BATTERY_REAPER_INTERVAL_MS else NORMAL_REAPER_INTERVAL_MS)
                val now = System.currentTimeMillis()
                if (dnsCache.size > MAX_DNS_CACHE_ENTRIES) {
                    dnsCache.entries.removeIf { now - it.value.cachedAtMillis > DNS_CACHE_TTL_MS }
                }
                if (tcpSessions.isEmpty()) continue
                val activeTimeout = if (lowBatteryMode.get()) LOW_BATTERY_ACTIVE_IDLE_TIMEOUT_MS else ACTIVE_IDLE_TIMEOUT_MS
                val halfClosedTimeout = if (lowBatteryMode.get()) LOW_BATTERY_HALF_CLOSED_TIMEOUT_MS else HALF_CLOSED_IDLE_TIMEOUT_MS
                val expired = tcpSessions.entries.filter { entry ->
                    val idleMs = now - entry.value.lastActivityMillis
                    if (entry.value.isHalfClosedByClient()) {
                        idleMs > halfClosedTimeout
                    } else {
                        idleMs > activeTimeout
                    }
                }
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
     * Switches Low-Battery Operation mode dynamically: pauses high-resource background TCP traffic
     * when screen is off or concurrent streams exceed the low-power cap, and prunes idle sockets faster.
     */
    fun setLowBatteryMode(enabled: Boolean) {
        val prev = lowBatteryMode.getAndSet(enabled)
        if (prev == enabled) return
        if (enabled) {
            val now = System.currentTimeMillis()
            var pruned = 0
            for (entry in tcpSessions.entries.toList()) {
                val idleMs = now - entry.value.lastActivityMillis
                if (idleMs > LOW_BATTERY_ACTIVE_IDLE_TIMEOUT_MS ||
                    (!isDeviceInteractiveProvider() && entry.value.totalFlowBytes() >= HIGH_RESOURCE_BG_FLOW_BYTES)
                ) {
                    entry.value.closeSilently()
                    if (tcpSessions.remove(entry.key) != null) {
                        pruned++
                    }
                }
            }
            activeStreamCount.set(tcpSessions.size)
            if (pruned > 0) {
                val totalPaused = pausedBackgroundCount.addAndGet(pruned)
                onPausedCountChanged(totalPaused)
                onLog("Low-Battery Mode: paused $pruned idle/background stream(s)")
            }
        } else {
            pausedBackgroundCount.set(0)
            onPausedCountChanged(0)
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
        connectionLostNotified.set(false)
        dropAllMode.set(false)
    }

    fun stop() {
        running.set(false)
        readerJob?.cancel()
        reaperJob?.cancel()
        tcpSessions.values.forEach { it.closeSilently() }
        tcpSessions.clear()
        appCounters.clear()
        dnsCache.clear()
        activeStreamCount.set(0)
    }

    fun snapshotAppTraffic(): List<AppTrafficInfo> {
        return appCounters.entries
            .map { (pkg, c) ->
                AppTrafficInfo(
                    packageName = pkg,
                    bytesSent = c.bytesSent.get(),
                    bytesReceived = c.bytesReceived.get(),
                    passedConnections = c.passedConnections.get(),
                    failedConnections = c.failedConnections.get(),
                    activeStreams = c.activeStreams.get().coerceAtLeast(0),
                    lastActivityMillis = c.lastActivityMillis
                )
            }
            .sortedWith(
                compareByDescending<AppTrafficInfo> { it.activeStreams > 0 }
                    .thenByDescending { it.lastActivityMillis }
            )
    }

    private fun resolveAppPackage(
        protocol: Int,
        srcIp: String,
        srcPort: Int,
        dstIp: String,
        dstPort: Int
    ): String {
        val resolved = runCatching {
            uidResolver?.invoke(protocol, srcIp, srcPort, dstIp, dstPort)
        }.getOrNull()
        return if (!resolved.isNullOrBlank()) resolved else "system.network"
    }

    private fun getCounter(pkg: String): MutableAppCounters =
        appCounters.getOrPut(pkg) { MutableAppCounters() }

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

        // DNS queries (UDP port 53) are checked for AdBlock & Smart Routing, cached briefly, and relayed
        if (udp.dstPort == 53) {
            val dnsQuery = packet.copyOfRange(payloadOffset, payloadOffset + payloadLen)
            val appPkg = resolveAppPackage(OsConstants.IPPROTO_UDP, ip.srcIpStr, udp.srcPort, ip.dstIpStr, udp.dstPort)
            val counter = getCounter(appPkg)
            counter.bytesSent.addAndGet(payloadLen.toLong())
            counter.lastActivityMillis = System.currentTimeMillis()
            totalTxBytes.addAndGet(payloadLen.toLong())

            val queryDomain = SmartRoutingRules.extractDnsQueryDomain(dnsQuery)
            if (adBlockDnsEnabled && queryDomain != null && SmartRoutingRules.isAdOrTrackerDomain(queryDomain)) {
                val blockedResp = SmartRoutingRules.buildNxDomainResponse(dnsQuery)
                if (blockedResp != null && !dropAllMode.get()) {
                    val respPacket = PacketUtils.buildIpv4UdpPacket(
                        srcIp = ip.dstIp,
                        dstIp = ip.srcIp,
                        srcPort = udp.dstPort,
                        dstPort = udp.srcPort,
                        payload = blockedResp
                    )
                    writeToTun(respPacket)
                    onAdBlocked()
                    onLog("[ADBLOCK] Blocked $queryDomain")
                }
                return
            }

            val shouldBypassDomain = queryDomain != null && SmartRoutingRules.isBypassDomain(
                domain = queryDomain,
                bypassIran = bypassIranEnabled,
                customBypassDomains = customBypassDomains
            )

            val cacheKey = DnsCacheHelper.extractQuestionKey(dnsQuery)
            if (cacheKey != null) {
                val cached = dnsCache[cacheKey]
                if (cached != null && System.currentTimeMillis() - cached.cachedAtMillis < DNS_CACHE_TTL_MS) {
                    val rewritten = DnsCacheHelper.withTransactionId(cached.response, dnsQuery[0], dnsQuery[1])
                    if (shouldBypassDomain) {
                        recordDynamicBypassIps(rewritten)
                    }
                    if (!dropAllMode.get()) {
                        val respPacket = PacketUtils.buildIpv4UdpPacket(
                            srcIp = ip.dstIp,
                            dstIp = ip.srcIp,
                            srcPort = udp.dstPort,
                            dstPort = udp.srcPort,
                            payload = rewritten
                        )
                        writeToTun(respPacket)
                        totalRxBytes.addAndGet(rewritten.size.toLong())
                        counter.bytesReceived.addAndGet(rewritten.size.toLong())
                        counter.passedConnections.incrementAndGet()
                        counter.lastActivityMillis = System.currentTimeMillis()
                    }
                    return
                }
            }

            scope.launch(Dispatchers.IO) {
                val dnsResp = dnsSemaphore.withPermit {
                    val primary = runCatching {
                        activeConnector.forwardDnsQuery(dnsQuery, dnsPrimary, customDohUrl)
                    }.getOrNull()
                    if (primary != null) {
                        primary
                    } else if (dnsSecondary.isNotBlank() && dnsSecondary != dnsPrimary) {
                        runCatching {
                            activeConnector.forwardDnsQuery(dnsQuery, dnsSecondary, customDohUrl)
                        }.getOrNull()
                    } else {
                        null
                    }
                }

                if (dnsResp == null) {
                    counter.failedConnections.incrementAndGet()
                    counter.lastActivityMillis = System.currentTimeMillis()
                    return@launch
                }

                if (shouldBypassDomain) {
                    recordDynamicBypassIps(dnsResp)
                }

                if (cacheKey != null && DnsCacheHelper.isCacheableResponse(dnsResp)) {
                    if (dnsCache.size >= MAX_DNS_CACHE_ENTRIES) {
                        val now = System.currentTimeMillis()
                        dnsCache.entries.removeIf { now - it.value.cachedAtMillis > DNS_CACHE_TTL_MS }
                    }
                    dnsCache[cacheKey] = CachedDnsEntry(dnsResp, System.currentTimeMillis())
                }

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
                counter.bytesReceived.addAndGet(dnsResp.size.toLong())
                counter.passedConnections.incrementAndGet()
                counter.lastActivityMillis = System.currentTimeMillis()
            }
        }
    }

    private fun recordDynamicBypassIps(dnsResponse: ByteArray) {
        val now = System.currentTimeMillis()
        if (dynamicBypassedIps.size > 512) {
            dynamicBypassedIps.entries.removeIf { now - it.value > 15 * 60_000L }
        }
        SmartRoutingRules.extractDnsAnswerIpv4s(dnsResponse).forEach { resolvedIp ->
            dynamicBypassedIps[resolvedIp] = now
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
            if (lowBatteryMode.get() && shouldPauseNewBackgroundStream(tcp.dstPort)) {
                val count = pausedBackgroundCount.incrementAndGet()
                onPausedCountChanged(count)
                val rst = PacketUtils.buildIpv4TcpPacket(
                    srcIp = ip.dstIp,
                    dstIp = ip.srcIp,
                    srcPort = tcp.dstPort,
                    dstPort = tcp.srcPort,
                    seqNum = 0L,
                    ackNum = (tcp.seqNum + 1L) and 0xFFFFFFFFL,
                    flags = PacketUtils.TCP_RST or PacketUtils.TCP_ACK
                )
                writeToTun(rst)
                return
            }
            val appPkg = if (lowBatteryMode.get()) {
                "low.battery.saver"
            } else {
                resolveAppPackage(OsConstants.IPPROTO_TCP, ip.srcIpStr, tcp.srcPort, ip.dstIpStr, tcp.dstPort)
            }
            val session = TcpFlowSession(
                key = key,
                appPackage = appPkg,
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

    private fun shouldPauseNewBackgroundStream(dstPort: Int): Boolean {
        // Always keep DNS-over-TCP (port 53) unblocked
        if (dstPort == 53) return false
        // Pause new background TCP bursts while device screen is off in Low-Battery mode
        if (!isDeviceInteractiveProvider()) return true
        // Cap concurrent streams while Low-Battery mode is active to prevent background saturation
        return tcpSessions.size >= LOW_BATTERY_MAX_CONCURRENT_STREAMS
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
        private val appPackage: String,
        private val clientIp: ByteArray,
        private val serverIp: ByteArray,
        private val clientPort: Int,
        private val serverPort: Int,
        initialClientSeq: Long
    ) {
        @Volatile var lastActivityMillis = System.currentTimeMillis()
        @Volatile private var closed = false
        private val established = AtomicBoolean(false)
        private val clientFinReceived = AtomicBoolean(false)
        private val activeRecorded = AtomicBoolean(false)
        private val flowBytes = AtomicLong(0L)
        private val clientNextSeq = AtomicLong((initialClientSeq + 1L) and 0xFFFFFFFFL)
        private val serverSeq = AtomicLong(Random.nextLong(100_000L, 2_000_000_000L))
        private val sendQueue = Channel<ByteArray>(capacity = SEND_QUEUE_CAPACITY)
        private var upstreamStream: TunnelStream? = null
        private var upstreamJob: Job? = null
        private var writerJob: Job? = null
        private val counter = getCounter(appPackage)

        fun isHalfClosedByClient(): Boolean = clientFinReceived.get()

        fun totalFlowBytes(): Long = flowBytes.get()

        fun startUpstream() {
            upstreamJob = scope.launch(Dispatchers.IO) {
                val activeConnector = connector
                if (activeConnector == null || dropAllMode.get()) {
                    counter.failedConnections.incrementAndGet()
                    counter.lastActivityMillis = System.currentTimeMillis()
                    sendRst()
                    closeSilently()
                    tcpSessions.remove(key)
                    activeStreamCount.set(tcpSessions.size)
                    return@launch
                }

                try {
                    val directOpener = directSocketOpener
                    val shouldBypassDirect = directOpener != null && SmartRoutingRules.shouldBypassIp(
                        ip = key.dstIp,
                        bypassLan = bypassLanEnabled,
                        bypassIran = bypassIranEnabled,
                        dynamicBypassedIps = dynamicBypassedIps.keys
                    )
                    val stream = if (shouldBypassDirect && directOpener != null) {
                        onConnectionBypassed()
                        directOpener.invoke(key.dstIp, key.dstPort)
                    } else {
                        try {
                            activeConnector.openTcpStream(key.dstIp, key.dstPort)
                        } catch (firstErr: Exception) {
                            if (closed || dropAllMode.get() || !running.get()) {
                                throw firstErr
                            }
                            // Retry once after brief backoff to survive mobile radio / NAT micro-blips
                            delay(UPSTREAM_RETRY_BACKOFF_MS)
                            val latestConnector = connector ?: throw firstErr
                            if (closed || dropAllMode.get() || !running.get()) {
                                throw firstErr
                            }
                            latestConnector.openTcpStream(key.dstIp, key.dstPort)
                        }
                    }
                    consecutiveFailures.set(0)
                    if (closed || dropAllMode.get()) {
                        stream.close()
                        return@launch
                    }
                    upstreamStream = stream
                    established.set(true)
                    if (activeRecorded.compareAndSet(false, true)) {
                        counter.activeStreams.incrementAndGet()
                        counter.passedConnections.incrementAndGet()
                        counter.lastActivityMillis = System.currentTimeMillis()
                    }

                    // Strict FIFO single writer coroutine guarantees TCP segments never reorder on the tunnel
                    writerJob = scope.launch(Dispatchers.IO) {
                        try {
                            for (chunk in sendQueue) {
                                if (closed || dropAllMode.get()) break
                                stream.write(chunk, 0, chunk.size)
                                lastActivityMillis = System.currentTimeMillis()
                            }
                        } catch (_: Exception) {
                            if (!closed && !dropAllMode.get()) {
                                sendRst()
                                closeSilently()
                                if (tcpSessions.remove(key) != null) {
                                    activeStreamCount.set(tcpSessions.size)
                                }
                            }
                        }
                    }

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
                        val now = System.currentTimeMillis()
                        lastActivityMillis = now
                        counter.lastActivityMillis = now
                        val transferred = flowBytes.addAndGet(n.toLong())
                        totalRxBytes.addAndGet(n.toLong())
                        counter.bytesReceived.addAndGet(n.toLong())

                        // Pause sustained high-resource background transfers while screen is off in Low-Battery mode
                        if (lowBatteryMode.get() &&
                            !isDeviceInteractiveProvider() &&
                            transferred >= HIGH_RESOURCE_BG_FLOW_BYTES
                        ) {
                            val totalPaused = pausedBackgroundCount.incrementAndGet()
                            onPausedCountChanged(totalPaused)
                            sendRst()
                            break
                        }

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
                        counter.failedConnections.incrementAndGet()
                        counter.lastActivityMillis = System.currentTimeMillis()
                        onLog("TCP ${key.dstIp}:${key.dstPort} failed ($appPackage): ${e.message}")
                        sendRst()

                        if (!established.get()) {
                            val now = System.currentTimeMillis()
                            val prevFailAt = lastFailureMillis.getAndSet(now)
                            val failCount = if (now - prevFailAt > FAILURE_WINDOW_RESET_MS) {
                                consecutiveFailures.set(1)
                                1
                            } else {
                                consecutiveFailures.incrementAndGet()
                            }
                            val connectorLost = if (activeConnector.supportsStatefulHealthCheck) {
                                !activeConnector.isHealthy()
                            } else {
                                failCount >= STATELESS_FAILURE_THRESHOLD
                            }
                            if (connectorLost && connectionLostNotified.compareAndSet(false, true)) {
                                consecutiveFailures.set(0)
                                onConnectionLost()
                            }
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
            if (closed || !established.get() || dropAllMode.get() || clientFinReceived.get()) return
            val expected = clientNextSeq.get()
            if (seqNum != expected) {
                sendAckOnly()
                return
            }
            clientNextSeq.set((expected + data.size) and 0xFFFFFFFFL)
            flowBytes.addAndGet(data.size.toLong())
            totalTxBytes.addAndGet(data.size.toLong())
            counter.bytesSent.addAndGet(data.size.toLong())
            counter.lastActivityMillis = System.currentTimeMillis()
            sendAckOnly()

            val enqueue = sendQueue.trySend(data)
            if (enqueue.isFailure && !closed) {
                sendRst()
                closeSilently()
                if (tcpSessions.remove(key) != null) {
                    activeStreamCount.set(tcpSessions.size)
                }
            }
        }

        fun onClientFin(finSeq: Long) {
            if (closed || !clientFinReceived.compareAndSet(false, true)) return
            clientNextSeq.set((finSeq + 1L) and 0xFFFFFFFFL)
            // Properly half-close: ACK the FIN and drain queued writes so server response can still finish
            sendAckOnly()
            sendQueue.close()
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
            if (!closed) {
                closed = true
                if (activeRecorded.compareAndSet(true, false)) {
                    counter.activeStreams.decrementAndGet()
                }
            }
            sendQueue.close()
            writerJob?.cancel()
            upstreamJob?.cancel()
            runCatching { upstreamStream?.close() }
        }
    }

    companion object {
        private const val ACTIVE_IDLE_TIMEOUT_MS = 300_000L
        private const val HALF_CLOSED_IDLE_TIMEOUT_MS = 45_000L
        private const val LOW_BATTERY_ACTIVE_IDLE_TIMEOUT_MS = 45_000L
        private const val LOW_BATTERY_HALF_CLOSED_TIMEOUT_MS = 15_000L
        private const val NORMAL_REAPER_INTERVAL_MS = 30_000L
        private const val LOW_BATTERY_REAPER_INTERVAL_MS = 60_000L
        private const val LOW_BATTERY_MAX_CONCURRENT_STREAMS = 10
        private const val HIGH_RESOURCE_BG_FLOW_BYTES = 512L * 1024L
        private const val DNS_CACHE_TTL_MS = 60_000L
        private const val MAX_DNS_CACHE_ENTRIES = 256
        private const val MAX_CONCURRENT_DNS = 6
        private const val SEND_QUEUE_CAPACITY = 1024
        private const val STATELESS_FAILURE_THRESHOLD = 5
        private const val UPSTREAM_RETRY_BACKOFF_MS = 250L
        private const val FAILURE_WINDOW_RESET_MS = 30_000L
    }
}

internal object DnsCacheHelper {
    fun extractQuestionKey(query: ByteArray): String? {
        if (query.size < 17) return null
        val isResponse = (query[2].toInt() and 0x80) != 0
        val opcode = (query[2].toInt() ushr 3) and 0x0F
        val qdCount = ((query[4].toInt() and 0xFF) shl 8) or (query[5].toInt() and 0xFF)
        if (isResponse || opcode != 0 || qdCount != 1) return null

        val sb = StringBuilder()
        var idx = 12
        while (idx < query.size) {
            val len = query[idx].toInt() and 0xFF
            idx++
            if (len == 0) break
            if ((len and 0xC0) != 0 || idx + len > query.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 0 until len) {
                val ch = (query[idx + i].toInt() and 0xFF).toChar()
                sb.append(ch.lowercaseChar())
            }
            idx += len
        }
        if (idx + 4 > query.size || sb.isEmpty()) return null
        val qType = ((query[idx].toInt() and 0xFF) shl 8) or (query[idx + 1].toInt() and 0xFF)
        val qClass = ((query[idx + 2].toInt() and 0xFF) shl 8) or (query[idx + 3].toInt() and 0xFF)
        return "$sb|$qType|$qClass"
    }

    fun isCacheableResponse(response: ByteArray): Boolean {
        if (response.size < 12) return false
        val isResponse = (response[2].toInt() and 0x80) != 0
        val truncated = (response[2].toInt() and 0x02) != 0
        val rcode = response[3].toInt() and 0x0F
        return isResponse && !truncated && rcode == 0
    }

    fun withTransactionId(cachedResponse: ByteArray, id0: Byte, id1: Byte): ByteArray {
        val copy = cachedResponse.copyOf()
        if (copy.size >= 2) {
            copy[0] = id0
            copy[1] = id1
        }
        return copy
    }
}
