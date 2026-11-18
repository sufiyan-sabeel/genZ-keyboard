package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the duplicate-publishing hash.
 *
 * The hash must be stable across runs and must ignore the things a user legitimately changes when
 * re-publishing (title, description), while catching the same design submitted twice.
 */
class ThemeContentHashTest {

    private fun doc(
        id: String = "user.1",
        name: String = "Sunset",
        base: String = "builtin.preset.windmill",
        overrides: Map<String, String> = mapOf("colorKey" to "#1E1E1E", "colorAccent" to "#B87333"),
        wallpaper: String? = "wall_a.webp"
    ) = ThemeDoc(id, name, base, overrides, wallpaper)

    @Test
    fun theSameDesignHashesTheSame() {
        assertEquals(ThemeContentHash.of(doc()), ThemeContentHash.of(doc()))
    }

    @Test
    fun theHashIsStableAcrossKeyOrder() {
        val a = doc(overrides = linkedMapOf("colorKey" to "#1E1E1E", "colorAccent" to "#B87333"))
        val b = doc(overrides = linkedMapOf("colorAccent" to "#B87333", "colorKey" to "#1E1E1E"))
        assertEquals(ThemeContentHash.of(a), ThemeContentHash.of(b))
    }

    @Test
    fun renamingDoesNotChangeTheHash() {
        // re-publishing under a new title is exactly the duplicate this catches
        assertEquals(ThemeContentHash.of(doc(name = "Sunset")), ThemeContentHash.of(doc(name = "Sunset v2")))
    }

    @Test
    fun aDifferentWallpaperFileDoesNotChangeTheHash() {
        assertEquals(ThemeContentHash.of(doc(wallpaper = "a.webp")), ThemeContentHash.of(doc(wallpaper = "b.webp")))
    }

    @Test
    fun aDifferentColourIsADifferentDesign() {
        val a = doc(overrides = mapOf("colorKey" to "#1E1E1E"))
        val b = doc(overrides = mapOf("colorKey" to "#202020"))
        assertNotEquals(ThemeContentHash.of(a), ThemeContentHash.of(b))
        assertFalse(ThemeContentHash.isDuplicate(a, b))
    }

    @Test
    fun aDifferentBasePresetIsADifferentDesign() {
        assertNotEquals(
            ThemeContentHash.of(doc(base = "builtin.preset.windmill")),
            ThemeContentHash.of(doc(base = "builtin.preset.night"))
        )
    }

    @Test
    fun theHashIsAStableLengthHex() {
        val h = ThemeContentHash.of(doc())
        assertEquals(32, h.length)
        assertTrue("not hex: $h", h.all { it in "0123456789abcdef" })
    }

    @Test
    fun duplicatesAreDetected() {
        assertTrue(ThemeContentHash.isDuplicate(doc(id = "user.1"), doc(id = "user.2")))
    }
}
