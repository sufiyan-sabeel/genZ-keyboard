package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the structured property encoders.
 *
 * The important rule: an unrecognised value decodes to null, never to a plausible default. A control
 * that shows "glass" for a value the renderer will ignore is worse than showing nothing.
 */
class ThemeStructuredTest {

    @Test
    fun everyListMirrorsTheRendererEnums() {
        assertEquals(listOf("solid", "glass", "outline", "neumorph", "bevel", "none"), ThemeStructured.MATERIALS)
        assertEquals(listOf("rounded", "pill", "cut", "squircle", "wavy"), ThemeStructured.SHAPES)
        assertEquals(listOf("scale", "jelly", "bounce", "sink", "none"), ThemeStructured.PRESSES)
        assertEquals(listOf("solid", "dots", "dashed"), ThemeStructured.BORDERS)
        assertEquals(listOf("none", "dots"), ThemeStructured.LABELS)
    }

    @Test
    fun aKnownValueRoundTrips() {
        val encoded = ThemeStructured.encodeEnum("material", "glass")!!
        assertEquals("glass", encoded)
        assertEquals("glass", ThemeStructured.decodeEnum("material", encoded))
    }

    @Test
    fun encodingIsCaseInsensitiveButOutputIsCanonical() {
        assertEquals("bevel", ThemeStructured.encodeEnum("material", "  BEVEL "))
    }

    @Test
    fun anUnknownValueIsRejectedRatherThanStored() {
        assertNull(ThemeStructured.encodeEnum("material", "holographic"))
        assertNull(ThemeStructured.encodeEnum("shape", "hexagon"))
        assertNull(ThemeStructured.encodeEnum("press", "explode"))
    }

    @Test
    fun anUnknownKeyIsRejected() {
        assertNull(ThemeStructured.encodeEnum("notAProperty", "solid"))
        assertNull(ThemeStructured.decodeEnum("notAProperty", "solid"))
        assertTrue(ThemeStructured.options("notAProperty").isEmpty())
    }

    @Test
    fun aValueTheRendererWouldIgnoreDecodesToNull() {
        assertNull(ThemeStructured.decodeEnum("material", "holographic"))
        assertNull(ThemeStructured.decodeEnum("material", null))
        assertNull(ThemeStructured.decodeEnum("material", ""))
    }

    @Test
    fun aSolidFillIsWrittenAsABareColour() {
        val encoded = ThemeStructured.encodeSolid(0xFF1E1E1E.toInt())
        assertTrue(ThemeStructured.isSolid(encoded))
        assertFalse(ThemeStructured.isGradient(encoded))
    }

    @Test
    fun aGradientCarriesItsColoursAndAngle() {
        val g = ThemeStructured.encodeGradient(listOf(0xFF111111.toInt(), 0xFF222222.toInt()), 45f, false)!!
        assertTrue(ThemeStructured.isGradient(g))
        assertTrue(g.contains("45"))
        assertTrue(g.contains("\"colors\""))
        assertFalse(g.contains("radial"))
    }

    @Test
    fun aRadialGradientIsMarkedAsSuch() {
        val g = ThemeStructured.encodeGradient(listOf(0xFF111111.toInt(), 0xFF222222.toInt()), 0f, true)!!
        assertTrue(g.contains("\"type\":\"radial\""))
    }

    @Test
    fun aGradientNeedsAtLeastTwoColours() {
        assertNull(ThemeStructured.encodeGradient(listOf(0xFF111111.toInt()), 0f, false))
        assertNull(ThemeStructured.encodeGradient(emptyList(), 0f, false))
    }

    @Test
    fun theAngleIsClampedToAFullTurn() {
        val g = ThemeStructured.encodeGradient(listOf(0xFF111111.toInt(), 0xFF222222.toInt()), 400f, false)!!
        assertTrue(g.contains("360"))
    }

    @Test
    fun everyEncodedValueSurvivesTheThemeCodec() {
        val d = ThemeDoc(
            "t1", "T", "builtin.preset.windmill",
            mapOf(
                "material" to ThemeStructured.encodeEnum("material", "glass")!!,
                "shape" to ThemeStructured.encodeEnum("shape", "pill")!!
            )
        )
        val decoded = ThemeCodec.decode(ThemeCodec.encode(d))
        assertTrue(decoded is ThemeValidation.Ok)
        assertEquals("glass", (decoded as ThemeValidation.Ok).doc.overrides["material"])
    }

    @Test
    fun aGradientSurvivesTheCodec() {
        // the format originally accepted only hex and numbers, so a gradient could not be saved at all
        val g = ThemeStructured.encodeGradient(
            listOf(0xFF111111.toInt(), 0xFF222222.toInt()), 90f, false
        )!!
        val d = ThemeDoc("t1", "T", "builtin.preset.windmill", mapOf("background" to g))
        assertTrue(ThemeCodec.decode(ThemeCodec.encode(d)) is ThemeValidation.Ok)
    }

    @Test
    fun aMalformedGradientIsRejectedByTheCodec() {
        // parsed, not pattern-matched: one bad colour must invalidate the whole value
        val bad = """{"colors":["#111111","not-a-colour"],"angle":90}"""
        val d = ThemeDoc("t1", "T", "builtin.preset.windmill", mapOf("background" to bad))
        assertTrue(ThemeCodec.decode(ThemeCodec.encode(d)) is ThemeValidation.Invalid)
    }

    @Test
    fun anEnumTokenIsOnlyAcceptedForItsOwnProperty() {
        val d = ThemeDoc("t1", "T", "builtin.preset.windmill", mapOf("shape" to "glass"))
        assertTrue(ThemeCodec.decode(ThemeCodec.encode(d)) is ThemeValidation.Invalid)
    }

    @Test
    fun anUnknownEnumTokenIsRejected() {
        val d = ThemeDoc("t1", "T", "builtin.preset.windmill", mapOf("material" to "holographic"))
        assertTrue(ThemeCodec.decode(ThemeCodec.encode(d)) is ThemeValidation.Invalid)
    }
}
