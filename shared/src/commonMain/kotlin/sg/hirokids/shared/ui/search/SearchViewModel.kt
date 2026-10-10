package sg.hirokids.shared.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sg.hirokids.shared.data.DirectorySource
import sg.hirokids.shared.data.NlbRepository
import sg.hirokids.shared.data.SearchQuery
import sg.hirokids.shared.data.parseSearchQuery
import sg.hirokids.shared.domain.Library
import sg.hirokids.shared.domain.Mode
import sg.hirokids.shared.domain.OtherLibrary
import sg.hirokids.shared.domain.ResultGroup
import sg.hirokids.shared.domain.ResultGrouper
import sg.hirokids.shared.domain.SearchHit
import sg.hirokids.shared.domain.SearchPage
import sg.hirokids.shared.domain.Title

enum class SearchScreen { HOME, RESULTS }

/** One paged list of cards: "on the shelf here", or "in this library, but all copies out". */
data class Shelf(
    val hits: List<SearchHit> = emptyList(),
    /** Where the next page starts, or null when the group has no more (the screen then shows the end of the results). */
    val nextOffset: Int? = 0,
    val loading: Boolean = false,
    /** The last request for this group failed, after any back-off. The group waits for an explicit retry. */
    val failed: Boolean = false,
)

/** "More on the shelf at other libraries" (FR-15). Empty and not loading or failed means no other library has the book. */
data class OtherLibraries(
    val items: List<OtherLibrary> = emptyList(),
    val loading: Boolean = false,
    val failed: Boolean = false,
)

sealed interface ResultsState {
    data object Idle : ResultsState

    data object Loading : ResultsState

    /** [onLoan] and [others] are null for an ISBN lookup, which has one list only. */
    data class Loaded(
        val onShelf: Shelf,
        val onLoan: Shelf?,
        val others: OtherLibraries? = null,
    ) : ResultsState

    /** Nothing to show: no matches, or every match was hidden by the audience gate. */
    data object Empty : ResultsState

    /** Offline, a proxy error, or the proxy staying busy (HTTP 429). [saved] are earlier results from this phone that match. */
    data class Failed(
        val saved: List<Title>,
    ) : ResultsState
}

data class SearchUiState(
    val query: String = "",
    val screen: SearchScreen = SearchScreen.HOME,
    /** The user pressed Search with fewer than 2 characters. */
    val tooShort: Boolean = false,
    val results: ResultsState = ResultsState.Idle,
    /** The words the current [results] are for, which may differ from [query] if the user has typed since. */
    val searchedQuery: String = "",
    /** The library whose books are shown after View (FR-16), or null for the user's own current library. Never saved. */
    val viewing: Library? = null,
)

/** The results the user left when they tapped View, so Back can bring them back without asking NLB again. */
private class Snapshot(
    val viewing: Library?,
    val results: ResultsState,
    val searchedQuery: String,
)

/** Everything in flight for the current search, so a newer search or Back can cancel it all. */
private class SearchJobs {
    var search: Job? = null
    var others: Job? = null
    val pages = mutableMapOf<ResultGroup, Job>()

    fun cancelAll() {
        search?.cancel()
        others?.cancel()
        pages.values.forEach { it.cancel() }
        pages.clear()
    }
}

/**
 * Search home and results in Children mode (FR-1, FR-4, FR-5, FR-11, FR-12, FR-15, FR-16). The library is passed in with each
 * call; after View it is the library being viewed ([SearchUiState.viewing]), which the screen passes instead of the current one.
 */
