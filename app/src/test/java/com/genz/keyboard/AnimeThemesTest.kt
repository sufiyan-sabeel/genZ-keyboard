package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the anime theme collection.
 *
 * The themes are data (JSON skin files in `assets/plugins`), not code, so nothing in the compiler
 * checks them: a missing file, a duplicated id or a typo in a colour would only show up as a
 * broken theme on a device. These checks run on the JVM instead.
 *
 * They are deliberately structural (text matching) rather than a full parse: `org.json` is an
 * Android class and is only stubbed in unit tests, so parsing here would test the stub.
 */
class AnimeThemesTest {

    private val dir = File("src/main/assets/plugins")

    private val expected = listOf(
        "anime_crimson.json", "anime_ego.json", "anime_flame.json", "anime_ink.json",
        "anime_manga.json", "anime_rose.json", "anime_sage.json", "anime_sky.json",
        "anime_split.json", "anime_violet.json"
    )

    private fun themeFiles() = dir.listFiles { f -> f.name.startsWith("anime_") && f.name.endsWith(".json") }
        .orEmpty().sortedBy { it.name }

    @Test
    fun everyAnimeThemeShips() {
        val names = themeFiles().map { it.name }
        assertEquals("anime theme files on disk", expected.sorted(), names)
    }

    @Test
    fun everyThemeDeclaresTheReservedId() {
        themeFiles().forEach { f ->
            val text = f.readText()
            val slug = f.nameWithoutExtension.removePrefix("anime_")
            assertTrue(
                "${f.name} must declare id builtin.anime.$slug",
                text.contains("\"id\": \"$ANIME_SKIN_PREFIX$slug\"")
            )
        }
    }

    @Test
    fun idsAreUnique() {
        val ids = themeFiles().mapNotNull {
            Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(it.readText())?.groupValues?.get(1)
        }
        assertEquals("every theme needs an id", expected.size, ids.size)
        assertEquals("ids must not repeat", ids.size, ids.toSet().size)
    }

    @Test
    fun everyThemeHasTheSectionsTheRendererNeeds() {
        val required = listOf("\"keyboard\"", "\"colors\"", "\"keys\"", "\"popup\"", "\"chips\"", "\"buttons\"", "\"panel\"")
        themeFiles().forEach { f ->
            val text = f.readText()
            required.forEach { key ->
                assertTrue("${f.name} is missing $key", text.contains(key))
            }
            listOf("\"accent\"", "\"key\"", "\"keyText\"", "\"func\"", "\"funcText\"", "\"accentText\"").forEach { key ->
                assertTrue("${f.name} is missing colour $key", text.contains(key))
            }
        }
    }

    @Test
    fun coloursAreWellFormed() {
        val valid = Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")
        themeFiles().forEach { f ->
            val bad = Regex("\"#[^\"]*\"").findAll(f.readText())
                .map { it.value.trim('"') }
                .filter { !valid.matches(it) }
                .toList()
            assertTrue("${f.name} has a malformed colour: $bad", bad.isEmpty())
        }
    }

    @Test
    fun everyThemeDeclaresItsArtworkPath() {
        themeFiles().forEach { f ->
            val slug = f.nameWithoutExtension.removePrefix("anime_")
            assertTrue(
                "${f.name} must point at anime/$slug.webp",
                f.readText().contains("\"backgroundImage\": \"anime/$slug.webp\"")
            )
        }
    }

    @Test
    fun everyThemeShipsItsArtwork() {
        val dir = File("src/standard/assets/anime")
        themeFiles().forEach { f ->
            val slug = f.nameWithoutExtension.removePrefix("anime_")
            val art = File(dir, "$slug.webp")
            assertTrue("${f.name} has no artwork at assets/anime/$slug.webp", art.isFile)
            assertTrue("${f.name} artwork is empty", art.length() > 10_000)
        }
    }

    @Test
    fun accentColourIsOpaque() {
        // A translucent accent would wash out the Enter key and the pressed-key fill.
        val accent = Regex("\"accent\"\\s*:\\s*\"(#[0-9A-Fa-f]{6})\"")
        themeFiles().forEach { f ->
            assertTrue("${f.name} needs an opaque 6-digit accent", accent.containsMatchIn(f.readText()))
        }
    }
}
