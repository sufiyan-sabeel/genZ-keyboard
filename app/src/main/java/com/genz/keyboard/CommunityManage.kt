package com.genz.keyboard

import android.content.Context

/**
 * Managing themes the signed-in user already published.
 *
 * Ownership is enforced by row-level security, not here: the client sends its own JWT and Postgres
 * decides. This object only sequences the calls and cleans up Storage objects so a failed operation
 * does not leave a file nobody references.
 */
object CommunityManage {

    sealed interface State {
        data object Idle : State
        data object Working : State
        data object Done : State
        data class Failed(val reason: String) : State
    }

    private fun token(context: Context): String? = CommunityAuth.currentToken(context)

    /** Edit an owned theme's metadata. Does not touch its artwork. */
    fun update(
        context: Context,
        themeId: String,
        title: String,
        description: String,
        tags: List<String>,
        category: String
    ): State {
        val normalised = PublishRules.normaliseTags(tags)
        PublishRules.validate(title, description, normalised, category)?.let { return State.Failed(it) }
        val t = token(context) ?: return State.Failed("Sign in to edit")

        return when (val r = CommunityApi.updateTheme(t, themeId, title.trim(), description.trim(), normalised, category)) {
            is CommunityResult.Ok -> State.Done
            is CommunityResult.Unauthorized -> State.Failed("You can only edit your own themes")
            is CommunityResult.NotConfigured -> State.Failed("Community is not configured")
            is CommunityResult.NetworkError -> State.Failed("Could not reach the server")
            is CommunityResult.ServerError -> State.Failed("Edit rejected (${r.code})")
        }
    }

    /** Hide a theme from the marketplace without deleting it. */
    fun unpublish(context: Context, themeId: String): State {
        val t = token(context) ?: return State.Failed("Sign in to unpublish")
        return when (val r = CommunityApi.setVisibility(t, themeId, isPublic = false)) {
            is CommunityResult.Ok -> State.Done
            is CommunityResult.Unauthorized -> State.Failed("You can only unpublish your own themes")
            is CommunityResult.NotConfigured -> State.Failed("Community is not configured")
            is CommunityResult.NetworkError -> State.Failed("Could not reach the server")
            is CommunityResult.ServerError -> State.Failed("Unpublish rejected (${r.code})")
        }
    }

    /**
     * Delete an owned theme and its Storage objects.
     *
     * The row goes first: if the row delete is refused, the artwork must stay where it is, because
     * removing the file would orphan the row instead of the other way round.
     */
    fun delete(context: Context, themeId: String, wallpaperPath: String?): State {
        val t = token(context) ?: return State.Failed("Sign in to delete")
        return when (val r = CommunityApi.deleteTheme(t, themeId)) {
            is CommunityResult.Ok -> {
                if (wallpaperPath != null && DownloadGuard.isSafeObjectPath(wallpaperPath)) {
                    // best effort: a leftover file is harmless, a deleted row with a live file is not
                    CommunityApi.deleteObject(t, "theme-wallpapers", wallpaperPath)
                }
                State.Done
            }
            is CommunityResult.Unauthorized -> State.Failed("You can only delete your own themes")
            is CommunityResult.NotConfigured -> State.Failed("Community is not configured")
            is CommunityResult.NetworkError -> State.Failed("Could not reach the server")
            is CommunityResult.ServerError -> State.Failed("Delete rejected (${r.code})")
        }
    }
}
