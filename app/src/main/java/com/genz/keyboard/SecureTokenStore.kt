package com.genz.keyboard

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The on-disk envelope for an encrypted value: `v1:<iv base64>:<ciphertext base64>`.
 *
 * Pure and versioned, so a future format can be introduced without misreading old blobs, and so the
 * parsing rules are tested without an Android Keystore.
 */
object TokenEnvelope {

    const val PREFIX = "v1"

    /** AES-GCM standard nonce length. */
    const val IV_BYTES = 12

    fun format(iv: ByteArray, ciphertext: ByteArray): String =
        "$PREFIX:${Base64.getEncoder().encodeToString(iv)}:${Base64.getEncoder().encodeToString(ciphertext)}"

    /** @return iv to ciphertext, or null when the blob is malformed or from another version. */
    fun parse(blob: String): Pair<ByteArray, ByteArray>? {
        val parts = blob.split(':')
        if (parts.size != 3) return null
        if (parts[0] != PREFIX) return null
        val iv = runCatching { Base64.getDecoder().decode(parts[1]) }.getOrNull() ?: return null
        val ct = runCatching { Base64.getDecoder().decode(parts[2]) }.getOrNull() ?: return null
        if (iv.size != IV_BYTES) return null
        if (ct.isEmpty()) return null
        return iv to ct
    }
}

/**
 * AES-GCM encryption backed by a key that lives in the Android Keystore.
 *
 * The key material never enters the app process: it is generated inside the Keystore and used
 * through a `Cipher` handle. GCM is authenticated, so a tampered blob fails to decrypt rather than
 * returning altered plaintext.
 */
object AndroidTokenCipher {

    private const val KEY_ALIAS = "genz_community_token_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plaintext: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        TokenEnvelope.format(cipher.iv, cipher.doFinal(plaintext.toByteArray()))
    }.getOrNull()

    fun decrypt(blob: String): String? = runCatching {
        val (iv, ciphertext) = TokenEnvelope.parse(blob) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(ciphertext))
    }.getOrNull()
}

/**
 * Persists the session encrypted.
 *
 * Nothing readable is ever written to disk: the preferences hold one opaque blob. If the Keystore
 * key is lost (for example the user clears credentials), decryption fails and the session is
 * treated as absent, which is the safe outcome.
 */
object SecureTokenStore {

    private const val PREFS = "genz_community_secure"
    private const val KEY_BLOB = "session_blob"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(context: Context, tokens: AuthTokens, handle: String?): Boolean {
        val payload = buildString {
            append("{\"access\":").append(q(tokens.accessToken))
            append(",\"refresh\":").append(q(tokens.refreshToken))
            append(",\"expires\":").append(tokens.expiresAtEpochSeconds)
            append(",\"handle\":").append(q(handle ?: ""))
            append("}")
        }
        val blob = AndroidTokenCipher.encrypt(payload) ?: return false
        prefs(context).edit().putString(KEY_BLOB, blob).apply()
        return true
    }

    fun load(context: Context): Pair<AuthTokens, String?>? {
        val blob = prefs(context).getString(KEY_BLOB, null) ?: return null
        val plain = AndroidTokenCipher.decrypt(blob) ?: return null
        val o = ThemeJson.decode(plain) ?: return null
        val access = o["access"] as? String ?: return null
        val refresh = o["refresh"] as? String ?: return null
        if (access.isEmpty() || refresh.isEmpty()) return null
        val expires = (o["expires"] as? Number)?.toLong() ?: 0L
        val handle = (o["handle"] as? String)?.takeIf { it.isNotBlank() }
        return AuthTokens(access, refresh, expires) to handle
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun q(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
