package com.genz.keyboard

import android.content.Context

/**
 * Favourites, downloads and reports.
 *
 * None of these are trusted client-side: a favourite is a row the database ties to the caller's own
 * id, a download counter is incremented by a function the caller cannot forge, and a report is
 * insert-only. The app decides what to *offer*; Postgres decides what is allowed.
 */
object CommunitySocial {

    sealed interface State {
        data object Idle : State
        data object Working : State
        data class Done(val message: String) : State
        data class Failed(val reason: String) : State
    }

    private fun token(context: Context): String? = CommunityAuth.currentToken(context)

    /** Add or remove this theme from the signed-in user's favourites. */
    fun toggleFavourite(context: Context, themeId: String, currentlyFavourite: Boolean): State {
        val t = token(context) ?: return State.Failed("Sign in to save themes")
        val result = if (currentlyFavourite) {
            CommunityApi.removeFavourite(t, themeId)
        } else {
            CommunityApi.addFavourite(t, themeId)
        }
        return when (result) {
            is CommunityResult.Ok -> State.Done(if (currentlyFavourite) "Removed from favourites" else "Saved to favourites")
            is CommunityResult.Unauthorized -> State.Failed("Sign in to save themes")
            is CommunityResult.NotConfigured -> State.Failed("Community is not configured")
            is CommunityResult.NetworkError -> State.Failed("Could not reach the server")
            // a duplicate favourite is not an error worth surfacing
            is CommunityResult.ServerError -> if (result.code == 409) State.Done("Already saved") else State.Failed("Could not update favourites")
        }
    }

    fun isFavourite(context: Context, themeId: String): Boolean {
        val t = token(context) ?: return false
        return when (val r = CommunityApi.isFavourite(t, themeId)) {
            is CommunityResult.Ok -> r.value
            else -> false
        }
    }

    /** Count a download. The server increments; the client cannot choose the value. */
    fun registerDownload(context: Context, themeId: String) {
        val t = token(context)
        CommunityApi.registerDownload(t, themeId)
    }

    /** Report a theme for moderation. */
    fun reportTheme(context: Context, themeId: String, reason: String): State {
        val t = token(context) ?: return State.Failed("Sign in to report")
        if (reason.trim().length < 3) return State.Failed("Describe the problem in a few words")
        return when (val r = CommunityApi.report(t, "theme", themeId, reason.trim())) {
            is CommunityResult.Ok -> State.Done("Report sent for review")
            is CommunityResult.Unauthorized -> State.Failed("Sign in to report")
            is CommunityResult.NotConfigured -> State.Failed("Community is not configured")
            is CommunityResult.NetworkError -> State.Failed("Could not reach the server")
            is CommunityResult.ServerError ->
                if (r.code == 409) State.Done("You have already reported this theme")
                else State.Failed("Could not send the report")
        }
    }
}
