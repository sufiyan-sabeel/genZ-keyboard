package com.genz.keyboard

import android.content.Context

/**
 * Draft → publish lifecycle for community themes.
 *
 * The two phases are separate on purpose. Storage and the database cannot share a transaction, so
 * "upload the artwork and publish" is not atomic: the safe order is to put the artwork somewhere
 * private first, then move it into public storage only once the theme is actually being published.
 *
 * Nothing here can leak a draft. The drafts bucket is private and only its owner can read it; the
 * public object is created by a server-side copy, so the bytes are never exposed under a public path
 * until the publish step runs.
 */
object ThemePublishing {

    sealed interface State {
        data object Idle : State
        data object Validating : State
        data class Uploading(val fraction: Float) : State
        data object Saving : State
        data class DraftSaved(val themeId: String) : State
        data object Transferring : State
        data object Publishing : State
        data class Done(val themeId: String) : State
        data class Failed(val reason: String, val retryable: Boolean) : State
    }

    private const val DRAFTS = "theme-drafts"
    private const val PUBLIC = "theme-wallpapers"

    /**
     * Save an unpublished theme, uploading its wallpaper into private storage.
     *
     * @return the stored draft, or a failure. A failure here leaves nothing public behind.
     */
    fun saveDraft(
        context: Context,
        doc: ThemeDoc,
        title: String,
        description: String,
        tags: List<String>,
        category: String,
        onState: (State) -> Unit
    ): State {
        onState(State.Validating)
        val normalised = PublishRules.normaliseTags(tags)
        PublishRules.validate(title, description, normalised, category)
            ?.let { return State.Failed(it, false).also(onState) }

        val wallpaper = doc.wallpaper ?: return State.Failed("Pick a wallpaper first", false).also(onState)
        val ext = PublishRules.imageExtension(wallpaper)
            ?: return State.Failed("Wallpaper must be a PNG, JPEG or WebP image", false).also(onState)
        val file = ThemeAssets.wallpaperFile(context, wallpaper)
            ?: return State.Failed("Wallpaper file name is not valid", false).also(onState)
        if (!file.isFile) return State.Failed("Wallpaper file is missing", false).also(onState)
        if (file.length() > PublishRules.MAX_WALLPAPER_BYTES) {
            return State.Failed("Wallpaper is larger than 8 MB", false).also(onState)
        }

        val token = CommunityAuth.currentToken(context)
            ?: return State.Failed("Sign in to publish", false).also(onState)
        val userId = AuthResponse.subjectOf(token)
            ?: return State.Failed("Could not read your account id", false).also(onState)

        val bytes = runCatching { file.readBytes() }.getOrNull()
            ?: return State.Failed("Could not read the wallpaper", false).also(onState)

        val draftPath = "$userId/draft_${System.currentTimeMillis()}.$ext"
        onState(State.Uploading(0.4f))
        when (val up = CommunityApi.uploadObject(token, DRAFTS, draftPath, bytes, PublishRules.mimeType(ext))) {
            is CommunityResult.Ok -> onState(State.Uploading(1f))
            is CommunityResult.Unauthorized -> return State.Failed("Your session expired. Sign in again.", false).also(onState)
            is CommunityResult.NotConfigured -> return State.Failed("Community is not configured", false).also(onState)
            is CommunityResult.NetworkError -> return State.Failed("Upload failed: check your connection", true).also(onState)
            is CommunityResult.ServerError -> return State.Failed("Upload rejected (${up.code})", false).also(onState)
        }

        onState(State.Saving)
        val result = CommunityApi.createDraftTheme(
            token = token,
            name = title.trim(),
            description = description.trim(),
            tags = normalised,
            category = category,
            docJson = ThemeCodec.encode(doc),
            draftPath = draftPath,
            contentHash = ThemeContentHash.of(doc)
        )
        return when (result) {
            is CommunityResult.Ok -> {
                val id = CommunityThemeId.from(result.value)
                if (id == null) State.Failed("Draft saved but the server returned no id", false).also(onState)
                else State.DraftSaved(id).also(onState)
            }
            is CommunityResult.Unauthorized -> State.Failed("Your session expired. Sign in again.", false).also(onState)
            is CommunityResult.NotConfigured -> State.Failed("Community is not configured", false).also(onState)
            // the draft object is now unreferenced; remove it so it does not linger
            is CommunityResult.NetworkError -> State.Failed("Saving failed: check your connection", true).also(onState)
            is CommunityResult.ServerError -> {
                // the new draft object is unreferenced either way, so remove it
                CommunityApi.deleteObject(token, DRAFTS, draftPath)
                if (PublishRecovery.isDuplicateDraft(result.code)) {
                    State.Failed("This design is already saved as a draft", false).also(onState)
                } else {
                    State.Failed("Draft rejected (${result.code})", false).also(onState)
                }
            }
        }
    }

