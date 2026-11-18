package com.genz.keyboard

import android.content.Context

/**
 * Publishing a saved theme to the community.
 *
 * The order matters: validate locally, upload the wallpaper, then create the theme row. Doing the
 * row first would leave a published theme whose artwork never arrived — visible to everyone and
 * broken for everyone.
 */
object CommunityPublish {

    /** What the UI shows while publishing. Each state is distinct on purpose. */
    sealed interface State {
        data object Idle : State
        data object Validating : State
        data class Uploading(val fraction: Float) : State
        data object Saving : State
        data class Done(val themeId: String) : State
        data class Failed(val reason: String) : State
    }

    /**
     * Publish [doc] as the signed-in user.
     *
     * @param onState called on every transition so the UI can show genuine progress rather than a
     *   spinner that lies.
     */
    fun publish(
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
        PublishRules.validate(title, description, normalised, category)?.let {
            return State.Failed(it).also(onState)
        }
        if (doc.wallpaper.isNullOrBlank()) {
            return State.Failed("Pick a wallpaper before publishing").also(onState)
        }

        val wallpaperName = doc.wallpaper
        val extension = PublishRules.imageExtension(wallpaperName)
            ?: return State.Failed("Wallpaper must be a PNG, JPEG or WebP image").also(onState)

        val file = ThemeAssets.wallpaperFile(context, wallpaperName)
            ?: return State.Failed("Wallpaper file name is not valid").also(onState)
        if (!file.isFile) return State.Failed("Wallpaper file is missing").also(onState)
        if (file.length() > PublishRules.MAX_WALLPAPER_BYTES) {
            return State.Failed("Wallpaper is larger than 8 MB").also(onState)
        }

        // the document must survive the same validation an imported one does
        val json = ThemeCodec.encode(doc)
        if (json.toByteArray().size > PublishRules.MAX_THEME_BYTES) {
            return State.Failed("Theme is too large to publish").also(onState)
        }
        when (val check = ThemeCodec.decode(json)) {
            is ThemeValidation.Invalid -> return State.Failed("Theme is not valid: ${check.reason}").also(onState)
            is ThemeValidation.Ok -> Unit
        }

        val token = CommunityAuth.currentToken(context)
            ?: return State.Failed("Sign in to publish").also(onState)
        val userId = AuthResponse.subjectOf(token)
            ?: return State.Failed("Could not read your account id").also(onState)

        val path = PublishRules.storagePath(userId, doc.id, extension)
        val bytes = runCatching { file.readBytes() }.getOrNull()
            ?: return State.Failed("Could not read the wallpaper").also(onState)

        onState(State.Uploading(0.15f))
        when (val up = CommunityApi.uploadObject(
            token = token,
            bucket = "theme-wallpapers",
            path = path,
            bytes = bytes,
            contentType = PublishRules.mimeType(extension)
        )) {
            is CommunityResult.Ok -> onState(State.Uploading(1f))
            is CommunityResult.Unauthorized -> return State.Failed("Your session expired. Sign in again.").also(onState)
            is CommunityResult.NotConfigured -> return State.Failed("Community is not configured").also(onState)
            is CommunityResult.NetworkError -> return State.Failed("Upload failed: check your connection").also(onState)
            is CommunityResult.ServerError -> return State.Failed("Upload rejected (${up.code})").also(onState)
        }

        onState(State.Saving)
        val result = CommunityApi.publishTheme(
            token = token,
            name = title.trim(),
            description = description.trim(),
            tags = normalised,
            category = category,
            docJson = json
        )
        return when (result) {
            is CommunityResult.Ok -> State.Done(result.value).also(onState)
            is CommunityResult.Unauthorized -> State.Failed("Your session expired. Sign in again.").also(onState)
            is CommunityResult.NotConfigured -> State.Failed("Community is not configured").also(onState)
            is CommunityResult.NetworkError -> State.Failed("Publishing failed: check your connection").also(onState)
            is CommunityResult.ServerError -> State.Failed("Publishing rejected (${result.code})").also(onState)
        }
    }
}
