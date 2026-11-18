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
 * Persists the signed-in session.
 *
 * Stored in the app's private preferences: readable only by this app's process, and excluded from
 * backups because the manifest sets `allowBackup="false"`.
 */
object CommunitySession {

    private const val PREFS = "genz_community_session"
    private const val KEY_ACCESS = "access_token"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_EXPIRES = "expires_at"
    private const val KEY_HANDLE = "handle"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(context: Context, tokens: AuthTokens, handle: String? = null) {
        prefs(context).edit()
            .putString(KEY_ACCESS, tokens.accessToken)
            .putString(KEY_REFRESH, tokens.refreshToken)
            .putLong(KEY_EXPIRES, tokens.expiresAtEpochSeconds)
            .apply { if (handle != null) putString(KEY_HANDLE, handle) }
            .apply()
    }

    fun load(context: Context): AuthTokens? {
        val p = prefs(context)
        val access = p.getString(KEY_ACCESS, null) ?: return null
        val refresh = p.getString(KEY_REFRESH, null) ?: return null
        return AuthTokens(access, refresh, p.getLong(KEY_EXPIRES, 0L))
    }

    fun handle(context: Context): String? = prefs(context).getString(KEY_HANDLE, null)

    fun isSignedIn(context: Context): Boolean = load(context) != null

    /** Clear the session. Used by sign-out and by account deletion. */
    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
