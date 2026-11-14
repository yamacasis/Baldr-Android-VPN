package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vpn_profiles")
data class VpnProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val protocol: VpnProtocol,
    val host: String,
    val port: Int,
    val usernameOrUuid: String = "",
    val passwordOrSecret: String = "",
    val privateKeyPem: String = "",
    val transport: TransportType = TransportType.TCP,
    val tlsEnabled: Boolean = false,
    val sni: String = "",
    val wsPath: String = "/",
    val wsHost: String = "",
    val allowInsecure: Boolean = false,
    val cipher: String = "aes-256-gcm",
    val alterId: Int = 0,
    val latencyMs: Int? = null,
    val lastSpeedBps: Long? = null,
    val lastCountryCode: String? = null,
    val memberProfileIds: String = "",
    val loadBalanceStrategy: LoadBalanceStrategy = LoadBalanceStrategy.ROUND_ROBIN,
    val subscriptionId: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun parsedMemberIds(): List<Long> {
        if (memberProfileIds.isBlank()) return emptyList()
        return memberProfileIds.split(",")
            .mapNotNull { it.trim().toLongOrNull() }
            .filter { it > 0L && it != id }
            .distinct()
    }
}
