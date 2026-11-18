package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for marketplace query construction.
 *
 * The rules must be transparent and must never surface a removed or private theme, so each section's
 * ordering is pinned here.
 */
class MarketplaceQueryTest {

    private fun page(section: MarketplaceQuery.Section, offset: Int = 0) =
        MarketplaceQuery.Page(section, offset)

    @Test
    fun everyQueryExcludesRemovedAndPrivateThemes() {
        MarketplaceQuery.Section.entries.forEach { s ->
            val p = MarketplaceQuery.params(page(s))
            assertEquals("eq.true", p["is_public"])
            assertEquals("eq.false", p["is_removed"])
        }
    }

    @Test
    fun newestOrdersByCreationTime() {
        assertEquals("created_at.desc", MarketplaceQuery.params(page(MarketplaceQuery.Section.NEWEST))["order"])
    }

    @Test
    fun popularOrdersByDownloads() {
        assertTrue(MarketplaceQuery.params(page(MarketplaceQuery.Section.POPULAR))["order"]!!.startsWith("downloads.desc"))
    }

    @Test
    fun featuredRequiresTheFeaturedFlag() {
        assertEquals("eq.true", MarketplaceQuery.params(page(MarketplaceQuery.Section.FEATURED))["is_featured"])
    }

    @Test
    fun pagingIsApplied() {
        val p = MarketplaceQuery.params(page(MarketplaceQuery.Section.NEWEST, offset = 40))
        assertEquals("20", p["limit"])
        assertEquals("40", p["offset"])
    }

    @Test
    fun searchAndCategoryCompose() {
        val p = MarketplaceQuery.params(page(MarketplaceQuery.Section.NEWEST), search = "sunset", category = "anime")
        assertEquals("ilike.*sunset*", p["name"])
        assertEquals("eq.anime", p["category"])
    }

    @Test
    fun aBlankFilterIsOmitted() {
        val p = MarketplaceQuery.params(page(MarketplaceQuery.Section.NEWEST), search = "  ", category = "")
        assertNull(p["name"])
        assertNull(p["category"])
    }

    @Test
    fun topRatedHidesThemesWithoutEnoughReviews() {
        // a single 5-star review must not top the chart
        val thin = ThemeSummary("a", "Thin", "u", "", emptyList(), "anime", null, 5.0, 1, 10, 1)
        val solid = ThemeSummary("b", "Solid", "u", "", emptyList(), "anime", null, 4.6, 12, 40, 1)
        val ranked = MarketplaceQuery.rank(MarketplaceQuery.Section.TOP_RATED, listOf(thin, solid))
        assertEquals(listOf("Solid"), ranked.map { it.name })
    }

    @Test
    fun topRatedSortsByAverageThenCount() {
        val a = ThemeSummary("a", "A", "u", "", emptyList(), "x", null, 4.9, 5, 1, 1)
        val b = ThemeSummary("b", "B", "u", "", emptyList(), "x", null, 4.9, 20, 1, 1)
        val c = ThemeSummary("c", "C", "u", "", emptyList(), "x", null, 4.2, 30, 1, 1)
        val ranked = MarketplaceQuery.rank(MarketplaceQuery.Section.TOP_RATED, listOf(a, b, c))
        assertEquals(listOf("B", "A", "C"), ranked.map { it.name })
    }

    @Test
    fun otherSectionsAreLeftInServerOrder() {
        val rows = listOf(
            ThemeSummary("a", "A", "u", "", emptyList(), "x", null, 1.0, 0, 1, 1),
            ThemeSummary("b", "B", "u", "", emptyList(), "x", null, 5.0, 50, 1, 1)
        )
        assertEquals(rows, MarketplaceQuery.rank(MarketplaceQuery.Section.POPULAR, rows))
    }

    @Test
    fun pagingStopsOnAShortPage() {
        assertNull(MarketplaceQuery.nextOffset(loaded = 40, lastPageSize = 7))
        assertEquals(40, MarketplaceQuery.nextOffset(loaded = 40, lastPageSize = 20))
    }

    @Test
    fun theThresholdIsNotZero() {
        assertTrue(MarketplaceQuery.MIN_REVIEWS_FOR_RATING >= 2)
    }
}
