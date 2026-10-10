package sg.hirokids.shared.ui.search

import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import sg.hirokids.shared.data.NlbRepository
import sg.hirokids.shared.data.response
import sg.hirokids.shared.ui.library.FakeDirectorySource
import sg.hirokids.shared.ui.library.Spots
import kotlin.test.assertIs

/** Helpers shared by the view model tests: a pretend proxy that answers the three calls of a search, and ways to wait and look. */
internal val searchTestDirectory = Spots.directory()

internal fun searchViewModel(repo: NlbRepository) = SearchViewModel(repo, FakeDirectorySource(searchTestDirectory))

/** Answers the on-the-shelf request (branch and Availability=true), the all-copies-out request (branch only) and the other-libraries one (no branch). */
internal fun searchRouter(
    onShelf: String,
    allHere: String,
    others: String = response(),
): (HttpRequestData) -> Pair<HttpStatusCode, String> =
    { request ->
        val p = request.url.parameters
        HttpStatusCode.OK to
            when {
                p["Locations"] == null -> others // no branch: the other-libraries call
                p["Availability"] == "true" -> onShelf
                else -> allHere
            }
    }

internal fun List<HttpRequestData>.groups() =
    map {
        val p = it.url.parameters
        when {
            p["Locations"] == null -> "others"
            p["Availability"] == "true" -> "shelf"
            else -> "out"
        }
    }

/** The first state in which nothing is on its way: no search running and no page loading. */
internal suspend fun SearchViewModel.settled(): SearchUiState =
    state.first { s ->
        val loaded = s.results as? ResultsState.Loaded
        s.results !is ResultsState.Loading &&
            loaded?.onShelf?.loading != true &&
            loaded?.onLoan?.loading != true &&
            loaded?.others?.loading != true
    }

internal fun SearchViewModel.loaded() = assertIs<ResultsState.Loaded>(state.value.results)

internal fun Shelf.titles() = hits.map { it.title.title }
