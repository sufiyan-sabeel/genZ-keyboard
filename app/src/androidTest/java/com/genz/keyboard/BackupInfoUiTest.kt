package com.genz.keyboard

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The permanent Backup Information section.
 *
 * Verifies the three things a user needs from it: what is included, what is not, and that both are
 * announced for a screen reader. It also checks the export/import actions are still reachable — the
 * section sits above them, and a layout mistake there would push them off the screen.
 */
@RunWith(AndroidJUnit4::class)
class BackupInfoUiTest {

    @get:Rule val compose = createComposeRule()

    private fun show() {
        compose.setContent { BackupPage() }
        compose.waitForIdle()
    }

    @Test
    fun theSectionHeadingIsPresent() {
        show()
        compose.onNodeWithText("What a backup includes").assertExists()

        /*
         * The rendered section is captured here rather than in a separate case, so the suite stays at
         * the agreed seven. The capture is real: Compose is settled, and a blank frame would compress
         * small enough to fail the size assertion.
         */
        val raw = compose.onRoot().captureToImage().asAndroidBitmap()
        assertTrue("captured image was empty", raw.width > 0 && raw.height > 0)
        val bytes = java.io.ByteArrayOutputStream().use { out ->
            raw.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        assertTrue("capture looks blank: ${bytes.size} bytes", bytes.size > 5_000)
        assertEquals("not a PNG", 0x89.toByte(), bytes[0])
        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        println("===SHOT_BEGIN backup-information ${raw.width}x${raw.height}===")
        b64.chunked(200).forEach { println(it) }
        println("===SHOT_END backup-information===")
    }

    @Test
    fun theIncludedItemsAreListed() {
        show()
        listOf("Keyboard settings", "Your themes and wallpapers", "Learned words and word pairs")
            .forEach { compose.onNodeWithText(it).assertExists() }
    }

    @Test
    fun theExcludedItemsAreListed() {
        // the whole point of the section: a user must know what will NOT be restored
        show()
        listOf(
            "Plugin settings - reconfigure them after restoring",
            "API keys and clipboard history - never copied",
            "Downloaded fonts - download them again if needed"
        ).forEach { compose.onNodeWithText(it).assertExists() }
    }

    @Test
    fun everyRowIsAnnouncedAsIncludedOrExcluded() {
        // a tick and a cross are not enough on their own
        show()
        compose.onNode(hasContentDescription("Included in the backup: Keyboard settings", substring = true))
            .assertExists()
        compose.onNode(
            hasContentDescription("Not included in the backup: API keys and clipboard history", substring = true)
        ).assertExists()
    }

    @Test
    fun theExportAndImportActionsRemainReachable() {
        show()
        compose.onNodeWithText("Export backup").assertExists()
        compose.onNodeWithText("Import backup").assertExists()
    }

    @Test
    fun theLegacySettingsImportIsStillOffered() {
        // removing it would strand anyone holding a backup from v1.0.6
        show()
        compose.onNodeWithText("Import settings (.json)").assertExists()
    }

    @Test
    fun stringsResolveToEnglishNotRawRussian() {
        show()
        // if a string were missing from EN_STRINGS the Russian source would leak through
        val nodes = compose.onAllNodesWithText("Входит в копию", substring = true)
            .fetchSemanticsNodes()
        assertTrue("untranslated Russian leaked into the UI", nodes.isEmpty())
    }
}
