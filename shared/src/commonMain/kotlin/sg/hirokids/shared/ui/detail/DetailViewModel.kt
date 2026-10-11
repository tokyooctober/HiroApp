package sg.hirokids.shared.ui.detail

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
import sg.hirokids.shared.domain.BookAvailability
import sg.hirokids.shared.domain.Library
import sg.hirokids.shared.domain.Mode
import sg.hirokids.shared.domain.SearchHit
import sg.hirokids.shared.domain.Title

/** The copies part of the detail screen, which arrives after the rest. */
sealed interface CopiesState {
    data object Loading : CopiesState

    /** Offline or a proxy error, after any back-off. The rest of the screen stays; Try again asks again. */
    data object Failed : CopiesState

    data class Loaded(
        val availability: BookAvailability,
    ) : CopiesState
}

data class DetailUiState(
    /** What the card showed, plus the summary once it is known. Present from the first frame: it comes from the search result. */
    val title: Title? = null,
    /** The library whose shelf the "here" panel is about: the one on screen when the book was opened. */
    val library: Library? = null,
    val copies: CopiesState = CopiesState.Loading,
)

/**
 * Book detail (FR-6, FR-14, FR-15). [open] shows the title at once from what the search already has (or the local store), then
 * asks NLB for the copies (`GetAvailabilityInfo`, all of them) and, only if no summary is kept yet, for the summary.
 */
class DetailViewModel(
    private val repository: NlbRepository,
    private val directory: DirectorySource,
    private val mode: Mode = Mode.CHILDREN,
) : ViewModel() {
    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private var records: List<Title> = emptyList()
    private var job: Job? = null

    /** Opens [hit] at [library]. Opening the book already open (same title, same library) keeps what is on screen. */
    fun open(
        hit: SearchHit,
        library: Library,
    ) {
        val now = _state.value
        if (now.title?.brn == hit.title.brn && now.library?.code == library.code && now.copies !is CopiesState.Failed) return
        records = hit.records
        // a summary kept on the phone from an earlier visit shows at once, with no call
        val first = repository.stored(hit.title.brn)?.summary?.let { hit.title.copy(summary = it) } ?: hit.title
        _state.value = DetailUiState(title = first, library = library)
        load(library)
    }

    /** Leaves the book. The next visit asks NLB again, so a shelf seen minutes ago is never shown as current. */
    fun close() {
        job?.cancel()
        _state.value = DetailUiState()
    }

    fun retry() {
        val library = _state.value.library ?: return
        _state.update { it.copy(copies = CopiesState.Loading) }
        load(library)
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun load(library: Library) {
        job?.cancel()
        job =
            viewModelScope.launch {
                try {
                    val copies = repository.copies(records, mode)
                    val availability = BookAvailability.from(copies, library, directory.directory())
                    _state.update { it.copy(copies = CopiesState.Loaded(availability)) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _state.update { it.copy(copies = CopiesState.Failed) }
                    return@launch
                }
                loadSummary()
            }
    }

    /** The summary is a courtesy: if it cannot be fetched the screen simply has none, and the next visit tries again. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun loadSummary() {
        val title = _state.value.title ?: return
        if (title.summary != null) return
        try {
            repository.withSummary(title, mode)?.let { full -> _state.update { it.copy(title = full) } }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
    }
}
