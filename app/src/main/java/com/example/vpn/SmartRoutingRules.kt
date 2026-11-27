package com.example.vpn

/**
 * Smart Routing & Ad/Tracker DNS filtering engine:
 * - Bypasses local LAN / private subnets (RFC 1918, loopback, link-local, CGNAT).
 * - Bypasses Iranian domains (.ir, domestic banks/services), user custom bypass domains,
 *   and major Iranian ISP/banking IPv4 CIDR allocations so domestic & banking apps stay fast.
 * - Filters known ad & tracker domains with instant synthetic NXDOMAIN responses when AdBlock DNS is enabled.
 */
object SmartRoutingRules {

    private data class Ipv4Cidr(val network: Long, val mask: Long)

    private val lanCidrs: List<Ipv4Cidr> = listOf(
        "10.0.0.0/8",
        "127.0.0.0/8",
        "100.64.0.0/10",
        "169.254.0.0/16",
        "172.16.0.0/12",
        "192.168.0.0/16",
        "224.0.0.0/4"
    ).mapNotNull(::parseCidr)

    // Major Iranian ISP, IXP, domestic cloud, and banking IPv4 allocations
    private val iranCidrs: List<Ipv4Cidr> = listOf(
        "2.144.0.0/12",
        "2.176.0.0/12",
        "5.22.0.0/16",
        "5.23.0.0/16",
        "5.52.0.0/14",
        "5.56.0.0/16",
        "5.112.0.0/12",
        "5.160.0.0/16",
        "5.190.0.0/16",
        "5.200.0.0/14",
        "5.208.0.0/12",
        "31.2.0.0/16",
        "31.56.0.0/13",
        "37.98.0.0/15",
        "37.137.0.0/16",
        "37.152.0.0/15",
        "37.156.0.0/16",
        "37.254.0.0/15",
        "46.32.0.0/15",
        "46.51.0.0/16",
        "46.100.0.0/15",
        "46.143.0.0/16",
        "46.164.0.0/15",
        "46.182.0.0/16",
        "46.209.0.0/16",
        "46.224.0.0/14",
        "46.245.0.0/16",
        "78.38.0.0/15",
        "79.127.0.0/16",
        "79.132.0.0/16",
        "80.191.0.0/16",
        "81.12.0.0/16",
        "81.31.160.0/20",
        "81.91.128.0/20",
        "82.99.192.0/18",
        "85.15.0.0/16",
        "85.133.128.0/17",
        "85.185.0.0/16",
        "86.55.0.0/16",
        "86.104.0.0/16",
        "87.107.0.0/16",
        "89.144.128.0/18",
        "89.165.0.0/16",
        "89.196.0.0/16",
        "91.92.0.0/16",
        "91.98.0.0/15",
        "91.108.128.0/18",
        "91.133.128.0/17",
        "91.184.64.0/18",
        "91.245.224.0/20",
        "92.114.16.0/20",
        "93.110.0.0/16",
        "93.113.0.0/16",
        "93.114.0.0/16",
        "93.115.0.0/16",
        "93.117.0.0/16",
        "93.118.0.0/15",
        "94.74.128.0/18",
        "94.101.176.0/20",
        "94.139.160.0/19",
        "94.182.0.0/15",
        "94.184.0.0/16",
        "95.38.0.0/16",
        "95.64.0.0/17",
        "95.80.128.0/17",
        "95.81.0.0/16",
        "95.82.0.0/16",
        "109.122.192.0/18",
        "109.125.128.0/18",
        "109.162.128.0/17",
        "151.232.0.0/13",
        "151.240.0.0/13",
        "164.138.128.0/18",
        "176.65.192.0/18",
        "178.131.0.0/16",
        "178.252.128.0/18",
        "185.4.28.0/22",
        "185.8.172.0/22",
        "185.51.200.0/22",
        "185.55.224.0/22",
        "185.73.0.0/22",
        "185.88.152.0/22",
        "185.105.236.0/22",
        "185.110.188.0/22",
        "185.120.220.0/22",
        "185.143.232.0/22",
        "185.147.176.0/22",
        "185.166.104.0/22",
        "185.173.104.0/22",
        "185.208.172.0/22",
        "185.211.56.0/22",
        "185.213.164.0/22",
        "185.235.40.0/22",
        "188.158.0.0/15",
        "188.208.0.0/13",
        "188.229.0.0/16",
        "188.245.0.0/16",
        "188.253.0.0/16",
        "194.225.0.0/16",
        "195.146.32.0/19",
        "217.218.0.0/15"
    ).mapNotNull(::parseCidr)

