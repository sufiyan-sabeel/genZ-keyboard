package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the eight numeric editor properties.
 *
 * The ranges are not cosmetic: the renderer coerces these values, so a control offering a value the
 * renderer would silently change is a control that misrepresents the result.
 */
class ThemePropertyTest {

    private fun doc(overrides: Map<String, String> = emptyMap()) =
        ThemeDoc("t1", "T", "builtin.preset.windmill", overrides)

    @Test
    fun everyExposedPropertyHasASpec() {
        val expected = listOf(
            "radius", "borderWidth", "shadow", "shadowY",
            "fontScale", "pressScale", "corner", "windowBlur"
        )
        assertEquals(expected, ThemeProperty.NUMERIC.map { it.key })
        expected.forEach { assertNotNull("missing spec for $it", ThemeProperty.byKey(it)) }
    }

    @Test
    fun rangesAreOrderedAndContainTheirDefaults() {
        ThemeProperty.NUMERIC.forEach { spec ->
            assertTrue("${spec.key} range inverted", spec.min < spec.max)
            assertTrue("${spec.key} default outside range", spec.default in spec.min..spec.max)
        }
    }

    @Test
    fun aMissingPropertyShowsTheRendererDefault() {
        // showing the bottom of the range would misrepresent a keyboard that uses its default
        val spec = ThemeProperty.byKey("radius")!!
        assertEquals(spec.default, ThemeProperty.valueOf(doc(), spec), 0.001f)
    }

    @Test
    fun aSetPropertyShowsItsStoredValue() {
        val spec = ThemeProperty.byKey("radius")!!
        assertEquals(22f, ThemeProperty.valueOf(doc(mapOf("radius" to "22")), spec), 0.001f)
    }

    @Test
    fun anUnparsableValueFallsBackToTheDefault() {
        val spec = ThemeProperty.byKey("shadow")!!
        assertEquals(spec.default, ThemeProperty.valueOf(doc(mapOf("shadow" to "abc")), spec), 0.001f)
    }

    @Test
    fun aValueOutsideTheRangeIsClampedForDisplay() {
        val spec = ThemeProperty.byKey("borderWidth")!!
        assertEquals(spec.max, ThemeProperty.valueOf(doc(mapOf("borderWidth" to "99")), spec), 0.001f)
    }

    @Test
    fun encodingClampsToTheRendererRange() {
        val spec = ThemeProperty.byKey("borderWidth")!!
        assertEquals("6", ThemeProperty.encode(spec, 99f))
        assertEquals("0", ThemeProperty.encode(spec, -5f))
    }

    @Test
    fun encodingIsStableAndTrimsTrailingZeros() {
        val spec = ThemeProperty.byKey("radius")!!
        assertEquals("12", ThemeProperty.encode(spec, 12f))
        assertEquals("12.5", ThemeProperty.encode(spec, 12.5f))
        // the same value must always produce the same string, or undo entries would never compare equal
        assertEquals(ThemeProperty.encode(spec, 12.5f), ThemeProperty.encode(spec, 12.50f))
    }

    @Test
    fun anEncodedValueRoundTripsThroughTheDocument() {
        val spec = ThemeProperty.byKey("fontScale")!!
        val encoded = ThemeProperty.encode(spec, 1.25f)
        assertEquals(1.25f, ThemeProperty.valueOf(doc(mapOf("fontScale" to encoded)), spec), 0.001f)
    }

    @Test
    fun resetIsOnlyOfferedWhenTheValueActuallyDiffers() {
        val spec = ThemeProperty.byKey("radius")!!
        val original = doc(mapOf("radius" to "12"))
        assertFalse(ThemeProperty.isModified(original, original, spec))
        assertTrue(ThemeProperty.isModified(doc(mapOf("radius" to "20")), original, spec))
    }

    @Test
    fun anUnknownKeyHasNoSpec() {
        assertNull(ThemeProperty.byKey("notARealProperty"))
    }

    @Test
    fun everyEncodedValueSurvivesTheThemeCodec() {
        // a property the codec rejects would silently vanish on save
        val spec = ThemeProperty.byKey("shadowY")!!
        val d = doc(mapOf("shadowY" to ThemeProperty.encode(spec, -4f)))
        val decoded = ThemeCodec.decode(ThemeCodec.encode(d))
        assertTrue("codec rejected an encoded property", decoded is ThemeValidation.Ok)
        assertEquals("-4", (decoded as ThemeValidation.Ok).doc.overrides["shadowY"])
    }
}
