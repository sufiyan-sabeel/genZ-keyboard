package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG contrast maths, checked against published reference cases.
 *
 * If these pass, the numbers reported for the six themes can be trusted; if they do not, every ratio
 * in the report is suspect.
 */
class ThemeContrastTest {

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    @Test
    fun blackOnWhiteIsTwentyOneToOne() {
        // the maximum possible ratio, stated in the WCAG specification itself
        assertEquals(21.0, ThemeContrast.ratio(white, black), 0.01)
    }

    @Test
    fun identicalColoursAreOneToOne() {
        assertEquals(1.0, ThemeContrast.ratio(white, white), 0.001)
        assertEquals(1.0, ThemeContrast.ratio(0xFF3A7BD5.toInt(), 0xFF3A7BD5.toInt()), 0.001)
    }

    @Test
    fun orderDoesNotMatter() {
        val a = 0xFF1E1E1E.toInt()
        assertEquals(ThemeContrast.ratio(white, a), ThemeContrast.ratio(a, white), 0.0001)
    }

    @Test
    fun whiteLuminanceIsOne() {
        assertEquals(1.0, ThemeContrast.relativeLuminance(white), 0.0001)
    }

    @Test
    fun blackLuminanceIsZero() {
        assertEquals(0.0, ThemeContrast.relativeLuminance(black), 0.0001)
    }

    @Test
    fun midGreyMatchesTheKnownValue() {
        // #777777 is the classic "just fails AA on white" grey
        val grey = 0xFF777777.toInt()
        assertEquals(0.1845, ThemeContrast.relativeLuminance(grey), 0.001)
        assertEquals(4.48, ThemeContrast.ratio(white, grey), 0.02)
    }

    @Test
    fun aaThresholdIsFourPointFive() {
        assertTrue(ThemeContrast.passesAA(4.5))
        assertFalse(ThemeContrast.passesAA(4.49))
    }

    @Test
    fun largeTextUsesThreeToOne() {
        assertTrue(ThemeContrast.passesAA(3.0, large = true))
        assertFalse(ThemeContrast.passesAA(2.99, large = true))
    }

    @Test
    fun aaaUsesSevenToOne() {
        assertTrue(ThemeContrast.passesAAA(7.0))
        assertFalse(ThemeContrast.passesAAA(6.99))
    }

    @Test
    fun luminanceIgnoresTheAlphaChannel() {
        // the keyboard composites over artwork; only the colour matters for the ratio
        assertEquals(
            ThemeContrast.relativeLuminance(0xFFF4F1EA.toInt()),
            ThemeContrast.relativeLuminance(0x80F4F1EA.toInt()),
            0.0001
        )
    }

    @Test
    fun roundingIsTwoDecimals() {
        assertEquals(4.48, ThemeContrast.rounded(4.4789), 0.0001)
    }
}
