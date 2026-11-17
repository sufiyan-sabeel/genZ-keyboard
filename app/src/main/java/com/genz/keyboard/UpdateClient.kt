package com.genz.keyboard

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches the latest stable release and applies the notification throttle.
 *
 * Never runs on the keyboard's initialisation path: callers launch it from a background scope and
 * it is bounded by timeouts. A failed check is silent — a keyboard must work with no network.
 */
class UpdateClient(context: Context) {

    private val app = context.applicationContext
    private val prefs: SharedPreferences = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    companion object {
        const val PREFS = "genz_update"
        const val KEY_LAST_CHECK = "last_check_ms"
        const val KEY_LAST_BODY = "last_body"
        const val KEY_DISMISSED_VERSION = "dismissed_version"
        const val KEY_DISMISSED_AT = "dismissed_at_ms"
        const val KEY_FAILURES = "consecutive_failures"

        /** A successful check is reused for this long. */
        const val CACHE_MS = 12 * 60 * 60 * 1000L

        /** The same version is not offered again for this long after being dismissed. */
        const val DISMISS_MS = 7 * 24 * 60 * 60 * 1000L

        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 8_000

        const val LATEST_URL =
            "https://api.github.com/repos/sufiyan-sabeel/genZ-keyboard/releases/latest"
    }

    /** True when a cached response is still fresh enough to use. */
    fun cacheFresh(now: Long): Boolean {
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        return last > 0 && now - last < CACHE_MS && prefs.getString(KEY_LAST_BODY, null) != null
    }

    /** True when this version was dismissed recently and must not be shown again yet. */
    fun recentlyDismissed(version: String, now: Long): Boolean {
        if (prefs.getString(KEY_DISMISSED_VERSION, null) != version) return false
        val at = prefs.getLong(KEY_DISMISSED_AT, 0L)
        return at > 0 && now - at < DISMISS_MS
    }

    fun markDismissed(version: String, now: Long) {
        prefs.edit().putString(KEY_DISMISSED_VERSION, version).putLong(KEY_DISMISSED_AT, now).apply()
    }

    /**
     * The latest release, from cache when fresh, otherwise from the network.
     *
     * @return null on any failure. Rate limiting (HTTP 403) and offline both land here, and both
     * are treated as "no update to report" rather than an error the user must see.
     */
    suspend fun latestRelease(now: Long, force: Boolean = false): ReleaseInfo? {
        if (!force && cacheFresh(now)) {
            prefs.getString(KEY_LAST_BODY, null)?.let { return UpdateChecker.parseRelease(it) }
        }
        val body = fetch() ?: run {
            prefs.edit().putInt(KEY_FAILURES, prefs.getInt(KEY_FAILURES, 0) + 1).apply()
            return null
        }
        prefs.edit()
            .putString(KEY_LAST_BODY, body)
            .putLong(KEY_LAST_CHECK, now)
            .putInt(KEY_FAILURES, 0)
            .apply()
        return UpdateChecker.parseRelease(body)
    }

    private suspend fun fetch(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                // /releases/latest already excludes drafts and prereleases.
                setRequestProperty("User-Agent", "GenZKeyboard-UpdateCheck")
            }
            try {
                if (conn.responseCode != 200) return@runCatching null
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }
}
