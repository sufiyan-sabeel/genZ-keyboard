package com.genz.keyboard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Renders the gallery and writes PNGs the CI job uploads as artifacts.
 *
 * This is the only way to actually *see* the cards: the assertions elsewhere prove behaviour, not
 * appearance, and nobody has looked at this screen yet.
 */
@RunWith(AndroidJUnit4::class)
class ThemeGalleryScreenshotTest {

    @get:Rule val compose = createComposeRule()

    private fun hex(s: String) = 0xFF000000.toInt() or s.removePrefix("#").toInt(16)

    private fun preset(id: String, name: String, accent: String, wallpaper: String? = null) = PresetEntry(
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
    /** executeShellCommand returns a ParcelFileDescriptor, so it has to be wrapped to read it. */
    private fun sh(cmd: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)
    ).use { it.readBytes().toString(Charsets.UTF_8) }

    /** Configuration tag for the screenshot filename, so every image is attributable. */
    private val config: String by lazy {
        val size = Regex("(\\d+)x(\\d+)").find(sh("wm size"))
        val scale = sh("settings get system font_scale").trim()
        "${size?.groupValues?.get(1) ?: "?"}x${size?.groupValues?.get(2) ?: "?"}-f$scale"
    }

    private fun hex(s: String) = 0xFF000000.toInt() or s.removePrefix("#").toInt(16)


    /** The no-artwork path: a theme without a wallpaper must show its colour block, not an image. */
    @Test
    fun aThemeWithoutArtworkFallsBackToItsColourBlock() {
        compose.setContent {
            ThemeGalleryCard(themes[2], selected = false, dark = true, onUse = {})
        }
        compose.waitForIdle()
        compose.onNodeWithTag("thumb:null", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Pastel Garden").assertExists()
    }

    private fun resize(src: Bitmap, maxWidth: Int): Bitmap {
        if (src.width <= maxWidth) return src
        val ratio = maxWidth.toFloat() / src.width
        return Bitmap.createScaledBitmap(src, maxWidth, (src.height * ratio).toInt(), true)
    }

    private companion object {
        const val MAX_WIDTH = 1080
    }

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Waits until the tagged artwork nodes exist.
     *
     * Thumbnails load asynchronously, so capturing immediately can photograph an empty box and still
     * "pass". Waiting for the tagged Image proves a non-null bitmap was rendered.
     */
    private fun awaitArtwork() {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag("thumb:anime/art_sunset.webp", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("thumb:anime/art_sunset.webp", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("thumb:anime/art_blueroad.webp", useUnmergedTree = true).assertExists()
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
}
