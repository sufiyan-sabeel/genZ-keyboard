package com.genz.keyboard

import android.content.Context

/**
 * Downloading a community theme into local storage.
 *
 * The order protects the user's existing data: fetch, validate everything, then decide about a
 * clash, and only write once. Nothing is imported that has not passed the same gates as an
 * imported archive, and an existing local theme is never replaced without an explicit decision.
 */
object ThemeDownload {

    sealed interface State {
        data object Idle : State
        data class Fetching(val fraction: Float) : State
        data object Validating : State
        data object Importing : State
        data class NeedsConfirmation(val existingId: String, val reason: String) : State
        data class Done(val doc: ThemeDoc) : State
        data class Failed(val reason: String, val retryable: Boolean) : State
    }

    /**
     * Fetch and validate a theme without writing anything.
     *
     * @param wallpaperPath the Storage object path recorded on the theme row.
     */
    fun fetchAndValidate(
        context: Context,
        themeId: String,
        wallpaperPath: String?,
        overwrite: Boolean = false,
        onState: (State) -> Unit
    ): State {
        onState(State.Fetching(0.1f))
        val token = CommunityAuth.currentToken(context)

        val docJson = when (val r = CommunityApi.fetchThemeDoc(token, themeId)) {
            is CommunityResult.Ok -> r.value
            is CommunityResult.Unauthorized -> return State.Failed("Sign in to download", retryable = false).also(onState)
            is CommunityResult.NotConfigured -> return State.Failed("Community is not configured", retryable = false).also(onState)
            is CommunityResult.NetworkError -> return State.Failed("Could not reach the server", retryable = true).also(onState)
            is CommunityResult.ServerError -> return State.Failed("Download failed (${r.code})", retryable = r.code >= 500).also(onState)
        }

        onState(State.Fetching(0.4f))
        val doc = when (val v = DownloadGuard.accept(docJson)) {
            is DownloadGuard.Result.Accepted -> v.doc
            is DownloadGuard.Result.Rejected -> return State.Failed("Theme is not valid: ${v.reason}", retryable = false).also(onState)
        }

        // the document's own wallpaper name is what the renderer will look for, so it must be safe
        val declared = doc.wallpaper
        if (declared != null && !DownloadGuard.isSafeWallpaperName(declared)) {
            return State.Failed("Theme references an unsafe wallpaper name", retryable = false).also(onState)
        }

        var wallpaper = ByteArray(0)
        var format: ImageMagic.Format? = null
        if (wallpaperPath != null) {
            if (!DownloadGuard.isSafeObjectPath(wallpaperPath)) {
                return State.Failed("Theme references an unsafe wallpaper path", retryable = false).also(onState)
            }
            onState(State.Fetching(0.6f))
            wallpaper = when (val r = CommunityApi.downloadObject("theme-wallpapers", wallpaperPath)) {
                is CommunityResult.Ok -> r.value
                is CommunityResult.Unauthorized -> return State.Failed("Sign in to download", retryable = false).also(onState)
                is CommunityResult.NotConfigured -> return State.Failed("Community is not configured", retryable = false).also(onState)
                is CommunityResult.NetworkError -> return State.Failed("Wallpaper download failed", retryable = true).also(onState)
                is CommunityResult.ServerError -> return State.Failed("Wallpaper download failed (${r.code})", retryable = r.code >= 500).also(onState)
            }
            if (wallpaper.size > DownloadGuard.MAX_WALLPAPER_BYTES) {
                return State.Failed("Wallpaper is larger than 8 MB", retryable = false).also(onState)
            }
            // the bytes decide, not the header the server sent
            format = ImageMagic.detect(wallpaper)
                ?: return State.Failed("Wallpaper is not a valid image", retryable = false).also(onState)
        }

        onState(State.Validating)
        return commit(context, doc, wallpaper, format, onState, overwrite)
    }

    /**
     * Import a validated theme.
     *
     * An existing local theme with the same id is never overwritten silently: the caller is told and
     * must confirm, because losing someone's edited theme to a download would be unrecoverable.
     */
    fun commit(
        context: Context,
        doc: ThemeDoc,
        wallpaper: ByteArray,
        format: ImageMagic.Format?,
        onState: (State) -> Unit,
        overwrite: Boolean = false
    ): State {
        val store = ThemeStore(ThemeAssets.themeDir(context))
        if (store.exists(doc.id) && !overwrite) {
            return State.NeedsConfirmation(doc.id, "A theme with this id already exists on this device").also(onState)
        }

        onState(State.Importing)
        var stored: ThemeDoc = doc
        if (wallpaper.isNotEmpty() && format != null) {
            val name = "dl_" + doc.id.replace(Regex("[^A-Za-z0-9._-]"), "_").take(40) + "." + format.extension
            val dir = ThemeAssets.imageDir(context).apply { if (!isDirectory) mkdirs() }
            val ok = runCatching {
                java.io.File(dir, name).outputStream().use { it.write(wallpaper) }
                true
            }.getOrDefault(false)
            if (!ok) return State.Failed("Could not save the wallpaper", retryable = true).also(onState)
            stored = doc.copy(wallpaper = name)
        }

        val saved = runCatching { store.save(stored) }.getOrDefault(false)
        if (!saved) return State.Failed("Could not save the theme", retryable = true).also(onState)
        return State.Done(stored).also(onState)
    }

    /** Apply a downloaded theme using the production bridge. */
    fun apply(context: Context, doc: ThemeDoc): Boolean =
        ThemeApply.applyAndPersist(context, doc, KeyboardPrefs.current(context))
}
