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

    @Test
    fun appSettings_defaultsToEnglishLanguage() {
        val defaults = com.example.data.AppSettings()
        assertEquals(com.example.model.AppLanguage.ENGLISH, defaults.language)
    }

    @Test
    fun uriConfigParser_parsesBase64BundleTelegramPrefixAndSubscriptionUrls() {
        val vmessJson = """{"v":"2","ps":"VMess-Sub","add":"vm.example.com","port":443,"id":"22222222-2222-2222-2222-222222222222","aid":0,"net":"ws","host":"cdn.example.com","path":"/v","tls":"tls"}"""
        val vmessUri = "vmess://" + android.util.Base64.encodeToString(vmessJson.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        val rawBundle = "Copy this -> $vmessUri\nServer2: vless://33333333-3333-3333-3333-333333333333@vl.example.com:443?security=tls&type=tcp#VLESS-Node"
        val b64Bundle = android.util.Base64.encodeToString(rawBundle.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)

        val parsed = UriConfigParser.parseBatch(b64Bundle)
        assertEquals(2, parsed.size)
        assertEquals(VpnProtocol.VMESS, parsed[0].protocol)
        assertEquals("vm.example.com", parsed[0].host)
        assertEquals(VpnProtocol.VLESS, parsed[1].protocol)
        assertEquals("vl.example.com", parsed[1].host)

        org.junit.Assert.assertTrue(UriConfigParser.looksLikeSubscriptionUrl("https://sub.example.com/api/v1/client/subscribe?token=abc"))
        org.junit.Assert.assertFalse(UriConfigParser.looksLikeSubscriptionUrl("vless://111@host:443#tag"))
        org.junit.Assert.assertFalse(UriConfigParser.looksLikeSubscriptionUrl("http://user:pass@proxy.example.com:8080#Proxy"))
    }

    @Test
    fun subscriptionManager_parsesHeadersCorrectly() {
        val encodedTitle = "base64:" + android.util.Base64.encodeToString("Baldr VIP".toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        assertEquals("Baldr VIP", com.example.data.SubscriptionManager.parseProfileTitle(encodedTitle))
        assertEquals("Direct Feed", com.example.data.SubscriptionManager.parseProfileTitle("Direct Feed"))

        val info = com.example.data.SubscriptionManager.parseUserInfo("upload=1024; download=4096; total=1048576; expire=1893456000")
        assertEquals(1024L, info.uploadBytes)
        assertEquals(4096L, info.downloadBytes)
        assertEquals(1048576L, info.totalBytes)
        assertEquals(1893456000L, info.expireAtSeconds)
    }

    @Test
    fun remoteUpdateManager_evaluatesForcedAndOptionalUpdates() {
        org.junit.Assert.assertTrue(com.example.vpn.RemoteUpdateManager.parseBooleanFlag(false, "true"))
        org.junit.Assert.assertTrue(com.example.vpn.RemoteUpdateManager.parseBooleanFlag(false, "1"))
        org.junit.Assert.assertFalse(com.example.vpn.RemoteUpdateManager.parseBooleanFlag(false, "false"))

        val forced = com.example.vpn.RemoteUpdateManager.evaluateUpdateState(
            forcedUpdate = true,
            apkUrl = "https://cdn.example.com/baldr-v7.apk",
            latestVersionCode = 0L,
            currentVersionCode = 6L,
            updateMessage = "Critical security update"
        )
        org.junit.Assert.assertTrue(forced.hasUpdate)
        org.junit.Assert.assertTrue(forced.forced)
        assertEquals("https://cdn.example.com/baldr-v7.apk", forced.apkUrl)

        val optional = com.example.vpn.RemoteUpdateManager.evaluateUpdateState(
            forcedUpdate = false,
            apkUrl = "https://cdn.example.com/baldr-v7.apk",
            latestVersionCode = 7L,
            currentVersionCode = 6L
        )
        org.junit.Assert.assertTrue(optional.hasUpdate)
        org.junit.Assert.assertFalse(optional.forced)

        val alreadyLatest = com.example.vpn.RemoteUpdateManager.evaluateUpdateState(
            forcedUpdate = true,
            apkUrl = "https://cdn.example.com/baldr-v6.apk",
            latestVersionCode = 6L,
            currentVersionCode = 6L
        )
        org.junit.Assert.assertFalse(alreadyLatest.hasUpdate)
        org.junit.Assert.assertFalse(alreadyLatest.forced)

        val invalidUrl = com.example.vpn.RemoteUpdateManager.evaluateUpdateState(
            forcedUpdate = true,
            apkUrl = "not-a-valid-url",
            latestVersionCode = 9L,
            currentVersionCode = 6L
        )
        org.junit.Assert.assertFalse(invalidUrl.hasUpdate)
    }

    @Test
    fun appAnalytics_handlesDiagnosticAndScreenEventsSafely() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        com.example.vpn.AppAnalytics.init(context, true)
        com.example.vpn.AppAnalytics.logScreen("dashboard")
        com.example.vpn.AppAnalytics.logVpnConnectAttempt("VLESS", "FULL_SYSTEM", "ULTRA_LOW_POWER")
        com.example.vpn.AppAnalytics.logVpnConnected("VLESS")
        com.example.vpn.AppAnalytics.logConfigImported("clipboard", 2)
        com.example.vpn.AppAnalytics.logSubscriptionSynced(5)
        com.example.vpn.AppAnalytics.sendDiagnosticTestEvent(context)
    }

    @Test
    fun vpnShortcutController_cachesLastServerAndDetectsBusyStates() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val profile = com.example.model.VpnProfile(
            id = 42L,
            name = "Germany-Fast",
            protocol = VpnProtocol.VLESS,
            host = "de.example.com",
            port = 443
        )
        com.example.vpn.VpnShortcutController.cacheLastServer(context, profile)
        assertEquals(42L, com.example.vpn.VpnShortcutController.getCachedLastServerId(context))
        assertEquals("Germany-Fast", com.example.vpn.VpnShortcutController.getCachedLastServerName(context))
        assertEquals("VLESS", com.example.vpn.VpnShortcutController.getCachedProtocolBadge(context))

        org.junit.Assert.assertTrue(com.example.vpn.VpnShortcutController.isTunnelActiveOrBusy(com.example.model.ConnectionState.CONNECTED))
        org.junit.Assert.assertTrue(com.example.vpn.VpnShortcutController.isTunnelActiveOrBusy(com.example.model.ConnectionState.CONNECTING))
        org.junit.Assert.assertTrue(com.example.vpn.VpnShortcutController.isTunnelActiveOrBusy(com.example.model.ConnectionState.FIREWALL_LOCKED))
        org.junit.Assert.assertFalse(com.example.vpn.VpnShortcutController.isTunnelActiveOrBusy(com.example.model.ConnectionState.DISCONNECTED))
        org.junit.Assert.assertFalse(com.example.vpn.VpnShortcutController.isTunnelActiveOrBusy(com.example.model.ConnectionState.ERROR))
    }

    @Test
    fun compactWidget_buildsRemoteViewsForCompactAndNormalSizes() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val profile = com.example.model.VpnProfile(
            id = 7L,
            name = "Tokyo-SS",
            protocol = VpnProtocol.SHADOWSOCKS,
            host = "jp.example.com",
            port = 8388
        )
        com.example.vpn.VpnShortcutController.cacheLastServer(context, profile)
        com.example.vpn.TunnelTelemetryBus.setConnectionState(com.example.model.ConnectionState.CONNECTED, profile)

        val wideOptions = android.os.Bundle().apply {
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 160)
        }
        val wideViews = com.example.widget.BaldrVpnWidgetProvider.buildRemoteViews(context, wideOptions)
        val wideInflated = wideViews.apply(context, android.widget.FrameLayout(context))
        val nameView = wideInflated.findViewById<android.widget.TextView>(R.id.widget_server_name)
        val statusView = wideInflated.findViewById<android.widget.TextView>(R.id.widget_status_text)
        val textContainer = wideInflated.findViewById<android.view.View>(R.id.widget_text_container)
        assertEquals("Tokyo-SS", nameView.text.toString())
        assertEquals(context.getString(R.string.widget_status_connected), statusView.text.toString())
        assertEquals(android.view.View.VISIBLE, textContainer.visibility)

        val tinyOptions = android.os.Bundle().apply {
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 55)
        }
        val tinyViews = com.example.widget.BaldrVpnWidgetProvider.buildRemoteViews(context, tinyOptions)
        val tinyInflated = tinyViews.apply(context, android.widget.FrameLayout(context))
        val tinyTextContainer = tinyInflated.findViewById<android.view.View>(R.id.widget_text_container)
        assertEquals(android.view.View.GONE, tinyTextContainer.visibility)

        com.example.vpn.TunnelTelemetryBus.setConnectionState(com.example.model.ConnectionState.DISCONNECTED)
    }

    @Test
    fun quickSettingsTile_mapsConnectionStatesAndHandlesSync() {
        assertEquals(
            android.service.quicksettings.Tile.STATE_ACTIVE,
            com.example.vpn.BaldrQuickTileService.resolveTileState(com.example.model.ConnectionState.CONNECTED)
        )
        assertEquals(
            android.service.quicksettings.Tile.STATE_ACTIVE,
            com.example.vpn.BaldrQuickTileService.resolveTileState(com.example.model.ConnectionState.CONNECTING)
        )
        assertEquals(
            android.service.quicksettings.Tile.STATE_INACTIVE,
            com.example.vpn.BaldrQuickTileService.resolveTileState(com.example.model.ConnectionState.DISCONNECTED)
        )
    }

    @Test
    fun fcmService_parsesSilentRemoteSyncAndActionUrls() {
        org.junit.Assert.assertTrue(
            com.example.vpn.AppFirebaseMessagingService.shouldSyncRemoteConfig(
                mapOf("sync_remote_config" to "true")
            )
        )
        org.junit.Assert.assertTrue(
            com.example.vpn.AppFirebaseMessagingService.shouldSyncRemoteConfig(
                mapOf("forced_update" to "true")
            )
        )
        org.junit.Assert.assertFalse(
            com.example.vpn.AppFirebaseMessagingService.shouldSyncRemoteConfig(
                mapOf("title" to "Hello")
            )
        )

        assertEquals(
            "https://example.com/app.apk",
            com.example.vpn.AppFirebaseMessagingService.resolveActionUrl(
                mapOf("apk_url" to "https://example.com/app.apk", "url" to "https://example.com")
            )
        )
        assertEquals(
            "https://example.com/news",
            com.example.vpn.AppFirebaseMessagingService.resolveActionUrl(
                mapOf("apk_url" to "invalid-url", "url" to "https://example.com/news")
            )
        )
        org.junit.Assert.assertNull(
            com.example.vpn.AppFirebaseMessagingService.resolveActionUrl(mapOf("url" to "ftp://bad"))
        )
    }

    @Test
    fun exitIpChecker_measuresRealTcpLatencyAndHandlesLoadBalance() {
        java.net.ServerSocket(0, 5, java.net.InetAddress.getByName("127.0.0.1")).use { serverSocket ->
            val port = serverSocket.localPort
            val reachable = com.example.model.VpnProfile(
                id = 1L,
                name = "Local-Reachable",
                protocol = VpnProtocol.VLESS,
                host = "127.0.0.1",
                port = port
            )
            val invalid = com.example.model.VpnProfile(
                id = 2L,
                name = "Invalid-Host",
                protocol = VpnProtocol.VMESS,
                host = "",
                port = 0
            )
            val loadBalance = com.example.model.VpnProfile(
                id = 3L,
                name = "Combined-LB",
                protocol = VpnProtocol.LOAD_BALANCE,
                host = "multi",
                port = 0,
                memberProfileIds = "1,2"
            )

            val measured = kotlinx.coroutines.runBlocking {
                com.example.data.ExitIpChecker.measureLatency(reachable, timeoutMs = 1500)
            }
            assertNotNull(measured)
            org.junit.Assert.assertTrue(measured!! >= 1)

            val invalidMs = kotlinx.coroutines.runBlocking {
                com.example.data.ExitIpChecker.measureLatency(invalid, timeoutMs = 500)
            }
            org.junit.Assert.assertNull(invalidMs)

            val lbMs = kotlinx.coroutines.runBlocking {
                com.example.data.ExitIpChecker.measureLatency(
                    loadBalance,
                    allProfiles = listOf(reachable, invalid),
                    timeoutMs = 1500
                )
            }
            assertNotNull(lbMs)
            org.junit.Assert.assertTrue(lbMs!! >= 1)
        }
    }

    @Test
    fun exitIpChecker_classifiesLatencySortsByFastestAndFindsFastestId() {
        assertEquals(com.example.data.LatencyTier.UNTESTED, com.example.data.ExitIpChecker.classifyLatency(null))
        assertEquals(com.example.data.LatencyTier.UNREACHABLE, com.example.data.ExitIpChecker.classifyLatency(-1))
        assertEquals(com.example.data.LatencyTier.EXCELLENT, com.example.data.ExitIpChecker.classifyLatency(45))
        assertEquals(com.example.data.LatencyTier.GOOD, com.example.data.ExitIpChecker.classifyLatency(180))
        assertEquals(com.example.data.LatencyTier.FAIR, com.example.data.ExitIpChecker.classifyLatency(420))
        assertEquals(com.example.data.LatencyTier.POOR, com.example.data.ExitIpChecker.classifyLatency(850))

        val pTimeout = com.example.model.VpnProfile(id = 1L, name = "Dead", protocol = VpnProtocol.VLESS, host = "a", port = 443, latencyMs = -1)
        val pUntested = com.example.model.VpnProfile(id = 2L, name = "New", protocol = VpnProtocol.VLESS, host = "b", port = 443, latencyMs = null)
        val pSlow = com.example.model.VpnProfile(id = 3L, name = "Slow", protocol = VpnProtocol.VLESS, host = "c", port = 443, latencyMs = 310)
        val pFast = com.example.model.VpnProfile(id = 4L, name = "Fast", protocol = VpnProtocol.VLESS, host = "d", port = 443, latencyMs = 52)
        val pMed = com.example.model.VpnProfile(id = 5L, name = "Med", protocol = VpnProtocol.VLESS, host = "e", port = 443, latencyMs = 140)

        val list = listOf(pTimeout, pUntested, pSlow, pFast, pMed)
        assertEquals(4L, com.example.data.ExitIpChecker.findFastestProfileId(list))

        val sorted = com.example.data.ExitIpChecker.sortProfilesByFastest(list)
        assertEquals(listOf(4L, 5L, 3L, 2L, 1L), sorted.map { it.id })
    }

    @Test
    fun lowBatteryController_activatesBelow20PercentAndReducesPingAndBackgroundPolicy() {
        com.example.vpn.LowBatteryController.resetForTesting()
        try {
            assertEquals(19, com.example.vpn.LowBatteryController.parseBatteryPercent(19, 100))
            assertEquals(15, com.example.vpn.LowBatteryController.parseBatteryPercent(3, 20))
            assertEquals(-1, com.example.vpn.LowBatteryController.parseBatteryPercent(-1, 100))

            // At 20%: normal operation
            com.example.vpn.LowBatteryController.onBatteryReading(
                level = 20,
                scale = 100,
                status = android.os.BatteryManager.BATTERY_STATUS_DISCHARGING,
                plugged = 0
            )
            val at20 = com.example.vpn.LowBatteryController.state.value
            org.junit.Assert.assertFalse(at20.isLowBatteryActive)
            val normalPolicy = com.example.vpn.LowBatteryController.resolvePolicy(at20)
            org.junit.Assert.assertFalse(normalPolicy.pauseHighResourceBackgroundTraffic)
            assertEquals(6, normalPolicy.maxPingConcurrency)
            assertEquals(0L, normalPolicy.pingPacingDelayMs)
            org.junit.Assert.assertTrue(normalPolicy.allowAutoPingOnOpen)
            org.junit.Assert.assertTrue(normalPolicy.allowAutoExitIpCheck)
            org.junit.Assert.assertTrue(normalPolicy.allowBulkBenchmark)
            assertEquals(12_000, normalPolicy.sshKeepAliveIntervalMs)
            assertEquals(1_000L, normalPolicy.telemetrySampleIntervalMs)

            // Drop to 19%: Low-Battery Operation engages automatically
            com.example.vpn.LowBatteryController.onBatteryReading(
                level = 19,
                scale = 100,
                status = android.os.BatteryManager.BATTERY_STATUS_DISCHARGING,
                plugged = 0
            )
            com.example.vpn.LowBatteryController.updatePausedBackgroundOperations(3)
            val at19 = com.example.vpn.LowBatteryController.state.value
            org.junit.Assert.assertTrue(at19.isLowBatteryActive)
            assertEquals(19, at19.batteryPercent)
            assertEquals(3, at19.pausedBackgroundOperations)

            val lowPolicy = com.example.vpn.LowBatteryController.resolvePolicy(at19)
            org.junit.Assert.assertTrue(lowPolicy.pauseHighResourceBackgroundTraffic)
            assertEquals(1, lowPolicy.maxPingConcurrency)
            assertEquals(1_500L, lowPolicy.pingPacingDelayMs)
            assertEquals(10_000L, lowPolicy.minSinglePingCooldownMs)
            org.junit.Assert.assertFalse(lowPolicy.allowAutoPingOnOpen)
            org.junit.Assert.assertFalse(lowPolicy.allowAutoExitIpCheck)
            org.junit.Assert.assertFalse(lowPolicy.allowBulkBenchmark)
            assertEquals(60_000, lowPolicy.sshKeepAliveIntervalMs)
            assertEquals(5_000L, lowPolicy.telemetrySampleIntervalMs)
            assertEquals(15_000L, lowPolicy.autoReconnectInitialBackoffMs)

            // Disabling feature at 15% turns off Low-Battery Operation & resets paused count
            com.example.vpn.LowBatteryController.onBatteryReading(
                level = 15,
                scale = 100,
                status = android.os.BatteryManager.BATTERY_STATUS_DISCHARGING,
                plugged = 0
            )
            com.example.vpn.LowBatteryController.setFeatureEnabled(false)
            val disabledState = com.example.vpn.LowBatteryController.state.value
            org.junit.Assert.assertFalse(disabledState.isLowBatteryActive)
            assertEquals(0, disabledState.pausedBackgroundOperations)

            // Re-enable and recover to 25%: returns to standby
            com.example.vpn.LowBatteryController.setFeatureEnabled(true)
            org.junit.Assert.assertTrue(com.example.vpn.LowBatteryController.state.value.isLowBatteryActive)
            com.example.vpn.LowBatteryController.onBatteryReading(
                level = 25,
                scale = 100,
                status = android.os.BatteryManager.BATTERY_STATUS_CHARGING,
                plugged = android.os.BatteryManager.BATTERY_PLUGGED_USB
            )
            val recovered = com.example.vpn.LowBatteryController.state.value
            org.junit.Assert.assertFalse(recovered.isLowBatteryActive)
            org.junit.Assert.assertTrue(recovered.isCharging)
            assertEquals(0, recovered.pausedBackgroundOperations)
        } finally {
            com.example.vpn.LowBatteryController.resetForTesting()
        }
    }

    @Test
    fun sshConnector_reducesKeepAlivePingFrequencyInLowBatteryMode() {
        val profile = com.example.model.VpnProfile(
            name = "SSH-Battery",
            protocol = VpnProtocol.SSH,
            host = "127.0.0.1",
            port = 22,
            usernameOrUuid = "user",
            passwordOrSecret = "pass"
        )
        val connector = com.example.vpn.protocols.SshConnector(profile) { true }
        assertEquals(12_000, connector.effectiveKeepAliveIntervalMs())
        connector.setLowBatteryMode(true)
        assertEquals(60_000, connector.effectiveKeepAliveIntervalMs())
        connector.setLowBatteryMode(false)
        assertEquals(12_000, connector.effectiveKeepAliveIntervalMs())
        connector.close()
    }

    @Test
    fun appSettings_enablesLowBatteryAutoModeByDefault() {
        val defaults = com.example.data.AppSettings()
        org.junit.Assert.assertTrue(defaults.lowBatteryAutoModeEnabled)
    }
}
