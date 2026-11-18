package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for publish validation.
 *
 * These run before anything is uploaded, so a bad draft fails on the device with a clear reason
 * instead of as a 400 from Postgres.
 */
class PublishRulesTest {

    private fun ok(title: String = "Sunset", desc: String = "", tags: List<String> = emptyList(),
                   category: String = "anime") = PublishRules.validate(title, desc, tags, category)

    @Test
    fun aReasonableDraftPasses() {
        assertNull(ok(tags = listOf("warm", "anime")))
    }

    @Test
    fun aTooShortTitleIsRejected() {
        assertNotNull(ok(title = "ab"))
        assertNotNull(ok(title = "   "))
    }

    @Test
    fun aTooLongTitleIsRejected() {
        assertNotNull(ok(title = "a".repeat(PublishRules.MAX_TITLE + 1)))
    }

    @Test
    fun aTooLongDescriptionIsRejected() {
        assertNotNull(ok(desc = "d".repeat(PublishRules.MAX_DESCRIPTION + 1)))
    }

    @Test
    fun anUnknownCategoryIsRejected() {
        assertNotNull(ok(category = "not-a-category"))
        assertNull(ok(category = "minimal"))
    }

    @Test
    fun tooManyTagsAreRejected() {
        assertNotNull(ok(tags = (0..PublishRules.MAX_TAGS).map { "t$it" }))
    }

    @Test
    fun aTooLongTagIsRejected() {
        assertNotNull(ok(tags = listOf("x".repeat(PublishRules.MAX_TAG_LENGTH + 1))))
    }

    // ---------------------------------------------------------------- tag handling

    @Test
    fun tagsAreNormalised() {
        assertEquals(listOf("warm", "anime"), PublishRules.parseTags("  Warm , ANIME , warm , "))
    }

    @Test
    fun tagCountIsCapped() {
        val many = (0..20).map { "t$it" }
        assertEquals(PublishRules.MAX_TAGS, PublishRules.normaliseTags(many).size)
    }

    @Test
    fun blankTagsAreDropped() {
        assertEquals(emptyList<String>(), PublishRules.parseTags(" , , "))
    }

    // ---------------------------------------------------------------- files

    @Test
    fun onlyImageExtensionsAreAccepted() {
        assertEquals("webp", PublishRules.imageExtension("wall_ab.webp"))
        assertEquals("jpg", PublishRules.imageExtension("a.JPG"))
        assertNull(PublishRules.imageExtension("wall.apk"))
        assertNull(PublishRules.imageExtension("wall"))
    }

    @Test
    fun theStoragePathStartsWithTheUserId() {
        // the bucket policy requires this: storage.foldername(name)[1] = auth.uid()
        val path = PublishRules.storagePath("user-123", "theme-abc", "webp")
        assertTrue(path.startsWith("user-123/"))
        assertTrue(path.endsWith(".webp"))
    }

    @Test
    fun theMimeTypeMatchesTheExtension() {
        assertEquals("image/webp", PublishRules.mimeType("webp"))
        assertEquals("image/png", PublishRules.mimeType("png"))
        assertEquals("image/jpeg", PublishRules.mimeType("jpg"))
    }

    @Test
    fun theUploadCapMatchesTheWallpaperCap() {
        assertEquals(8L * 1024 * 1024, PublishRules.MAX_WALLPAPER_BYTES)
    }
}
