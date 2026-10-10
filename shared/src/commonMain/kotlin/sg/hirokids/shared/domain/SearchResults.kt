package sg.hirokids.shared.domain

/** The two result groups that come from the current library (spec section 5, steps 3 and 4). */
enum class ResultGroup {
    /** Titles the search reports on the shelf at the current library. */
    ON_SHELF,

    /** Titles the library holds, whichever copies are out. The ones also on the shelf are left to [ON_SHELF]. */
    ALL_HERE,
}

/**
 * One card in the results: a title group from NLB with only the records [AudiencePolicy] allowed ([title] is the first of them).
 * [reportedOnShelfHere] is what the search said for the current library; it is not a count of copies (spec section 9).
 */
data class SearchHit(
    val title: Title,
    val records: List<Title>,
    val reportedOnShelfHere: Boolean,
)

/** One page of results. [hidden] counts the groups the audience gate removed; it is for tests and reports, never shown. */
data class SearchPage(
    val hits: List<SearchHit>,
    val hasMore: Boolean,
    val nextOffset: Int?,
    val hidden: Int,
)
