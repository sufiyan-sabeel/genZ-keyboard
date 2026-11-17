package com.genz.keyboard

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validates the bundled presets as data.
 *
 * These are the guarantees a preset must meet before it ships: it parses, it declares every colour
 * the engine needs, and its key label is actually readable against its keycap. The contrast check is
 * the one that stops a pretty theme from being unusable.
 */
class ThemePresetTest {

    private val dir = File("src/main/assets/plugins")

    private fun presets(): List<Pair<String, JSONObject>> =
        dir.listFiles { f -> f.isFile && f.name.startsWith("anime_") && f.name.endsWith(".json") }
            .orEmpty()
            .sortedBy { it.name }
            .map { it.name to JSONObject(it.readText()) }

    private fun colour(o: JSONObject, key: String): String? =
        o.optJSONObject("skin")?.optJSONObject("colors")?.optString(key)?.takeIf { it.isNotEmpty() }

    private fun rgb(hex: String): Triple<Int, Int, Int> {
        val h = hex.removePrefix("#")
        return Triple(
            h.substring(0, 2).toInt(16),
            h.substring(2, 4).toInt(16),
            h.substring(4, 6).toInt(16)
        )
    }

    private fun luminance(hex: String): Double {
        fun f(c: Int): Double {
            val u = c / 255.0
            return if (u <= 0.03928) u / 12.92 else Math.pow((u + 0.055) / 1.055, 2.4)
        }
        val (r, g, b) = rgb(hex)
        return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)
    }

    private fun contrast(a: String, b: String): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun theCollectionHasAtLeastTwelvePresets() {
        assertTrue("expected at least 12 anime presets, found ${presets().size}", presets().size >= 12)
    }

    @Test
    fun everyPresetIsValidJsonWithAnIdAndName() {
        presets().forEach { (file, o) ->
            assertTrue("$file has no id", o.optString("id").isNotEmpty())
            assertTrue("$file has no name", o.optString("name").isNotEmpty())
            assertTrue("$file has no skin object", o.has("skin"))
        }
    }

    @Test
    fun presetIdsAreUnique() {
        val ids = presets().map { it.second.optString("id") }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun everyPresetDeclaresTheColoursTheEngineNeeds() {
        val required = listOf("background", "key", "keyText", "func", "funcText", "accent", "accentText")
        presets().forEach { (file, o) ->
            required.forEach { key ->
                val v = colour(o, key)
                assertTrue("$file is missing colors.$key", v != null)
                assertTrue("$file colors.$key is not a hex colour", v!!.matches(Regex("^#[0-9a-fA-F]{6}$")))
            }
        }
    }

    @Test
    fun everyPresetHasAReadableKeyLabel() {
        // 4.5:1 is the WCAG AA threshold for normal text
        presets().forEach { (file, o) ->
            val key = colour(o, "key")!!
            val text = colour(o, "keyText")!!
            val ratio = contrast(key, text)
            assertTrue(
                "$file key label contrast is %.2f:1 (needs 4.5)".format(ratio),
                ratio >= 4.5
            )
        }
    }

    @Test
    fun everyPresetHasAReadableFunctionKeyLabel() {
        presets().forEach { (file, o) ->
            val func = colour(o, "func")!!
            val text = colour(o, "funcText")!!
            val ratio = contrast(func, text)
            assertTrue("$file func label contrast is %.2f:1".format(ratio), ratio >= 4.5)
        }
    }

    @Test
    fun everyPresetHasAReadableAccentLabel() {
        presets().forEach { (file, o) ->
            val accent = colour(o, "accent")!!
            val text = colour(o, "accentText")!!
            val ratio = contrast(accent, text)
            assertTrue("$file accent label contrast is %.2f:1".format(ratio), ratio >= 4.5)
        }
    }

    /** The twelve presets added with the theme overhaul. */
    private val newPresets = setOf(
        "anime_night.json", "anime_samurai.json", "anime_sakura.json", "anime_tokyo.json",
        "anime_forest.json", "anime_cyber.json", "anime_academia.json", "anime_minimal.json",
        "anime_pastel.json", "anime_ocean.json", "anime_cyberdark.json", "anime_windmill.json"
    )

    @Test
    fun theNewPresetsAreAllPresent() {
        val files = presets().map { it.first }.toSet()
        val missing = newPresets - files
        assertTrue("missing new presets: $missing", missing.isEmpty())
    }

    @Test
    fun theNewPresetsArePaletteOnlySoBothFlavoursRenderThem() {
        // The ten older skins reference anime/*.webp, which only the standard flavour ships and
        // which is therefore unavailable in fdroid. The new presets deliberately carry no artwork,
        // so they render identically in both flavours.
        presets().filter { it.first in newPresets }.forEach { (file, o) ->
            assertTrue(
                "$file references artwork; the fdroid flavour cannot provide it",
                !o.has("backgroundImage") || o.optString("backgroundImage").isEmpty()
            )
        }
    }

    @Test
    fun everyPresetWithArtworkStillHasAPaletteFallback() {
        // a skin referencing a missing image must degrade to its gradient, never to a blank view
        presets().filter { it.second.has("backgroundImage") }.forEach { (file, o) ->
            val bg = o.optJSONObject("skin")?.optJSONObject("keyboard")?.optJSONObject("background")
            val colours = bg?.optJSONArray("colors")
            assertTrue(
                "$file depends on artwork but declares no gradient fallback",
                colours != null && colours.length() >= 2
            )
        }
    }

    @Test
    fun everyPresetHasAGradientBackground() {
        presets().forEach { (file, o) ->
            val bg = o.optJSONObject("skin")?.optJSONObject("keyboard")?.optJSONObject("background")
            assertTrue("$file has no keyboard background", bg != null)
            val colours = bg!!.optJSONArray("colors")
            assertTrue("$file background has fewer than two colours", colours != null && colours.length() >= 2)
        }
    }

    @Test
    fun theCollectionIsNotTwelveCopiesOfOneTheme() {
        // the brief forbids "every theme look identical with a different wallpaper"
        val accents = presets().map { colour(it.second, "accent")!!.lowercase() }.toSet()
        assertTrue("presets share too few distinct accents: ${accents.size}", accents.size >= 8)
    }

    @Test
    fun descriptionsRecordWhereThePaletteCameFrom() {
        // provenance is part of the data, not just the design doc
        presets().forEach { (file, o) ->
            val d = o.optString("description")
            assertTrue("$file has no description", d.isNotEmpty())
            assertTrue(
                "$file description does not record provenance",
                d.contains("palette from", ignoreCase = true) || d.contains("derived", ignoreCase = true)
            )
        }
    }
}
