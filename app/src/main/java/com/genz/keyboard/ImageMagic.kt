package com.genz.keyboard

/**
 * Identifies an image from its first bytes.
 *
 * A `Content-Type` header is a claim, not a fact: a server or a hostile actor can label anything
 * `image/png`. The bytes are what actually decides, so a downloaded wallpaper is only accepted when
 * its header matches a real image format the renderer can decode.
 */
object ImageMagic {

    enum class Format(val extension: String, val mime: String) { PNG("png", "image/png"), JPEG("jpg", "image/jpeg"), WEBP("webp", "image/webp") }

    private val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())

    /** @return the real format, or null when the bytes are not a supported image. */
    fun detect(bytes: ByteArray): Format? {
        if (bytes.size < 12) return null
        if (bytes.copyOfRange(0, 8).contentEquals(PNG)) return Format.PNG
        if (bytes.copyOfRange(0, 3).contentEquals(JPEG)) return Format.JPEG
        // RIFF....WEBP
        if (String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
        ) return Format.WEBP
        return null
    }

    /**
     * Whether a declared MIME type agrees with the bytes.
     *
     * Disagreement is treated as a rejection rather than a correction: a file that claims to be a
     * PNG and is not has no business being written to the theme directory.
     */
    fun matchesMime(bytes: ByteArray, declaredMime: String?): Boolean {
        val actual = detect(bytes) ?: return false
        if (declaredMime.isNullOrBlank()) return true
        return actual.mime.equals(declaredMime.trim().lowercase(), ignoreCase = true) ||
            // image/jpg is a common misspelling of image/jpeg
            (actual == Format.JPEG && declaredMime.trim().lowercase() == "image/jpg")
    }
}
