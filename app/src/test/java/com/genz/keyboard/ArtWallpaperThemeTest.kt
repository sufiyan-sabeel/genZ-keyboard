package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validates the nine image-backed themes.
 *
 * The point of this suite is that a theme is only complete when its artwork is actually there:
 * every case checks the real asset on disk, not just a colour palette.
 */
class ArtWallpaperThemeTest {

    private val themeDir = File("src/main/assets/plugins")
    private val artDir = File("src/standard/assets/anime")

    private val expected = listOf(
        "art_blueroad", "art_cabin", "art_pastel", "art_rainytokyo", "art_sakura",
        "art_samurai", "art_snowfall", "art_sunset", "art_windmill"
    )

    private fun files() = themeDir.listFiles { f -> f.name.startsWith("art_") && f.name.endsWith(".json") }
        .orEmpty().sortedBy { it.name }

    private fun themes(): List<Pair<String, Map<String, Any?>>> =
        files().map { it.name to (ThemeJson.decode(it.readText()) ?: emptyMap()) }

    @Suppress("UNCHECKED_CAST")
    private fun obj(m: Map<String, Any?>?, key: String) = m?.get(key) as? Map<String, Any?>

    private fun str(m: Map<String, Any?>?, key: String) = m?.get(key) as? String

    private fun colours(m: Map<String, Any?>?) = obj(obj(m, "skin"), "colors")

    private fun rgb(hex: String): Triple<Int, Int, Int> {
        val h = hex.removePrefix("#").takeLast(6)
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

    // ---------------------------------------------------------------- the nine exist

    @Test
    fun allNineImageThemesArePresent() {
        assertEquals(expected.map { "$it.json" }, files().map { it.name })
    }

    @Test
    fun everyThemeParsesWithAUniqueNamespacedId() {
        val ids = themes().map { str(it.second, "id") }
        ids.forEach { assertNotNull(it); assertTrue(it!!.startsWith("builtin.art.")) }
        assertEquals("ids must not repeat", ids.size, ids.toSet().size)
    }

    // ---------------------------------------------------------------- the artwork is real

    @Test
    fun everyThemeReferencesItsOwnWallpaper() {
        // a theme without its actual wallpaper is incomplete
        themes().forEach { (file, o) ->
            val slug = file.removeSuffix(".json")
            assertEquals("$file must point at anime/$slug.webp", "anime/$slug.webp", str(o, "backgroundImage"))
        }
    }

    @Test
    fun everyWallpaperAssetExistsAndIsARealImage() {
        themes().forEach { (file, o) ->
            val ref = str(o, "backgroundImage")!!
            val asset = File(artDir, ref.removePrefix("anime/"))
            assertTrue("$file: missing asset ${asset.path}", asset.isFile)
            assertTrue("$file: wallpaper is suspiciously small", asset.length() > 10_000)
            // RIFF....WEBP magic: proves it is a decodable WebP, not a renamed placeholder
            val head = asset.inputStream().use { it.readNBytes(12) }
            assertEquals("RIFF", String(head, 0, 4, Charsets.US_ASCII))
            assertEquals("WEBP", String(head, 8, 4, Charsets.US_ASCII))
        }
    }

    @Test
    fun noThemeUsesARemoteOrAbsoluteWallpaper() {
        themes().forEach { (file, o) ->
            val ref = str(o, "backgroundImage").orEmpty()
            assertTrue("$file uses a URL", !ref.startsWith("http"))
            assertTrue("$file uses an absolute path", !ref.startsWith("/"))
            assertTrue("$file escapes its directory", !ref.contains(".."))
        }
    }

    @Test
    fun theArtworkStaysVisibleThroughTheKeycaps() {
        // keycaps are translucent, otherwise the wallpaper would be pointless
        themes().forEach { (file, o) ->
            val keys = obj(obj(o, "skin"), "keys")
            val any = obj(keys, "*")
            val bg = str(any, "background").orEmpty()
            assertTrue("$file keycap background must carry alpha", bg.length == 9 && bg.startsWith("#"))
            val alpha = bg.substring(1, 3).toInt(16)
            assertTrue("$file keycap alpha $alpha is too opaque for a wallpaper theme", alpha in 0x80..0xDD)
        }
    }

    // ---------------------------------------------------------------- readability

    @Test
    fun everyThemeIsReadable() {
        themes().forEach { (file, o) ->
            val c = colours(o)
            listOf("key" to "keyText", "func" to "funcText", "accent" to "accentText").forEach { (bg, fg) ->
                val ratio = contrast(str(c, bg)!!, str(c, fg)!!)
                assertTrue("$file $bg/$fg contrast is %.2f:1".format(ratio), ratio >= 4.5)
            }
        }
    }

    // ---------------------------------------------------------------- identity

    @Test
    fun eachThemeHasItsOwnAccent() {
        // the brief forbids one palette applied to nine images
        val accents = themes().map { str(colours(it.second), "accent")!!.lowercase() }.toSet()
        assertEquals("nine themes must have nine accents", 9, accents.size)
    }

    @Test
    fun eachThemeHasItsOwnKeycapColour() {
        val keys = themes().map { str(colours(it.second), "key")!!.lowercase() }.toSet()
        assertEquals("nine themes must have nine keycap colours", 9, keys.size)
    }

    @Test
    fun themesAreNotAllDarkPurpleOrBlue() {
        // a guard against the lazy default the brief explicitly calls out
        val accents = themes().map { str(colours(it.second), "accent")!! }
        val warm = accents.count { a ->
            val (r, _, b) = rgb(a)
            r > b + 24
        }
        assertTrue("expected several warm accents, found $warm", warm >= 3)
    }

    @Test
    fun everyThemeDescribesWhereItsPaletteCameFrom() {
        themes().forEach { (file, o) ->
            assertTrue("$file has no description", str(o, "description").orEmpty().length > 20)
        }
    }
}
