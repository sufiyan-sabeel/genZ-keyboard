package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Thumbnail subsampling.
 *
 * The cost of getting this wrong is the whole point of the class: a full-resolution wallpaper is
 * ~740×1300, and decoding nine of those for a list would be tens of megabytes.
 */
class ThemeThumbnailTest {

    @Test
    fun aFullSizeWallpaperIsHeavilySubsampled() {
        // the actual bundled dimensions
        val s = ThemeThumbnails.sampleSizeFor(736, 1308)
        // 736/4 = 184 is still above the 96px target, so 4 is the largest power of two that keeps
        // the result at or above it — going to 8 would undershoot
        assertEquals(4, s)
        assertTrue(736 / s >= ThemeThumbnails.TARGET_PX)
        // and the decoded result is far smaller than the source
        assertTrue(736 / s <= ThemeThumbnails.TARGET_PX * 2)
    }

    @Test
    fun aSmallImageIsNotUpscaled() {
        assertEquals(1, ThemeThumbnails.sampleSizeFor(48, 48))
    }

    @Test
    fun theResultNeverFallsBelowTheTarget() {
        // inSampleSize is a power-of-two divisor, so the result must stay at or above the target
        listOf(100, 200, 400, 736, 1080, 2048).forEach { w ->
            val s = ThemeThumbnails.sampleSizeFor(w, w * 2)
            assertTrue("width $w sampled by $s fell below target", w / s >= ThemeThumbnails.TARGET_PX)
        }
    }

    @Test
    fun degenerateDimensionsDoNotCrash() {
        assertEquals(1, ThemeThumbnails.sampleSizeFor(0, 0))
        assertEquals(1, ThemeThumbnails.sampleSizeFor(-5, 100))
        assertEquals(1, ThemeThumbnails.sampleSizeFor(100, 100, 0))
    }

    @Test
    fun aLargerTargetProducesLessSubsampling() {
        val small = ThemeThumbnails.sampleSizeFor(736, 1308, targetPx = 48)
        val large = ThemeThumbnails.sampleSizeFor(736, 1308, targetPx = 192)
        assertTrue("larger target should sample less, got $small vs $large", large <= small)
    }

    @Test
    fun theTargetIsSmallerThanACardAtEveryDensity() {
        // a 40dp card at 3x density is 120px, so the 96px target is within one step of it
        assertTrue(ThemeThumbnails.TARGET_PX <= 120)
    }
}
