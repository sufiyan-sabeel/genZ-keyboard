package com.genz.keyboard

/**
 * What makes a review acceptable.
 *
 * Pure, so the rules are tested rather than trusted, and duplicated on the server: the database
 * already constrains `rating between 1 and 5` and `unique (theme_id, author_id)`, so a client that
 * skips these checks still cannot store an invalid review.
 */
object ReviewRules {

    const val MIN_RATING = 1
    const val MAX_RATING = 5
    const val MAX_BODY = 2000

    /** @return a reason the review cannot be submitted, or null when it is fine. */
    fun validate(rating: Int, body: String): String? {
        if (rating < MIN_RATING || rating > MAX_RATING) return "Choose a rating from 1 to 5 stars"
        if (body.length > MAX_BODY) return "Review is longer than $MAX_BODY characters"
        return null
    }

    /** A review is a star rating plus optional words; an empty body is allowed. */
    fun isBodyOptional(): Boolean = true

    /** The user may only change their own review; the database enforces this, this names it. */
    fun canEdit(reviewAuthorId: String?, currentUserId: String?): Boolean =
        reviewAuthorId != null && currentUserId != null && reviewAuthorId == currentUserId

    /** Average of the ratings that actually exist. Zero reviews means no average, not 0 stars. */
    fun average(ratings: List<Int>): Double? =
        if (ratings.isEmpty()) null else ratings.average()

    /** Rounded to one decimal, matching what the UI prints. */
    fun displayAverage(ratings: List<Int>): String =
        average(ratings)?.let { "%.1f".format(it) } ?: "—"

    /** Sort orders the UI actually offers. "Helpful" is absent because no such data exists. */
    enum class Sort(val label: String) { NEWEST("Newest"), HIGHEST("Highest rated"), LOWEST("Lowest rated") }

    fun sort(rows: List<ThemeReview>, order: Sort): List<ThemeReview> = when (order) {
        Sort.NEWEST -> rows.sortedByDescending { it.createdAt }
        Sort.HIGHEST -> rows.sortedByDescending { it.rating }
        Sort.LOWEST -> rows.sortedBy { it.rating }
    }
}
