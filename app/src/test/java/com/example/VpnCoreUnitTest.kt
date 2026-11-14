package com.example

import com.example.data.UriConfigParser
import com.example.model.TransportType
import com.example.model.VpnProtocol
import com.example.model.countryCodeToFlag
import com.example.vpn.formatBytes
import com.example.vpn.tun.PacketUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VpnCoreUnitTest {

    @Test
    fun parseMultiProtocolUris_succeeds() {
        val batch = """
            vless://11111111-1111-1111-1111-111111111111@de.example.com:443?type=ws&security=tls&sni=de.example.com&path=%2Fws#Germany-VLESS
            trojan://secretpass@nl.example.com:443?sni=nl.example.com#Netherlands-Trojan
            ss://YWVzLTI1Ni1nY206cGFzc3dvcmQ=@us.example.com:8388#US-SS
            socks5://user1:pass1@192.168.1.50:1080#Local-SOCKS5
            socks4://myid@10.0.0.5:1080#SOCKS4-Node
            http://alice:bob@proxy.example.com:8080#HTTP-Proxy
            ssh://ubuntu@ssh.example.com:22#SSH-Tunnel
        """.trimIndent()

        val parsed = UriConfigParser.parseBatch(batch)
        assertEquals(7, parsed.size)

        assertEquals(VpnProtocol.VLESS, parsed[0].protocol)
        assertEquals(TransportType.WS, parsed[0].transport)
        assertEquals(true, parsed[0].tlsEnabled)
        assertEquals("/ws", parsed[0].wsPath)

        assertEquals(VpnProtocol.TROJAN, parsed[1].protocol)
        assertEquals("secretpass", parsed[1].passwordOrSecret)

        assertEquals(VpnProtocol.SHADOWSOCKS, parsed[2].protocol)
        assertEquals("aes-256-gcm", parsed[2].cipher)
        assertEquals("password", parsed[2].passwordOrSecret)

        assertEquals(VpnProtocol.SOCKS5, parsed[3].protocol)
        assertEquals("user1", parsed[3].usernameOrUuid)
        assertEquals("pass1", parsed[3].passwordOrSecret)

        assertEquals(VpnProtocol.SOCKS4, parsed[4].protocol)
        assertEquals("myid", parsed[4].usernameOrUuid)

        assertEquals(VpnProtocol.HTTP_PROXY, parsed[5].protocol)
        assertEquals(8080, parsed[5].port)

        assertEquals(VpnProtocol.SSH, parsed[6].protocol)
        assertEquals("ubuntu", parsed[6].usernameOrUuid)
        assertEquals(22, parsed[6].port)
    }

    @Test
    fun countryCodeToFlag_mapsIsoCodes() {
        assertEquals("\uD83C\uDDE9\uD83C\uDDEA", countryCodeToFlag("DE"))
        assertEquals("\uD83C\uDDFA\uD83C\uDDF8", countryCodeToFlag("us"))
        assertEquals("\uD83C\uDF10", countryCodeToFlag(""))
    }

    @Test
    fun packetBuilderAndParser_roundTrip() {
        val src = byteArrayOf(10, 8, 0, 2)
        val dst = byteArrayOf(1, 1, 1, 1)
        val pkt = PacketUtils.buildIpv4TcpPacket(
            srcIp = src,
            dstIp = dst,
            srcPort = 54321,
            dstPort = 443,
            seqNum = 1000L,
            ackNum = 2000L,
            flags = PacketUtils.TCP_SYN or PacketUtils.TCP_ACK,
            payload = "hello".toByteArray()
        )
        val ip = PacketUtils.parseIpv4Header(pkt, pkt.size)
        assertNotNull(ip)
        assertEquals("10.8.0.2", ip!!.srcIpStr)
        assertEquals("1.1.1.1", ip.dstIpStr)
        val tcp = PacketUtils.parseTcpHeader(pkt, ip.headerLength, ip.totalLength)
        assertNotNull(tcp)
        assertEquals(54321, tcp!!.srcPort)
        assertEquals(443, tcp.dstPort)
        assertEquals(1000L, tcp.seqNum)
        assertEquals(2000L, tcp.ackNum)
    }

    @Test
    fun formatBytesAndRate_formatsUnits() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("2.0 KB", formatBytes(2048))
        assertEquals("1.00 MB/s", com.example.vpn.formatRate(1024L * 1024L))
    }

    @Test
    fun connectionStates_includeFirewallAndReconnect() {
        val states = com.example.model.ConnectionState.entries.map { it.name }
        org.junit.Assert.assertTrue(states.contains("RECONNECTING"))
        org.junit.Assert.assertTrue(states.contains("FIREWALL_LOCKED"))
    }

    @Test
    fun loadBalanceProfile_parsesMembersAndExportsUri() {
        val profile = com.example.model.VpnProfile(
            id = 5L,
            name = "Multi Balance",
            protocol = VpnProtocol.LOAD_BALANCE,
            host = "multi",
            port = 0,
            memberProfileIds = "1, 2, 5, 2, -1, 0, abc, 3",
            loadBalanceStrategy = com.example.model.LoadBalanceStrategy.LEAST_LATENCY
        )

        assertEquals(listOf(1L, 2L, 3L), profile.parsedMemberIds())

        val exported = UriConfigParser.exportToUri(profile)
        org.junit.Assert.assertTrue(
            exported.startsWith("netshield-lb://least_latency?members=1, 2, 5, 2, -1, 0, abc, 3#")
        )
        org.junit.Assert.assertTrue(
            com.example.model.LoadBalanceStrategy.entries.map { it.name }
                .containsAll(listOf("ROUND_ROBIN", "LEAST_LATENCY", "RANDOM"))
        )
        assertEquals(com.example.model.ProtocolGroup.MULTI, VpnProtocol.LOAD_BALANCE.group)
    }

    @Test
    fun configBackupManager_encryptsOnlyPasswordsAndRestoresOnImport() {
        val profiles = listOf(
            com.example.model.VpnProfile(
                id = 10L,
                name = "My-SSH-Server",
                protocol = VpnProtocol.SSH,
                host = "ssh.secure.example.com",
                port = 2222,
                usernameOrUuid = "deploy_user",
                passwordOrSecret = "UltraSecretPass#99",
                privateKeyPem = "-----BEGIN PRIVATE KEY-----\nSECRETKEYDATA\n-----END PRIVATE KEY-----"
            ),
            com.example.model.VpnProfile(
                id = 11L,
                name = "My-SOCKS5",
                protocol = VpnProtocol.SOCKS5,
                host = "10.20.30.40",
                port = 1080,
                usernameOrUuid = "alice",
                passwordOrSecret = "socksPass!"
            ),
            com.example.model.VpnProfile(
                id = 12L,
                name = "Combined-LB",
                protocol = VpnProtocol.LOAD_BALANCE,
                host = "multi",
                port = 0,
                memberProfileIds = "10,11",
                loadBalanceStrategy = com.example.model.LoadBalanceStrategy.LEAST_LATENCY
            )
        )

        val json = com.example.data.ConfigBackupManager.exportProfiles(profiles, "BackupKey2026")
        org.junit.Assert.assertTrue(com.example.data.ConfigBackupManager.isEncryptedBackup(json))

        // Non-secret fields remain readable in exported JSON
        org.junit.Assert.assertTrue(json.contains("ssh.secure.example.com"))
        org.junit.Assert.assertTrue(json.contains("deploy_user"))
        org.junit.Assert.assertTrue(json.contains("My-SOCKS5"))

        // Passwords & private keys MUST NOT appear in plaintext in the exported JSON
        org.junit.Assert.assertFalse(json.contains("UltraSecretPass#99"))
        org.junit.Assert.assertFalse(json.contains("SECRETKEYDATA"))
        org.junit.Assert.assertFalse(json.contains("socksPass!"))
        org.junit.Assert.assertTrue(json.contains("ENC:v1:"))

        // Wrong passphrase fails cleanly
        val wrongOutcome = com.example.data.ConfigBackupManager.importBackup(json, "WrongPass")
        org.junit.Assert.assertTrue(wrongOutcome is com.example.data.ConfigBackupManager.ImportOutcome.WrongPasswordOrCorrupted)

        // Correct passphrase decrypts passwords & keys accurately
        val okOutcome = com.example.data.ConfigBackupManager.importBackup(json, "BackupKey2026")
        org.junit.Assert.assertTrue(okOutcome is com.example.data.ConfigBackupManager.ImportOutcome.Success)
        val restored = (okOutcome as com.example.data.ConfigBackupManager.ImportOutcome.Success).profiles
        assertEquals(3, restored.size)
        assertEquals("UltraSecretPass#99", restored[0].passwordOrSecret)
        org.junit.Assert.assertTrue(restored[0].privateKeyPem.contains("SECRETKEYDATA"))
        assertEquals("socksPass!", restored[1].passwordOrSecret)
        assertEquals("10,11", restored[2].memberProfileIds)
        assertEquals(com.example.model.LoadBalanceStrategy.LEAST_LATENCY, restored[2].loadBalanceStrategy)
    }

    @Test
    fun tunnelTelemetryBus_tracksAppTrafficAndClearsOnDisconnect() {
        val sample = listOf(
            com.example.model.AppTrafficInfo(
                packageName = "com.example.browser",
                bytesSent = 2048L,
                bytesReceived = 16384L,
                passedConnections = 4,
                failedConnections = 1,
                activeStreams = 1
            )
        )
        com.example.vpn.TunnelTelemetryBus.updateAppTraffic(sample)
        assertEquals(1, com.example.vpn.TunnelTelemetryBus.appTraffic.value.size)
        assertEquals(4, com.example.vpn.TunnelTelemetryBus.appTraffic.value.first().passedConnections)
        assertEquals(1, com.example.vpn.TunnelTelemetryBus.appTraffic.value.first().failedConnections)

        com.example.vpn.TunnelTelemetryBus.setConnectionState(com.example.model.ConnectionState.DISCONNECTED)
        assertEquals(0, com.example.vpn.TunnelTelemetryBus.appTraffic.value.size)
    }

    @Test
    fun crashReporter_handlesCallsSafelyWithoutThrowing() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        com.example.vpn.CrashReporter.init(context, true)
        com.example.vpn.CrashReporter.logBreadcrumb("unit_test_breadcrumb")
        com.example.vpn.CrashReporter.setTunnelContext("VLESS", "FULL_SYSTEM", "ULTRA_LOW_POWER", "CONNECTED")
        com.example.vpn.CrashReporter.recordError("unit_test", "sample error")
        com.example.vpn.CrashReporter.setCollectionEnabled(context, false)
    }

    @Test
    fun sshConnector_disablesCompressionAndTracksHealth() {
        val defaults = com.example.vpn.protocols.SshConnector.createDefaultConfigMap(tlsEnabled = true)
        assertEquals("none", defaults["compression.s2c"])
        assertEquals("none", defaults["compression.c2s"])
        assertEquals("true", defaults["tlsEnabled"])

        val profile = com.example.model.VpnProfile(
            name = "SSH-Test",
            protocol = VpnProtocol.SSH,
            host = "127.0.0.1",
            port = 22,
            usernameOrUuid = "user",
            passwordOrSecret = "pass"
        )
        val connector = com.example.vpn.protocols.SshConnector(profile) { true }
        org.junit.Assert.assertTrue(connector.supportsStatefulHealthCheck)
        org.junit.Assert.assertTrue(connector.isHealthy())
        connector.close()
        org.junit.Assert.assertFalse(connector.isHealthy())
    }

    @Test
    fun dnsCacheHelper_extractsKeyAndRewritesTransactionId() {
        // Minimal standard DNS A query for example.com with TxID = 0x1234
        val query = byteArrayOf(
            0x12, 0x34, // TxID
            0x01, 0x00, // Standard query
            0x00, 0x01, // QDCOUNT = 1
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x07, 'E'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(),
            'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            0x03, 'C'.code.toByte(), 'O'.code.toByte(), 'M'.code.toByte(),
            0x00,
            0x00, 0x01, // QTYPE = A
            0x00, 0x01  // QCLASS = IN
        )
        val key = com.example.vpn.tun.DnsCacheHelper.extractQuestionKey(query)
        assertEquals("example.com|1|1", key)

        val response = query.copyOf().apply {
            this[2] = 0x81.toByte() // QR=1 (response)
            this[3] = 0x80.toByte() // RCODE=0 (NOERROR)
        }
        org.junit.Assert.assertTrue(com.example.vpn.tun.DnsCacheHelper.isCacheableResponse(response))

        val rewritten = com.example.vpn.tun.DnsCacheHelper.withTransactionId(response, 0xAB.toByte(), 0xCD.toByte())
        assertEquals(0xAB.toByte(), rewritten[0])
        assertEquals(0xCD.toByte(), rewritten[1])
        // Original cached response remains untouched
        assertEquals(0x12.toByte(), response[0])
    }
}
