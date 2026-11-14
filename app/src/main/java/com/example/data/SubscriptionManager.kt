package com.example.data

import com.example.model.VpnProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class SubscriptionUserInfo(
    val uploadBytes: Long? = null,
    val downloadBytes: Long? = null,
    val totalBytes: Long? = null,
    val expireAtSeconds: Long? = null
)

sealed class SubscriptionFetchResult {
    data class Success(
        val profiles: List<VpnProfile>,
        val titleFromHeader: String? = null,
        val userInfo: SubscriptionUserInfo = SubscriptionUserInfo()
    ) : SubscriptionFetchResult()

    data class Failure(val message: String) : SubscriptionFetchResult()
}

object SubscriptionManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun fetchSubscription(rawUrl: String): SubscriptionFetchResult =
        withContext(Dispatchers.IO) {
            val url = rawUrl.trim()
            if (!url.startsWith("http://", ignoreCase = true) &&
                !url.startsWith("https://", ignoreCase = true)
            ) {
                return@withContext SubscriptionFetchResult.Failure("Subscription URL must start with http:// or https://")
            }

            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "BaldrVPN/6.0")
                    .header("Accept", "*/*")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext SubscriptionFetchResult.Failure("HTTP ${response.code}: ${response.message}")
                    }
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) {
                        return@withContext SubscriptionFetchResult.Failure("Empty subscription response")
                    }

                    val titleHeader = parseProfileTitle(response.header("Profile-Title"))
                    val userInfo = parseUserInfo(response.header("Subscription-Userinfo"))
                    val profiles = UriConfigParser.parseBatch(body)
                    if (profiles.isEmpty()) {
                        return@withContext SubscriptionFetchResult.Failure("No valid VMess, VLESS, Trojan, or proxy configs found")
                    }

                    SubscriptionFetchResult.Success(
                        profiles = profiles,
                        titleFromHeader = titleHeader,
                        userInfo = userInfo
                    )
                }
            } catch (e: Exception) {
                SubscriptionFetchResult.Failure(e.message ?: "Failed to download subscription")
            }
        }

    internal fun parseProfileTitle(rawHeader: String?): String? {
        val trimmed = rawHeader?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return if (trimmed.startsWith("base64:", ignoreCase = true)) {
            val b64 = trimmed.substringAfter(":").trim()
            UriConfigParser.decodeBase64Flexible(b64)?.trim()?.takeIf { it.isNotEmpty() }
        } else {
            trimmed
        }
    }

    internal fun parseUserInfo(rawHeader: String?): SubscriptionUserInfo {
        if (rawHeader.isNullOrBlank()) return SubscriptionUserInfo()
        val map = mutableMapOf<String, Long>()
        rawHeader.split(";").forEach { part ->
            val key = part.substringBefore("=").trim().lowercase()
            val value = part.substringAfter("=", "").trim().toLongOrNull()
            if (key.isNotEmpty() && value != null) {
                map[key] = value
            }
        }
        return SubscriptionUserInfo(
            uploadBytes = map["upload"],
            downloadBytes = map["download"],
            totalBytes = map["total"],
            expireAtSeconds = map["expire"]
        )
    }
}
