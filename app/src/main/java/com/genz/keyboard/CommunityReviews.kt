package com.genz.keyboard

import android.content.Context

/**
 * Review workflow.
 *
 * One review per account per theme is enforced by a unique index, not by this object: a retried
 * submission returns 409 and is reported as "you have already reviewed this", which is the truthful
 * outcome rather than a silent second review.
 */
object CommunityReviews {

    sealed interface State {
        data object Idle : State
        data object Working : State
        data class Loaded(val reviews: List<ThemeReview>, val average: String, val count: Int) : State
        data class Done(val message: String) : State
        data class Failed(val reason: String) : State
    }

    private fun token(context: Context): String? = CommunityAuth.currentToken(context)

    /** Load reviews and compute the average from the rows actually returned. */
    fun load(context: Context, themeId: String, order: ReviewRules.Sort = ReviewRules.Sort.NEWEST): State {
        val t = token(context)
        return when (val r = CommunityApi.listReviews(t, themeId)) {
            is CommunityResult.Ok -> {
                val sorted = ReviewRules.sort(r.value, order)
                State.Loaded(sorted, ReviewRules.displayAverage(sorted.map { it.rating }), sorted.size)
            }
            is CommunityResult.NotConfigured -> State.Failed("Community is not configured")
            is CommunityResult.Unauthorized -> State.Failed("Sign in to read reviews")
            is CommunityResult.NetworkError -> State.Failed("Could not reach the server")
            is CommunityResult.ServerError ->
                if (r.code == 404) State.Failed("This theme is no longer available") else State.Failed("Server error (${r.code})")
        }
    }

    fun submit(context: Context, themeId: String, rating: Int, body: String): State {
        ReviewRules.validate(rating, body)?.let { return State.Failed(it) }
        val t = token(context) ?: return State.Failed("Sign in to review")
        return when (val r = CommunityApi.submitReview(t, themeId, rating, body.trim())) {
            is CommunityResult.Ok -> State.Done("Review posted")
            is CommunityResult.Unauthorized -> State.Failed("Sign in to review")
            is CommunityResult.NotConfigured -> State.Failed("Community is not configured")
            is CommunityResult.NetworkError -> State.Failed("Could not reach the server")
            // 409 = the unique index refused a second review from this account
            is CommunityResult.ServerError ->
                if (r.code == 409) State.Done("You have already reviewed this theme")
                else State.Failed("Review rejected (${r.code})")
        }
    }

    fun edit(context: Context, reviewId: String, rating: Int, body: String): State {
        ReviewRules.validate(rating, body)?.let { return State.Failed(it) }
        val t = token(context) ?: return State.Failed("Sign in to edit your review")
        return when (val r = CommunityApi.updateReview(t, reviewId, rating, body.trim())) {
            is CommunityResult.Ok -> State.Done("Review updated")
            is CommunityResult.Unauthorized -> State.Failed("You can only edit your own review")
            is CommunityResult.NotConfigured -> State.Failed("Community is not configured")
            is CommunityResult.NetworkError -> State.Failed("Could not reach the server")
            is CommunityResult.ServerError -> State.Failed("Edit rejected (${r.code})")
        }
    }

    fun delete(context: Context, reviewId: String): State {
        val t = token(context) ?: return State.Failed("Sign in to delete your review")
        return when (val r = CommunityApi.deleteReview(t, reviewId)) {
            is CommunityResult.Ok -> State.Done("Review deleted")
            is CommunityResult.Unauthorized -> State.Failed("You can only delete your own review")
            is CommunityResult.NotConfigured -> State.Failed("Community is not configured")
            is CommunityResult.NetworkError -> State.Failed("Could not reach the server")
            is CommunityResult.ServerError -> State.Failed("Delete rejected (${r.code})")
        }
    }
}
