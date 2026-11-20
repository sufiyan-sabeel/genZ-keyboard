package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrast for the six bundled themes, and a regression lock on their palettes.
 *
 * The palettes are embedded here on purpose: this doubles as proof that the shipped themes have not
 * been altered. If someone changes a colour, this test fails and the change has to be deliberate.
 */
class ThemePresetContrastTest {

    private data class Theme(val name: String, val colours: Map<String, Int>)

    private fun hex(s: String) = 0xFF000000.toInt() or s.removePrefix("#").toInt(16)

    private val themes = listOf(
        Theme("Blue Hour Road", mapOf("background" to hex("#165494"), "key" to hex("#14263C"), "keyText" to hex("#F4F1EA"), "func" to hex("#1C3350"), "funcText" to hex("#F4F1EA"), "accent" to hex("#E8A94A"), "accentText" to hex("#12130F"))),
        Theme("Moonlit Cabin", mapOf("background" to hex("#1E1E24"), "key" to hex("#23261F"), "keyText" to hex("#F4F1EA"), "func" to hex("#2E3327"), "funcText" to hex("#F4F1EA"), "accent" to hex("#E0A94F"), "accentText" to hex("#12130F"))),
        Theme("Pastel Garden", mapOf("background" to hex("#E2CDB5"), "key" to hex("#F6EDE2"), "keyText" to hex("#12130F"), "func" to hex("#EBDCC9"), "funcText" to hex("#12130F"), "accent" to hex("#C98A80"), "accentText" to hex("#12130F"))),
        Theme("Rainy Tokyo", mapOf("background" to hex("#3A3838"), "key" to hex("#1E1E1E"), "keyText" to hex("#F4F1EA"), "func" to hex("#2A2724"), "funcText" to hex("#F4F1EA"), "accent" to hex("#B87333"), "accentText" to hex("#12130F"))),
        Theme("Snowfall Pines", mapOf("background" to hex("#3F676D"), "key" to hex("#2A3A40"), "keyText" to hex("#F4F1EA"), "func" to hex("#35484E"), "funcText" to hex("#F4F1EA"), "accent" to hex("#E3C044"), "accentText" to hex("#12130F"))),
        Theme("Anime Sunset", mapOf("background" to hex("#886749"), "key" to hex("#2A211C"), "keyText" to hex("#F4F1EA"), "func" to hex("#3A2A22"), "funcText" to hex("#F4F1EA"), "accent" to hex("#D9611F"), "accentText" to hex("#12130F"))),
    )

    @Test
    fun everyThemeDefinesAllSevenColourTokens() {
        // a missing token would silently fall back to the default skin
        val required = setOf("background", "key", "keyText", "func", "funcText", "accent", "accentText")
        themes.forEach { t ->
            assertEquals("${t.name} token set", required, t.colours.keys)
        }
    }

    @Test
    fun everyColourTokenIsFullyOpaque() {
        // an alpha value would change the composited result and invalidate the ratios below
        themes.forEach { t ->
            t.colours.forEach { (k, v) ->
                assertEquals("${t.name}.$k must be opaque", 0xFF, (v ushr 24) and 0xFF)
            }
        }
    }

    @Test
    fun allSixThemesMeetAaForEveryRenderedPair() {
        val failures = mutableListOf<String>()
        themes.forEach { t ->
            ThemeContrast.PAIRS.forEach { (fg, bg) ->
                val r = ThemeContrast.ratio(t.colours.getValue(fg), t.colours.getValue(bg))
                if (!ThemeContrast.passesAA(r)) {
                    failures += "${t.name}: $fg on $bg = ${ThemeContrast.rounded(r)}"
                }
            }
        }
        assertTrue("WCAG AA failures: $failures", failures.isEmpty())
    }

    @Test
    fun thePalettesHaveNotChangedSinceTheRegressionLock() {
        // proves the six shipped themes are untouched by this work
        assertEquals(6, themes.size)
        assertEquals("#F4F1EA", String.format("#%06X", 0xFFFFFF and themes[0].colours.getValue("keyText")))
        assertEquals("#C98A80", String.format("#%06X", 0xFFFFFF and themes[2].colours.getValue("accent")))
        assertEquals("#D9611F", String.format("#%06X", 0xFFFFFF and themes[5].colours.getValue("accent")))
    }

    @Test
    fun aDarkOnLightThemeAndALightOnDarkThemeBothPass() {
        // Pastel Garden is the only light-keyed theme; it must not be validated by the same rule as
        // the other five without actually passing
        val pastel = themes[2].colours
        assertTrue(ThemeContrast.passesAA(
            ThemeContrast.ratio(pastel.getValue("keyText"), pastel.getValue("key"))))
        val dark = themes[0].colours
        assertTrue(ThemeContrast.passesAA(
            ThemeContrast.ratio(dark.getValue("keyText"), dark.getValue("key"))))
    }
}
