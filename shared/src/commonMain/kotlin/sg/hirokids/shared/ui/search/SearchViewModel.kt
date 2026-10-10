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
import sg.hirokids.shared.data.NlbRepository
import sg.hirokids.shared.data.SearchQuery
import sg.hirokids.shared.data.parseSearchQuery
import sg.hirokids.shared.domain.Library
import sg.hirokids.shared.domain.Mode
import sg.hirokids.shared.domain.ResultGroup
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

sealed interface ResultsState {
    data object Idle : ResultsState

    data object Loading : ResultsState

    /** [onLoan] is null for an ISBN lookup, which has one list only. */
    data class Loaded(
        val onShelf: Shelf,
        val onLoan: Shelf?,
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
)

/**
 * Search home and results in Children mode (FR-1, FR-4, FR-5, FR-11, FR-12). The library is passed in with each call, so it is
 * always the one the "You're at" card shows. Library switching comes in Task 9.
 */
class SearchViewModel(
    private val repository: NlbRepository,
    private val mode: Mode = Mode.CHILDREN,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private val pageJobs = mutableMapOf<ResultGroup, Job>()

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

    fun back() {
        cancelAll()
        _state.update { it.copy(screen = SearchScreen.HOME, results = ResultsState.Idle, tooShort = false) }
    }

    /** The user reached the end of [group]: load its next page. Ignored while a page is loading, after a failure, or at the end. */
    fun loadMore(
        library: Library,
        group: ResultGroup,
    ) {
        val shelf = shelf(group) ?: return
        val offset = shelf.nextOffset ?: return
        if (!shelf.loading && !shelf.failed) fetchPage(library, group, offset)
    }

    /** The Try again button of a group whose last request failed. */
    fun retryGroup(
        library: Library,
        group: ResultGroup,
    ) {
        val shelf = shelf(group) ?: return
        if (shelf.failed && !shelf.loading) fetchPage(library, group, shelf.nextOffset ?: 0)
    }

    private fun shelf(group: ResultGroup): Shelf? =
        (_state.value.results as? ResultsState.Loaded)?.let { if (group == ResultGroup.ON_SHELF) it.onShelf else it.onLoan }

    private fun cancelAll() {
        searchJob?.cancel()
        pageJobs.values.forEach { it.cancel() }
        pageJobs.clear()
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun search(
        query: SearchQuery,
        library: Library,
    ) {
        cancelAll() // a newer search replaces an older one, so a slow answer never overwrites a fresh one
        val words =
            when (query) {
                is SearchQuery.Keywords -> query.text
                is SearchQuery.Isbn -> query.value
            }
        _state.update { it.copy(screen = SearchScreen.RESULTS, results = ResultsState.Loading, tooShort = false, searchedQuery = words) }
        searchJob =
            viewModelScope.launch {
                try {
                    val first = repository.search(query, library, mode)
                    val onShelf = Shelf(first.hits, first.nextOffset)
                    val hasSecondGroup = query is SearchQuery.Keywords
                    val loaded = ResultsState.Loaded(onShelf, if (hasSecondGroup) Shelf(nextOffset = 0, loading = true) else null)
                    _state.update { it.copy(results = loaded.normalised()) }
                    if (hasSecondGroup) fetchPage(library, ResultGroup.ALL_HERE, 0)
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
    ) {
        val query = parseSearchQuery(_state.value.searchedQuery) ?: return
        _state.changeShelf(group) { it.copy(loading = true, failed = false) }
        pageJobs[group] =
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
    }
}

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

/** Both groups finished with nothing to show is the empty state; anything still loading, failed or pageable is not. */
private fun ResultsState.Loaded.normalised(): ResultsState {
    fun Shelf.exhausted() = hits.isEmpty() && nextOffset == null && !loading && !failed
    return if (onShelf.exhausted() && (onLoan == null || onLoan.exhausted())) ResultsState.Empty else this
}
