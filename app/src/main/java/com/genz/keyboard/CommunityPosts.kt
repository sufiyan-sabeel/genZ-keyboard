package com.genz.keyboard

import android.content.Context

/**
 * Creating and interacting with design posts.
 *
 * Media is uploaded **before** the row is created: a post whose image failed to upload would show a
 * broken tile to everyone, and deleting the row afterwards is a race. If the row insert then fails,
 * the uploaded object is removed so no orphan is left behind.
 */
object CommunityPosts {

    sealed interface State {
        data object Idle : State
        data object Validating : State
        data class Uploading(val fraction: Float) : State
        data object Saving : State
        data class Done(val message: String) : State
        data class Failed(val reason: String, val retryable: Boolean) : State
    }

    private fun token(context: Context): String? = CommunityAuth.currentToken(context)

    /**
     * Create a post, optionally with one image and one attached theme.
     *
     * @param image the picked image bytes, already validated by [PostRules.validateImage]
     * @param imageName its file name, used only to derive the extension
     */
    fun create(
        context: Context,
        title: String,
        body: String,
        tags: List<String>,
        themeId: String?,
        image: ByteArray?,
        imageName: String?,
        onState: (State) -> Unit
    ): State {
        onState(State.Validating)

        val normalised = PostRules.normaliseTags(tags)
        PostRules.validate(title, body, normalised)?.let { return State.Failed(it, retryable = false).also(onState) }

        if (image != null) {
            val name = imageName ?: return State.Failed("Image has no file name", retryable = false).also(onState)
            PostRules.validateImage(name, image)?.let { return State.Failed(it, retryable = false).also(onState) }
        }

        val t = token(context) ?: return State.Failed("Sign in to post", retryable = false).also(onState)
        val userId = AuthResponse.subjectOf(t)
            ?: return State.Failed("Could not read your account id", retryable = false).also(onState)

        var uploadedPath: String? = null
        if (image != null) {
            val ext = imageName!!.substringAfterLast('.', "png").lowercase()
            val path = "$userId/post_${System.currentTimeMillis()}.$ext"
            onState(State.Uploading(0.3f))
            when (val up = CommunityApi.uploadObject(t, "post-images", path, image, PublishRules.mimeType(ext))) {
                is CommunityResult.Ok -> {
                    uploadedPath = path
                    onState(State.Uploading(1f))
                }
                is CommunityResult.Unauthorized -> return State.Failed("Your session expired. Sign in again.", retryable = false).also(onState)
                is CommunityResult.NotConfigured -> return State.Failed("Community is not configured", retryable = false).also(onState)
                is CommunityResult.NetworkError -> return State.Failed("Image upload failed: check your connection", retryable = true).also(onState)
                is CommunityResult.ServerError -> return State.Failed("Image upload rejected (${up.code})", retryable = false).also(onState)
            }
        }

        onState(State.Saving)
        val result = CommunityApi.createPost(
            token = t,
            title = title.trim(),
            body = body.trim(),
            tags = normalised,
            imagePaths = listOfNotNull(uploadedPath),
            themeId = themeId
        )
        return when (result) {
            is CommunityResult.Ok -> State.Done("Posted").also(onState)
            is CommunityResult.Unauthorized -> {
                cleanup(t, uploadedPath)
                State.Failed("Your session expired. Sign in again.", retryable = false).also(onState)
            }
            is CommunityResult.NotConfigured -> {
                cleanup(t, uploadedPath)
                State.Failed("Community is not configured", retryable = false).also(onState)
            }
            is CommunityResult.NetworkError -> {
                // the row may or may not exist; removing the image would break it if it does
                State.Failed("Posting failed: check your connection", retryable = true).also(onState)
            }
            is CommunityResult.ServerError -> {
                cleanup(t, uploadedPath)
                State.Failed("Post rejected (${result.code})", retryable = false).also(onState)
            }
        }
    }

    /** Remove an upload that no row references. Best effort: a leftover file is harmless. */
    private fun cleanup(token: String, path: String?) {
        if (path == null || !DownloadGuard.isSafeObjectPath(path)) return
        CommunityApi.deleteObject(token, "post-images", path)
    }

