package sg.hirokids.shared.ui.detail

import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import sg.hirokids.shared.data.availability
import sg.hirokids.shared.data.details
import sg.hirokids.shared.data.group
import sg.hirokids.shared.data.isAvailability
import sg.hirokids.shared.data.isDetails
import sg.hirokids.shared.data.item
import sg.hirokids.shared.data.mockRepository
import sg.hirokids.shared.data.record
import sg.hirokids.shared.data.response
import sg.hirokids.shared.ui.library.FakeDirectorySource
import sg.hirokids.shared.ui.search.ResultsState
import sg.hirokids.shared.ui.search.SearchScreen
import sg.hirokids.shared.ui.search.SearchViewModel
import sg.hirokids.shared.ui.search.loaded
import sg.hirokids.shared.ui.search.searchRouter
import sg.hirokids.shared.ui.search.searchTestDirectory
import sg.hirokids.shared.ui.search.searchViewModel
import sg.hirokids.shared.ui.search.settled
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The book page cannot check the shelf ("We couldn't check the shelf" and Try again). The child must still be able to go back to
 * the search results, which are exactly as they were, whether or not Try again was pressed. The screen does this with
 * `detail.close()` followed by `search.closeBook()` (`SearchFlow`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackFromDetailTest {
    private val tampines = searchTestDirectory.byCode("TRL")!!
    private var shelfCheckWorks = false

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Searches always work; the shelf check (`GetAvailabilityInfo`) works only when [shelfCheckWorks]. */
    private fun nlb(request: HttpRequestData): Pair<HttpStatusCode, String> =
        when {
            request.isAvailability ->
                if (shelfCheckWorks) HttpStatusCode.OK to availability(item(1, "TRL")) else HttpStatusCode.ServiceUnavailable to ""
            request.isDetails -> HttpStatusCode.OK to details(1)
            else -> searchRouter(onShelf = response(group("Dinosaur", record(1))), allHere = response())(request)
        }

    private class Screen(
        val search: SearchViewModel,
        val detail: DetailViewModel,
        val calls: List<HttpRequestData>,
    )

    private fun screen(): Screen {
        val (repo, seen, _) = mockRepository(respond = ::nlb)
        return Screen(searchViewModel(repo), DetailViewModel(repo, FakeDirectorySource(searchTestDirectory)), seen)
    }

    private suspend fun DetailViewModel.shown() = state.first { it.copies !is CopiesState.Loading }

    /** Searches, taps the first card and waits for the failed shelf check, as the screen does. */
    private suspend fun Screen.openBookWithFailedShelfCheck() {
        search.onQueryChange("dinosaur")
        search.submit(tampines)
        search.settled()
        val hit =
            search
                .loaded()
                .onShelf.hits
                .single()
        search.openBook(hit)
        detail.open(hit, tampines)
        assertEquals(CopiesState.Failed, detail.shown().copies) // "We couldn't check the shelf" and Try again are on screen
    }

    private fun Screen.back() {
        detail.close()
        search.closeBook()
    }

    @Test
    fun backFromAFailedShelfCheckReturnsToTheSameResultsWithoutAskingNlbForThemAgain() =
        runTest {
            val screen = screen()
            screen.openBookWithFailedShelfCheck()
            val results = screen.search.state.value.results
            val callsBefore = screen.calls.size

            screen.back()

            assertEquals(SearchScreen.RESULTS, screen.search.state.value.screen)
            assertNull(screen.search.state.value.openBook)
            assertEquals(results, screen.search.state.value.results)
            assertEquals(callsBefore, screen.calls.size) // going back asks NLB nothing
        }

    @Test
    fun backAfterTryAgainHasFailedAgainStillReturnsToTheResults() =
        runTest {
            val screen = screen()
            screen.openBookWithFailedShelfCheck()

            screen.detail.retry() // Try again, and the shelf still cannot be checked
            assertEquals(CopiesState.Failed, screen.detail.shown().copies)
            screen.back()

            assertEquals(SearchScreen.RESULTS, screen.search.state.value.screen)
            assertIs<ResultsState.Loaded>(screen.search.state.value.results)
        }

    @Test
    fun tryAgainThatWorksRemovesTheErrorAndBackStillReturnsToTheResults() =
        runTest {
            val screen = screen()
            screen.openBookWithFailedShelfCheck()

            shelfCheckWorks = true
            screen.detail.retry()
            val state = screen.detail.shown()
            assertIs<CopiesState.Loaded>(state.copies) // the error and its Try again are gone
            screen.back()

            assertEquals(SearchScreen.RESULTS, screen.search.state.value.screen)
        }

    @Test
    fun leavingClearsTheDetailSoTheNextBookDoesNotShowThePreviousFailure() =
        runTest {
            val screen = screen()
            screen.openBookWithFailedShelfCheck()

            screen.back()

            assertNull(screen.detail.state.value.title)
            assertTrue(screen.detail.state.value.copies is CopiesState.Loading) // not Failed: a fresh book starts fresh
        }

    @Test
    fun theSameBookOpenedAgainAfterBackChecksTheShelfAgainAndCanSucceed() =
        runTest {
            val screen = screen()
            screen.openBookWithFailedShelfCheck()
            screen.back()

            shelfCheckWorks = true
            val hit =
                screen.search
                    .loaded()
                    .onShelf.hits
                    .single()
            screen.search.openBook(hit)
            screen.detail.open(hit, tampines)

            assertIs<CopiesState.Loaded>(screen.detail.shown().copies)
        }
}
