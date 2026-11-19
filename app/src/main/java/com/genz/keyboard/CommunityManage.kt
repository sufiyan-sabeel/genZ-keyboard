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

    /**
     * Replace a published theme's wallpaper.
     *
     * Upload-and-swap, in that order and never the reverse: the old object is deleted only after
     * both the new upload and the database update succeed. A failure at any step leaves the theme
     * showing its previous wallpaper, which is the recoverable outcome. Deleting first would leave
     * a published theme pointing at a file that no longer exists.
     *
     * @return [State.Done] on success, otherwise a failure whose message names the step.
     */
    fun replaceWallpaper(
        context: Context,
        themeId: String,
        doc: ThemeDoc,
        oldPath: String?
    ): State {
        val token = token(context) ?: return State.Failed("Sign in to replace the wallpaper")
        val name = doc.wallpaper ?: return State.Failed("This theme has no wallpaper")
        val ext = PublishRules.imageExtension(name)
            ?: return State.Failed("Wallpaper must be a PNG, JPEG or WebP image")
        val file = ThemeAssets.wallpaperFile(context, name)
            ?: return State.Failed("Wallpaper file name is not valid")
        if (!file.isFile) return State.Failed("Wallpaper file is missing")
        if (file.length() > PublishRules.MAX_WALLPAPER_BYTES) {
            return State.Failed("Wallpaper is larger than 8 MB")
        }
        val userId = AuthResponse.subjectOf(token)
            ?: return State.Failed("Could not read your account id")

        // a fresh path each time, so the old object is never overwritten and can be removed after
        val newPath = PublishRules.storagePath(userId, themeId + "-" + ThemeContentHash.of(doc), ext)
        val bytes = runCatching { file.readBytes() }.getOrNull()
            ?: return State.Failed("Could not read the wallpaper")

        when (val up = CommunityApi.uploadObject(
            token, "theme-wallpapers", newPath, bytes, PublishRules.mimeType(ext)
        )) {
            is CommunityResult.Ok -> Unit
            is CommunityResult.Unauthorized -> return State.Failed("Your session expired. Sign in again.")
            is CommunityResult.NotConfigured -> return State.Failed("Community is not configured")
            is CommunityResult.NetworkError -> return State.Failed("Upload failed: check your connection")
            is CommunityResult.ServerError -> return State.Failed("Upload rejected (${up.code})")
        }

        // only now does the row start pointing at the new file
        when (val upd = CommunityApi.setWallpaperPath(token, themeId, newPath)) {
            is CommunityResult.Ok -> Unit
            is CommunityResult.Unauthorized -> {
                // the new object is unreferenced; try to clean it up
                cleanupOrphan(context, newPath)
                return State.Failed("You can only change your own themes")
            }
            is CommunityResult.NotConfigured -> return State.Failed("Community is not configured")
            is CommunityResult.NetworkError -> {
                cleanupOrphan(context, newPath)
                return State.Failed("Could not update the theme: check your connection")
            }
            is CommunityResult.ServerError -> {
                cleanupOrphan(context, newPath)
                return State.Failed("Update rejected (${upd.code})")
            }
        }

        // the swap is complete, so the previous object is now genuinely unreferenced
        if (oldPath != null && oldPath != newPath && DownloadGuard.isSafeObjectPath(oldPath)) {
            CommunityApi.deleteObject(token, "theme-wallpapers", oldPath)
        }
        return State.Done
    }

    /** Paths that failed to delete, retried on the next launch. */
    private val pendingOrphans = LinkedHashSet<String>()

    /**
     * Record an unreferenced object so it can be removed later.
     *
     * Cleanup is best effort: a leftover file costs storage but breaks nothing, whereas a failed
     * user action that also threw would break the flow.
     */
    fun cleanupOrphan(context: Context, path: String) {
        if (!DownloadGuard.isSafeObjectPath(path)) return
        val token = token(context)
        if (token == null) { pendingOrphans.add(path); return }
        when (CommunityApi.deleteObject(token, "theme-wallpapers", path)) {
            is CommunityResult.Ok -> pendingOrphans.remove(path)
            else -> pendingOrphans.add(path)
        }
    }

    /** Retry cleanup for everything left behind. Safe to call on every app start. */
    fun retryPendingCleanup(context: Context): Int {
        val token = token(context) ?: return 0
        var removed = 0
        val it = pendingOrphans.iterator()
        while (it.hasNext()) {
            val path = it.next()
            when (CommunityApi.deleteObject(token, "theme-wallpapers", path)) {
                is CommunityResult.Ok -> { it.remove(); removed++ }
                else -> Unit
            }
        }
        return removed
    }

    /** Paths still awaiting cleanup; exposed for tests and diagnostics. */
    fun pendingOrphanCount(): Int = pendingOrphans.size

    /** Public objects left behind by a failed publish, retried on a later attempt. */
    private val pendingPublicOrphans = LinkedHashSet<String>()

    fun cleanupOrphanPublic(context: Context, path: String) {
        if (!DownloadGuard.isSafeObjectPath(path)) return
        val token = token(context)
        if (token == null) { pendingPublicOrphans.add(path); return }
        when (CommunityApi.deleteObject(token, "theme-wallpapers", path)) {
            is CommunityResult.Ok -> pendingPublicOrphans.remove(path)
            else -> pendingPublicOrphans.add(path)
        }
    }

    fun pendingPublicOrphanCount(): Int = pendingPublicOrphans.size
}
