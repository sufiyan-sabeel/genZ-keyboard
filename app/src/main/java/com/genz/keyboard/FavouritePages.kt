package com.genz.keyboard

/**
 * Paging rules for the saved-themes list.
 *
 * Kept pure because the failure modes here are subtle: a page appended twice, or a saved theme that
 * was unpublished between pages, both show up as wrong content rather than as an error.
 */
object FavouritePages {

    /**
     * Append a page, dropping rows already shown.
     *
     * A theme can disappear between pages when it is unpublished or removed, which shifts the
     * offsets; de-duplicating by id makes that harmless instead of showing a duplicate.
     */
    fun merge(existing: List<ThemeSummary>, incoming: List<ThemeSummary>): List<ThemeSummary> {
        val seen = existing.mapTo(HashSet()) { it.id }
        return existing + incoming.filter { seen.add(it.id) }
    }

    /** The list is exhausted once a page comes back short. */
    fun isExhausted(pageSize: Int): Boolean = pageSize < MarketplaceQuery.PAGE_SIZE

    /** Removing a saved theme is local to the list; the server already dropped the favourite row. */
    fun without(items: List<ThemeSummary>, themeId: String): List<ThemeSummary> =
        items.filter { it.id != themeId }

    /**
     * A saved list belongs to one account.
     *
     * If the signed-in user changes, the previous account's themes must not remain on screen.
     */
    fun belongsTo(cachedOwnerId: String?, currentUserId: String?): Boolean =
        cachedOwnerId != null && currentUserId != null && cachedOwnerId == currentUserId
}
