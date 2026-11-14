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
}
