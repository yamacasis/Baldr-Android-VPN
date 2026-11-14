package com.example.vpn.protocols

import com.example.model.LoadBalanceStrategy
import com.example.model.VpnProfile
import com.example.model.VpnProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Multi-server load-balancing connector that distributes concurrent TCP streams & DNS queries
 * across multiple upstream profiles (SOCKS4/5, HTTP, V2Ray, SSH) with automatic failover.
 */
class LoadBalanceConnector(
    private val profile: VpnProfile,
    private val members: List<VpnProfile>,
    private val protector: (Socket) -> Boolean
) : UpstreamConnector {

    private data class MemberNode(
        val profile: VpnProfile,
        val connector: UpstreamConnector,
        @Volatile var healthy: Boolean = true
    )

    private val nodes: List<MemberNode> = members
        .filter { it.protocol != VpnProtocol.LOAD_BALANCE }
        .map { member ->
            MemberNode(
                profile = member,
                connector = ProtocolConnectorFactory.create(member, emptyList(), protector)
            )
        }

    private val rrCounter = AtomicInteger(0)

    override suspend fun connect() = withContext(Dispatchers.IO) {
        if (nodes.isEmpty()) {
            throw IOException("Load-balance profile has no member servers")
        }
        val results = nodes.map { node ->
            async {
                val ok = runCatching { node.connector.connect() }.isSuccess
                node.healthy = ok
                ok
            }
        }.awaitAll()

        if (results.none { it }) {
            throw IOException("All ${nodes.size} member servers failed to connect")
        }
    }

    override suspend fun openTcpStream(dstHost: String, dstPort: Int): TunnelStream =
        withContext(Dispatchers.IO) {
            val ordered = orderedCandidates()
            var lastError: Throwable? = null
            for (node in ordered) {
                try {
                    val stream = node.connector.openTcpStream(dstHost, dstPort)
                    node.healthy = true
                    return@withContext stream
                } catch (e: Throwable) {
                    node.healthy = false
                    lastError = e
                }
            }
            throw IOException(
                "All load-balance members failed for $dstHost:$dstPort (${lastError?.message})",
                lastError
            )
        }

    override suspend fun forwardDnsQuery(dnsQuery: ByteArray, dnsServer: String): ByteArray? =
        withContext(Dispatchers.IO) {
            for (node in orderedCandidates()) {
                val resp = runCatching { node.connector.forwardDnsQuery(dnsQuery, dnsServer) }.getOrNull()
                if (resp != null) return@withContext resp
            }
            null
        }

    private fun orderedCandidates(): List<MemberNode> {
        if (nodes.isEmpty()) return emptyList()
        val healthyFirst = nodes.filter { it.healthy }.ifEmpty { nodes }
        val unhealthyTail = if (healthyFirst.size < nodes.size) nodes.filter { !it.healthy } else emptyList()

        val rotatedHealthy = when (profile.loadBalanceStrategy) {
            LoadBalanceStrategy.ROUND_ROBIN -> {
                val start = (rrCounter.getAndIncrement() and 0x7FFFFFFF) % healthyFirst.size
                List(healthyFirst.size) { idx -> healthyFirst[(start + idx) % healthyFirst.size] }
            }
            LoadBalanceStrategy.RANDOM -> {
                val start = Random.nextInt(healthyFirst.size)
                List(healthyFirst.size) { idx -> healthyFirst[(start + idx) % healthyFirst.size] }
            }
            LoadBalanceStrategy.LEAST_LATENCY -> {
                val sorted = healthyFirst.sortedBy { node ->
                    val ms = node.profile.latencyMs
                    if (ms != null && ms > 0) ms else Int.MAX_VALUE
                }
                val topCount = minOf(2, sorted.size)
                val pick = (rrCounter.getAndIncrement() and 0x7FFFFFFF) % topCount
                if (pick == 0 || sorted.size <= 1) sorted
                else listOf(sorted[pick]) + sorted.filterIndexed { i, _ -> i != pick }
            }
        }
        return rotatedHealthy + unhealthyTail
    }

    override fun close() {
        nodes.forEach { runCatching { it.connector.close() } }
    }
}
