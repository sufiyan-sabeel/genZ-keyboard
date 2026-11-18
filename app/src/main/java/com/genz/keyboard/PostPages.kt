package com.genz.keyboard

/**
 * Paging rules for the design feed.
 *
 * Same failure modes as the other feeds: a page appended twice, or a post deleted between pages
 * shifting the offsets. De-duplicating by id makes both harmless.
 */
object PostPages {

    fun merge(existing: List<DesignPost>, incoming: List<DesignPost>): List<DesignPost> {
        val seen = existing.mapTo(HashSet()) { it.id }
        return existing + incoming.filter { seen.add(it.id) }
    }

    fun isExhausted(pageSize: Int): Boolean = pageSize < MarketplaceQuery.PAGE_SIZE

    fun without(items: List<DesignPost>, postId: String): List<DesignPost> = items.filter { it.id != postId }

    /** Only the author sees edit and delete controls; the server enforces it regardless. */
    fun canModify(post: DesignPost, currentUserId: String?): Boolean =
        PostRules.canModify(post.authorId, currentUserId)
}
