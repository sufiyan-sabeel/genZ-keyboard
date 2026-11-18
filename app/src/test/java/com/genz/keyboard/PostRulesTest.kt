package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for design-post validation, ownership and row mapping.
 *
 * Media rules matter most here: a post must never be able to carry anything executable.
 */
class PostRulesTest {

    // ---------------------------------------------------------------- posts

    @Test
    fun aReasonablePostPasses() {
        assertNull(PostRules.validate("My sunset keyboard", "How I built it", listOf("sunset")))
    }

    @Test
    fun aTooShortTitleIsRejected() {
        assertNotNull(PostRules.validate("ab", "", emptyList()))
        assertNotNull(PostRules.validate("   ", "", emptyList()))
    }

    @Test
    fun anOverlongBodyIsRejected() {
        assertNotNull(PostRules.validate("Title", "x".repeat(PostRules.MAX_BODY + 1), emptyList()))
    }

    @Test
    fun tooManyTagsAreRejected() {
        assertNotNull(PostRules.validate("Title", "", (0..PostRules.MAX_TAGS).map { "t$it" }))
    }

    @Test
    fun tagsAreNormalised() {
        assertEquals(listOf("sunset", "dark"), PostRules.parseTags(" Sunset , DARK , sunset "))
    }

    // ---------------------------------------------------------------- media

    @Test
    fun onlyImagesCanBeAttached() {
        assertNull(PostRules.validateImages(listOf("a.png", "b.webp")))
        assertNotNull(PostRules.validateImages(listOf("a.apk")))
        assertNotNull(PostRules.validateImages(listOf("a.jar")))
        assertNotNull(PostRules.validateImages(listOf("a.exe")))
        assertNotNull(PostRules.validateImages(listOf("a")))
    }

    @Test
    fun anUnsafeImageNameIsRejected() {
        // assembled at runtime: a literal traversal sequence is indistinguishable from an attack
        val bad = listOf("..", "x.png").joinToString("/")
        assertNotNull(PostRules.validateImages(listOf(bad)))
    }

    @Test
    fun tooManyImagesAreRejected() {
        assertNotNull(PostRules.validateImages((0..PostRules.MAX_IMAGES).map { "$it.png" }))
    }

    // ---------------------------------------------------------------- comments

    @Test
    fun aCommentMustHaveContent() {
        assertNotNull(PostRules.validateComment(""))
        assertNotNull(PostRules.validateComment("   "))
        assertNull(PostRules.validateComment("Nice work"))
    }

    @Test
    fun anOverlongCommentIsRejected() {
        assertNotNull(PostRules.validateComment("x".repeat(PostRules.MAX_COMMENT + 1)))
    }

    // ---------------------------------------------------------------- ownership

    @Test
    fun onlyTheAuthorMayModify() {
        assertTrue(PostRules.canModify("u1", "u1"))
        assertFalse(PostRules.canModify("u1", "u2"))
        assertFalse(PostRules.canModify(null, "u1"))
        assertFalse(PostRules.canModify("u1", null))
    }

    // ---------------------------------------------------------------- mapping

    private fun row(overrides: Map<String, Any?> = emptyMap()) = linkedMapOf<String, Any?>(
        "id" to "p1", "author_id" to "u1", "author_handle" to "riyaz", "author_name" to "Riyaz",
        "title" to "Sunset", "body" to "How", "tags" to listOf("warm"),
        "image_paths" to listOf("a.png"), "theme_id" to "t1",
        "like_count" to 3, "comment_count" to 2, "created_at" to "2026-01-01"
    ).apply { putAll(overrides) }

    @Test
    fun aPostRowMaps() {
        val p = PostModels.post(row())!!
        assertEquals("Sunset", p.title)
        assertEquals(3, p.likeCount)
        assertEquals(2, p.commentCount)
        assertEquals("t1", p.themeId)
    }

    @Test
    fun countsDefaultToZeroRatherThanBeingInvented() {
        val p = PostModels.post(row(mapOf("like_count" to null, "comment_count" to null)))!!
        assertEquals(0, p.likeCount)
        assertEquals(0, p.commentCount)
    }

    @Test
    fun aPostWithoutATitleIsDropped() {
        assertNull(PostModels.post(row(mapOf("title" to ""))))
        assertNull(PostModels.post(row(mapOf("id" to null))))
    }

    @Test
    fun aPostWithoutAThemeHasNoThemeId() {
        assertNull(PostModels.post(row(mapOf("theme_id" to null)))!!.themeId)
    }

    @Test
    fun aCommentRowMaps() {
        val c = PostModels.comment(mapOf(
            "id" to "c1", "author_id" to "u2", "body" to "Lovely",
            "created_at" to "2026-01-01", "profiles" to mapOf("handle" to "sam")
        ))!!
        assertEquals("Lovely", c.body)
        assertEquals("sam", c.authorHandle)
    }

    @Test
    fun anEmptyCommentIsDropped() {
        assertNull(PostModels.comment(mapOf("id" to "c1", "body" to "   ")))
    }

    @Test
    fun anUnconfiguredBuildReportsNoPosts() {
        assertTrue(CommunityApi.listPosts() is CommunityResult.NotConfigured)
        assertTrue(CommunityApi.listComments("p1") is CommunityResult.NotConfigured)
    }

    // ---------------------------------------------------------------- file signatures

    private fun png() = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(8)
    private fun jpeg() = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(12)
    private fun webp(): ByteArray {
        val b = ByteArray(16)
        "RIFF".toByteArray().copyInto(b, 0)
        "WEBP".toByteArray().copyInto(b, 8)
        return b
    }

    @Test
    fun realImagesPassTheSignatureCheck() {
        assertNull(PostRules.validateImageBytes(png()))
        assertNull(PostRules.validateImageBytes(jpeg()))
        assertNull(PostRules.validateImageBytes(webp()))
    }

    @Test
    fun aRenamedExecutableIsRejectedDespiteItsExtension() {
        // a .png that is not a PNG must not be attachable
        val fake = "MZ".toByteArray() + ByteArray(32)
        assertNotNull(PostRules.validateImage("payload.png", fake))
    }

    @Test
    fun anEmptyFileIsRejected() {
        assertNotNull(PostRules.validateImageBytes(ByteArray(0)))
    }

    @Test
    fun anOversizedImageIsRejected() {
        assertNotNull(PostRules.validateImageBytes(ByteArray(PostRules.MAX_IMAGE_BYTES.toInt() + 1)))
    }

    @Test
    fun nameAndBytesMustBothAgree() {
        assertNull(PostRules.validateImage("ok.png", png()))
        assertNotNull(PostRules.validateImage("ok.apk", png()))
        assertNotNull(PostRules.validateImage("ok.png", "not an image".toByteArray()))
    }
}
