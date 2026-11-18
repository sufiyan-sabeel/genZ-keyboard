package com.genz.keyboard

/** A theme as the marketplace lists it. */
data class ThemeSummary(
    val id: String,
    val name: String,
    val authorHandle: String,
    val description: String,
    val tags: List<String>,
    val category: String,
    val previewUrl: String?,
    val averageRating: Double,
    val reviewCount: Int,
    val downloads: Int,
    val version: Int,
    /** Needed to open the creator's profile: a handle is not unique enough to navigate by. */
    val authorId: String? = null
)

/** A review as shown on a theme page. */
data class ThemeReview(
    val id: String,
    val authorHandle: String,
    val rating: Int,
    val body: String,
    val createdAt: String,
    val authorId: String? = null
)

/** Maps PostgREST rows to models. Pure, so the mapping is tested without a server. */
object CommunityModels {

    fun theme(row: Map<String, Any?>, storageBase: String): ThemeSummary? {
        val id = row["id"] as? String ?: return null
        val name = row["name"] as? String ?: return null
        val preview = row["preview_path"] as? String
        return ThemeSummary(
            id = id,
            name = name,
            authorHandle = ((row["profiles"] as? Map<*, *>)?.get("handle") as? String).orEmpty(),
            description = (row["description"] as? String).orEmpty(),
            tags = (row["tags"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
            category = (row["category"] as? String).orEmpty(),
            previewUrl = preview?.takeIf { it.isNotBlank() }
                ?.let { CommunityUrls.storage(storageBase, "theme-previews", it) },
            averageRating = (row["average_rating"] as? Number)?.toDouble() ?: 0.0,
            reviewCount = (row["review_count"] as? Number)?.toInt() ?: 0,
            downloads = (row["downloads"] as? Number)?.toInt() ?: 0,
            version = (row["version"] as? Number)?.toInt() ?: 1,
            authorId = row["author_id"] as? String
        )
    }

    fun review(row: Map<String, Any?>): ThemeReview? {
        val id = row["id"] as? String ?: return null
        return ThemeReview(
            id = id,
            authorHandle = ((row["profiles"] as? Map<*, *>)?.get("handle") as? String).orEmpty(),
            rating = (row["rating"] as? Number)?.toInt() ?: 0,
            body = (row["body"] as? String).orEmpty(),
            createdAt = (row["created_at"] as? String).orEmpty(),
            authorId = row["author_id"] as? String
        )
    }

    fun post(row: Map<String, Any?>): DesignPost? = PostModels.post(row)

    fun comment(row: Map<String, Any?>): PostComment? = PostModels.comment(row)

    fun themes(rows: List<Map<String, Any?>>, storageBase: String): List<ThemeSummary> =
        rows.mapNotNull { theme(it, storageBase) }
}
