package com.genz.keyboard

/**
 * What may be published, and what the server will be told about it.
 *
 * Pure: every rule here is checked before a single byte leaves the device, and every rule is
 * tested. The database enforces its own constraints; this exists so the user gets a clear reason
 * instead of a 400 from Postgres.
 */
object PublishRules {

    const val MIN_TITLE = 3
    const val MAX_TITLE = 64
    const val MAX_DESCRIPTION = 2000
    const val MAX_TAGS = 8
    const val MAX_TAG_LENGTH = 24

    /** Matches the storage bucket policy and the client-side wallpaper cap. */
    const val MAX_WALLPAPER_BYTES = 8L * 1024 * 1024

    /** The theme document is small JSON; the same cap as the backup format. */
    const val MAX_THEME_BYTES = 64 * 1024

    val CATEGORIES = listOf("anime", "minimal", "dark", "light", "gradient", "glass", "photo", "other")

    val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")

    /** @return a human-readable reason the draft cannot be published, or null when it is fine. */
    fun validate(title: String, description: String, tags: List<String>, category: String): String? {
        val t = title.trim()
        if (t.length < MIN_TITLE) return "Title needs at least $MIN_TITLE characters"
        if (t.length > MAX_TITLE) return "Title is longer than $MAX_TITLE characters"
        if (description.length > MAX_DESCRIPTION) return "Description is longer than $MAX_DESCRIPTION characters"
        if (category !in CATEGORIES) return "Choose a category"
        if (tags.size > MAX_TAGS) return "Use at most $MAX_TAGS tags"
        if (tags.any { it.isBlank() }) return "Tags cannot be empty"
        if (tags.any { it.length > MAX_TAG_LENGTH }) return "Each tag must be $MAX_TAG_LENGTH characters or fewer"
        return null
    }

    /** Normalise tags: trim, drop blanks, lowercase, de-duplicate, cap the count. */
    fun normaliseTags(raw: List<String>): List<String> =
        raw.map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_TAGS)

    /** Parse a comma-separated tag field. */
    fun parseTags(raw: String): List<String> = normaliseTags(raw.split(','))

    /** Extension of a wallpaper file name, or null when it is not an accepted image type. */
    fun imageExtension(name: String): String? {
        val ext = name.substringAfterLast('.', "").lowercase()
        return if (ext in IMAGE_EXTENSIONS) ext else null
    }

    /** The storage object path. The leading folder must be the user id: the bucket policy checks it. */
    fun storagePath(userId: String, themeId: String, extension: String): String =
        "$userId/${themeId.take(32)}.$extension"

    /** MIME type for the upload, from the extension. */
    fun mimeType(extension: String): String = when (extension.lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }
}
