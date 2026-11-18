package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for saved-theme paging and account scoping.
 */
class FavouritePagesTest {

    private fun t(id: String) = ThemeSummary(id, "n$id", "u", "", emptyList(), "anime", null, 0.0, 0, 0, 1)

    @Test
    fun pagesAppend() {
        val merged = FavouritePages.merge(listOf(t("a")), listOf(t("b")))
        assertEquals(listOf("a", "b"), merged.map { it.id })
    }

    @Test
    fun aRepeatedRowIsDropped() {
        // offsets shift when a saved theme is unpublished mid-paging
        val merged = FavouritePages.merge(listOf(t("a"), t("b")), listOf(t("b"), t("c")))
        assertEquals(listOf("a", "b", "c"), merged.map { it.id })
    }

    @Test
    fun anEntirelyDuplicatePageAddsNothing() {
        val merged = FavouritePages.merge(listOf(t("a"), t("b")), listOf(t("a"), t("b")))
        assertEquals(2, merged.size)
    }

    @Test
    fun orderIsPreserved() {
        // saved_at desc is the server's order; merging must not re-sort it
        val merged = FavouritePages.merge(listOf(t("c"), t("a")), listOf(t("b")))
        assertEquals(listOf("c", "a", "b"), merged.map { it.id })
    }

    @Test
    fun aShortPageEndsTheList() {
        assertTrue(FavouritePages.isExhausted(MarketplaceQuery.PAGE_SIZE - 1))
        assertFalse(FavouritePages.isExhausted(MarketplaceQuery.PAGE_SIZE))
    }

    @Test
    fun removingDropsOnlyThatTheme() {
        val left = FavouritePages.without(listOf(t("a"), t("b")), "a")
        assertEquals(listOf("b"), left.map { it.id })
    }

    @Test
    fun aCachedListBelongsToOneAccount() {
        assertTrue(FavouritePages.belongsTo("u1", "u1"))
        // switching accounts must not leave the previous user's saved themes on screen
        assertFalse(FavouritePages.belongsTo("u1", "u2"))
        assertFalse(FavouritePages.belongsTo("u1", null))
        assertFalse(FavouritePages.belongsTo(null, "u1"))
    }

    @Test
    fun anUnconfiguredBuildDoesNotReportSavedThemes() {
        // no fabricated list when the backend is absent
        assertTrue(
            CommunityApi.listFavourites("token", "3f1b2c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d")
                is CommunityResult.NotConfigured
        )
    }

    @Test
    fun anInvalidUserIdIsRejectedBeforeTheNetwork() {
        assertTrue(CommunityApi.listFavourites("token", "not-a-uuid") is CommunityResult.ServerError)
    }
}
