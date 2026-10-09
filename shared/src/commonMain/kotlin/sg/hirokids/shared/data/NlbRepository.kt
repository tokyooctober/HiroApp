package sg.hirokids.shared.data

import sg.hirokids.shared.domain.AudiencePolicy
import sg.hirokids.shared.domain.Library
import sg.hirokids.shared.domain.Mode
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
) {
    /** [offset] is `nextOffset` of the previous page. Only the branch code of [library] is sent (NFR-5). */
    suspend fun search(
        query: SearchQuery,
        library: Library,
        mode: Mode,
        offset: Int = 0,
    ): SearchPage {
        val policy = AudiencePolicy(mode)
        return when (query) {
            is SearchQuery.Keywords -> {
                val response =
                    api.searchTitles(
                        SearchTitlesRequest(
                            keywords = query.text,
                            audience = mode.intendedAudience(),
                            location = library.code.lowercase(), // NLB filters on the lower-case branch code
                            availableOnly = true,
                            offset = offset,
                        ),
                    )
                page(response.titles.map { it to it.records }, response.hasMoreRecords, response.nextRecordsOffset, policy, true)
            }
            is SearchQuery.Isbn -> {
                // GetTitles has no audience filter, so the policy alone decides; nothing says where it is on the shelf
                val response = api.titlesByIsbn(query.value, offset)
                page(response.titles.map { null to listOf(it) }, response.hasMoreRecords, response.nextRecordsOffset, policy, false)
            }
        }
    }

    /** A title kept from an earlier search, read without any network call. */
    fun stored(brn: Long): Title? = store.get(brn)

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
