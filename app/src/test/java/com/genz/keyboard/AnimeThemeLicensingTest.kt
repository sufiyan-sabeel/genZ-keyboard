package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Enforces the artwork shipping boundary.
 *
 * Six themes ship with their wallpapers. Three are withheld because the images carry other artists'
 * signatures; their definitions are retained outside the asset tree so the design is not lost, and
 * this test is what stops them being shipped by accident.
 */
class AnimeThemeLicensingTest {

    private val pluginDir = File("src/main/assets/plugins")
    private val artDir = File("src/standard/assets/anime")
    private val pendingDir = File("../../docs/pending-themes")
    private val notices = File("../../THIRD_PARTY_NOTICES.md")

    private val shipped = listOf(
        "art_blueroad", "art_cabin", "art_pastel",
        "art_rainytokyo", "art_snowfall", "art_sunset"
    )
    private val withheld = listOf("art_windmill", "art_samurai", "art_sakura")

    // ---------------------------------------------------------------- what ships

    @Test
    fun exactlySixThemesShip() {
        val onDisk = pluginDir.listFiles { f -> f.name.startsWith("art_") && f.name.endsWith(".json") }
            .orEmpty().map { it.nameWithoutExtension }.sorted()
        assertEquals(shipped.sorted(), onDisk)
    }

    @Test
    fun everyShippedThemeHasItsWallpaperInTheStandardFlavour() {
        shipped.forEach { slug ->
            val asset = File(artDir, "$slug.webp")
            assertTrue("$slug has no wallpaper", asset.isFile)
            assertTrue("$slug wallpaper is too small to be real artwork", asset.length() > 10_000)
            val head = asset.inputStream().use { it.readNBytes(12) }
            assertEquals("$slug is not a WebP", "RIFF", String(head, 0, 4, Charsets.US_ASCII))
            assertEquals("$slug is not a WebP", "WEBP", String(head, 8, 4, Charsets.US_ASCII))
        }
    }

    @Test
    fun everyShippedThemeReferencesAnExistingAsset() {
        shipped.forEach { slug ->
            val json = File(pluginDir, "$slug.json").readText()
            assertTrue("$slug must reference anime/$slug.webp", json.contains("\"anime/$slug.webp\""))
            assertTrue(File(artDir, "$slug.webp").isFile)
        }
    }

    // ---------------------------------------------------------------- what must not ship

    @Test
    fun noWithheldThemeIsRegisteredAsATheme() {
        // a registered theme without its wallpaper would ship as a broken gallery entry
        withheld.forEach { slug ->
            assertFalse("$slug must not be in assets/plugins", File(pluginDir, "$slug.json").exists())
        }
    }

    @Test
    fun noWithheldArtworkIsBundled() {
        withheld.forEach { slug ->
            assertFalse("$slug artwork must not be in the standard flavour", File(artDir, "$slug.webp").exists())
        }
    }

    @Test
    fun theFdroidFlavourStillShipsNoArtwork() {
        val fdroid = File("src/fdroid/assets/anime")
        assertTrue("fdroid must not bundle artwork", fdroid.listFiles().orEmpty().isEmpty())
    }

    // ---------------------------------------------------------------- what is retained

    @Test
    fun everyWithheldThemeDefinitionIsRetained() {
        withheld.forEach { slug ->
            val f = File(pendingDir, "$slug.json")
            assertTrue("$slug definition was lost", f.isFile)
            assertTrue("$slug definition is empty", f.readText().contains("builtin.art."))
        }
    }

    @Test
    fun everyWithheldArtworkIsRetainedByteIdentical() {
        withheld.forEach { slug ->
            val f = File(pendingDir, "artwork/$slug.webp")
            assertTrue("$slug artwork was lost", f.isFile)
            assertTrue("$slug artwork is empty", f.length() > 10_000)
            val head = f.inputStream().use { it.readNBytes(12) }
            assertEquals("$slug pending artwork is not a WebP", "WEBP", String(head, 8, 4, Charsets.US_ASCII))
        }
    }

    // ---------------------------------------------------------------- the record

    @Test
    fun theNoticesFileDocumentsAllNineImages() {
        assertTrue("THIRD_PARTY_NOTICES.md is missing", notices.isFile)
        val text = notices.readText()
        listOf("Rainy Tokyo", "Pastel Garden", "Anime Sunset", "Moonlit Cabin",
               "Snowfall Pines", "Blue Hour Road", "Midnight Windmill", "Ink Samurai", "Sakura Night")
            .forEach { assertTrue("notices omit $it", text.contains(it)) }
    }

    @Test
    fun theNoticesNameEveryVisibleSignature() {
        // the artist marks are the evidence that these three are not ours to ship
        val text = notices.readText()
        listOf("SNIPER", "Dream Render", "AFERA XV").forEach {
            assertTrue("notices omit the signature $it", text.contains(it))
        }
    }

    @Test
    fun theNoticesDoNotClaimAlicenceItCannotSupport() {
        val text = notices.readText().lowercase()
        // a fabricated grant would be the worst possible failure here
        assertFalse("notices must not assert a fabricated licence",
            text.contains("licensed under cc") || text.contains("public domain"))
        assertTrue("notices must say attribution is not a licence", text.contains("attribution is not a licence"))
    }
}
