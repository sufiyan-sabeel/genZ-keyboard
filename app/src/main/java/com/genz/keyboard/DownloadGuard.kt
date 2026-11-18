package com.genz.keyboard

/**
 * Validation for anything downloaded from the community.
 *
 * A community theme is untrusted input: it arrives over the network from another user's account.
 * It goes through exactly the same gates as an imported archive, plus a size cap, so a hostile or
 * corrupt payload is rejected before it can be written to the theme store.
 */
object DownloadGuard {

    /** Same ceiling as the backup format and the publish cap. */
    const val MAX_THEME_BYTES = 64L * 1024

    /** Wallpapers are fetched separately and capped here. */
    const val MAX_WALLPAPER_BYTES = 8L * 1024 * 1024

    val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")

    /**
     * Validate a downloaded theme document.
     *
     * @return the theme, or a reason it cannot be used. Never returns a partially-trusted document.
     */
    fun accept(json: String): Result {
        if (json.toByteArray().size > MAX_THEME_BYTES) {
            return Result.Rejected("Theme is larger than 64 KB")
        }
        return when (val v = ThemeCodec.decode(json)) {
            is ThemeValidation.Ok -> {
                val doc = v.doc
                if (doc.wallpaper != null && !isSafeWallpaperName(doc.wallpaper)) {
                    Result.Rejected("Theme references an unsafe wallpaper name")
                } else {
                    Result.Accepted(doc)
                }
            }
            is ThemeValidation.Invalid -> Result.Rejected(v.reason)
        }
    }

    /**
     * A wallpaper name from the network must be a bare file name.
     *
     * Anything with a separator or traversal segment would let a downloaded theme write outside the
     * wallpaper directory, so it is refused rather than sanitised.
     */
    fun isSafeWallpaperName(name: String): Boolean {
        if (name.isEmpty() || name.length > 80) return false
        if (name.contains('/') || name.contains('\\') || name.contains("..")) return false
        return name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS
    }

    /** Storage object paths are relative and single-segment-per-level, never absolute. */
    fun isSafeObjectPath(path: String): Boolean {
        if (path.isEmpty() || path.length > 200) return false
        if (path.startsWith("/") || path.contains("\\") || path.contains("..")) return false
        return path.split('/').none { it.isEmpty() }
    }

    sealed interface Result {
        data class Accepted(val doc: ThemeDoc) : Result
        data class Rejected(val reason: String) : Result
    }
}
