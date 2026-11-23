package com.example

import com.example.model.LoadBalanceStrategy
import com.example.model.MultiTunnelStatus
import com.example.model.VpnProfile
import com.example.model.VpnProtocol
import com.example.vpn.protocols.LoadBalanceConnector
import com.example.vpn.protocols.ProtectedSocketFactory
import com.example.vpn.protocols.TunnelStream
import com.example.vpn.protocols.UpstreamConnector
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class StabilityAndMultiModeUnitTest {

    private class FakeTunnelStream(val ownerName: String) : TunnelStream {
        private val bytes = ownerName.toByteArray(Charsets.UTF_8)
        private val stream = ByteArrayInputStream(bytes)
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = stream.read(buffer, offset, length)
        override fun write(data: ByteArray, offset: Int, length: Int) = Unit
        override fun inputStream(): InputStream = stream
        override fun close() = Unit
    }

    private fun TunnelStream.readOwnerName(): String =
        inputStream().readBytes().toString(Charsets.UTF_8)

    private class FakeUpstreamConnector(
        val name: String,
        var connectDelayMs: Long = 5L,
        var openDelayMs: Long = 5L
    ) : UpstreamConnector {
        val failConnect = AtomicBoolean(false)
        val failOpen = AtomicBoolean(false)
        val failProbe = AtomicBoolean(false)
        val connectCalls = AtomicInteger(0)
        val openCalls = AtomicInteger(0)

        override suspend fun connect() {
            connectCalls.incrementAndGet()
            if (connectDelayMs > 0L) delay(connectDelayMs)
            if (failConnect.get()) throw IOException("$name connect failed")
        }

        override suspend fun openTcpStream(dstHost: String, dstPort: Int): TunnelStream {
            openCalls.incrementAndGet()
            if (openDelayMs > 0L) delay(openDelayMs)
            if (failOpen.get()) throw IOException("$name stream failed")
            return FakeTunnelStream(name)
        }

        override suspend fun probeHealth(): Boolean {
            return !failProbe.get()
        }

        override fun close() = Unit
    }

    private fun buildProfile(id: Long, name: String, protocol: VpnProtocol = VpnProtocol.VLESS): VpnProfile {
        return VpnProfile(
            id = id,
            name = name,
            protocol = protocol,
            host = "127.0.0.1",
            port = 443
        )
    }

    @Test
    fun dualVpnConnectsTwoMembersAndFailsOverAutomaticallyOnPrimaryError() = runBlocking {
        val p1 = buildProfile(1L, "VPN-Fast", VpnProtocol.VLESS)
        val p2 = buildProfile(2L, "VPN-Backup", VpnProtocol.SSH)
        val lbProfile = VpnProfile(
            id = 99L,
            name = "Dual Shield",
            protocol = VpnProtocol.LOAD_BALANCE,
            host = "2 servers",
            port = 2,
            memberProfileIds = "1,2",
            loadBalanceStrategy = LoadBalanceStrategy.FAILOVER
        )

        val fastConn = FakeUpstreamConnector("VPN-Fast", connectDelayMs = 5L)
        val backupConn = FakeUpstreamConnector("VPN-Backup", connectDelayMs = 45L)
        var latestStatus = MultiTunnelStatus()

        val lb = LoadBalanceConnector(
            profile = lbProfile,
            members = listOf(p1, p2),
            protector = { true },
            connectorFactory = { if (it.id == 1L) fastConn else backupConn },
            statusPublisher = { latestStatus = it },
            logSink = {},
            enableBackgroundMonitor = false
        )

        try {
            lb.connect()

            // Both VPNs connected concurrently; fastest is Active and 2nd is Warm Standby
            assertEquals(1, fastConn.connectCalls.get())
            assertEquals(1, backupConn.connectCalls.get())
            assertTrue(latestStatus.isMultiMode)
            assertEquals("VPN-Fast", latestStatus.activeNodeName)
            assertEquals("VPN-Backup", latestStatus.standbyNodeName)
            assertTrue(latestStatus.standbyReady)
            assertEquals(2, latestStatus.healthyCount)
            assertEquals(0, latestStatus.switchCount)

            // Normal stream goes through Active VPN
            val stream1 = lb.openTcpStream("1.1.1.1", 443)
            assertEquals("VPN-Fast", stream1.readOwnerName())
            stream1.close()

            // Primary fails -> seamlessly fails over to Warm Standby VPN with zero exception
            fastConn.failOpen.set(true)
            val stream2 = lb.openTcpStream("1.1.1.1", 443)
            assertEquals("VPN-Backup", stream2.readOwnerName())
            stream2.close()

            assertEquals("VPN-Backup", latestStatus.activeNodeName)
            assertEquals(1, latestStatus.switchCount)
            assertTrue(lb.isHealthy())
        } finally {
            lb.close()
        }
    }

    @Test
    fun dualVpnManualSwitchSwapsActiveAndStandbyImmediately() = runBlocking {
        val p1 = buildProfile(10L, "Germany-VLESS", VpnProtocol.VLESS)
        val p2 = buildProfile(20L, "Finland-VMess", VpnProtocol.VMESS)
        val lbProfile = VpnProfile(
            id = 100L,
            name = "EU Dual",
            protocol = VpnProtocol.LOAD_BALANCE,
            host = "2 servers",
            port = 2,
            memberProfileIds = "10,20",
            loadBalanceStrategy = LoadBalanceStrategy.FAILOVER
        )

        val c1 = FakeUpstreamConnector("Germany-VLESS", connectDelayMs = 5L)
        val c2 = FakeUpstreamConnector("Finland-VMess", connectDelayMs = 35L)
        var latestStatus = MultiTunnelStatus()

        val lb = LoadBalanceConnector(
            profile = lbProfile,
            members = listOf(p1, p2),
            protector = { true },
            connectorFactory = { if (it.id == 10L) c1 else c2 },
            statusPublisher = { latestStatus = it },
            logSink = {},
            enableBackgroundMonitor = false
        )

        try {
            lb.connect()
            val initialActive = latestStatus.activeNodeName
            val initialStandby = latestStatus.standbyNodeName
            assertNotEquals(initialActive, initialStandby)

            val switched = lb.switchToStandbyNow()
            assertTrue(switched)
            assertEquals(initialStandby, latestStatus.activeNodeName)
            assertEquals(initialActive, latestStatus.standbyNodeName)
            assertEquals(1, latestStatus.switchCount)

            val stream = lb.openTcpStream("8.8.8.8", 443)
            assertEquals(initialStandby, stream.readOwnerName())
            stream.close()
        } finally {
            lb.close()
        }
    }

    @Test
    fun probeHealthFailsOverToStandbyWithoutReportingTunnelDown() = runBlocking {
        val p1 = buildProfile(30L, "Primary-Node", VpnProtocol.TROJAN)
        val p2 = buildProfile(40L, "Standby-Node", VpnProtocol.VLESS)
        val lbProfile = VpnProfile(
            id = 101L,
            name = "Failover Watchdog",
            protocol = VpnProtocol.LOAD_BALANCE,
            host = "2 servers",
            port = 2,
            memberProfileIds = "30,40",
            loadBalanceStrategy = LoadBalanceStrategy.FAILOVER
        )

        val c1 = FakeUpstreamConnector("Primary-Node", connectDelayMs = 5L)
        val c2 = FakeUpstreamConnector("Standby-Node", connectDelayMs = 35L)
        var latestStatus = MultiTunnelStatus()

        val lb = LoadBalanceConnector(
            profile = lbProfile,
            members = listOf(p1, p2),
            protector = { true },
            connectorFactory = { if (it.id == 30L) c1 else c2 },
            statusPublisher = { latestStatus = it },
            logSink = {},
            enableBackgroundMonitor = false
        )

        try {
            lb.connect()
            assertEquals("Primary-Node", latestStatus.activeNodeName)

            // Active node stops responding to health probes -> probeHealth promotes Standby and stays healthy
            c1.failProbe.set(true)
            val stillHealthy = lb.probeHealth()
            assertTrue(stillHealthy)
            assertEquals("Standby-Node", latestStatus.activeNodeName)
            assertEquals(1, latestStatus.switchCount)

            // If BOTH nodes fail probes & reconnects, probeHealth returns false
            c2.failProbe.set(true)
            c2.failConnect.set(true)
            c1.failConnect.set(true)
            assertFalse(lb.probeHealth())
        } finally {
            lb.close()
        }
    }

    @Test
    fun protectedSocketFactoryFallsBackToCachedIpWhenDnsFails() {
        ProtectedSocketFactory.clearHostCache()
        val fakeHost = "nonexistent-dns-blip.invalid"
        val expectedIp = InetAddress.getByName("198.51.100.42")
        ProtectedSocketFactory.seedHostCacheForTest(fakeHost, expectedIp)

        val resolved = ProtectedSocketFactory.resolveWithFallback(fakeHost)
        assertEquals(expectedIp.hostAddress, resolved.hostAddress)

        // Direct IP literals resolve without touching cache
        val directIp = ProtectedSocketFactory.resolveWithFallback("1.1.1.1")
        assertEquals("1.1.1.1", directIp.hostAddress)
        ProtectedSocketFactory.clearHostCache()
    }
}
