package com.genz.keyboard

import android.content.Context

/**
 * A signed-in session.
 *
 * The refresh token is what keeps a user signed in across restarts, so both tokens are persisted.
 * Neither is ever written to a log, a crash report or a backup: [toString] is overridden to hide
 * them, because the easiest way to leak a token is to print the object that holds it.
 */
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long
) {
    override fun toString(): String = "AuthTokens(expiresAt=$expiresAtEpochSeconds, tokens=redacted)"
}

/** Parses GoTrue auth responses. Pure, so the shape is tested without a server. */
object AuthResponse {

    fun parse(json: String): AuthTokens? {
        val o = ThemeJson.decode(json) ?: return null
        val access = o["access_token"] as? String ?: return null
        val refresh = o["refresh_token"] as? String ?: return null
        val expiresIn = (o["expires_in"] as? Number)?.toLong() ?: 3600L
        val now = (o["expires_at"] as? Number)?.toLong()
            ?: (System.currentTimeMillis() / 1000 + expiresIn)
        if (access.isEmpty() || refresh.isEmpty()) return null
        return AuthTokens(access, refresh, now)
    }

    /** True when the token has expired or is within a minute of doing so. */
    fun needsRefresh(tokens: AuthTokens, nowEpochSeconds: Long = System.currentTimeMillis() / 1000): Boolean =
        tokens.expiresAtEpochSeconds - nowEpochSeconds <= 60

    /** The user id from a JWT payload, without verifying it (the server verifies). */
    fun subjectOf(accessToken: String): String? {
        val payload = accessToken.split('.').getOrNull(1) ?: return null
        return try {
            val json = String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP))
            (ThemeJson.decode(json)?.get("sub") as? String)?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * The signed-in session.
 *
 * Delegates storage to [SecureTokenStore], which keeps it AES-GCM encrypted under a key held by the
 * Android Keystore. Nothing readable is written to disk, and the tokens are redacted from any log.
 */
object CommunitySession {

    fun save(context: Context, tokens: AuthTokens, handle: String? = null) {
        SecureTokenStore.save(context, tokens, handle)
    }

    /** @return the stored tokens and handle, or null when there is no readable session. */
    fun load(context: Context): Pair<AuthTokens, String?>? = SecureTokenStore.load(context)

    fun tokens(context: Context): AuthTokens? = SecureTokenStore.load(context)?.first

    fun handle(context: Context): String? = SecureTokenStore.load(context)?.second

    fun isSignedIn(context: Context): Boolean = SecureTokenStore.load(context) != null

    /** Clear the session. Used by sign-out and by account deletion. */
    fun clear(context: Context) = SecureTokenStore.clear(context)
}
