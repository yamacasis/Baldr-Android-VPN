package com.example.model

enum class VpnProtocol(
    val displayName: String,
    val shortBadge: String,
    val defaultPort: Int,
    val group: ProtocolGroup
) {
    VLESS("VLESS (V2Ray/Xray)", "VLESS", 443, ProtocolGroup.V2RAY),
    VMESS("VMess (V2Ray)", "VMESS", 443, ProtocolGroup.V2RAY),
    TROJAN("Trojan", "TROJAN", 443, ProtocolGroup.V2RAY),
    SHADOWSOCKS("Shadowsocks", "SS", 8388, ProtocolGroup.V2RAY),
    SSH("SSH Tunnel", "SSH", 22, ProtocolGroup.SSH),
    SOCKS5("SOCKS v5", "SOCKS5", 1080, ProtocolGroup.PROXY),
    SOCKS4("SOCKS v4 / 4a", "SOCKS4", 1080, ProtocolGroup.PROXY),
    HTTP_PROXY("HTTP / HTTPS Proxy", "HTTP", 8080, ProtocolGroup.PROXY),
    LOAD_BALANCE("Multi-Server Load Balance", "MULTI", 0, ProtocolGroup.MULTI)
}

enum class ProtocolGroup {
    V2RAY,
    SSH,
    PROXY,
    MULTI
}

enum class LoadBalanceStrategy {
    FAILOVER,
    ROUND_ROBIN,
    LEAST_LATENCY,
    RANDOM
}

enum class TransportType(val wireName: String) {
    TCP("tcp"),
    WS("ws")
}

enum class TunnelMode {
    FULL_SYSTEM,
    SPLIT_INCLUDE,
    SPLIT_EXCLUDE
}

enum class BatteryMode {
    ULTRA_LOW_POWER,
    BALANCED
}

enum class AppLanguage {
    FARSI,
    ENGLISH
}

enum class AppThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}

enum class DnsPreset(
    val displayName: String,
    val primaryIp: String,
    val secondaryIp: String,
    val dohUrl: String,
    val blocksAds: Boolean = false
) {
    CLOUDFLARE(
        displayName = "Cloudflare",
        primaryIp = "1.1.1.1",
        secondaryIp = "1.0.0.1",
        dohUrl = "https://cloudflare-dns.com/dns-query"
    ),
    GOOGLE(
        displayName = "Google",
        primaryIp = "8.8.8.8",
        secondaryIp = "8.8.4.4",
        dohUrl = "https://dns.google/dns-query"
    ),
    QUAD9(
        displayName = "Quad9",
        primaryIp = "9.9.9.9",
        secondaryIp = "149.112.112.112",
        dohUrl = "https://dns.quad9.net/dns-query"
    ),
    ADGUARD(
        displayName = "AdGuard AdBlock",
        primaryIp = "94.140.14.14",
        secondaryIp = "94.140.15.15",
        dohUrl = "https://dns.adguard-dns.com/dns-query",
        blocksAds = true
    ),
    ADGUARD_FAMILY(
        displayName = "AdGuard Family",
        primaryIp = "94.140.14.15",
        secondaryIp = "94.140.15.16",
        dohUrl = "https://family.adguard-dns.com/dns-query",
        blocksAds = true
    ),
    CUSTOM(
        displayName = "Custom",
        primaryIp = "1.1.1.1",
        secondaryIp = "8.8.8.8",
        dohUrl = ""
    )
}

enum class ServerSortMode {
    DEFAULT,
    PING_ASC,
    COUNTRY
}
