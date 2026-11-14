package com.example.data

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import com.example.model.TransportType
import com.example.model.VpnProfile
import com.example.model.VpnProtocol
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder

object UriConfigParser {

    fun parseBatch(rawText: String): List<VpnProfile> {
        return rawText
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { parseSingle(it) }
            .toList()
    }

    fun parseSingle(rawUri: String): VpnProfile? {
        val trimmed = rawUri.trim()
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
        val encoded = uri.substringAfter("vmess://").trim()
        val decoded = decodeBase64Flexible(encoded) ?: return null
        val json = JSONObject(decoded)
        val host = json.optString("add").trim()
        val port = json.optInt("port", json.optString("port").toIntOrNull() ?: 443)
        val uuid = json.optString("id").trim()
        if (host.isEmpty() || uuid.isEmpty()) return null
        val net = json.optString("net", "tcp").lowercase()
        val tls = json.optString("tls", "").equals("tls", ignoreCase = true)
        val sni = json.optString("sni").ifBlank { json.optString("host") }
        val wsHost = json.optString("host")
        val wsPath = json.optString("path", "/").ifBlank { "/" }
        val ps = json.optString("ps").ifBlank { "$host:$port" }
        val alterId = json.optInt("aid", 0)
        val scy = json.optString("scy", "auto").ifBlank { "auto" }
        return VpnProfile(
            name = ps,
            protocol = VpnProtocol.VMESS,
            host = host,
            port = port,
            usernameOrUuid = uuid,
            transport = if (net == "ws") TransportType.WS else TransportType.TCP,
            tlsEnabled = tls,
            sni = sni,
            wsHost = wsHost,
            wsPath = wsPath,
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
        val type = parsed.getQueryParameter("type")?.lowercase() ?: "tcp"
        val security = parsed.getQueryParameter("security")?.lowercase() ?: "none"
        val sni = parsed.getQueryParameter("sni").orEmpty()
        val wsHost = parsed.getQueryParameter("host").orEmpty()
        val wsPath = parsed.getQueryParameter("path")?.let { decodeUrl(it) }.orEmpty().ifBlank { "/" }
        val allowInsecure = parsed.getQueryParameter("allowInsecure") == "1"
        val name = parsed.fragment?.let { decodeUrl(it) }?.ifBlank { null } ?: "$host:$port"
        return VpnProfile(
            name = name,
            protocol = VpnProtocol.VLESS,
            host = host,
            port = port,
            usernameOrUuid = uuid,
            transport = if (type == "ws") TransportType.WS else TransportType.TCP,
            tlsEnabled = security == "tls" || security == "reality",
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
        val type = parsed.getQueryParameter("type")?.lowercase() ?: "tcp"
        val sni = parsed.getQueryParameter("sni").orEmpty()
        val wsHost = parsed.getQueryParameter("host").orEmpty()
        val wsPath = parsed.getQueryParameter("path")?.let { decodeUrl(it) }.orEmpty().ifBlank { "/" }
        val allowInsecure = parsed.getQueryParameter("allowInsecure") == "1"
        val name = parsed.fragment?.let { decodeUrl(it) }?.ifBlank { null } ?: "$host:$port"
        return VpnProfile(
            name = name,
            protocol = VpnProtocol.TROJAN,
            host = host,
            port = port,
            passwordOrSecret = password,
            transport = if (type == "ws") TransportType.WS else TransportType.TCP,
            tlsEnabled = true,
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
        return VpnProfile(
            name = name,
            protocol = protocol,
            host = host,
            port = port,
            usernameOrUuid = username,
            passwordOrSecret = password,
            tlsEnabled = forceTls ?: false
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
                "ssh://$auth${profile.host}:${profile.port}#$encodedName"
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

    private fun decodeBase64Flexible(input: String): String? {
        val clean = input.trim().replace("-", "+").replace("_", "/")
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
