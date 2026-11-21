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
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

    private fun preset(id: String, name: String, accent: String) = PresetEntry(
        id = id,
        name = name,
        sourceFile = "art_test.json",
        wallpaper = null,
        colours = mapOf(
            "background" to hex("#1E1714"), "key" to hex("#2A211C"), "keyText" to hex("#F4F1EA"),
            "func" to hex("#3A2A22"), "funcText" to hex("#F4F1EA"),
            "accent" to hex(accent), "accentText" to hex("#12130F")
        ),
        description = "Artwork-derived palette for $name."
    )

    private val themes = listOf(
        preset("builtin.art.sunset", "Anime Sunset", "#D9611F"),
        preset("builtin.art.blueroad", "Blue Hour Road", "#E8A94A"),
        preset("builtin.art.pastel", "Pastel Garden", "#C98A80")
    )

    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        assertTrue("captured image was empty", bitmap.width > 0 && bitmap.height > 0)

        // Assert rather than return. The earlier version used `?: return`, which meant a null
        // external-files dir skipped the assertion entirely and the test passed having written
        // nothing — which is exactly how the artifact ended up empty.
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        assertNotNull("external files dir is unavailable; screenshots cannot be extracted", dir)

        val out = File(dir, "$name.png")
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("screenshot $name was not written", out.isFile)
        assertTrue("screenshot $name is empty", out.length() > 0)
        // record the absolute path so CI can pull exactly this location
        println("SCREENSHOT_PATH=${out.absolutePath}")
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
        compose.waitForIdle()
        capture("gallery-dark")
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
        compose.waitForIdle()
        capture("gallery-light")
    }
}
