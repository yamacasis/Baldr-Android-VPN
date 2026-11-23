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
