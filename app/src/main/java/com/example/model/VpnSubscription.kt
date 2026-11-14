package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vpn_subscriptions")
data class VpnSubscription(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val url: String,
    val lastUpdatedAt: Long = 0L,
    val serverCount: Int = 0,
    val uploadBytes: Long? = null,
    val downloadBytes: Long? = null,
    val totalBytes: Long? = null,
    val expireAtSeconds: Long? = null,
    val lastError: String? = null
)
