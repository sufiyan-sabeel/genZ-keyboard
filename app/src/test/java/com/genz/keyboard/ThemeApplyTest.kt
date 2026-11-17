package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the theme-to-settings bridge.
 *
 * These cover what the Studio actually does when you press Save or Apply: which settings a theme
 * writes, what it inherits from its base preset, and what happens when a value is nonsense.
 */
class ThemeApplyTest {

    private val base = KeyboardSettings()

    private fun doc(overrides: Map<String, String>) =
        ThemeDoc("user.1", "Mine", "builtin.preset.windmill", overrides)

    // ---------------------------------------------------------------- colour parsing

    @Test
    fun sixDigitHexBecomesOpaqueArgb() {
        assertEquals(0xFF1A2B3C.toInt(), ThemeApply.parseColor("#1A2B3C"))
    }

    @Test
    fun eightDigitHexKeepsItsAlpha() {
        assertEquals(0x801A2B3C.toInt(), ThemeApply.parseColor("#801A2B3C"))
    }

    @Test
    fun aColourWithoutTheHashIsAccepted() {
        assertEquals(0xFF1A2B3C.toInt(), ThemeApply.parseColor("1A2B3C"))
    }

    @Test
    fun nonsenseIsRejectedRatherThanGuessed() {
        assertNull(ThemeApply.parseColor("cyan"))
        assertNull(ThemeApply.parseColor("#12345"))
        assertNull(ThemeApply.parseColor("#GGGGGG"))
        assertNull(ThemeApply.parseColor(""))
    }

    @Test
    fun formattingRoundTrips() {
        val c = 0xFF1A2B3C.toInt()
        assertEquals(c, ThemeApply.parseColor(ThemeApply.formatColor(c)))
    }

    // ---------------------------------------------------------------- application

    @Test
    fun applyingWritesOnlyTheSurfacesTheThemeOverrides() {
        val s = ThemeApply.applyTo(doc(mapOf("colorKey" to "#112233")), base)
        assertEquals(0xFF112233.toInt(), s.colorKey)
        // untouched surfaces stay at their settings default
        assertEquals(base.colorFunc, s.colorFunc)
        assertEquals(base.colorAccent, s.colorAccent)
    }

    @Test
    fun applyingEveryColourKeyWorks() {
        val ov = mapOf(
            "colorBackground" to "#010101", "colorKey" to "#020202", "colorKeyText" to "#030303",
            "colorFunc" to "#040404", "colorFuncText" to "#050505", "colorAccent" to "#060606",
            "colorAccentText" to "#070707"
        )
        val s = ThemeApply.applyTo(doc(ov), base)
        assertEquals(0xFF010101.toInt(), s.colorBackground)
        assertEquals(0xFF020202.toInt(), s.colorKey)
        assertEquals(0xFF030303.toInt(), s.colorKeyText)
        assertEquals(0xFF040404.toInt(), s.colorFunc)
        assertEquals(0xFF050505.toInt(), s.colorFuncText)
        assertEquals(0xFF060606.toInt(), s.colorAccent)
        assertEquals(0xFF070707.toInt(), s.colorAccentText)
    }

    @Test
    fun anInvalidColourIsIgnoredRatherThanApplied() {
        val s = ThemeApply.applyTo(doc(mapOf("colorKey" to "not-a-colour")), base)
        assertEquals("an unparseable colour must leave the setting alone", base.colorKey, s.colorKey)
    }

    @Test
    fun opacityAndRadiusAreClampedToUsableRanges() {
        val s = ThemeApply.applyTo(doc(mapOf(ThemeApply.KEY_OPACITY to "9", ThemeApply.CORNER_RADIUS to "999")), base)
        assertTrue(s.keyOpacity <= 1f)
        assertTrue(s.cornerRadiusDp <= 40f)
    }

    @Test
    fun aThemeWithNoOverridesChangesNothing() {
        assertEquals(base, ThemeApply.applyTo(doc(emptyMap()), base))
    }

    // ---------------------------------------------------------------- capture

    @Test
    fun captureRecordsTheCurrentSettings() {
        val from = base.copy(colorKey = 0xFF112233.toInt(), keyOpacity = 0.8f, cornerRadiusDp = 12f)
        val captured = ThemeApply.capture("Mine", "builtin.preset.windmill", from)
        assertEquals(0xFF112233.toInt(), ThemeApply.color(captured, "colorKey"))
        assertEquals(0.8f, captured.overrides[ThemeApply.KEY_OPACITY]?.toFloat())
        assertEquals(12f, captured.overrides[ThemeApply.CORNER_RADIUS]?.toFloat())
    }

    @Test
    fun capturedThemesRoundTripThroughTheCodec() {
        val from = base.copy(colorKey = 0xFF112233.toInt())
        val captured = ThemeApply.capture("Round trip", "builtin.preset.night", from)
        val decoded = ThemeCodec.decode(ThemeCodec.encode(captured))
        assertTrue("a captured theme must be a valid document", decoded is ThemeValidation.Ok)
        assertEquals(captured, (decoded as ThemeValidation.Ok).doc)
    }

    @Test
    fun captureSkipsUnsetColours() {
        // 0 means "automatic" in KeyboardSettings; writing it as a colour would force black
        val captured = ThemeApply.capture("Fresh", "builtin.preset.night", base)
        assertNull(ThemeApply.color(captured, "colorKey"))
    }

    // ---------------------------------------------------------------- preset inheritance

    @Test
    fun aThemeInheritsEveryColourItDoesNotOverride() {
        val preset = mapOf("colorKey" to 0xFFAAAAAA.toInt(), "colorAccent" to 0xFFBBBBBB.toInt())
        val resolved = ThemeApply.resolvedColors(doc(mapOf("colorAccent" to "#00FF00")), preset)
        assertEquals(0xFFAAAAAA.toInt(), resolved["colorKey"])
        assertEquals(0xFF00FF00.toInt(), resolved["colorAccent"])
    }

    @Test
    fun thePresetAssetNameFollowsTheIdConvention() {
        assertEquals("plugins/preset_windmill.json", ThemeApply.presetAsset("builtin.preset.windmill"))
    }
}
