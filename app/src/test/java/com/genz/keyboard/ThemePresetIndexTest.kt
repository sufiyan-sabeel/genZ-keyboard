package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the Studio gallery and the contrast guard.
 *
 * The important rule here: a theme whose artwork this build does not ship must not appear in the
 * gallery, because it would render as a card with a missing wallpaper.
 */
class ThemePresetIndexTest {

    private fun theme(id: String, name: String, wallpaper: String?, key: String = "#1E1E1E", text: String = "#F4F1EA"): String {
        val w = if (wallpaper != null) """"backgroundImage":"$wallpaper",""" else ""
        return """{"id":"$id","name":"$name",$w"description":"d","skin":{"colors":{"key":"$key","keyText":"$text","accent":"#B87333","accentText":"#14100C"}}}"""
    }

    // ---------------------------------------------------------------- gallery filtering

    @Test
    fun aThemeWithShippedArtworkAppears() {
        val e = ThemePresetIndex.parse("art_sunset.json", theme("builtin.art.sunset", "Anime Sunset", "anime/art_sunset.webp"), setOf("anime/art_sunset.webp"))
        assertNotNull(e)
        assertEquals("Anime Sunset", e!!.name)
        assertEquals("anime/art_sunset.webp", e.wallpaper)
    }

    @Test
    fun aThemeWhoseArtworkIsMissingIsHidden() {
        // this is how the three withheld themes stay out of the UI
        val e = ThemePresetIndex.parse("art_windmill.json", theme("builtin.art.windmill", "Midnight Windmill", "anime/art_windmill.webp"), setOf("anime/art_sunset.webp"))
        assertNull(e)
    }

    @Test
    fun aPaletteOnlyThemeAppearsInEitherFlavour() {
        val e = ThemePresetIndex.parse("preset_night.json", theme("builtin.preset.night", "Anime Night", null), emptySet())
        assertNotNull(e)
        assertNull(e!!.wallpaper)
    }

    @Test
    fun aThemeWithNoArtworkStillAppearsWhenArtworkIsAvailable() {
        val e = ThemePresetIndex.parse("preset_night.json", theme("builtin.preset.night", "Night", null), setOf("anime/art_sunset.webp"))
        assertNotNull(e)
    }

    @Test
    fun onlyGalleryFilesAreOffered() {
        assertTrue(ThemePresetIndex.isGalleryFile("art_sunset.json"))
        assertTrue(ThemePresetIndex.isGalleryFile("preset_night.json"))
        assertTrue(!ThemePresetIndex.isGalleryFile("classic.json"))
        assertTrue(!ThemePresetIndex.isGalleryFile("calculator.js"))
    }

    @Test
    fun malformedEntriesAreSkippedRatherThanCrashing() {
        assertNull(ThemePresetIndex.parse("x.json", "{ not json", emptySet()))
        assertNull(ThemePresetIndex.parse("x.json", """{"name":"no id"}""", emptySet()))
        assertNull(ThemePresetIndex.parse("x.json", """{"id":"a"}""", emptySet()))
    }

    @Test
    fun theGalleryIsSortedForAStableUi() {
        val list = ThemePresetIndex.build(
            listOf(
                "art_sunset.json" to theme("builtin.art.sunset", "Zebra", null),
                "art_cabin.json" to theme("builtin.art.cabin", "Alpha", null)
            ),
            emptySet()
        )
        assertEquals(listOf("Alpha", "Zebra"), list.map { it.name })
    }

    // ---------------------------------------------------------------- contrast guard

    @Test
    fun aReadableThemeReportsNoIssues() {
        val doc = ThemeDoc("user.1", "T", "b", mapOf(
            "colorKey" to "#1E1E1E", "colorKeyText" to "#F4F1EA",
            "colorAccent" to "#B87333", "colorAccentText" to "#14100C"
        ))
        assertTrue(ThemeApply.readabilityIssues(doc, KeyboardSettings()).isEmpty())
    }

    @Test
    fun anUnreadablePairIsReported() {
        val doc = ThemeDoc("user.1", "T", "b", mapOf(
            "colorKey" to "#1E1E1E", "colorKeyText" to "#2A2A2A"
        ))
        val issues = ThemeApply.readabilityIssues(doc, KeyboardSettings())
        assertEquals(1, issues.size)
        assertTrue(issues.first().startsWith("Keys"))
    }

    @Test
    fun unsetColoursAreNotReported() {
        // 0 means "inherit the keyboard palette", which is already known-good
        assertTrue(ThemeApply.readabilityIssues(ThemeDoc("u", "T", "b"), KeyboardSettings()).isEmpty())
    }

    @Test
    fun resetDropsColourOverridesButKeepsTheWallpaper() {
        val doc = ThemeDoc("u", "T", "b",
            overrides = mapOf("colorKey" to "#123456", "keyOpacity" to "0.8", WallpaperTuning.KEY_BLUR to "6"),
            wallpaper = "bundled_art_sunset.webp")
        val reset = ThemeApply.resetToPreset(doc)
        assertTrue(reset.overrides.keys.none { it in ThemeApply.COLOR_KEYS })
        assertEquals("bundled_art_sunset.webp", reset.wallpaper)
        assertEquals("6", reset.overrides[WallpaperTuning.KEY_BLUR])
    }
}
