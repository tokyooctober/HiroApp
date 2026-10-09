package sg.hirokids.shared.domain

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
