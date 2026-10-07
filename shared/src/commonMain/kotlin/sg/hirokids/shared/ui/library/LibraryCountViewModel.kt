package sg.hirokids.shared.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sg.hirokids.shared.data.ProxyApi

sealed interface LibraryCountState {
    data object Loading : LibraryCountState

    data class Loaded(
        val count: Int,
    ) : LibraryCountState

    data object Error : LibraryCountState
}

/** Skeleton screen model: proves app -> proxy -> NLB works on both platforms. */
class LibraryCountViewModel(
    private val api: ProxyApi,
) : ViewModel() {
    private val _state = MutableStateFlow<LibraryCountState>(LibraryCountState.Loading)
    val state: StateFlow<LibraryCountState> = _state.asStateFlow()

    init {
        load()
    }

    // Any failure (network, 429, 5xx) is shown as one retryable error state (FR-12), so the exception itself is dropped on purpose.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun load() {
        _state.value = LibraryCountState.Loading
        viewModelScope.launch {
            _state.value =
                try {
                    LibraryCountState.Loaded(api.publicLibraries().size)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    LibraryCountState.Error
                }
        }
    }
}
