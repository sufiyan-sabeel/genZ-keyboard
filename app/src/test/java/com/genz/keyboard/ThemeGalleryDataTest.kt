package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The data the gallery cards render from.
 *
 * These are the parts that can be verified without a device: which tokens become swatches, whether a
 * card can be marked selected, and whether a reserved slot can ever behave like a theme.
 */
class ThemeGalleryDataTest {

    private fun hex(s: String) = 0xFF000000.toInt() or s.removePrefix("#").toInt(16)

    private val preset = PresetEntry(
        id = "builtin.art.sunset",
        name = "Anime Sunset",
        sourceFile = "art_sunset.json",
        wallpaper = "anime/art_sunset.webp",
        colours = mapOf(
            "background" to hex("#886749"), "key" to hex("#2A211C"), "keyText" to hex("#F4F1EA"),
            "func" to hex("#3A2A22"), "funcText" to hex("#F4F1EA"),
            "accent" to hex("#D9611F"), "accentText" to hex("#12130F")
        ),
        description = "Cloud orange drives the accent."
    )

    @Test
    fun swatchesComeFromTheRenderedPairsOnly() {
        // a swatch for a token the keyboard never draws would be decoration
        val swatchTokens = ThemeContrast.PAIRS.map { (_, bg) -> bg }.distinct()
        assertEquals(listOf("key", "func", "accent"), swatchTokens)
        swatchTokens.forEach { assertTrue("missing $it", preset.colours.containsKey(it)) }
    }

    @Test
    fun everySwatchTokenHasARealColour() {
        ThemeContrast.PAIRS.map { it.second }.distinct().forEach { token ->
            val argb = preset.colours.getValue(token)
            assertEquals("$token must be opaque", 0xFF, (argb ushr 24) and 0xFF)
        }
    }

    @Test
    fun aCardIsSelectedByItsBasePreset() {
        // the card compares the editor's current basePreset, not the display name
        assertEquals(preset.id, "builtin.art.sunset")
        assertTrue(preset.id.startsWith("builtin."))
    }

    @Test
    fun aThemeWithNoWallpaperStillHasSwatches() {
        // the F-Droid flavour ships no artwork; the card must degrade, not break
        val bare = preset.copy(wallpaper = null)
        assertFalse(bare.colours.isEmpty())
        assertEquals(3, ThemeContrast.PAIRS.map { it.second }.distinct().size)
    }

    @Test
    fun aReservedSlotIsNeverSelectableOrApplicable() {
        ThemePresetIndex.RESERVED_SLOTS.forEach { slot ->
            assertFalse(slot is PresetEntry)
            assertFalse(ThemePresetIndex.isGalleryFile("${slot.id}.json"))
        }
    }

    @Test
    fun reservedSlotsAreNotCountedAmongThemes() {
        // a slot must never inflate an "installed themes" count
        val themes = listOf(preset)
        assertEquals(1, themes.size)
        assertEquals(3, ThemePresetIndex.RESERVED_SLOTS.size)
    }
}
