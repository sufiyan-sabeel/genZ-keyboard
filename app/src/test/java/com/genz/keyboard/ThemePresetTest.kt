package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validates the twelve bundled presets as data.
 *
 * Parsed with [ThemeJson] rather than `org.json`, because the platform's `org.json` is a stub in
 * JVM unit tests and would return defaults instead of the real values — the test would pass while
 * checking nothing.
 *
 * The contrast checks are the ones that matter: a preset that is pretty but unreadable is not
 * shippable.
 */
class ThemePresetTest {

    private val dir = File("src/main/assets/plugins")

    private val expected = setOf(
        "preset_academia.json", "preset_cyber.json", "preset_cyberdark.json", "preset_forest.json",
        "preset_minimal.json", "preset_night.json", "preset_ocean.json", "preset_pastel.json",
        "preset_sakura.json", "preset_samurai.json", "preset_tokyo.json", "preset_windmill.json"
    )

    private fun files() = dir.listFiles { f -> f.name.startsWith("preset_") && f.name.endsWith(".json") }
        .orEmpty().sortedBy { it.name }

    private fun presets(): List<Pair<String, Map<String, Any?>>> =
        files().map { it.name to (ThemeJson.decode(it.readText()) ?: emptyMap()) }

    @Suppress("UNCHECKED_CAST")
    private fun obj(m: Map<String, Any?>?, key: String) = m?.get(key) as? Map<String, Any?>

    private fun str(m: Map<String, Any?>?, key: String) = m?.get(key) as? String

    private fun colours(m: Map<String, Any?>?) = obj(obj(m, "skin"), "colors")

    private fun background(m: Map<String, Any?>?) = obj(obj(m, "skin"), "keyboard")?.get("background")

    private fun rgb(hex: String): Triple<Int, Int, Int> {
        val h = hex.removePrefix("#")
        return Triple(h.substring(0, 2).toInt(16), h.substring(2, 4).toInt(16), h.substring(4, 6).toInt(16))
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
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    // ---------------------------------------------------------------- structure

    @Test
    fun allTwelvePresetsArePresent() {
        assertEquals("preset files on disk", expected.sorted(), files().map { it.name })
    }

    @Test
    fun everyPresetParsesAsJson() {
        presets().forEach { (file, o) ->
            assertTrue("$file did not parse", o.isNotEmpty())
            assertTrue("$file has no id", str(o, "id")?.isNotEmpty() == true)
            assertTrue("$file has no name", str(o, "name")?.isNotEmpty() == true)
            assertNotNull("$file has no skin", obj(o, "skin"))
        }
    }

    @Test
    fun presetIdsAreUniqueAndNamespaced() {
        val ids = presets().map { str(it.second, "id")!! }
        assertEquals("ids must not repeat", ids.size, ids.toSet().size)
        ids.forEach { assertTrue("id $it is not in the builtin.preset namespace", it.startsWith("builtin.preset.")) }
    }

    @Test
    fun everyPresetDeclaresTheColoursTheEngineNeeds() {
        val required = listOf("background", "key", "keyText", "func", "funcText", "accent", "accentText")
        presets().forEach { (file, o) ->
            required.forEach { key ->
                val v = str(colours(o), key)
                assertTrue("$file is missing colors.$key", v != null)
                assertTrue("$file colors.$key is not a hex colour", v!!.matches(Regex("^#[0-9a-fA-F]{6}$")))
            }
        }
    }

    @Test
    fun everyPresetHasAGradientBackground() {
        presets().forEach { (file, o) ->
            val bg = background(o) as? Map<*, *>
            assertNotNull("$file has no keyboard background", bg)
            val list = bg!!["colors"] as? List<*>
            assertTrue("$file background needs at least two colours", list != null && list.size >= 2)
            list!!.forEach { assertTrue("$file background colour is not hex", (it as String).matches(Regex("^#[0-9a-fA-F]{6}$"))) }
        }
    }

    @Test
    fun presetsCarryNoArtworkSoBothFlavoursRenderThem() {
        // The ten older anime_* skins reference anime/*.webp, which only the standard flavour
        // ships. These presets deliberately carry none, so they look the same in fdroid.
        presets().forEach { (file, o) ->
            assertTrue(
                "$file references artwork, which the fdroid flavour cannot provide",
                (str(o, "backgroundImage") ?: "").isEmpty()
            )
        }
    }

    // ---------------------------------------------------------------- readability

    @Test
    fun everyPresetHasAReadableKeyLabel() {
        presets().forEach { (file, o) ->
            val ratio = contrast(str(colours(o), "key")!!, str(colours(o), "keyText")!!)
            assertTrue("$file key label contrast is %.2f:1 (needs 4.5)".format(ratio), ratio >= 4.5)
        }
    }

    @Test
    fun everyPresetHasAReadableFunctionKeyLabel() {
        presets().forEach { (file, o) ->
            val ratio = contrast(str(colours(o), "func")!!, str(colours(o), "funcText")!!)
            assertTrue("$file func label contrast is %.2f:1 (needs 4.5)".format(ratio), ratio >= 4.5)
        }
    }

    @Test
    fun everyPresetHasAReadableAccentLabel() {
        presets().forEach { (file, o) ->
            val ratio = contrast(str(colours(o), "accent")!!, str(colours(o), "accentText")!!)
            assertTrue("$file accent label contrast is %.2f:1 (needs 4.5)".format(ratio), ratio >= 4.5)
        }
    }

    // ---------------------------------------------------------------- distinctness and provenance

    @Test
    fun theCollectionIsNotOneThemeWithDifferentWallpapers() {
        val accents = presets().map { str(colours(it.second), "accent")!!.lowercase() }.toSet()
        assertTrue("only ${accents.size} distinct accents across twelve presets", accents.size >= 8)
    }

    @Test
    fun everyPresetRecordsWhereItsPaletteCameFrom() {
        // provenance is part of the data, not only the design document
        presets().forEach { (file, o) ->
            val d = str(o, "description").orEmpty()
            assertTrue("$file has no description", d.isNotEmpty())
            assertTrue(
                "$file description does not record provenance",
                d.contains("palette from", ignoreCase = true) || d.contains("derived", ignoreCase = true)
            )
        }
    }

    @Test
    fun noPresetClaimsToShipSomeoneElsesArtwork() {
        // three of the supplied references are signed by other artists and cannot be redistributed
        presets().forEach { (file, o) ->
            assertTrue(
                "$file mentions a bundled image path",
                !str(o, "description").orEmpty().contains(".webp")
            )
        }
    }
}
