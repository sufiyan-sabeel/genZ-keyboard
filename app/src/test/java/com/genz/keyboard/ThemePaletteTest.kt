package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the rule that a keyboard theme keeps its own palette.
 *
 * The brand colour paints the app interface. It used to be stored as a "user accent", which made
 * every theme repaint itself in brand violet - the artwork was applied but the keys, Enter and
 * suggestion bar all stayed one colour. These tests fail if that regresses.
 */
class ThemePaletteTest {

    @Test
    fun brandColourIsNotAUserAccent() {
        val defaults = KeyboardSettings()
        assertEquals("BRAND", defaults.colorSource)
        assertEquals("the brand colour must not override theme palettes", 0, defaults.userAccent)
    }

    @Test
    fun chosenAccentStillOverrides() {
        // an explicit accent is a deliberate user choice and must keep working
        assertEquals(0xFFAA3366.toInt(), KeyboardSettings(colorAccent = 0xFFAA3366.toInt()).userAccent)
    }

    @Test
    fun explicitSeedStillOverrides() {
        val s = KeyboardSettings(colorSource = "SEED", seedColor = 0xFF228844.toInt())
        assertEquals(0xFF228844.toInt(), s.userAccent)
    }

    @Test
    fun everyAnimeThemeHasItsOwnAccent() {
        val accents = File("src/main/assets/plugins")
            .listFiles { f -> f.name.startsWith("anime_") && f.name.endsWith(".json") }
            .orEmpty()
            .associate { f ->
                f.name to (Regex("\"accent\"\\s*:\\s*\"(#[0-9A-Fa-f]{6})\"").find(f.readText())?.groupValues?.get(1) ?: "")
            }
        assertEquals("ten anime themes expected", 10, accents.size)
        accents.forEach { (name, accent) ->
            assertTrue("$name has no accent", accent.startsWith("#"))
            assertNotEquals("$name must not fall back to the brand violet", "#6C4DF6", accent.uppercase())
        }
        assertTrue("accents should be distinct per theme", accents.values.toSet().size >= 7)
    }

    @Test
    fun everyAnimeThemeStylesTheSpecialKeys() {
        val needed = listOf("shift", "backspace", "globe", "emoji", "mode", "space", "enter", "func", "chips")
        File("src/main/assets/plugins")
            .listFiles { f -> f.name.startsWith("anime_") && f.name.endsWith(".json") }
            .orEmpty()
            .forEach { f ->
                val text = f.readText()
                needed.forEach { key ->
                    assertTrue("${f.name} does not style $key", text.contains("\"$key\""))
                }
            }
    }
}