    private val iranDomainSuffixes: Set<String> = setOf(
        "ir",
        "digikala.com",
        "snapp.ir",
        "snappfood.ir",
        "snapp.cab",
        "tapsi.ir",
        "tapsi.cab",
        "divar.ir",
        "divarcdn.com",
        "aparat.com",
        "filimo.com",
        "namava.ir",
        "cafebazaar.ir",
        "myket.ir",
        "bale.ai",
        "eitaa.com",
        "rubika.ir",
        "soroush-app.ir",
        "shaparak.ir",
        "blu.bank",
        "blubank.com",
        "mellatbank.com",
        "samanpr.com",
        "sb24.ir",
        "bmi.ir",
        "tejaratbank.ir",
        "bsi.ir",
        "refah-bank.ir",
        "banksepah.ir",
        "parsian-bank.ir",
        "pasargad.ir",
        "bpi.ir",
        "enbank.ir",
        "karafarinbank.ir",
        "sinabank.ir",
        "ayandehbank.ir",
        "shahr-bank.ir",
        "zarinpal.com",
        "idpay.ir",
        "zibal.ir",
        "asanpardakht.ir",
        "724.ir",
        "balad.ir",
        "neshan.org",
        "torob.com",
        "emalls.ir",
        "sheypoor.com",
        "alibaba.ir",
        "mrbilit.com",
        "varzesh3.com",
        "soft98.ir",
        "downloadha.com",
        "arvancloud.ir",
        "arvancloud.com",
        "mci.ir",
        "irancell.ir",
        "rightel.ir",
        "shatel.ir"
    )

