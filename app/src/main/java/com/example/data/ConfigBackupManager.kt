package com.example.data

import android.util.Base64
import com.example.model.LoadBalanceStrategy
import com.example.model.TransportType
import com.example.model.VpnProfile
import com.example.model.VpnProtocol
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Exports and imports VPN server configurations as structured JSON.
 * Only sensitive credentials (`passwordOrSecret` and `privateKeyPem`) are encrypted
 * using AES-256-GCM with a PBKDF2-derived key; server addresses, ports, protocols, and
 * non-secret routing options remain readable in the JSON structure.
 */
object ConfigBackupManager {

    private const val FORMAT_ID = "netshield_backup_v1"
    private const val ENCRYPTED_PREFIX = "ENC:v1:"
    private const val SENTINEL_PLAINTEXT = "netshield-key-valid"
    private const val PBKDF2_ITERATIONS = 65_536
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12
    private const val SALT_BYTES = 16

    sealed class ImportOutcome {
        data class Success(val profiles: List<VpnProfile>) : ImportOutcome()
        data object WrongPasswordOrCorrupted : ImportOutcome()
        data class InvalidFormat(val reason: String) : ImportOutcome()
    }

    fun isEncryptedBackup(rawText: String): Boolean {
        val trimmed = rawText.trim()
        if (!trimmed.startsWith("{")) return false
        return runCatching {
            val root = JSONObject(trimmed)
            root.optString("format") == FORMAT_ID
        }.getOrDefault(false)
    }

    fun exportProfiles(
        profiles: List<VpnProfile>,
        passphrase: String
    ): String {
        require(passphrase.isNotEmpty()) { "Passphrase must not be empty" }
        val rng = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { rng.nextBytes(it) }
        val (kdfAlg, keySpec) = deriveKey(passphrase, salt)

        val root = JSONObject().apply {
            put("format", FORMAT_ID)
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("encryption", "AES-256-GCM")
            put("kdf", kdfAlg)
            put("iterations", PBKDF2_ITERATIONS)
            put("salt", encodeB64(salt))
            put("keyCheck", encryptString(SENTINEL_PLAINTEXT, keySpec, rng))
        }

        val items = JSONArray()
        profiles.forEach { profile ->
            val obj = JSONObject().apply {
                put("exportId", profile.id)
                put("name", profile.name)
                put("protocol", profile.protocol.name)
                put("host", profile.host)
                put("port", profile.port)
                put("usernameOrUuid", profile.usernameOrUuid)
                put(
                    "passwordOrSecret",
                    if (profile.passwordOrSecret.isNotEmpty()) {
                        encryptString(profile.passwordOrSecret, keySpec, rng)
                    } else {
                        ""
                    }
                )
                put(
                    "privateKeyPem",
                    if (profile.privateKeyPem.isNotEmpty()) {
                        encryptString(profile.privateKeyPem, keySpec, rng)
                    } else {
                        ""
                    }
                )
                put("transport", profile.transport.name)
                put("tlsEnabled", profile.tlsEnabled)
                put("sni", profile.sni)
                put("wsPath", profile.wsPath)
                put("wsHost", profile.wsHost)
                put("allowInsecure", profile.allowInsecure)
                put("cipher", profile.cipher)
                put("alterId", profile.alterId)
                put("memberProfileIds", profile.memberProfileIds)
                put("loadBalanceStrategy", profile.loadBalanceStrategy.name)
            }
            items.put(obj)
        }
        root.put("profiles", items)
        return root.toString(2)
    }

