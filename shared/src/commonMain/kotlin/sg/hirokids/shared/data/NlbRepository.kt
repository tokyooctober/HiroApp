package sg.hirokids.shared.data

import sg.hirokids.shared.domain.AudiencePolicy
import sg.hirokids.shared.domain.Library
import sg.hirokids.shared.domain.Mode
import sg.hirokids.shared.domain.ResultGroup
import sg.hirokids.shared.domain.SearchHit
import sg.hirokids.shared.domain.SearchPage
import sg.hirokids.shared.domain.Title

/**
 * Search through the proxy (FR-1). Everything that comes back passes [AudiencePolicy] before it is returned or stored, so a
 * screen cannot show what the policy hides. Network errors are not caught here: the screen offers Retry (FR-12).
 */
class NlbRepository(
    private val api: ProxyApi,
    private val store: BookStore,
    private val retry: RetryPolicy = RetryPolicy(),
) {
    /**
     * One page of [group] for [query]. [offset] is `nextOffset` of the previous page of the same group. Only the branch code
     * of [library] is sent (NFR-5). A busy proxy (HTTP 429) is retried with back-off before the error is thrown.
     */
    suspend fun search(
        query: SearchQuery,
        library: Library,
        mode: Mode,
        offset: Int = 0,
        group: ResultGroup = ResultGroup.ON_SHELF,
    ): SearchPage {
        val policy = AudiencePolicy(mode)
        return when (query) {
            is SearchQuery.Keywords -> {
                val onShelf = group == ResultGroup.ON_SHELF
                val response =
                    retry.run {
                        api.searchTitles(
                            SearchTitlesRequest(
                                keywords = query.text,
                                audience = mode.intendedAudience(),
                                location = library.code.lowercase(), // NLB filters on the lower-case branch code
                                availableOnly = onShelf,
                                offset = offset,
                            ),
                        )
                    }
                page(response.titles.map { it to it.records }, response.hasMoreRecords, response.nextRecordsOffset, policy, onShelf)
            }
            is SearchQuery.Isbn ->
                if (group == ResultGroup.ALL_HERE) {
                    SearchPage(emptyList(), hasMore = false, nextOffset = null, hidden = 0) // a lookup by ISBN has one list only
                } else {
                    // GetTitles has no audience filter, so the policy alone decides; nothing says where it is on the shelf
                    val response = retry.run { api.titlesByIsbn(query.value, offset) }
                    page(response.titles.map { null to listOf(it) }, response.hasMoreRecords, response.nextRecordsOffset, policy, false)
                }
        }
    }

    /**
     * Group 3 (FR-15): one search for books on the shelf anywhere, read for its `location` facet only. The result is branch code
     * (lower case, as NLB sends it) to number of books. Nothing about the user is sent: no branch, no position.
     */
    suspend fun shelfCounts(
        query: SearchQuery.Keywords,
        mode: Mode,
    ): Map<String, Int> {
        val response =
            retry.run {
                api.searchTitles(
                    SearchTitlesRequest(keywords = query.text, audience = mode.intendedAudience(), location = null, availableOnly = true),
                )
            }
        return response.facets
            .firstOrNull { it.id == "location" }
            ?.values
            .orEmpty()
            .associate { it.id.lowercase() to it.count }
    }

    /** A title kept from an earlier search, read without any network call. */
    fun stored(brn: Long): Title? = store.get(brn)

    /**
     * Saved titles that match what was typed, for when the network is down (FR-12). They are checked by [AudiencePolicy] again
     * on the way out, and carry no claim about the shelf.
     */
    fun saved(
        query: SearchQuery,
        mode: Mode,
        limit: Int = SEARCH_PAGE_SIZE,
    ): List<Title> {
        val words =
            when (query) {
                is SearchQuery.Keywords -> query.text
                is SearchQuery.Isbn -> query.value
            }
        val policy = AudiencePolicy(mode)
        return store.search(words, limit).filter { policy.allows(it) }
    }

    private fun page(
        groups: List<Pair<TitleGroupDto?, List<RecordDto>>>,
        hasMore: Boolean,
        nextOffset: Int,
        policy: AudiencePolicy,
        reportedOnShelf: Boolean,
    ): SearchPage {
        val seen = mutableSetOf<Long>()
        val hits = mutableListOf<SearchHit>()
        var hidden = 0
        for ((group, records) in groups) {
            val allowed = records.map { it.toTitle(group) }.filter { policy.allows(it) && seen.add(it.brn) }
            if (allowed.isEmpty()) hidden++ else hits += SearchHit(allowed.first(), allowed, reportedOnShelf)
        }
        store.put(hits.flatMap { it.records })
        return SearchPage(hits, hasMore, nextOffset.takeIf { hasMore }, hidden)
    }

    private fun Mode.intendedAudience() =
        when (this) {
            Mode.CHILDREN -> "juvenile"
            Mode.ADULT -> "adult"
        }
}
