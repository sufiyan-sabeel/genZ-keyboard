package com.genz.keyboard

/**
 * Reads the public GitHub Releases API for this project and decides whether a newer stable
 * release exists.
 *
 * Pure: no networking, no Android types. The HTTP call lives in [UpdateClient]; everything that
 * decides *what the response means* is here, so it is unit-tested against fixed JSON instead of a
 * live API.
 */
data class ReleaseInfo(
    val versionName: String,
    val versionCode: Int?,
    val notes: String,
    val apkUrl: String,
    val publishedAt: String,
    val prerelease: Boolean,
    val draft: Boolean
)

object UpdateChecker {

    /** Only assets served from this repository's own release downloads are ever accepted. */
    const val DOWNLOAD_PREFIX =
        "https://github.com/sufiyan-sabeel/genZ-keyboard/releases/download/"

    /** A release older than this many days is still valid; the cap is only for the notes field. */
    const val MAX_NOTES = 4000

    /**
     * Parse a GitHub release object.
     *
     * @return null when the payload is not a release we can act on (bad JSON, no tag, no APK).
     */
    fun parseRelease(json: String): ReleaseInfo? {
        val o = ThemeJson.decode(json) ?: return null
        val tag = (o["tag_name"] as? String)?.trim().orEmpty()
        val version = versionFromTag(tag) ?: return null

        val draft = o["draft"] as? Boolean ?: false
        val prerelease = o["prerelease"] as? Boolean ?: false
        val body = (o["body"] as? String).orEmpty()

        val assets = o["assets"] as? List<*> ?: emptyList<Any?>()
        val apk = assets.asSequence()
            .mapNotNull { it as? Map<*, *> }
            .mapNotNull { a ->
                val name = a["name"] as? String ?: return@mapNotNull null
                val url = a["browser_download_url"] as? String ?: return@mapNotNull null
                if (!name.endsWith(".apk", ignoreCase = true)) return@mapNotNull null
                if (!url.startsWith(DOWNLOAD_PREFIX)) return@mapNotNull null
                url
            }
            .firstOrNull()
            ?: return null

        return ReleaseInfo(
            versionName = version,
            versionCode = versionCodeMarker(body),
            notes = body.take(MAX_NOTES),
            apkUrl = apk,
            publishedAt = (o["published_at"] as? String).orEmpty(),
            prerelease = prerelease,
            draft = draft
        )
    }

    /** `v1.0.6` or `1.0.6` -> `1.0.6`; anything else -> null. */
    fun versionFromTag(tag: String): String? {
        val t = tag.trim().removePrefix("v").removePrefix("V")
        val m = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)$").find(t) ?: return null
        return "${m.groupValues[1]}.${m.groupValues[2]}.${m.groupValues[3]}"
    }

    /**
     * An explicit `versionCode: N` line in the release body, when the release author included one.
     *
     * The APK's own versionCode is the only exact identity; a tag can only be compared as a
     * version string. When the marker is present it is preferred.
     */
    fun versionCodeMarker(body: String): Int? =
        Regex("versionCode\\s*[:=]\\s*(\\d{1,9})\\b", RegexOption.IGNORE_CASE)
            .find(body)?.groupValues?.get(1)?.toIntOrNull()

    /** Compare two dotted version strings. @return true when [remote] is strictly newer. */
    fun isNewer(remote: String, installed: String): Boolean {
        val r = parts(remote) ?: return false
        val i = parts(installed) ?: return false
        for (n in 0..2) {
            if (r[n] != i[n]) return r[n] > i[n]
        }
        return false
    }

    private fun parts(v: String): IntArray? {
        val m = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)$").find(v.trim()) ?: return null
        return intArrayOf(
            m.groupValues[1].toInt(),
            m.groupValues[2].toInt(),
            m.groupValues[3].toInt()
        )
    }

    /**
     * The decision to show a dialog.
     *
     * @param installedCode the installed versionCode, or null when unknown
     * @param installedName the installed versionName
     */
    fun shouldNotify(
        release: ReleaseInfo?,
        installedCode: Int?,
        installedName: String
    ): Boolean {
        if (release == null) return false
        // Drafts are invisible to everyone else; prereleases are opt-out by policy.
        if (release.draft || release.prerelease) return false
        val byCode = release.versionCode != null && installedCode != null &&
            release.versionCode > installedCode
        return byCode || isNewer(release.versionName, installedName)
    }
}
