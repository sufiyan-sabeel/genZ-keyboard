package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the theme document codec and its validation.
 *
 * Imported themes are untrusted input, so most of these cases are rejections: a malformed file must
 * produce a reason, not an exception, and must never be able to reference a URL or escape the theme
 * directory.
 */
class ThemeDocTest {

    private fun ok(json: String): ThemeDoc =
        (ThemeCodec.decode(json) as ThemeValidation.Ok).doc

    private fun reason(json: String): String =
        (ThemeCodec.decode(json) as ThemeValidation.Invalid).reason

    private fun json(
        id: String = "user.abc",
        name: String = "Mine",
        base: String = "builtin.anime.night",
        overrides: String = """{"colors.key":"#1A1C22"}""",
        schema: Int = ThemeDoc.SCHEMA,
        wallpaper: String? = null
    ): String = buildString {
        append("""{"schema":$schema,"id":"$id","name":"$name","base":"$base",""")
        if (wallpaper != null) append(""""wallpaper":"$wallpaper",""")
        append(""""overrides":$overrides}""")
    }

    // ---------------------------------------------------------------- round trip

    @Test
    fun encodeThenDecodePreservesEverything() {
        val original = ThemeDoc(
            id = "user.1a2b",
            name = "Midnight Windmill edit",
            basePreset = "builtin.anime.windmill",
            overrides = mapOf("colors.key" to "#283031", "keys.*.radius" to "12"),
            wallpaper = "wall_abc123.webp"
        )
        val back = ok(ThemeCodec.encode(original))
        assertEquals(original, back)
    }

    @Test
    fun aThemeWithoutWallpaperRoundTrips() {
        val original = ThemeDoc("user.1", "Plain", "builtin.anime.minimal")
        assertEquals(original, ok(ThemeCodec.encode(original)))
    }

    @Test
    fun overridesAreWrittenInStableOrder() {
        // a stable encoding keeps ids and diffs predictable
        val a = ThemeDoc("user.1", "T", "builtin.anime.night", linkedMapOf("b" to "#111111", "a" to "#222222"))
        val b = ThemeDoc("user.1", "T", "builtin.anime.night", linkedMapOf("a" to "#222222", "b" to "#111111"))
        assertEquals(ThemeCodec.encode(a), ThemeCodec.encode(b))
    }

    // ---------------------------------------------------------------- rejections

    @Test
    fun aFutureSchemaIsRejected() {
        assertTrue(reason(json(schema = 2)).contains("version"))
    }

    @Test
    fun aMissingSchemaIsRejected() {
        assertTrue(reason("""{"id":"user.1","name":"T","base":"b"}""").contains("version"))
    }

    @Test
    fun invalidJsonIsRejectedWithoutThrowing() {
        assertTrue(reason("{not json").contains("JSON"))
    }

    @Test
    fun anEmptyNameIsRejected() {
        assertTrue(reason(json(name = "  ")).contains("name"))
    }

    @Test
    fun anOverlongNameIsRejected() {
        assertTrue(reason(json(name = "x".repeat(ThemeDoc.MAX_NAME + 1))).contains("long"))
    }

    @Test
    fun aBadIdIsRejected() {
        assertTrue(reason(json(id = "User/../etc")).contains("id"))
    }

    @Test
    fun aBadBasePresetIsRejected() {
        assertTrue(reason(json(base = "../assets")).contains("preset"))
    }

    @Test
    fun aUrlWallpaperIsRejected() {
        // a shared theme must not become a network fetch
        assertTrue(reason(json(wallpaper = "https://example.com/a.png")).contains("wallpaper"))
    }

    @Test
    fun anAbsolutePathWallpaperIsRejected() {
        assertTrue(reason(json(wallpaper = "/etc/passwd")).contains("wallpaper"))
    }

    @Test
    fun aTraversalWallpaperIsRejected() {
        assertTrue(reason(json(wallpaper = "../secret.png")).contains("wallpaper"))
    }

    @Test
    fun aNonImageWallpaperIsRejected() {
        assertTrue(reason(json(wallpaper = "payload.apk")).contains("wallpaper"))
    }

    @Test
    fun aValueThatIsNeitherColourNorNumberIsRejected() {
        assertTrue(reason(json(overrides = """{"colors.key":"javascript:alert(1)"}""")).contains("value"))
    }

    @Test
    fun aMalformedHexColourIsRejected() {
        assertTrue(reason(json(overrides = """{"colors.key":"#GGGGGG"}""")).contains("value"))
    }

    @Test
    fun anInvalidPropertyNameIsRejected() {
        assertTrue(reason(json(overrides = """{"bad key":"#FFFFFF"}""")).contains("property"))
    }

    @Test
    fun tooManyOverridesAreRejected() {
        val many = (0..ThemeDoc.MAX_OVERRIDES).joinToString(",") { """"k$it":"#FFFFFF"""" }
        assertTrue(reason(json(overrides = "{$many}")).contains("many"))
    }

    @Test
    fun anOversizedFileIsRejectedBeforeParsing() {
        val huge = "{\"schema\":1,\"id\":\"user.1\",\"name\":\"" + "x".repeat(ThemeDoc.MAX_JSON_BYTES) + "\"}"
        assertTrue(reason(huge).contains("large"))
    }

    // ---------------------------------------------------------------- accepted shapes

    @Test
    fun numericAndAlphaColourValuesAreAccepted() {
        val doc = ok(json(overrides = """{"keys.*.radius":"12.5","colors.key":"#801A1C22"}"""))
        assertEquals("#801A1C22", doc.overrides["colors.key"])
        assertEquals("12.5", doc.overrides["keys.*.radius"])
    }

    @Test
    fun aValidWallpaperNameIsAccepted() {
        assertEquals("wall_abc-1.webp", ok(json(wallpaper = "wall_abc-1.webp")).wallpaper)
    }

    @Test
    fun noOverridesIsValid() {
        assertTrue(ok(json(overrides = "{}")).overrides.isEmpty())
    }

    // ---------------------------------------------------------------- id derivation

    @Test
    fun theIdIsStableForTheSameContent() {
        val a = ThemeCodec.idFor("Mine", "builtin.anime.night", mapOf("colors.key" to "#111111"))
        val b = ThemeCodec.idFor("Mine", "builtin.anime.night", mapOf("colors.key" to "#111111"))
        assertEquals(a, b)
    }

    @Test
    fun theIdChangesWhenTheNameChanges() {
        // required by duplicate(): the copy must be a different file
        val a = ThemeCodec.idFor("Mine", "builtin.anime.night", emptyMap())
        val b = ThemeCodec.idFor("Mine copy", "builtin.anime.night", emptyMap())
        assertFalse(a == b)
    }

    @Test
    fun theIdChangesWhenAnOverrideChanges() {
        val a = ThemeCodec.idFor("Mine", "b", mapOf("colors.key" to "#111111"))
        val b = ThemeCodec.idFor("Mine", "b", mapOf("colors.key" to "#222222"))
        assertFalse(a == b)
    }

    @Test
    fun theDerivedIdIsAValidDocumentId() {
        val id = ThemeCodec.idFor("Name with spaces & symbols!", "builtin.anime.night", mapOf("a" to "#FFFFFF"))
        val doc = ok(json(id = id, name = "N"))
        assertNotNull(doc)
    }
}
