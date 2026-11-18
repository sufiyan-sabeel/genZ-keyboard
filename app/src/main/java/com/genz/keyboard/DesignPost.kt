package com.genz.keyboard

/** A design post as the feed shows it. Counts are the server's, from real rows. */
data class DesignPost(
    val id: String,
    val authorId: String,
    val authorHandle: String,
    val authorName: String,
    val title: String,
    val body: String,
    val tags: List<String>,
    val imagePaths: List<String>,
    val themeId: String?,
    val likeCount: Int,
    val commentCount: Int,
    val createdAt: String
)

/** A comment on a design post. */
data class PostComment(
    val id: String,
    val authorId: String?,
    val authorHandle: String,
    val body: String,
    val createdAt: String
)

/** Row mapping for posts and comments. Pure, so the fallbacks are tested. */
object PostModels {

    fun post(row: Map<String, Any?>): DesignPost? {
        val id = row["id"] as? String ?: return null
        val title = (row["title"] as? String)?.trim().orEmpty()
        if (title.isEmpty()) return null
        return DesignPost(
            id = id,
            authorId = (row["author_id"] as? String).orEmpty(),
            authorHandle = (row["author_handle"] as? String).orEmpty(),
            authorName = ((row["author_name"] as? String)?.takeIf { it.isNotBlank() })
                ?: (row["author_handle"] as? String).orEmpty(),
            title = title,
            body = (row["body"] as? String).orEmpty(),
            tags = (row["tags"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
            imagePaths = (row["image_paths"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
            themeId = (row["theme_id"] as? String)?.takeIf { it.isNotBlank() },
            likeCount = (row["like_count"] as? Number)?.toInt() ?: 0,
            commentCount = (row["comment_count"] as? Number)?.toInt() ?: 0,
            createdAt = (row["created_at"] as? String).orEmpty()
        )
    }

    fun comment(row: Map<String, Any?>): PostComment? {
        val id = row["id"] as? String ?: return null
        val body = (row["body"] as? String)?.trim().orEmpty()
        if (body.isEmpty()) return null
        return PostComment(
            id = id,
            authorId = row["author_id"] as? String,
            authorHandle = ((row["profiles"] as? Map<*, *>)?.get("handle") as? String).orEmpty(),
            body = body,
            createdAt = (row["created_at"] as? String).orEmpty()
        )
    }
}
