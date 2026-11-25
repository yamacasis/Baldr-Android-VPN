package com.example

import com.example.data.FreeProxyProviderCache
import com.example.data.FreeProxyProviderManager
import com.example.model.VpnProfile
import com.example.model.VpnProtocol
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Calendar

class FreeProxyProviderUnitTest {

    @Test
    fun parsePlainIpPort_filtersInvalidAndDeduplicates() {
        val raw = """
            185.220.101.1:1080
            socks5://185.220.101.1:1080
            45.95.147.20:9050 # Germany
            invalid-line
            99.10.20.30:99999
            103.152.112.145:8080
        """.trimIndent()

        val parsed = FreeProxyProviderManager.parsePlainIpPort(
            rawText = raw,
            providerName = "ProxyScrape",
            protocol = VpnProtocol.SOCKS5
        )

        assertEquals(3, parsed.size)
        assertEquals("185.220.101.1", parsed[0].host)
        assertEquals(1080, parsed[0].port)
        assertEquals(VpnProtocol.SOCKS5, parsed[0].protocol)
        assertTrue(parsed[0].name.startsWith("ProxyScrape ·"))
        assertEquals("45.95.147.20", parsed[1].host)
        assertEquals(9050, parsed[1].port)
        assertEquals("103.152.112.145", parsed[2].host)
    }

    @Test
    fun parseGeoNodeJson_extractsProtocolAndCountry() {
        val json = """
            {
              "data": [
                {"ip": "51.158.68.68", "port": "8811", "protocols": ["socks5"], "country": "FR"},
                {"ip": "138.68.60.8", "port": 8080, "protocols": ["http"], "country": "US"},
                {"ip": "bad", "port": "0", "protocols": ["socks5"], "country": "XX"}
              ]
            }
        """.trimIndent()

        val parsed = FreeProxyProviderManager.parseGeoNodeJson(json, "GeoNode")
        assertEquals(2, parsed.size)
        assertEquals("51.158.68.68", parsed[0].host)
        assertEquals(8811, parsed[0].port)
        assertEquals(VpnProtocol.SOCKS5, parsed[0].protocol)
        assertEquals("FR", parsed[0].lastCountryCode)

        assertEquals("138.68.60.8", parsed[1].host)
        assertEquals(8080, parsed[1].port)
        assertEquals(VpnProtocol.HTTP_PROXY, parsed[1].protocol)
        assertEquals("US", parsed[1].lastCountryCode)
    }

    @Test
    fun pingAndPickBest_selectsFastestHandshakeVerifiedCandidate() = runTest {
        val candidates = listOf(
            VpnProfile(name = "A", protocol = VpnProtocol.SOCKS5, host = "1.1.1.1", port = 1080),
            VpnProfile(name = "B", protocol = VpnProtocol.SOCKS5, host = "2.2.2.2", port = 1080),
            VpnProfile(name = "C", protocol = VpnProtocol.SOCKS5, host = "3.3.3.3", port = 1080),
            VpnProfile(name = "D", protocol = VpnProtocol.SOCKS5, host = "4.4.4.4", port = 1080)
        )

        val latencies = mapOf(
            "1.1.1.1" to 210,
            "2.2.2.2" to 42,   // lowest ping, but handshake fails
            "3.3.3.3" to 68,   // second lowest ping, handshake succeeds!
            "4.4.4.4" to null  // unreachable
        )

        var lastProgressDone = 0
        val result = FreeProxyProviderManager.pingAndPickBest(
            candidates = candidates,
            pingMeasure = { latencies[it.host] },
            handshakeVerifier = { it.host == "3.3.3.3" },
            onProgress = { progress -> lastProgressDone = progress.pingedCount }
        )

        assertNotNull(result.bestProfile)
        assertEquals("3.3.3.3", result.bestProfile?.host)
        assertEquals(68, result.bestProfile?.latencyMs)
        assertEquals(3, result.reachableCount)
        assertEquals(4, result.testedCount)
        assertTrue(result.verifiedHandshake)
        assertEquals(4, lastProgressDone)
    }

    @Test
    fun catalogHasIranRecommendedProvidersAndPrebundledAssetCoversAll() {
        val providers = FreeProxyProviderManager.providers
        assertTrue(providers.size >= 15)
        assertTrue(providers.count { it.iranRecommended } >= 8)
        assertTrue(providers.all { p -> p.sources.isNotEmpty() && p.sources.all { it.url.startsWith("https://") } })

        val assetFile = File("src/main/assets/free_providers_default.json")
        assertTrue("Bundled default asset must exist", assetFile.exists())
        val root = Json.parseToJsonElement(assetFile.readText()).jsonObject
        val bundledProviders = root["providers"]!!.jsonObject
        for (provider in providers) {
            val entry = bundledProviders[provider.id]?.jsonObject
            assertNotNull("Missing bundled snapshot for ${provider.id}", entry)
            val uris = entry!!["uris"]!!.jsonArray
            assertTrue("Bundled snapshot for ${provider.id} must have servers", uris.isNotEmpty())
        }
    }

    @Test
    fun jalaliConversionAndServerNameDateTagWorkAccurately() {
        // 2025-03-21 is Nowruz 1404/01/01
        val (jy, jm, jd) = FreeProxyProviderCache.gregorianToJalali(2025, 3, 21)
        assertEquals(1404, jy)
        assertEquals(1, jm)
        assertEquals(1, jd)

        val cal = Calendar.getInstance().apply { set(2025, Calendar.MARCH, 21, 14, 30, 0) }
        val faName = FreeProxyProviderCache.formatServerNameWithDate(
            providerName = "MatinGhanbari V2Ray",
            baseServerLabel = "MatinGhanbari V2Ray · Germany",
            updatedAtMillis = cal.timeInMillis,
            isFarsi = true
        )
        assertEquals("MatinGhanbari V2Ray (1404/01/01) · Germany", faName)

        val enName = FreeProxyProviderCache.formatServerNameWithDate(
            providerName = "ProxyScrape",
            baseServerLabel = "45.12.34.56:1080",
            updatedAtMillis = cal.timeInMillis,
            isFarsi = false
        )
        assertEquals("ProxyScrape (Mar 21) · 45.12.34.56:1080", enName)
    }
}
