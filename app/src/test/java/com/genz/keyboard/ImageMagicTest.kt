package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for image identification by content.
 *
 * A downloaded wallpaper's Content-Type is a claim; these tests pin the rule that the bytes decide.
 */
class ImageMagicTest {

    private fun png() = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(8)
    private fun jpeg() = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(12)
    private fun webp(): ByteArray {
        val b = ByteArray(16)
        "RIFF".toByteArray().copyInto(b, 0)
        "WEBP".toByteArray().copyInto(b, 8)
        return b
    }

    @Test
    fun pngIsDetected() = assertEquals(ImageMagic.Format.PNG, ImageMagic.detect(png()))

    @Test
    fun jpegIsDetected() = assertEquals(ImageMagic.Format.JPEG, ImageMagic.detect(jpeg()))

    @Test
    fun webpIsDetected() = assertEquals(ImageMagic.Format.WEBP, ImageMagic.detect(webp()))

    @Test
    fun otherBytesAreNotAnImage() {
        assertNull(ImageMagic.detect(ByteArray(32)))
        assertNull(ImageMagic.detect("not an image at all".toByteArray()))
        assertNull(ImageMagic.detect(ByteArray(4)))
    }

    @Test
    fun aMatchingMimeIsAccepted() {
        assertTrue(ImageMagic.matchesMime(png(), "image/png"))
        assertTrue(ImageMagic.matchesMime(webp(), "image/webp"))
    }

    @Test
    fun aLyingMimeIsRejected() {
        // a file claiming to be a PNG while carrying JPEG bytes is refused, not corrected
        assertFalse(ImageMagic.matchesMime(jpeg(), "image/png"))
    }

    @Test
    fun anAbsentMimeFallsBackToTheBytes() {
        assertTrue(ImageMagic.matchesMime(png(), null))
        assertTrue(ImageMagic.matchesMime(png(), ""))
    }

    @Test
    fun theJpegAliasIsTolerated() {
        assertTrue(ImageMagic.matchesMime(jpeg(), "image/jpg"))
    }
}
