package sg.hirokids.shared.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sg.hirokids.shared.data.CurrentLibraryStore
import sg.hirokids.shared.data.DirectorySource
import sg.hirokids.shared.domain.LatLon
import sg.hirokids.shared.domain.Library
import sg.hirokids.shared.domain.LibraryDirectory
import sg.hirokids.shared.domain.LocationProvider
import sg.hirokids.shared.domain.LocationResult

/** Why detection did not pick a library; shown above the list after the user taps "Detect the library I'm in". */
enum class DetectNotice {
    NOT_AT_A_LIBRARY,
    APPROXIMATE_ONLY,
    DENIED,
    UNAVAILABLE,
}

/** One row of the picker; [meters] is set only when the phone gave a precise location. */
data class PickerItem(
    val library: Library,
    val meters: Double?,
)

data class LibraryUiState(
    val loading: Boolean = true,
    val current: Library? = null,
    val pickerOpen: Boolean = false,
    val selectedCode: String? = null,
    val selectedName: String? = null,
    val query: String = "",
    val items: List<PickerItem> = emptyList(),
    val detecting: Boolean = false,
    val notice: DetectNotice? = null,
)

/**
 * The current library (FR-3). On open it restores the saved choice and quietly tries to detect the library (never asking for
 * permission); the picker opens when there is no choice. The user's coordinates stay inside this class: only branch codes
 * are saved, and only distances reach the screen (NFR-5).
 */
class LibraryViewModel(
    private val source: DirectorySource,
    private val store: CurrentLibraryStore,
    private val location: LocationProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    private var directory = LibraryDirectory(emptyList())
    private var userPosition: LatLon? = null
    private var userPositionIsPrecise = false

    init {
        viewModelScope.launch {
            directory = source.directory()
            val saved = directory.byCode(store.code)
            if (saved == null) store.clear() // a library that can no longer be chosen is forgotten
            _state.update { it.selecting(saved).copy(loading = false, current = saved, pickerOpen = saved == null) }
            refreshItems()
            apply(location.locate(allowPrompt = false), userInitiated = false)
        }
    }

    /** The "Detect the library I'm in" button: may show the permission dialog. */
    fun detect() {
        viewModelScope.launch {
            _state.update { it.copy(detecting = true, notice = null) }
            apply(location.locate(allowPrompt = true), userInitiated = true)
            _state.update { it.copy(detecting = false) }
        }
    }

    /** Opens the picker with the current library ticked. */
    fun change() {
        _state.update { it.selecting(it.current).copy(pickerOpen = true, query = "", notice = null) }
        refreshItems()
    }

    fun select(code: String) {
        directory.byCode(code)?.let { library -> _state.update { it.selecting(library) } }
    }

    fun search(query: String) {
        _state.update { it.copy(query = query) }
        refreshItems()
    }

    fun confirm() {
        directory.byCode(_state.value.selectedCode)?.let(::setCurrent)
    }

    /** Back out of the picker; only possible when there is a library to go back to. */
    fun closePicker() {
        val current = _state.value.current ?: return
        _state.update { it.selecting(current).copy(pickerOpen = false, query = "", notice = null) }
        refreshItems()
    }

    private fun apply(
        result: LocationResult,
        userInitiated: Boolean,
    ) {
        val notice: DetectNotice? =
            when (result) {
                is LocationResult.Precise -> {
                    userPosition = result.position
                    userPositionIsPrecise = true
                    val here = directory.detect(result.position)
                    if (here != null) setCurrent(here)
                    if (here == null) DetectNotice.NOT_AT_A_LIBRARY else null
                }
                is LocationResult.Approximate -> {
                    userPosition = result.position // good enough to order the list, never to pick one or to show a distance
                    userPositionIsPrecise = false
                    DetectNotice.APPROXIMATE_ONLY
                }
                LocationResult.Denied -> DetectNotice.DENIED
                LocationResult.Unavailable -> DetectNotice.UNAVAILABLE
            }
        // notices appear only after the user tapped Detect; the quiet attempt on open stays silent
        if (userInitiated && notice != null) _state.update { it.copy(notice = notice) }
        refreshItems()
    }

    private fun LibraryUiState.selecting(library: Library?) = copy(selectedCode = library?.code, selectedName = library?.name)

    private fun setCurrent(library: Library) {
        store.code = library.code
        _state.update { it.selecting(library).copy(current = library, pickerOpen = false, query = "", notice = null) }
        refreshItems()
    }

    private fun refreshItems() {
        val position = userPosition
        val all =
            if (position != null) {
                // an approximate fix is blurred by the phone (hundreds of metres to kilometres): order by it, but show no distances
                directory.closestFirst(position).map { PickerItem(it.library, it.meters.takeIf { userPositionIsPrecise }) }
            } else {
                directory.libraries.map { PickerItem(it, null) }
            }
        val wanted = _state.value.query.trim()
        val shown = if (wanted.isEmpty()) all else all.filter { it.library.name.contains(wanted, ignoreCase = true) }
        _state.update { it.copy(items = shown) }
    }
}