    fun importBackup(
        rawJson: String,
        passphrase: String
    ): ImportOutcome {
        val trimmed = rawJson.trim()
        val root = runCatching { JSONObject(trimmed) }.getOrElse {
            return ImportOutcome.InvalidFormat("Invalid JSON")
        }
        if (root.optString("format") != FORMAT_ID) {
            return ImportOutcome.InvalidFormat("Unsupported backup format")
        }
        val saltB64 = root.optString("salt")
        val salt = runCatching { decodeB64(saltB64) }.getOrElse {
            return ImportOutcome.InvalidFormat("Missing salt")
        }
        val kdfAlg = root.optString("kdf", "PBKDF2WithHmacSHA256")
        val iterations = root.optInt("iterations", PBKDF2_ITERATIONS).coerceIn(10_000, 300_000)

        val keySpec = deriveKeyWithAlgorithm(passphrase, salt, kdfAlg, iterations)

        val keyCheck = root.optString("keyCheck")
        if (keyCheck.isNotEmpty()) {
            val verified = runCatching { decryptString(keyCheck, keySpec) }.getOrNull()
            if (verified != SENTINEL_PLAINTEXT) {
                return ImportOutcome.WrongPasswordOrCorrupted
            }
        }

        val arr = root.optJSONArray("profiles") ?: return ImportOutcome.InvalidFormat("Missing profiles array")
        val result = mutableListOf<VpnProfile>()

        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val protocol = runCatching {
                VpnProtocol.valueOf(obj.optString("protocol"))
            }.getOrNull() ?: continue

            val transport = runCatching {
                TransportType.valueOf(obj.optString("transport", TransportType.TCP.name))
            }.getOrDefault(TransportType.TCP)

            val lbStrategy = runCatching {
                LoadBalanceStrategy.valueOf(obj.optString("loadBalanceStrategy", LoadBalanceStrategy.ROUND_ROBIN.name))
            }.getOrDefault(LoadBalanceStrategy.ROUND_ROBIN)

            val rawPass = obj.optString("passwordOrSecret", "")
            val rawPem = obj.optString("privateKeyPem", "")

            val plainPass = try {
                if (rawPass.startsWith(ENCRYPTED_PREFIX)) decryptString(rawPass, keySpec) else rawPass
            } catch (_: Throwable) {
                return ImportOutcome.WrongPasswordOrCorrupted
            }

            val plainPem = try {
                if (rawPem.startsWith(ENCRYPTED_PREFIX)) decryptString(rawPem, keySpec) else rawPem
            } catch (_: Throwable) {
                return ImportOutcome.WrongPasswordOrCorrupted
            }

            val exportId = obj.optLong("exportId", (i + 1).toLong())
            result += VpnProfile(
                id = exportId,
                name = obj.optString("name").ifBlank { "Imported Server" },
                protocol = protocol,
                host = obj.optString("host", ""),
                port = obj.optInt("port", protocol.defaultPort),
                usernameOrUuid = obj.optString("usernameOrUuid", ""),
                passwordOrSecret = plainPass,
                privateKeyPem = plainPem,
                transport = transport,
                tlsEnabled = obj.optBoolean("tlsEnabled", false),
                sni = obj.optString("sni", ""),
                wsPath = obj.optString("wsPath", "/").ifBlank { "/" },
                wsHost = obj.optString("wsHost", ""),
                allowInsecure = obj.optBoolean("allowInsecure", false),
                cipher = obj.optString("cipher", "aes-256-gcm").ifBlank { "aes-256-gcm" },
                alterId = obj.optInt("alterId", 0),
                memberProfileIds = obj.optString("memberProfileIds", ""),
                loadBalanceStrategy = lbStrategy
            )
        }

        return ImportOutcome.Success(result)
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): Pair<String, SecretKeySpec> {
        val preferred = "PBKDF2WithHmacSHA256"
        return try {
            preferred to deriveKeyWithAlgorithm(passphrase, salt, preferred, PBKDF2_ITERATIONS)
        } catch (_: Throwable) {
            val fallback = "PBKDF2WithHmacSHA1"
            fallback to deriveKeyWithAlgorithm(passphrase, salt, fallback, PBKDF2_ITERATIONS)
        }
    }

    private fun deriveKeyWithAlgorithm(
        passphrase: String,
        salt: ByteArray,
        algorithm: String,
        iterations: Int
    ): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(algorithm)
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)
        val secret = factory.generateSecret(spec)
        return SecretKeySpec(secret.encoded, "AES")
    }

    private fun encryptString(
        plaintext: String,
        keySpec: SecretKeySpec,
        rng: SecureRandom
    ): String {
        val iv = ByteArray(GCM_IV_BYTES).also { rng.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
        val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return "$ENCRYPTED_PREFIX${encodeB64(iv)}:${encodeB64(cipherBytes)}"
    }

    private fun decryptString(
        encoded: String,
        keySpec: SecretKeySpec
    ): String {
        if (!encoded.startsWith(ENCRYPTED_PREFIX)) return encoded
        val body = encoded.removePrefix(ENCRYPTED_PREFIX)
        val parts = body.split(":")
        require(parts.size == 2) { "Malformed encrypted token" }
        val iv = decodeB64(parts[0])
        val cipherBytes = decodeB64(parts[1])
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, Charsets.UTF_8)
    }

    private fun encodeB64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decodeB64(str: String): ByteArray =
        Base64.decode(str, Base64.NO_WRAP)
}
