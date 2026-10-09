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
import sg.hirokids.shared.domain.SearchHit

enum class SearchScreen { HOME, RESULTS }

sealed interface ResultsState {
    data object Idle : ResultsState

    data object Loading : ResultsState

    data class Loaded(
        val hits: List<SearchHit>,
        val hasMore: Boolean,
    ) : ResultsState

    /** Nothing to show: no matches, or every match was hidden by the audience gate. */
    data object Empty : ResultsState

    /** Offline, a proxy error, or the proxy being busy (HTTP 429). The screen offers Retry (FR-12). */
    data object Failed : ResultsState
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
 * Search home and results in Children mode (FR-1, FR-5). The library is passed in with each search, so it is always the one
 * the "You're at" card shows. Paging, the busy back-off and library switching come in Tasks 8 and 9.
 */
class SearchViewModel(
    private val repository: NlbRepository,
    private val mode: Mode = Mode.CHILDREN,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun onQueryChange(text: String) {
        _state.update { it.copy(query = text, tooShort = false) }
    }

    fun submit(library: Library) {
        val query = parseSearchQuery(_state.value.query)
        if (query == null) {
            _state.update { it.copy(tooShort = true) }
            return
        }
        run(query, library)
    }

    /** Asks again for the words of the last search, even if the field has been edited since. */
    fun retry(library: Library) {
        val query = parseSearchQuery(_state.value.searchedQuery) ?: return
        run(query, library)
    }

    fun back() {
        job?.cancel()
        _state.update { it.copy(screen = SearchScreen.HOME, results = ResultsState.Idle, tooShort = false) }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun run(
        query: SearchQuery,
        library: Library,
    ) {
        job?.cancel() // a newer search replaces an older one, so a slow answer never overwrites a fresh one
        val words =
            when (query) {
                is SearchQuery.Keywords -> query.text
                is SearchQuery.Isbn -> query.value
            }
        _state.update { it.copy(screen = SearchScreen.RESULTS, results = ResultsState.Loading, tooShort = false, searchedQuery = words) }
        job =
            viewModelScope.launch {
                val outcome =
                    try {
                        val page = repository.search(query, library, mode)
                        if (page.hits.isEmpty()) ResultsState.Empty else ResultsState.Loaded(page.hits, page.hasMore)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        ResultsState.Failed
                    }
                _state.update { it.copy(results = outcome) }
            }
    }
}
