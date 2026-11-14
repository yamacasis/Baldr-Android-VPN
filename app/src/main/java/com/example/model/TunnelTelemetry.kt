package com.example.model

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    FIREWALL_LOCKED,
    DISCONNECTING,
    ERROR
}

data class ExitNetworkInfo(
    val ipAddress: String = "",
    val countryCode: String = "",
    val countryName: String = "",
    val city: String = "",
    val isp: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val checkedAtMillis: Long = 0L
) {
    val flagEmoji: String
        get() = countryCodeToFlag(countryCode)
}

data class SpeedSample(
    val timestampMillis: Long,
    val downloadBps: Long,
    val uploadBps: Long
)

data class TrafficStatsSnapshot(
    val downloadSpeedBps: Long = 0L,
    val uploadSpeedBps: Long = 0L,
    val totalDownloadedBytes: Long = 0L,
    val totalUploadedBytes: Long = 0L,
    val connectedSinceMillis: Long = 0L,
    val activeStreams: Int = 0,
    val speedHistory: List<SpeedSample> = emptyList()
)

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean
)

fun countryCodeToFlag(isoCode: String): String {
    val code = isoCode.trim().uppercase()
    if (code.length != 2 || !code[0].isLetter() || !code[1].isLetter()) return "\uD83C\uDF10"
    val first = Character.codePointAt(code, 0) - 'A'.code + 0x1F1E6
    val second = Character.codePointAt(code, 1) - 'A'.code + 0x1F1E6
    return String(Character.toChars(first)) + String(Character.toChars(second))
}
