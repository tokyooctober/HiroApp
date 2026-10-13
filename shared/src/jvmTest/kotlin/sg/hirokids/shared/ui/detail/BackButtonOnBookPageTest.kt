package sg.hirokids.shared.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import sg.hirokids.shared.data.availability
import sg.hirokids.shared.data.details
import sg.hirokids.shared.data.group
import sg.hirokids.shared.data.isAvailability
import sg.hirokids.shared.data.isDetails
import sg.hirokids.shared.data.item
import sg.hirokids.shared.data.mockRepository
import sg.hirokids.shared.data.record
import sg.hirokids.shared.data.response
import sg.hirokids.shared.ui.LocalScreenInsets
import sg.hirokids.shared.ui.library.FakeDirectorySource
import sg.hirokids.shared.ui.search.ResultsState
import sg.hirokids.shared.ui.search.SearchFlow
import sg.hirokids.shared.ui.search.SearchScreen
import sg.hirokids.shared.ui.search.SearchViewModel
import sg.hirokids.shared.ui.search.searchRouter
import sg.hirokids.shared.ui.search.searchTestDirectory
import sg.hirokids.shared.ui.search.searchViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * FR-6: the Back button at the top of the book page takes the child back to the search results, exactly as they were.
 *
 * Bug this guards: the book page was the only screen not padded clear of the status bar, so on a phone its Back button sat
 * under the bar and a tap never reached it. The view model tests (`BackFromDetailTest`) call `closeBook()` directly and
 * could not see that. This test draws the real screens, with a pretend status bar that takes every touch on it the way
 * Android's does, and taps Back where a finger would.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class BackButtonOnBookPageTest {
    private val tampines = searchTestDirectory.byCode("TRL")!!

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }

    private fun nlb(request: HttpRequestData): Pair<HttpStatusCode, String> =
        when {
            request.isAvailability -> HttpStatusCode.OK to availability(item(1, "TRL"))
            request.isDetails -> HttpStatusCode.OK to details(1)
            else -> searchRouter(onShelf = response(group("Dinosaur", record(1))), allHere = response())(request)
        }

    /** Like `settled()` in the view model tests, but without suspending: no group of the results is still loading. */
    private fun SearchViewModel.nothingLoading(): Boolean {
        val loaded = state.value.results as? ResultsState.Loaded ?: return false
        return !loaded.onShelf.loading && loaded.onLoan?.loading != true && loaded.others?.loading != true
    }

    private val HttpRequestData.isSearch get() = url.encodedPath.endsWith("SearchTitles")

    @Test
    fun theBackButtonAtTheTopOfTheBookPageReturnsToTheSameResultsWithoutSearchingAgain() =
        runComposeUiTest {
            val (repo, calls, _) = mockRepository(respond = ::nlb)
            val search = searchViewModel(repo)
            val detail = DetailViewModel(repo, FakeDirectorySource(searchTestDirectory))
            startKoin { modules(module { viewModel { detail } }) }
            setContent { PhoneWithStatusBar { SearchFlow(tampines, search, onChangeLibrary = {}) } }
            waitForIdle()

            // a search at Tampines, then tap the book's card
            search.onQueryChange("dinosaur")
            search.submit(tampines)
            waitUntil(timeoutMillis = TIMEOUT) { bookCards().fetchSemanticsNodes().isNotEmpty() }
            bookCards().onFirst().performClick()

            // the book page, with its shelf check done
            waitUntil(timeoutMillis = TIMEOUT) { detail.state.value.copies is CopiesState.Loaded && search.nothingLoading() }
            waitForIdle()
            assertEquals(SearchScreen.DETAIL, search.state.value.screen)
            val results = search.state.value.results
            val searches = calls.count { it.isSearch }
            val back = onNodeWithContentDescription(BACK)
            assertTrue(
                back.getBoundsInRoot().top >= STATUS_BAR,
                "Back must be below the status bar; it starts at ${back.getBoundsInRoot().top}",
            )

            // tap Back at the top of the screen, where a finger would
            back.performClick()
            waitForIdle()

            assertEquals(SearchScreen.RESULTS, search.state.value.screen, "Back did not leave the book page")
            assertSame(results, search.state.value.results, "the results must be the same ones, not a new search")
            assertEquals(searches, calls.count { it.isSearch }, "going back must not ask NLB for the results again")
            bookCards().onFirst().assertExists("the book is listed again")
        }

    private fun SemanticsNodeInteractionsProvider.bookCards() = onAllNodes(hasText("Dinosaur", substring = true) and hasClickAction())

    /** The app as a phone shows it: a status bar across the top that the app is told about and that swallows every touch on it. */
    @Composable
    private fun PhoneWithStatusBar(content: @Composable () -> Unit) {
        val owner =
            remember {
                object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                }
            }
        CompositionLocalProvider(
            LocalScreenInsets provides WindowInsets(top = STATUS_BAR),
            LocalViewModelStoreOwner provides owner,
        ) {
            Box(Modifier.fillMaxSize()) {
                content()
                Box(
                    Modifier.fillMaxWidth().height(STATUS_BAR).pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    },
                )
            }
        }
    }

    private companion object {
        const val TIMEOUT = 5_000L
        const val BACK = "Back to results" // detail_back, what TalkBack and VoiceOver read
        val STATUS_BAR = 48.dp // a phone with a notch; the Back button's middle used to be 32 dp from the top
    }
}
