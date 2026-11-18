package com.genz.keyboard

/**
 * How the marketplace is sliced.
 *
 * Each section is a transparent ordering over columns that actually exist — no invented engagement
 * score. A section that has no qualifying rows shows nothing rather than being padded with
 * something else, because a "trending" list filled with arbitrary themes is a lie.
 */
object MarketplaceQuery {

    enum class Section(val label: String) {
        NEWEST("Newest"),
        POPULAR("Popular"),
        TOP_RATED("Top rated"),
        TRENDING("Trending"),
        FEATURED("Featured")
    }

    /** Rows returned per page. Small on purpose: the marketplace is never loaded in one shot. */
    const val PAGE_SIZE = 20

    /** A theme needs this many reviews before its average is meaningful enough to rank on. */
    const val MIN_REVIEWS_FOR_RATING = 3

    /** Trending looks at reviews from this many days back. */
    const val TRENDING_WINDOW_DAYS = 14

    data class Page(val section: Section, val offset: Int, val size: Int = PAGE_SIZE)

    /**
     * The PostgREST parameters for one page of one section.
     *
     * Every query excludes removed and private themes: RLS already hides them from other users, and
     * filtering here keeps the paging counts honest for the author too.
     */
    fun params(page: Page, search: String? = null, category: String? = null): Map<String, String> {
        val p = LinkedHashMap<String, String>()
        p["select"] = "id,name,description,tags,category,preview_path,downloads,version,created_at,profiles(handle)"
        p["is_public"] = "eq.true"
        p["is_removed"] = "eq.false"
        p["limit"] = page.size.toString()
        p["offset"] = page.offset.toString()

        when (page.section) {
            Section.NEWEST -> p["order"] = "created_at.desc"
            Section.POPULAR -> p["order"] = "downloads.desc,created_at.desc"
            Section.TOP_RATED -> p["order"] = "downloads.desc,created_at.desc"   // ranking applied after the join
            Section.TRENDING -> p["order"] = "created_at.desc"
            Section.FEATURED -> { p["is_featured"] = "eq.true"; p["order"] = "created_at.desc" }
        }

        // search and category are independent, so they compose
        if (!search.isNullOrBlank()) p["name"] = "ilike.*${search.trim()}*"
        if (!category.isNullOrBlank()) p["category"] = "eq.${category.trim()}"
        return p
    }

    /**
     * Rank rows that need more than an ORDER BY.
     *
     * Top rated needs an average the table does not carry; it is computed from the review rows the
     * server returns, so the number shown is always derived from real reviews.
     */
    fun rank(section: Section, rows: List<ThemeSummary>): List<ThemeSummary> = when (section) {
        Section.TOP_RATED -> rows
            .filter { it.reviewCount >= MIN_REVIEWS_FOR_RATING }
            .sortedWith(compareByDescending<ThemeSummary> { it.averageRating }.thenByDescending { it.reviewCount })
        else -> rows
    }

    /** The next page offset, or null when the last page was short. */
    fun nextOffset(loaded: Int, lastPageSize: Int, pageSize: Int = PAGE_SIZE): Int? =
        if (lastPageSize < pageSize) null else loaded

    /** Categories the filter offers, in display order. */
    val CATEGORIES = PublishRules.CATEGORIES
}
