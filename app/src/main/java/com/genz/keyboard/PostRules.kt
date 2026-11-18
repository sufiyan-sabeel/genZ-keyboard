package com.genz.keyboard

/**
 * What a design post may contain.
 *
 * Pure, so the rules are tested and the UI can explain a rejection. The database enforces the same
 * limits, so a client that skips these checks still cannot store an oversized or invalid post.
 */
object PostRules {

    const val MIN_TITLE = 3
    const val MAX_TITLE = 120
    const val MAX_BODY = 8000
    const val MAX_TAGS = 8
    const val MAX_TAG_LENGTH = 24
    const val MAX_IMAGES = 4
    const val MAX_IMAGE_BYTES = 8L * 1024 * 1024

    /** Images only: a post must never carry anything the platform could execute. */
    val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")

    const val MIN_COMMENT = 1
    const val MAX_COMMENT = 2000

    /** @return a reason the post cannot be saved, or null when it is fine. */
    fun validate(title: String, body: String, tags: List<String>): String? {
        val t = title.trim()
        if (t.length < MIN_TITLE) return "Title needs at least $MIN_TITLE characters"
        if (t.length > MAX_TITLE) return "Title is longer than $MAX_TITLE characters"
        if (body.length > MAX_BODY) return "Post is longer than $MAX_BODY characters"
        if (tags.size > MAX_TAGS) return "Use at most $MAX_TAGS tags"
        if (tags.any { it.isBlank() }) return "Tags cannot be empty"
        if (tags.any { it.length > MAX_TAG_LENGTH }) return "Each tag must be $MAX_TAG_LENGTH characters or fewer"
        return null
    }

    /** @return a reason the comment cannot be posted, or null when it is fine. */
    fun validateComment(body: String): String? {
        val b = body.trim()
        if (b.length < MIN_COMMENT) return "Write something first"
        if (b.length > MAX_COMMENT) return "Comment is longer than $MAX_COMMENT characters"
        return null
    }

    /**
     * @return a reason the attachment list is unacceptable, or null when it is fine.
     *
     * Only image extensions are allowed, and the count is capped: a post is a design showcase, not
     * a file host, and nothing here may be executed.
     */
    fun validateImages(names: List<String>): String? {
        if (names.size > MAX_IMAGES) return "Attach at most $MAX_IMAGES images"
        names.forEach { n ->
            val ext = n.substringAfterLast('.', "").lowercase()
            if (ext !in IMAGE_EXTENSIONS) return "Only PNG, JPEG or WebP images can be attached"
            if (!DownloadGuard.isSafeWallpaperName(n)) return "Unsafe image name"
        }
        return null
    }

    fun normaliseTags(raw: List<String>): List<String> =
        raw.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct().take(MAX_TAGS)

    fun parseTags(raw: String): List<String> = normaliseTags(raw.split(','))

    /** The caller may edit or delete only their own post. */
    fun canModify(authorId: String?, currentUserId: String?): Boolean =
        authorId != null && currentUserId != null && authorId == currentUserId
}