    private val adAndTrackerSuffixes: Set<String> = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "ads.google.com",
        "app-measurement.com",
        "admob.com",
        "ads.facebook.com",
        "an.facebook.com",
        "pixel.facebook.com",
        "ads.twitter.com",
        "ads-api.twitter.com",
        "analytics.tiktok.com",
        "ads.tiktok.com",
        "taboola.com",
        "outbrain.com",
        "adnxs.com",
        "criteo.com",
        "criteo.net",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "casalemedia.com",
        "moatads.com",
        "scorecardresearch.com",
        "quantserve.com",
        "unityads.unity3d.com",
        "applovin.com",
        "applvn.com",
        "ironsrc.com",
        "supersonicads.com",
        "vungle.com",
        "chartboost.com",
        "inmobi.com",
        "startappservice.com",
        "adjust.com",
        "appsflyer.com",
        "branch.io",
        "mixpanel.com",
        "hotjar.com",
        "fullstory.com",
        "amplitude.com",
        "yektanet.com",
        "tapsell.ir",
        "adivery.com",
        "mediaad.org",
        "sabavision.com",
        "kaprila.com",
        "najva.com"
    )

    fun isLocalLanIp(ip: String): Boolean {
        val numeric = parseIpv4ToLong(ip) ?: return false
        return lanCidrs.any { (numeric and it.mask) == it.network }
    }

    fun isIranianIp(ip: String): Boolean {
        val numeric = parseIpv4ToLong(ip) ?: return false
        return iranCidrs.any { (numeric and it.mask) == it.network }
    }

    fun shouldBypassIp(
        ip: String,
        bypassLan: Boolean,
        bypassIran: Boolean,
        dynamicBypassedIps: Set<String> = emptySet()
    ): Boolean {
        if (bypassLan && isLocalLanIp(ip)) return true
        if (bypassIran && isIranianIp(ip)) return true
        if (dynamicBypassedIps.contains(ip)) return true
        return false
    }

    fun parseCustomDomainList(raw: String): Set<String> {
        if (raw.isBlank()) return emptySet()
        return raw.split(',', ';', '\n', ' ')
            .map { it.trim().lowercase().removePrefix("*.").removePrefix(".") }
            .filter { it.isNotEmpty() && it.contains('.') }
            .toSet()
    }

    fun isBypassDomain(
        domain: String,
        bypassIran: Boolean,
        customBypassDomains: Set<String> = emptySet()
    ): Boolean {
        val normalized = domain.trim().lowercase().trimEnd('.')
        if (normalized.isEmpty()) return false
        if (normalized == "localhost" || normalized.endsWith(".local") || normalized.endsWith(".lan")) {
            return true
        }
        if (bypassIran && matchesDomainSet(normalized, iranDomainSuffixes)) {
            return true
        }
        if (customBypassDomains.isNotEmpty() && matchesDomainSet(normalized, customBypassDomains)) {
            return true
        }
        return false
    }

    fun isAdOrTrackerDomain(domain: String): Boolean {
        val normalized = domain.trim().lowercase().trimEnd('.')
        if (normalized.isEmpty()) return false
        return matchesDomainSet(normalized, adAndTrackerSuffixes)
    }

    private fun matchesDomainSet(normalizedHost: String, suffixes: Set<String>): Boolean {
        if (suffixes.contains(normalizedHost)) return true
        var dotIndex = normalizedHost.indexOf('.')
        while (dotIndex >= 0 && dotIndex + 1 < normalizedHost.length) {
            val parent = normalizedHost.substring(dotIndex + 1)
            if (suffixes.contains(parent)) return true
            dotIndex = normalizedHost.indexOf('.', dotIndex + 1)
        }
        return false
    }

    /**
     * Parses the QNAME domain from a raw RFC 1035 DNS query packet (UDP payload).
     */
    fun extractDnsQueryDomain(dnsQuery: ByteArray): String? {
        if (dnsQuery.size < 13) return null
        val qdCount = ((dnsQuery[4].toInt() and 0xFF) shl 8) or (dnsQuery[5].toInt() and 0xFF)
        if (qdCount <= 0) return null
        val labels = mutableListOf<String>()
        var offset = 12
        var guard = 0
        while (offset < dnsQuery.size && guard++ < 64) {
            val len = dnsQuery[offset].toInt() and 0xFF
            if (len == 0) break
            if ((len and 0xC0) != 0) return null // compressed pointer not expected in query QNAME
            offset++
            if (offset + len > dnsQuery.size) return null
            labels.add(String(dnsQuery, offset, len, Charsets.US_ASCII))
            offset += len
        }
        return if (labels.isEmpty()) null else labels.joinToString(".").lowercase()
    }

    /**
     * Builds an instant RFC 1035 NXDOMAIN response for a blocked ad/tracker DNS query.
     */
    fun buildNxDomainResponse(dnsQuery: ByteArray): ByteArray? {
        if (dnsQuery.size < 17) return null
        var offset = 12
        var guard = 0
        while (offset < dnsQuery.size && guard++ < 64) {
            val len = dnsQuery[offset].toInt() and 0xFF
            offset++
            if (len == 0) break
            if ((len and 0xC0) != 0) return null
            offset += len
        }
        val questionEnd = offset + 4 // QTYPE + QCLASS
        if (questionEnd > dnsQuery.size) return null

        val resp = ByteArray(questionEnd)
        System.arraycopy(dnsQuery, 0, resp, 0, questionEnd)
        // Flags: QR=1, Opcode=0, AA=0, TC=0, RD=copied, RA=1, Z=0, RCODE=3 (NXDOMAIN)
        val rdBit = dnsQuery[2].toInt() and 0x01
        resp[2] = (0x80 or rdBit).toByte()
        resp[3] = 0x83.toByte()
        // QDCOUNT=1, ANCOUNT=0, NSCOUNT=0, ARCOUNT=0
        resp[4] = 0
        resp[5] = 1
        resp[6] = 0
        resp[7] = 0
        resp[8] = 0
        resp[9] = 0
        resp[10] = 0
        resp[11] = 0
        return resp
    }

    /**
     * Extracts IPv4 A-record addresses from a raw RFC 1035 DNS response so dynamically resolved
     * bypassed domains can have their TCP sockets routed directly without passing through the VPN.
     */
    fun extractDnsAnswerIpv4s(dnsResponse: ByteArray): List<String> {
        if (dnsResponse.size < 12) return emptyList()
        val qdCount = ((dnsResponse[4].toInt() and 0xFF) shl 8) or (dnsResponse[5].toInt() and 0xFF)
        val anCount = ((dnsResponse[6].toInt() and 0xFF) shl 8) or (dnsResponse[7].toInt() and 0xFF)
        if (anCount <= 0) return emptyList()

        var offset = 12
        repeat(qdCount) {
            offset = skipDnsName(dnsResponse, offset) ?: return emptyList()
            offset += 4 // QTYPE + QCLASS
            if (offset > dnsResponse.size) return emptyList()
        }

        val ips = mutableListOf<String>()
        repeat(anCount) {
            offset = skipDnsName(dnsResponse, offset) ?: return ips
            if (offset + 10 > dnsResponse.size) return ips
            val type = ((dnsResponse[offset].toInt() and 0xFF) shl 8) or (dnsResponse[offset + 1].toInt() and 0xFF)
            val rdLength = ((dnsResponse[offset + 8].toInt() and 0xFF) shl 8) or (dnsResponse[offset + 9].toInt() and 0xFF)
            offset += 10
            if (offset + rdLength > dnsResponse.size) return ips
            if (type == 1 && rdLength == 4) { // A record (IPv4)
                val ip = "${dnsResponse[offset].toInt() and 0xFF}.${dnsResponse[offset + 1].toInt() and 0xFF}." +
                    "${dnsResponse[offset + 2].toInt() and 0xFF}.${dnsResponse[offset + 3].toInt() and 0xFF}"
                ips.add(ip)
            }
            offset += rdLength
        }
        return ips
    }

    private fun skipDnsName(packet: ByteArray, startOffset: Int): Int? {
        var offset = startOffset
        var guard = 0
        while (offset < packet.size && guard++ < 64) {
            val len = packet[offset].toInt() and 0xFF
            if (len == 0) return offset + 1
            if ((len and 0xC0) == 0xC0) {
                return if (offset + 2 <= packet.size) offset + 2 else null
            }
            offset += 1 + len
        }
        return null
    }

    private fun parseCidr(cidr: String): Ipv4Cidr? {
        val parts = cidr.split("/")
        if (parts.size != 2) return null
        val ip = parseIpv4ToLong(parts[0]) ?: return null
        val prefix = parts[1].toIntOrNull() ?: return null
        if (prefix !in 0..32) return null
        val mask = if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        return Ipv4Cidr(ip and mask, mask)
    }

    private fun parseIpv4ToLong(ip: String): Long? {
        val octets = ip.trim().split('.')
        if (octets.size != 4) return null
        var result = 0L
        for (octet in octets) {
            val v = octet.toIntOrNull() ?: return null
            if (v !in 0..255) return null
            result = (result shl 8) or v.toLong()
        }
        return result
    }
}