    /**
     * Publish a saved draft.
     *
     * Order: copy artwork to public storage → verify → point the row at it → set public → remove the
     * draft. If the row update fails after the copy, the public object is unreferenced and is
     * deleted, and the draft is left untouched so the publish can be retried.
     */
    fun publishDraft(
        context: Context,
        themeId: String,
        draftPath: String,
        onState: (State) -> Unit
    ): State {
        val token = CommunityAuth.currentToken(context)
            ?: return State.Failed("Sign in to publish", false).also(onState)
        if (!DownloadGuard.isSafeObjectPath(draftPath)) {
            return State.Failed("Draft path is not valid", false).also(onState)
        }
        // The drafts bucket already refuses a cross-user read, but failing here gives a clear reason
        // instead of a confusing 403 from Storage.
        val owner = AuthResponse.subjectOf(token)
        if (owner == null || !draftPath.startsWith("$owner/")) {
            return State.Failed("This draft does not belong to you", false).also(onState)
        }
        val ext = draftPath.substringAfterLast('.', "webp")
        val publicPath = draftPath.replace("draft_", "")

        onState(State.Transferring)
        // A 409 here is not a failure: the destination already exists, which is exactly what a retry
        // of an interrupted publish looks like. Treating it as an error would make an already-copied
        // theme permanently unpublishable.
        val copyOutcome = when (val cp = CommunityApi.copyObject(token, DRAFTS, draftPath, PUBLIC, publicPath)) {
            is CommunityResult.Ok -> PublishRecovery.CopyOutcome.TRANSFERRED
            is CommunityResult.Unauthorized -> return State.Failed("Your session expired. Sign in again.", false).also(onState)
            is CommunityResult.NotConfigured -> return State.Failed("Community is not configured", false).also(onState)
            is CommunityResult.NetworkError -> return State.Failed("Artwork transfer failed: check your connection", true).also(onState)
            is CommunityResult.ServerError -> PublishRecovery.copyOutcome(cp.code)
        }
        // A 409 says "the destination exists", but it does not prove the destination is the object we
        // expect. Before treating it as success, confirm the public object is actually there and
        // readable; otherwise a 409 for any other reason would publish a theme with no artwork.
        if (copyOutcome == PublishRecovery.CopyOutcome.ALREADY_PRESENT &&
            !CommunityApi.publicObjectExists(PUBLIC, publicPath)
        ) {
            return State.Failed("Artwork is not available in public storage yet; try again", true).also(onState)
        }
        when (copyOutcome) {
            PublishRecovery.CopyOutcome.TRANSFERRED, PublishRecovery.CopyOutcome.ALREADY_PRESENT -> Unit
            PublishRecovery.CopyOutcome.RETRY_LATER -> return State.Failed("Artwork transfer failed: try again", true).also(onState)
            PublishRecovery.CopyOutcome.REJECTED -> return State.Failed("Artwork transfer was refused", false).also(onState)
        }

        onState(State.Publishing)
        val result = CommunityApi.publishThemeRow(token, themeId, publicPath)
        val rowOutcome = when (result) {
            is CommunityResult.Ok -> PublishRecovery.RowOutcome.PUBLISHED
            is CommunityResult.Unauthorized -> PublishRecovery.RowOutcome.REJECTED
            is CommunityResult.NotConfigured -> PublishRecovery.RowOutcome.REJECTED
            is CommunityResult.NetworkError -> PublishRecovery.RowOutcome.RETRY_LATER
            is CommunityResult.ServerError -> PublishRecovery.rowOutcome(result.code)
        }

        // The draft may only be removed once the row is actually published; deleting it earlier would
        // leave a theme pointing at artwork that exists nowhere.
        if (PublishRecovery.shouldDeleteDraftAfter(rowOutcome)) {
            CommunityApi.deleteObject(token, DRAFTS, draftPath)
            return State.Done(themeId).also(onState)
        }

        // Only remove the public object when *this* attempt created it. If the copy found it already
        // present it may belong to an earlier successful publish, and deleting it would destroy live
        // artwork.
        if (PublishRecovery.shouldDeletePublicAfterFailure(copyOutcome)) {
            CommunityManage.cleanupOrphanPublic(context, publicPath)
        }

        return when (rowOutcome) {
            PublishRecovery.RowOutcome.PUBLISHED -> State.Done(themeId).also(onState)
            PublishRecovery.RowOutcome.RETRY_LATER ->
                State.Failed("Publishing failed: check your connection", true).also(onState)
            PublishRecovery.RowOutcome.REJECTED ->
                State.Failed("You can only publish your own themes", false).also(onState)
        }
    }
}

/** The server returns the created row; pull its id out without a full JSON model. */
object CommunityThemeId {
    fun from(responseBody: String): String? {
        val arr = ThemeJson.decodeArray(responseBody) ?: return null
        return (arr.firstOrNull() as? Map<*, *>)?.get("id") as? String
    }
}
