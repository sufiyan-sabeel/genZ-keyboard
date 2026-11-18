package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for review validation, ownership and aggregation.
 *
 * Ownership is enforced by RLS on the server; these tests pin the client-side rule so the UI never
 * offers an edit that the database will reject.
 */
class ReviewRulesTest {

    @Test
    fun aValidReviewPasses() {
        assertNull(ReviewRules.validate(5, "Lovely colours"))
        assertNull(ReviewRules.validate(1, ""))
    }

    @Test
    fun ratingsOutsideOneToFiveAreRejected() {
        assertNotNull(ReviewRules.validate(0, ""))
        assertNotNull(ReviewRules.validate(6, ""))
        assertNotNull(ReviewRules.validate(-1, ""))
    }

    @Test
    fun anOverlongBodyIsRejected() {
        assertNotNull(ReviewRules.validate(4, "x".repeat(ReviewRules.MAX_BODY + 1)))
    }

    @Test
    fun theBodyIsOptional() {
        assertTrue(ReviewRules.isBodyOptional())
        assertNull(ReviewRules.validate(3, ""))
    }

    // ---------------------------------------------------------------- ownership

    @Test
    fun onlyTheAuthorCanEdit() {
        assertTrue(ReviewRules.canEdit("u1", "u1"))
        assertFalse(ReviewRules.canEdit("u1", "u2"))
    }

    @Test
    fun aMissingIdentityCannotEdit() {
        assertFalse(ReviewRules.canEdit(null, "u1"))
        assertFalse(ReviewRules.canEdit("u1", null))
    }

    // ---------------------------------------------------------------- aggregation

    @Test
    fun theAverageComesFromRealRatings() {
        assertEquals(4.0, ReviewRules.average(listOf(5, 4, 3))!!, 0.001)
        assertEquals(3.5, ReviewRules.average(listOf(5, 2))!!, 0.001)
    }

    @Test
    fun noReviewsMeansNoAverageRatherThanZeroStars() {
        assertNull(ReviewRules.average(emptyList()))
        assertEquals("—", ReviewRules.displayAverage(emptyList()))
    }

    @Test
    fun theAverageIsDisplayedToOneDecimal() {
        assertEquals("4.3", ReviewRules.displayAverage(listOf(4, 5, 4)))
    }

    // ---------------------------------------------------------------- sorting

    private fun review(id: String, rating: Int, at: String) =
        ThemeReview(id, "u", rating, "", at)

    @Test
    fun reviewsSortByTheOrdersActuallyOffered() {
        val rows = listOf(review("a", 3, "2026-01-01"), review("b", 5, "2026-03-01"), review("c", 1, "2026-02-01"))
        assertEquals(listOf("b", "c", "a"), ReviewRules.sort(rows, ReviewRules.Sort.NEWEST).map { it.id })
        assertEquals(listOf("b", "a", "c"), ReviewRules.sort(rows, ReviewRules.Sort.HIGHEST).map { it.id })
        assertEquals(listOf("c", "a", "b"), ReviewRules.sort(rows, ReviewRules.Sort.LOWEST).map { it.id })
    }

    @Test
    fun helpfulnessIsNotOffered() {
        // no helpfulness data exists, so there is no order that pretends otherwise
        assertTrue(ReviewRules.Sort.entries.none { it.name.contains("HELP") })
    }
}
