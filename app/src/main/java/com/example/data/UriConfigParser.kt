package com.example.data

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import com.example.model.TransportType
import com.example.model.VpnProfile
import com.example.model.VpnProtocol
import com.example.vpn.protocols.ShadowsocksConnector
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID

object UriConfigParser {

    private val KNOWN_SCHEMES = listOf(
        "vmess://",
        "vless://",
        "trojan://",
        "ss://",
        "socks5://",
        "socks4a://",
        "socks4://",
        "socks://",
        "https://",
        "http://",
        "ssh://"
    )

    fun parseBatch(rawText: String): List<VpnProfile> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return emptyList()

        // 1) Support direct single raw VMess JSON object if pasted without vmess:// prefix
        if (trimmed.startsWith("{") && trimmed.contains("\"add\"") && trimmed.contains("\"id\"")) {
            parseRawVmessJson(trimmed)?.let { return listOf(it) }
        }

        // 2) Line-by-line URI parsing (also extracts URIs after Telegram/note prefixes)
        val direct = parseLines(trimmed)
        if (direct.isNotEmpty()) return direct

        // 3) Support Base64-encoded multi-URI payloads (common in subscriptions & shared bundles)
        val compact = trimmed.replace("\n", "").replace("\r", "").trim()
        if (compact.length >= 12 && !compact.contains("://")) {
            val decoded = decodeBase64Flexible(compact)
            if (!decoded.isNullOrBlank() && KNOWN_SCHEMES.any { decoded.contains(it, ignoreCase = true) }) {
                val decodedProfiles = parseLines(decoded)
                if (decodedProfiles.isNotEmpty()) return decodedProfiles
            }
        }
        return emptyList()
    }

    fun parseCompatibleBatch(rawText: String): List<VpnProfile> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return emptyList()

        if (trimmed.startsWith("{") && trimmed.contains("\"add\"") && trimmed.contains("\"id\"")) {
            val profile = parseRawVmessJson(trimmed) ?: return emptyList()
            return if (isVmessJsonCompatible(trimmed, profile)) listOf(profile) else emptyList()
        }

        val direct = parseCompatibleLines(trimmed)
        if (direct.isNotEmpty()) return direct

        val compact = trimmed.replace("\n", "").replace("\r", "").trim()
        if (compact.length >= 12 && !compact.contains("://")) {
            val decoded = decodeBase64Flexible(compact)
            if (!decoded.isNullOrBlank() && KNOWN_SCHEMES.any { decoded.contains(it, ignoreCase = true) }) {
                val decodedProfiles = parseCompatibleLines(decoded)
                if (decodedProfiles.isNotEmpty()) return decodedProfiles
            }
        }
        return emptyList()
    }

    private fun parseLines(text: String): List<VpnProfile> {
        return text
            .lineSequence()
            .map { extractSchemeCandidate(it.trim()) }
            .filter { it.isNotEmpty() }
            .mapNotNull { parseSingle(it) }
            .toList()
    }

    private fun parseCompatibleLines(text: String): List<VpnProfile> {
        return text
            .lineSequence()
            .map { extractSchemeCandidate(it.trim()) }
            .filter { it.isNotEmpty() }
            .mapNotNull { candidate ->
                val profile = parseSingle(candidate) ?: return@mapNotNull null
                if (isConnectorCompatible(candidate, profile)) profile else null
            }
            .toList()
    }

    /**
     * Checks whether a parsed URI is genuinely supported by Baldr VPN's native connectors
     * (prevents selecting Reality / XTLS-Vision / gRPC / HTTPUpgrade / xHTTP / SS-2022 / legacy CFB
     * nodes that establish a decoy TCP/TLS socket and drop all tunneled RX traffic).
     */
    fun isConnectorCompatible(rawUri: String, profile: VpnProfile): Boolean {
        val cleanUri = rawUri.trim().replace("&amp;", "&")
        return when (profile.protocol) {
            VpnProtocol.VLESS -> {
                if (!isValidUuid(profile.usernameOrUuid)) return false
                val params = parseQueryParamsCaseInsensitive(cleanUri)
                val security = params["security"].orEmpty().lowercase()
                if (security == "reality" || params.containsKey("pbk") || params.containsKey("sid")) return false
                val flow = params["flow"].orEmpty().lowercase()
                if (flow.isNotBlank() && flow != "none") return false
                val encryption = params["encryption"].orEmpty().lowercase()
                if (encryption.isNotBlank() && encryption != "none") return false
                val type = params["type"].orEmpty().lowercase()
                if (type.isNotBlank() && type !in SUPPORTED_TRANSFORMS) return false
                val headerType = params["headertype"].orEmpty().lowercase()
                if (headerType == "http") return false
                // Reject corrupted paths where query params were glued without '&'
                if (profile.transport == TransportType.WS &&
                    (profile.wsPath.contains("fp=") || profile.wsPath.contains("security="))
                ) return false
                true
            }
            VpnProtocol.VMESS -> {
                if (!isValidUuid(profile.usernameOrUuid)) return false
                if (profile.alterId > 0) return false
                val encoded = cleanUri.substringAfter("://", "").substringBefore("#").trim()
                val decoded = if (encoded.startsWith("{")) encoded else decodeBase64Flexible(encoded)
                if (decoded != null) {
                    isVmessJsonCompatible(decoded, profile)
                } else {
                    true
                }
            }
            VpnProtocol.TROJAN -> {
                if (profile.passwordOrSecret.isBlank()) return false
                val params = parseQueryParamsCaseInsensitive(cleanUri)
                val security = params["security"].orEmpty().lowercase()
                if (security == "reality" || params.containsKey("pbk")) return false
                val type = params["type"].orEmpty().lowercase()
                if (type.isNotBlank() && type !in SUPPORTED_TRANSFORMS) return false
                val headerType = params["headertype"].orEmpty().lowercase()
                if (headerType == "http") return false
                true
            }
            VpnProtocol.SHADOWSOCKS -> {
                if (!ShadowsocksConnector.isSupportedCipher(profile.cipher)) return false
                if (profile.passwordOrSecret.isBlank()) return false
                val params = parseQueryParamsCaseInsensitive(cleanUri)
                if (params.containsKey("plugin") || params.containsKey("pbk") || params["security"] == "reality") {
                    return false
                }
                true
            }
            else -> true
        }
    }

    private fun isVmessJsonCompatible(jsonStr: String, profile: VpnProfile): Boolean {
        if (!isValidUuid(profile.usernameOrUuid)) return false
        if (profile.alterId > 0) return false
        val json = runCatching { JSONObject(jsonStr) }.getOrNull() ?: return false
        val net = json.optString("net", "tcp").trim().lowercase()
        if (net.isNotBlank() && net !in SUPPORTED_TRANSFORMS) return false
        val headerType = json.optString("type", "none").trim().lowercase()
        if (headerType == "http") return false
        return true
    }

    private fun isValidUuid(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.length < 32) return false
        return runCatching { UUID.fromString(trimmed) }.isSuccess
    }

    private fun parseQueryParamsCaseInsensitive(uri: String): Map<String, String> {
        val withoutFragment = uri.substringBefore("#")
        val query = withoutFragment.substringAfter("?", "")
        if (query.isBlank()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        for (pair in query.split("&")) {
            if (pair.isBlank()) continue
            val key = pair.substringBefore("=").trim().lowercase()
            val value = pair.substringAfter("=", "").trim()
            if (key.isNotEmpty() && !result.containsKey(key)) {
                result[key] = decodeUrl(value)
            }
        }
        return result
    }

    private val SUPPORTED_TRANSFORMS = setOf("tcp", "raw", "ws", "websocket")

    private fun extractSchemeCandidate(line: String): String {
        if (line.isEmpty()) return ""
        val lower = line.lowercase()
        if (KNOWN_SCHEMES.any { lower.startsWith(it) }) return line
        val earliestIdx = KNOWN_SCHEMES
            .mapNotNull { scheme ->
                val idx = lower.indexOf(scheme)
                if (idx >= 0) idx else null
            }
            .minOrNull()
        return if (earliestIdx != null) line.substring(earliestIdx).trim() else line
    }

    fun looksLikeSubscriptionUrl(rawText: String): Boolean {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty() || trimmed.contains('\n') || trimmed.contains('\r')) return false
        val lower = trimmed.lowercase()
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) return false
        if (trimmed.contains("@")) return false
        val parsed = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return false
        val host = parsed.host.orEmpty()
        if (host.isBlank()) return false
        val path = parsed.path.orEmpty()
        val hasQuery = !parsed.query.isNullOrBlank()
        return hasQuery || (path.isNotBlank() && path != "/")
    }

    fun parseSingle(rawUri: String): VpnProfile? {
        val trimmed = extractSchemeCandidate(rawUri.trim().replace("&amp;", "&"))
        return runCatching {
            when {
                trimmed.startsWith("vmess://", ignoreCase = true) -> parseVmess(trimmed)
                trimmed.startsWith("vless://", ignoreCase = true) -> parseVless(trimmed)
                trimmed.startsWith("trojan://", ignoreCase = true) -> parseTrojan(trimmed)
                trimmed.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(trimmed)
                trimmed.startsWith("socks5://", ignoreCase = true) ||
                    trimmed.startsWith("socks://", ignoreCase = true) -> parseStandardUri(trimmed, VpnProtocol.SOCKS5)
                trimmed.startsWith("socks4://", ignoreCase = true) ||
                    trimmed.startsWith("socks4a://", ignoreCase = true) -> parseStandardUri(trimmed, VpnProtocol.SOCKS4)
                trimmed.startsWith("https://", ignoreCase = true) -> parseStandardUri(trimmed, VpnProtocol.HTTP_PROXY, forceTls = true)
                trimmed.startsWith("http://", ignoreCase = true) -> parseStandardUri(trimmed, VpnProtocol.HTTP_PROXY, forceTls = false)
                trimmed.startsWith("ssh://", ignoreCase = true) -> parseStandardUri(trimmed, VpnProtocol.SSH)
                else -> null
            }
        }.getOrNull()
    }

    private fun parseVmess(uri: String): VpnProfile? {
        val encoded = uri.substringAfter("://").substringBefore("#").trim()
        val decoded = decodeBase64Flexible(encoded) ?: return null
        return parseRawVmessJson(decoded)
    }

    private fun parseRawVmessJson(jsonStr: String): VpnProfile? {
        val json = runCatching { JSONObject(jsonStr) }.getOrNull() ?: return null
        val host = json.optString("add").trim()
        val rawPort = json.opt("port")
        val port = when (rawPort) {
            is Number -> rawPort.toInt()
            is String -> rawPort.trim().toIntOrNull() ?: 443
            else -> 443
        }.coerceIn(1, 65535)
        val uuid = json.optString("id").trim()
        if (host.isEmpty() || uuid.isEmpty()) return null
        val net = json.optString("net", "tcp").lowercase()
        val tlsRaw = json.optString("tls", "").trim().lowercase()
        val tls = tlsRaw == "tls" || tlsRaw == "1" || tlsRaw == "true"
        val sni = json.optString("sni").ifBlank { json.optString("host") }
        val wsHost = json.optString("host")
        val wsPath = json.optString("path", "/").ifBlank { "/" }
        val ps = json.optString("ps").ifBlank { "$host:$port" }
        val alterId = when (val rawAid = json.opt("aid")) {
            is Number -> rawAid.toInt()
            is String -> rawAid.trim().toIntOrNull() ?: 0
            else -> 0
        }
        val scy = json.optString("scy", "auto").ifBlank { "auto" }
        val allowInsecure = json.optBoolean("allowInsecure", false) ||
            json.optString("allowInsecure") == "1" ||
            json.optBoolean("skip-cert-verify", false) ||
            json.optString("skip-cert-verify").equals("true", ignoreCase = true) ||
            json.optBoolean("insecure", false) ||
            json.optString("insecure") == "1"
        return VpnProfile(
            name = ps,
            protocol = VpnProtocol.VMESS,
            host = host,
            port = port,
            usernameOrUuid = uuid,
            transport = if (net == "ws" || net == "websocket") TransportType.WS else TransportType.TCP,
            tlsEnabled = tls,
            sni = sni,
            wsHost = wsHost,
            wsPath = wsPath,
            allowInsecure = allowInsecure,
            cipher = scy,
            alterId = alterId
        )
    }

    private fun parseVless(uri: String): VpnProfile? {
        val parsed = Uri.parse(uri)
        val uuid = parsed.userInfo?.trim().orEmpty()
        val host = parsed.host?.trim().orEmpty()
        val port = if (parsed.port > 0) parsed.port else 443
        if (host.isEmpty() || uuid.isEmpty()) return null
        val params = parseQueryParamsCaseInsensitive(uri)
        val type = params["type"]?.lowercase() ?: "tcp"
        val security = params["security"]?.lowercase() ?: "none"
        val tlsFlag = params["tls"]?.lowercase()
        val tlsEnabled = security == "tls" || security == "reality" || tlsFlag == "1" || tlsFlag == "true"
        val wsHost = params["host"].orEmpty()
        val sni = params["sni"].orEmpty().ifBlank { params["peer"].orEmpty() }.ifBlank {
            if (tlsEnabled) wsHost else ""
        }
        val wsPath = params["path"].orEmpty().ifBlank { "/" }
        val allowInsecure = params["allowinsecure"] == "1" ||
            params["allowinsecure"].equals("true", ignoreCase = true) ||
            params["insecure"] == "1" ||
            params["insecure"].equals("true", ignoreCase = true)
        val name = parsed.fragment?.let { decodeUrl(it) }?.ifBlank { null } ?: "$host:$port"
        return VpnProfile(
            name = name,
            protocol = VpnProtocol.VLESS,
            host = host,
            port = port,
            usernameOrUuid = uuid,
            transport = if (type == "ws" || type == "websocket") TransportType.WS else TransportType.TCP,
            tlsEnabled = tlsEnabled,
            sni = sni,
            wsHost = wsHost,
            wsPath = wsPath,
            allowInsecure = allowInsecure
        )
    }

    private fun parseTrojan(uri: String): VpnProfile? {
        val parsed = Uri.parse(uri)
        val password = parsed.userInfo?.trim().orEmpty()
        val host = parsed.host?.trim().orEmpty()
        val port = if (parsed.port > 0) parsed.port else 443
        if (host.isEmpty() || password.isEmpty()) return null
        val params = parseQueryParamsCaseInsensitive(uri)
        val type = params["type"]?.lowercase() ?: "tcp"
        val security = params["security"]?.lowercase()
        val tlsEnabled = security != "none"
        val wsHost = params["host"].orEmpty()
        val sni = params["sni"].orEmpty().ifBlank { params["peer"].orEmpty() }.ifBlank { wsHost }
        val wsPath = params["path"].orEmpty().ifBlank { "/" }
        val allowInsecure = params["allowinsecure"] == "1" ||
            params["allowinsecure"].equals("true", ignoreCase = true) ||
            params["insecure"] == "1" ||
            params["insecure"].equals("true", ignoreCase = true)
        val name = parsed.fragment?.let { decodeUrl(it) }?.ifBlank { null } ?: "$host:$port"
        return VpnProfile(
            name = name,
            protocol = VpnProtocol.TROJAN,
            host = host,
            port = port,
            passwordOrSecret = password,
            transport = if (type == "ws" || type == "websocket") TransportType.WS else TransportType.TCP,
            tlsEnabled = tlsEnabled,
            sni = sni.ifBlank { host },
            wsHost = wsHost,
            wsPath = wsPath,
            allowInsecure = allowInsecure
        )
    }

    private fun parseShadowsocks(uri: String): VpnProfile? {
        val withoutScheme = uri.substringAfter("ss://")
        val fragmentPart = withoutScheme.substringAfter("#", "")
        val name = decodeUrl(fragmentPart).ifBlank { "Shadowsocks" }
        val mainPart = withoutScheme.substringBefore("#").substringBefore("?")
        val (methodAndPass, hostAndPort) = if (mainPart.contains("@")) {
            val userInfoRaw = mainPart.substringBefore("@")
            val serverRaw = mainPart.substringAfter("@")
            val decodedUser = decodeBase64Flexible(userInfoRaw) ?: userInfoRaw
            decodedUser to serverRaw
        } else {
            val decoded = decodeBase64Flexible(mainPart) ?: return null
            decoded.substringBeforeLast("@") to decoded.substringAfterLast("@")
        }
        val method = methodAndPass.substringBefore(":").trim()
        val password = methodAndPass.substringAfter(":", "").trim()
        val host = hostAndPort.substringBeforeLast(":").trim().removePrefix("[").removeSuffix("]")
        val port = hostAndPort.substringAfterLast(":").toIntOrNull() ?: 8388
        if (host.isEmpty() || method.isEmpty() || password.isEmpty()) return null
        return VpnProfile(
            name = if (name == "Shadowsocks") "$host:$port" else name,
            protocol = VpnProtocol.SHADOWSOCKS,
            host = host,
            port = port,
            passwordOrSecret = password,
            cipher = method
        )
    }

    private fun parseStandardUri(
        uri: String,
        protocol: VpnProtocol,
        forceTls: Boolean? = null
    ): VpnProfile? {
        val parsed = Uri.parse(uri)
        val host = parsed.host?.trim().orEmpty()
        if (host.isEmpty()) return null
        val port = if (parsed.port > 0) parsed.port else protocol.defaultPort
        val userInfo = parsed.userInfo.orEmpty()
        val username = if (userInfo.contains(":")) decodeUrl(userInfo.substringBefore(":")) else decodeUrl(userInfo)
        val password = if (userInfo.contains(":")) decodeUrl(userInfo.substringAfter(":")) else ""
        val name = parsed.fragment?.let { decodeUrl(it) }?.ifBlank { null } ?: "${protocol.shortBadge} $host"
        val security = parsed.getQueryParameter("security")?.lowercase()
        val tlsParam = parsed.getQueryParameter("tls")?.lowercase()
        val tlsEnabled = forceTls ?: (security == "tls" || tlsParam == "1" || tlsParam == "true")
        val sni = parsed.getQueryParameter("sni").orEmpty()
        val allowInsecure = parsed.getQueryParameter("allowInsecure") == "1"
        return VpnProfile(
            name = name,
            protocol = protocol,
            host = host,
            port = port,
            usernameOrUuid = username,
            passwordOrSecret = password,
            tlsEnabled = tlsEnabled,
            sni = sni,
            allowInsecure = allowInsecure
        )
    }

    fun exportToUri(profile: VpnProfile): String {
        val encodedName = encodeUrl(profile.name)
        return when (profile.protocol) {
            VpnProtocol.VMESS -> {
                val json = JSONObject().apply {
                    put("v", "2")
                    put("ps", profile.name)
                    put("add", profile.host)
                    put("port", profile.port)
                    put("id", profile.usernameOrUuid)
                    put("aid", profile.alterId)
                    put("scy", profile.cipher)
                    put("net", profile.transport.wireName)
                    put("type", "none")
                    put("host", profile.wsHost)
                    put("path", profile.wsPath)
                    put("tls", if (profile.tlsEnabled) "tls" else "")
                    put("sni", profile.sni)
                }
                val b64 = Base64.encodeToString(json.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                "vmess://$b64"
            }
            VpnProtocol.VLESS -> {
                val sec = if (profile.tlsEnabled) "tls" else "none"
                "vless://${profile.usernameOrUuid}@${profile.host}:${profile.port}?type=${profile.transport.wireName}&security=$sec&sni=${encodeUrl(profile.sni)}&host=${encodeUrl(profile.wsHost)}&path=${encodeUrl(profile.wsPath)}#$encodedName"
            }
            VpnProtocol.TROJAN -> {
                "trojan://${encodeUrl(profile.passwordOrSecret)}@${profile.host}:${profile.port}?type=${profile.transport.wireName}&sni=${encodeUrl(profile.sni)}&host=${encodeUrl(profile.wsHost)}&path=${encodeUrl(profile.wsPath)}#$encodedName"
            }
            VpnProtocol.SHADOWSOCKS -> {
                val raw = "${profile.cipher}:${profile.passwordOrSecret}".toByteArray(Charsets.UTF_8)
                val b64 = Base64.encodeToString(raw, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
                "ss://$b64@${profile.host}:${profile.port}#$encodedName"
            }
            VpnProtocol.SOCKS5 -> {
                val auth = if (profile.usernameOrUuid.isNotBlank()) "${encodeUrl(profile.usernameOrUuid)}:${encodeUrl(profile.passwordOrSecret)}@" else ""
                "socks5://$auth${profile.host}:${profile.port}#$encodedName"
            }
            VpnProtocol.SOCKS4 -> {
                val auth = if (profile.usernameOrUuid.isNotBlank()) "${encodeUrl(profile.usernameOrUuid)}@" else ""
                "socks4://$auth${profile.host}:${profile.port}#$encodedName"
            }
            VpnProtocol.HTTP_PROXY -> {
                val scheme = if (profile.tlsEnabled) "https" else "http"
                val auth = if (profile.usernameOrUuid.isNotBlank()) "${encodeUrl(profile.usernameOrUuid)}:${encodeUrl(profile.passwordOrSecret)}@" else ""
                "$scheme://$auth${profile.host}:${profile.port}#$encodedName"
            }
            VpnProtocol.SSH -> {
                val auth = if (profile.usernameOrUuid.isNotBlank()) "${encodeUrl(profile.usernameOrUuid)}@" else ""
                val query = buildList {
                    if (profile.tlsEnabled) add("tls=1")
                    if (profile.sni.isNotBlank()) add("sni=${encodeUrl(profile.sni)}")
                    if (profile.allowInsecure) add("allowInsecure=1")
                }.joinToString("&")
                val suffix = if (query.isNotEmpty()) "?$query" else ""
                "ssh://$auth${profile.host}:${profile.port}$suffix#$encodedName"
            }
            VpnProtocol.LOAD_BALANCE -> {
                "netshield-lb://${profile.loadBalanceStrategy.name.lowercase()}?members=${profile.memberProfileIds}#$encodedName"
            }
        }
    }

    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap? {
        return runCatching {
            val hints = mapOf(EncodeHintType.MARGIN to 1)
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bmp
        }.getOrNull()
    }

    fun decodeBase64Flexible(input: String): String? {
        val clean = input.trim().replace("-", "+").replace("_", "/")
        if (clean.isEmpty()) return null
        val padded = when (clean.length % 4) {
            2 -> "$clean=="
            3 -> "$clean="
            else -> clean
        }
        return runCatching {
            String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun decodeUrl(s: String): String =
        runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

    private fun encodeUrl(s: String): String =
        runCatching { URLEncoder.encode(s, "UTF-8") }.getOrDefault(s)
}
