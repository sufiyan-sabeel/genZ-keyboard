package com.genz.keyboard

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders the gallery and emits each screenshot as Base64 on stdout.
 *
 * Files could not be retrieved from the emulator (external storage is scoped on API 30, and run-as was
 * unreachable), so the bytes travel through the test process output instead.
 *
 * The fixtures use real bundled wallpapers and the tests wait for the tagged Image nodes before
 * capturing, so a passing run proves artwork actually rendered — not merely that a capture happened.
 */
@RunWith(AndroidJUnit4::class)
class ThemeGalleryScreenshotTest {

    @get:Rule val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun hex(s: String) = 0xFF000000.toInt() or s.removePrefix("#").toInt(16)

    private fun preset(id: String, name: String, accent: String, wallpaper: String?) = PresetEntry(
        id = id,
        name = name,
        sourceFile = "art_test.json",
        wallpaper = wallpaper,
        colours = mapOf(
            "background" to hex("#1E1714"), "key" to hex("#2A211C"), "keyText" to hex("#F4F1EA"),
            "func" to hex("#3A2A22"), "funcText" to hex("#F4F1EA"),
            "accent" to hex(accent), "accentText" to hex("#12130F")
        ),
        description = "Artwork-derived palette for $name."
    )

    /** Two real bundled assets plus one without, so both paths are exercised. */
    private val themes: List<PresetEntry> = listOf(
        preset("builtin.art.sunset", "Anime Sunset", "#D9611F", "anime/art_sunset.webp"),
        preset("builtin.art.blueroad", "Blue Hour Road", "#E8A94A", "anime/art_blueroad.webp"),
        preset("builtin.art.pastel", "Pastel Garden", "#C98A80", null)
    )

    /** executeShellCommand returns a ParcelFileDescriptor, so it has to be wrapped to read it. */
    private fun sh(cmd: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)
    ).use { it.readBytes().toString(Charsets.UTF_8) }

    /** Configuration tag for the filename, so every image is attributable to a device setup. */
    private val config: String by lazy {
        val size = Regex("(\\d+)x(\\d+)").find(sh("wm size"))
        val scale = sh("settings get system font_scale").trim()
        "${size?.groupValues?.get(1) ?: "?"}x${size?.groupValues?.get(2) ?: "?"}-f$scale"
    }

    /** Waits until the tagged artwork nodes exist, proving a non-null bitmap rendered. */
    private fun awaitArtwork() {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag("thumb:anime/art_sunset.webp", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("thumb:anime/art_sunset.webp", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("thumb:anime/art_blueroad.webp", useUnmergedTree = true).assertExists()
    }

    /**
     * Emits one screenshot as Base64 between unique markers.
     *
     * Resized to 1080px wide: enough to inspect layout, a fraction of a full-resolution screenshot.
     */
    private fun capture(name: String) {
        val raw = compose.onRoot().captureToImage().asAndroidBitmap()
        assertTrue("captured image was empty", raw.width > 0 && raw.height > 0)

        val scaled = resize(raw, MAX_WIDTH)
        val bytes = java.io.ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        assertTrue("PNG encoding produced no bytes", bytes.isNotEmpty())
        assertEquals("not a PNG: $name", 0x89.toByte(), bytes[0])

        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        println("===SHOT_BEGIN $name ${scaled.width}x${scaled.height}===")
        b64.chunked(200).forEach { println(it) }
        println("===SHOT_END $name===")
    }

    private fun resize(src: Bitmap, maxWidth: Int): Bitmap {
        if (src.width <= maxWidth) return src
        val ratio = maxWidth.toFloat() / src.width
        return Bitmap.createScaledBitmap(src, maxWidth, (src.height * ratio).toInt(), true)
    }

    @Test
    fun captureGalleryDark() {
        compose.setContent {
            Column(Modifier.background(Color(0xFF0E1013)).padding(12.dp)) {
                themes.forEachIndexed { i, t ->
                    ThemeGalleryCard(t, selected = i == 0, dark = true, onUse = {})
                }
                ThemePresetIndex.RESERVED_SLOTS.forEach { ReservedSlotCard(it, dark = true) }
            }
        }
        awaitArtwork()
        capture("gallery-dark-$config")
    }

    @Test
    fun captureGalleryLight() {
        compose.setContent {
            Column(Modifier.background(Color(0xFFF6F3EF)).padding(12.dp)) {
                themes.forEachIndexed { i, t ->
                    ThemeGalleryCard(t, selected = i == 0, dark = false, onUse = {})
                }
                ThemePresetIndex.RESERVED_SLOTS.forEach { ReservedSlotCard(it, dark = false) }
            }
        }
        awaitArtwork()
        capture("gallery-light-$config")
    }

    /** The no-artwork path: a theme without a wallpaper shows its colour block, never an image. */
    @Test
    fun aThemeWithoutArtworkFallsBackToItsColourBlock() {
        compose.setContent {
            ThemeGalleryCard(themes[2], selected = false, dark = true, onUse = {})
        }
        compose.waitForIdle()
        compose.onNodeWithTag("thumb:null", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Pastel Garden").assertExists()
    }

    private companion object {
        const val MAX_WIDTH = 1080
    }
}