    /** Edit an owned post. */
    fun edit(context: Context, postId: String, title: String, body: String, tags: List<String>): State {
        val normalised = PostRules.normaliseTags(tags)
        PostRules.validate(title, body, normalised)?.let { return State.Failed(it, retryable = false) }
        val t = token(context) ?: return State.Failed("Sign in to edit", retryable = false)
        return when (val r = CommunityApi.updatePost(t, postId, title.trim(), body.trim(), normalised)) {
            is CommunityResult.Ok -> State.Done("Post updated")
            is CommunityResult.Unauthorized -> State.Failed("You can only edit your own post", retryable = false)
            is CommunityResult.NotConfigured -> State.Failed("Community is not configured", retryable = false)
            is CommunityResult.NetworkError -> State.Failed("Could not reach the server", retryable = true)
            is CommunityResult.ServerError -> State.Failed("Edit rejected (${r.code})", retryable = false)
        }
    }

    fun delete(context: Context, postId: String): State {
        val t = token(context) ?: return State.Failed("Sign in to delete", retryable = false)
        return when (val r = CommunityApi.deletePost(t, postId)) {
            is CommunityResult.Ok -> State.Done("Post deleted")
            is CommunityResult.Unauthorized -> State.Failed("You can only delete your own post", retryable = false)
            is CommunityResult.NotConfigured -> State.Failed("Community is not configured", retryable = false)
            is CommunityResult.NetworkError -> State.Failed("Could not reach the server", retryable = true)
            is CommunityResult.ServerError -> State.Failed("Delete rejected (${r.code})", retryable = false)
        }
    }

    /** Like or unlike. A duplicate like is a 409, which means the desired state already holds. */
    fun toggleLike(context: Context, postId: String, currentlyLiked: Boolean): State {
        val t = token(context) ?: return State.Failed("Sign in to like posts", retryable = false)
        val result = if (currentlyLiked) CommunityApi.unlikePost(t, postId) else CommunityApi.likePost(t, postId)
        return when (result) {
            is CommunityResult.Ok -> State.Done(if (currentlyLiked) "Unliked" else "Liked")
            is CommunityResult.Unauthorized -> State.Failed("Sign in to like posts", retryable = false)
            is CommunityResult.NotConfigured -> State.Failed("Community is not configured", retryable = false)
            is CommunityResult.NetworkError -> State.Failed("Could not reach the server", retryable = true)
            is CommunityResult.ServerError ->
                if (result.code == 409) State.Done("Already liked") else State.Failed("Could not update", retryable = false)
        }
    }

    fun addComment(context: Context, postId: String, body: String): State {
        PostRules.validateComment(body)?.let { return State.Failed(it, retryable = false) }
        val t = token(context) ?: return State.Failed("Sign in to comment", retryable = false)
        return when (val r = CommunityApi.addComment(t, postId, body.trim())) {
            is CommunityResult.Ok -> State.Done("Comment added")
            is CommunityResult.Unauthorized -> State.Failed("Sign in to comment", retryable = false)
            is CommunityResult.NotConfigured -> State.Failed("Community is not configured", retryable = false)
            is CommunityResult.NetworkError -> State.Failed("Could not reach the server", retryable = true)
            is CommunityResult.ServerError -> State.Failed("Comment rejected (${r.code})", retryable = false)
        }
    }

    fun deleteComment(context: Context, commentId: String): State {
        val t = token(context) ?: return State.Failed("Sign in to comment", retryable = false)
        return when (val r = CommunityApi.deleteComment(t, commentId)) {
            is CommunityResult.Ok -> State.Done("Comment deleted")
            is CommunityResult.Unauthorized -> State.Failed("You can only delete your own comment", retryable = false)
            is CommunityResult.NotConfigured -> State.Failed("Community is not configured", retryable = false)
            is CommunityResult.NetworkError -> State.Failed("Could not reach the server", retryable = true)
            is CommunityResult.ServerError -> State.Failed("Delete rejected (${r.code})", retryable = false)
        }
    }
}
