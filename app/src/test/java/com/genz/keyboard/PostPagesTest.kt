package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for design-feed paging, ownership display and creation rules.
 *
 * The feed is written to concurrently, so a page can repeat a row or skip one when a post is
 * deleted mid-scroll. De-duplicating by id is what keeps that from showing twice.
 */
class PostPagesTest {

    private fun post(id: String, author: String = "u1") = DesignPost(
        id = id, authorId = author, authorHandle = "riyaz", authorName = "Riyaz",
        title = "n$id", body = "", tags = emptyList(), imagePaths = emptyList(),
        themeId = null, likeCount = 0, commentCount = 0, createdAt = "2026-01-01"
    )

    @Test
    fun pagesAppend() {
        assertEquals(listOf("a", "b"), PostPages.merge(listOf(post("a")), listOf(post("b"))).map { it.id })
    }

    @Test
    fun aRepeatedRowIsDropped() {
        val merged = PostPages.merge(listOf(post("a"), post("b")), listOf(post("b"), post("c")))
        assertEquals(listOf("a", "b", "c"), merged.map { it.id })
    }

    @Test
    fun anEntirelyDuplicatePageAddsNothing() {
        assertEquals(2, PostPages.merge(listOf(post("a"), post("b")), listOf(post("a"), post("b"))).size)
    }

    @Test
    fun aShortPageEndsTheFeed() {
        assertTrue(PostPages.isExhausted(MarketplaceQuery.PAGE_SIZE - 1))
        assertFalse(PostPages.isExhausted(MarketplaceQuery.PAGE_SIZE))
    }

    @Test
    fun deletingRemovesOnlyThatPost() {
        assertEquals(listOf("b"), PostPages.without(listOf(post("a"), post("b")), "a").map { it.id })
    }

    @Test
    fun onlyTheAuthorSeesEditControls() {
        val p = post("a", author = "u1")
        assertTrue(PostPages.canModify(p, "u1"))
        assertFalse(PostPages.canModify(p, "u2"))
        // signed out: no controls at all
        assertFalse(PostPages.canModify(p, null))
    }

    // ---------------------------------------------------------------- creation rules

    @Test
    fun anUnconfiguredBuildDoesNotFabricateAFeed() {
        assertTrue(CommunityApi.listPosts() is CommunityResult.NotConfigured)
    }

    @Test
    fun postingWithoutAConfiguredBackendFailsRatherThanPretending() {
        // the client must never report a successful post when no backend exists
        assertTrue(CommunityApi.createPost("", "t", "b", emptyList(), emptyList(), null) is CommunityResult.NotConfigured)
        assertTrue(CommunityApi.likePost("", "p1") is CommunityResult.NotConfigured)
        assertTrue(CommunityApi.addComment("", "p1", "hi") is CommunityResult.NotConfigured)
    }
}
