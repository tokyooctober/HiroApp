package sg.hirokids.shared.data

import sg.hirokids.shared.domain.AudiencePolicy
import sg.hirokids.shared.domain.BookLanguage
import sg.hirokids.shared.domain.Copy
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
                                language = query.language.facetId,
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
                    SearchTitlesRequest(
                        keywords = query.text,
                        audience = mode.intendedAudience(),
                        location = null,
                        availableOnly = true,
                        language = query.language.facetId,
                    ),
                )
            }
        return response.facets
            .firstOrNull { it.id == "location" }
            ?.values
            .orEmpty()
            .associate { it.id.lowercase() to it.count }
    }

    /**
     * Every copy of [records] (one title can be several records) at every branch, for the book detail (FR-6). Only copies that pass
     * [AudiencePolicy] are returned. NLB sends copies in pages; they are read one after the other, to the last, because the copies
     * at the current library can be on any page. A busy proxy is retried like the other calls.
     */
    suspend fun copies(
        records: List<Title>,
        mode: Mode,
    ): List<Copy> {
        val all = mutableListOf<Copy>()
        for (record in records.take(MAX_RECORDS_PER_DETAIL)) {
            var offset = 0
            for (page in 0 until MAX_AVAILABILITY_PAGES) {
                val response = retry.run { api.availabilityInfo(record.brn, offset) }
                all += response.items.mapNotNull { it.toCopy() }
                if (!response.hasMoreRecords) break
                offset = response.nextRecordsOffset
            }
        }
        return AudiencePolicy(mode).countableCopies(all)
    }

    /**
     * The title with its summary. A summary kept from an earlier visit is returned without a call (FR-14); otherwise one
     * `GetTitleDetails` call is made and the result is kept. If the full record no longer passes [AudiencePolicy], null: do not show it.
     */
    suspend fun withSummary(
        title: Title,
        mode: Mode,
    ): Title? {
        if (title.summary != null) return title
        val details = retry.run { api.titleDetails(title.brn) }
        val full = details.toTitle().copy(summary = details.summary.joinToString(" ").trim())
        // the search record is what the card showed; the details add the summary and may know more about the audience
        val merged = title.copy(summary = full.summary, audience = full.audience.ifEmpty { title.audience })
        if (!AudiencePolicy(mode).allows(merged)) return null
        store.put(listOf(merged))
        return merged
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
        val language = (query as? SearchQuery.Keywords)?.language ?: BookLanguage.ALL
        val policy = AudiencePolicy(mode)
        return store.search(words, MAX_SAVED_CANDIDATES).filter { policy.allows(it) && language.allows(it) }.take(limit)
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

    private companion object {
        /** Saved titles are read in a larger batch than a page, so the language filter still leaves a page's worth. */
        const val MAX_SAVED_CANDIDATES = 100

        /** A title group is nearly always one record (18 of 20 in the recorded data); this bounds the calls for the rare big one. */
        const val MAX_RECORDS_PER_DETAIL = 4

        /** NLB pages hold 20 copies or more; 10 pages is far beyond any title seen, and stops a runaway loop. */
        const val MAX_AVAILABILITY_PAGES = 10
    }

    private fun Mode.intendedAudience() =
        when (this) {
            Mode.CHILDREN -> "juvenile"
            Mode.ADULT -> "adult"
        }
}