class SearchViewModel(
    private val repository: NlbRepository,
    private val directory: DirectorySource,
    private val mode: Mode = Mode.CHILDREN,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val jobs = SearchJobs()
    private val history = ArrayDeque<Snapshot>()

    private val otherLibraries: suspend (SearchQuery.Keywords, Library) -> List<OtherLibrary> = { query, library ->
        ResultGrouper.otherLibraries(repository.shelfCounts(query, mode), library, directory.directory())
    }

    fun onQueryChange(text: String) {
        _state.update { it.copy(query = text, tooShort = false) }
    }

    fun submit(library: Library) {
        val query = parseSearchQuery(_state.value.query)
        if (query == null) {
            _state.update { it.copy(tooShort = true) }
            return
        }
        search(query, library)
    }

    /** Starts the last search again, even if the field has been edited since. */
    fun retry(library: Library) {
        val query = parseSearchQuery(_state.value.searchedQuery) ?: return
        search(query, library)
    }

    /**
     * FR-16: look at the same search at [target]. The results being left are kept; [from] is the library now on screen.
     * Nothing is saved: the user's own current library does not change.
     */
    fun view(
        target: Library,
        from: Library,
    ) {
        val query = parseSearchQuery(_state.value.searchedQuery) ?: return
        if (target.code.equals(from.code, ignoreCase = true)) return
        val now = _state.value
        history.addLast(Snapshot(now.viewing, now.results.forSnapshot(), now.searchedQuery))
        _state.update { it.copy(viewing = target) }
        search(query, target)
    }

    /** One step back: to the results of the library viewed before, or, with none left, to the search home. */
    fun back() {
        jobs.cancelAll()
        val previous = history.removeLastOrNull()
        _state.update {
            if (previous == null) {
                it.copy(screen = SearchScreen.HOME, results = ResultsState.Idle, viewing = null, tooShort = false)
            } else {
                it.copy(
                    screen = SearchScreen.RESULTS,
                    results = previous.results,
                    searchedQuery = previous.searchedQuery,
                    viewing = previous.viewing,
                    tooShort = false,
                )
            }
        }
    }

    /** The user reached the end of [group]: load its next page. Ignored while a page is loading, after a failure, or at the end. */
    fun loadMore(
        library: Library,
        group: ResultGroup,
    ) {
        val shelf = _state.value.shelf(group) ?: return
        val offset = shelf.nextOffset ?: return
        if (!shelf.loading && !shelf.failed) fetchPage(library, group, offset)
    }

    /** The Try again button of a group whose last request failed. */
    fun retryGroup(
        library: Library,
        group: ResultGroup,
    ) {
        val shelf = _state.value.shelf(group) ?: return
        if (shelf.failed && !shelf.loading) fetchPage(library, group, shelf.nextOffset ?: 0)
    }

    /** The Try again button of the other-libraries box. */
    fun retryOthers(library: Library) {
        val query = parseSearchQuery(_state.value.searchedQuery) as? SearchQuery.Keywords ?: return
        val others = (_state.value.results as? ResultsState.Loaded)?.others ?: return
        if (others.loading) return
        jobs.others = viewModelScope.launch { _state.fillOthers(otherLibraries, query, library) }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun search(
        query: SearchQuery,
        library: Library,
    ) {
        jobs.cancelAll() // a newer search replaces an older one, so a slow answer never overwrites a fresh one
        val words =
            when (query) {
                is SearchQuery.Keywords -> query.text
                is SearchQuery.Isbn -> query.value
            }
        _state.update { it.copy(screen = SearchScreen.RESULTS, results = ResultsState.Loading, tooShort = false, searchedQuery = words) }
        jobs.search =
            viewModelScope.launch {
                try {
                    val first = repository.search(query, library, mode)
                    val keywords = query as? SearchQuery.Keywords
                    val loaded =
                        ResultsState.Loaded(
                            onShelf = Shelf(first.hits, first.nextOffset),
                            onLoan = keywords?.let { Shelf(nextOffset = 0, loading = true) },
                            others = keywords?.let { OtherLibraries(loading = true) },
                        )
                    _state.update { it.copy(results = loaded.normalised()) }
                    if (keywords != null) {
                        // the three calls of one search go one after the other: the proxy queues them at 1 call a second anyway
                        fetchPage(library, ResultGroup.ALL_HERE, 0)?.join()
                        _state.fillOthers(otherLibraries, keywords, library)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _state.update { it.copy(results = ResultsState.Failed(repository.saved(query, mode))) }
                }
            }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun fetchPage(
        library: Library,
        group: ResultGroup,
        offset: Int,
    ): Job? {
        val query = parseSearchQuery(_state.value.searchedQuery) ?: return null
        _state.changeShelf(group) { it.copy(loading = true, failed = false) }
        val job =
            viewModelScope.launch {
                try {
                    val page = repository.search(query, library, mode, offset, group)
                    _state.update { s ->
                        (s.results as? ResultsState.Loaded)?.let { s.copy(results = it.withPage(group, page).normalised()) }
                            ?: s
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _state.changeShelf(group) { it.copy(loading = false, failed = true) }
                }
            }
        jobs.pages[group] = job
        return job
    }
}

private fun SearchUiState.shelf(group: ResultGroup): Shelf? =
    (results as? ResultsState.Loaded)?.let { if (group == ResultGroup.ON_SHELF) it.onShelf else it.onLoan }

/** Edits one group's [Shelf] in place, if results are showing. */
private fun MutableStateFlow<SearchUiState>.changeShelf(
    group: ResultGroup,
    edit: (Shelf) -> Shelf,
) {
    update { s ->
        val loaded = s.results as? ResultsState.Loaded ?: return@update s
        val next =
            when (group) {
                ResultGroup.ON_SHELF -> loaded.copy(onShelf = edit(loaded.onShelf))
                ResultGroup.ALL_HERE -> loaded.copy(onLoan = loaded.onLoan?.let(edit))
            }
        s.copy(results = next.normalised())
    }
}

private fun MutableStateFlow<SearchUiState>.editOthers(edit: (OtherLibraries) -> OtherLibraries) {
    update { s ->
        val loaded = s.results as? ResultsState.Loaded ?: return@update s
        val others = loaded.others ?: return@update s
        s.copy(results = loaded.copy(others = edit(others)).normalised())
    }
}

/** Asks for the other libraries and puts the answer in; a failure leaves everything else on screen and offers Try again. */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
private suspend fun MutableStateFlow<SearchUiState>.fillOthers(
    load: suspend (SearchQuery.Keywords, Library) -> List<OtherLibrary>,
    query: SearchQuery.Keywords,
    library: Library,
) {
    editOthers { it.copy(loading = true, failed = false) }
    val result =
        try {
            OtherLibraries(items = load(query, library))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            OtherLibraries(failed = true)
        }
    editOthers { result }
}

private fun brns(hits: List<SearchHit>): Set<Long> = hits.flatMap { hit -> hit.records.map { it.brn } }.toSet()

private fun List<SearchHit>.withoutAny(taken: Set<Long>) = filter { hit -> hit.records.none { it.brn in taken } }

/**
 * Adds a page to one group. A book is in one group only: the shelf wins, so a title that turns up on the shelf on a later page
 * leaves "all copies out", and nothing is listed twice. Within a page "all copies out" is ordered by fewest waiting; cards
 * already on screen never move.
 */
private fun ResultsState.Loaded.withPage(
    group: ResultGroup,
    page: SearchPage,
): ResultsState.Loaded =
    when (group) {
        ResultGroup.ON_SHELF -> {
            val fresh = page.hits.withoutAny(brns(onShelf.hits))
            copy(
                onShelf = onShelf.copy(hits = onShelf.hits + fresh, nextOffset = page.nextOffset, loading = false, failed = false),
                onLoan = onLoan?.let { it.copy(hits = it.hits.withoutAny(brns(fresh))) },
            )
        }
        ResultGroup.ALL_HERE -> {
            val current = onLoan ?: Shelf()
            val fresh = page.hits.withoutAny(brns(onShelf.hits) + brns(current.hits)).sortedBy { it.title.reservations }
            copy(onLoan = current.copy(hits = current.hits + fresh, nextOffset = page.nextOffset, loading = false, failed = false))
        }
    }

/**
 * Everything finished with nothing to show is the empty state. Anything still loading, failed or pageable is not, and neither is a
 * search whose only hits are at other libraries: that is the useful answer, "not here, but there".
 */
private fun ResultsState.Loaded.normalised(): ResultsState {
    fun Shelf.exhausted() = hits.isEmpty() && nextOffset == null && !loading && !failed
    val shelvesEmpty = onShelf.exhausted() && (onLoan == null || onLoan.exhausted())
    val noOthers = others == null || (others.items.isEmpty() && !others.loading && !others.failed)
    return if (shelvesEmpty && noOthers) ResultsState.Empty else this
}

/**
 * Results that are put aside for Back must not come back mid-load: a spinner that nothing is working on would never stop. Whatever
 * was loading becomes a failure with Try again, resuming from the same place.
 */
internal fun ResultsState.forSnapshot(): ResultsState {
    fun Shelf.settled() = if (loading) copy(loading = false, failed = true) else this
    return when (this) {
        ResultsState.Loading -> ResultsState.Failed(emptyList())
        is ResultsState.Loaded ->
            copy(
                onShelf = onShelf.settled(),
                onLoan = onLoan?.settled(),
                others = others?.let { if (it.loading) it.copy(loading = false, failed = true) else it },
            )
        else -> this
    }
}
