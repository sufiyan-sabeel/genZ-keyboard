package com.genz.keyboard

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertHasNoClickAction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for the gallery cards.
 *
 * These run on a device or emulator. They exist because the unit tests can prove the data is right but
 * not that anything actually renders — which is the gap this milestone is closing.
 */
class ThemeGalleryUiTest {

    @get:Rule val compose = createComposeRule()

    private fun hex(s: String) = 0xFF000000.toInt() or s.removePrefix("#").toInt(16)

    private val preset = PresetEntry(
        id = "builtin.art.sunset",
        name = "Anime Sunset",
        sourceFile = "art_sunset.json",
        wallpaper = null,
        colours = mapOf(
            "background" to hex("#886749"), "key" to hex("#2A211C"), "keyText" to hex("#F4F1EA"),
            "func" to hex("#3A2A22"), "funcText" to hex("#F4F1EA"),
            "accent" to hex("#D9611F"), "accentText" to hex("#12130F")
        ),
        description = "Cloud orange drives the accent."
    )

    @Test
    fun aThemeCardShowsItsNameAndDescription() {
        compose.setContent {
            ThemeGalleryCard(preset, selected = false, dark = true, onUse = {})
        }
        compose.onNodeWithText("Anime Sunset").assertIsDisplayed()
        compose.onNodeWithText("Cloud orange drives the accent.").assertIsDisplayed()
    }

    @Test
    fun aSelectedCardSaysSoInWords() {
        // colour alone is not an accessible selected-state signal
        compose.setContent {
            ThemeGalleryCard(preset, selected = true, dark = true, onUse = {})
        }
        compose.onNodeWithText("Selected", substring = true).assertIsDisplayed()
    }

    @Test
    fun tappingUseAppliesTheTheme() {
        var used = false
        compose.setContent {
            ThemeGalleryCard(preset, selected = false, dark = true, onUse = { used = true })
        }
        compose.onNodeWithText("Use").performClick()
        assertEquals(true, used)
    }

    @Test
    fun aReservedSlotHasNoClickAction() {
        // the whole point of a slot: it must be inert
        compose.setContent {
            Column {
                ReservedSlotCard(ThemePresetIndex.RESERVED_SLOTS.first(), dark = true)
            }
        }
        compose.onNodeWithContentDescription("Reserved slot. Artwork coming soon. Not available.")
            .assertHasNoClickAction()
    }

    @Test
    fun aReservedSlotAnnouncesThatArtworkIsComing() {
        compose.setContent { ReservedSlotCard(ThemePresetIndex.RESERVED_SLOTS.first(), dark = true) }
        compose.onNodeWithText("Artwork coming soon").assertIsDisplayed()
    }

    @Test
    fun cardsRenderInBothLightAndDark() {
        compose.setContent {
            Column {
                ThemeGalleryCard(preset, selected = false, dark = true, onUse = {})
                ThemeGalleryCard(preset, selected = false, dark = false, onUse = {})
            }
        }
        compose.onAllNodesWithTextCount("Anime Sunset", 2)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(
        text: String,
        expected: Int
    ) {
        assertEquals(expected, onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size)
    }
}
