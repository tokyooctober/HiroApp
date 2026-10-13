package sg.hirokids.shared.ui.search

import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import sg.hirokids.shared.data.group
import sg.hirokids.shared.data.mockRepository
import sg.hirokids.shared.data.record
import sg.hirokids.shared.data.response
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The user types words NLB cannot search, the screen offers Try again, the user corrects the words. The failure, and the Try again
 * button that stands for it (`ResultsState.Failed`), must go once the corrected words are searched, whichever button is pressed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CorrectedSearchTest {
    private val tampines = searchTestDirectory.byCode("TRL")!!
    private val shelf = response(group("Dinosaur", record(1)))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** NLB refuses the invalid words (HTTP 400) and answers the corrected ones. */
    private fun nlb(request: HttpRequestData): Pair<HttpStatusCode, String> =
        if (request.url.parameters["Keywords"] == INVALID) {
            HttpStatusCode.BadRequest to ""
        } else {
            searchRouter(onShelf = shelf, allHere = shelf)(request)
        }

    private suspend fun SearchViewModel.failedOnInvalidWords() {
        onQueryChange(INVALID)
        submit(tampines)
        assertIs<ResultsState.Failed>(settled().results) // Try again is on screen
    }

    @Test
    fun correctedWordsSearchedWithSearchClearTheFailure() =
        runTest {
            val (repo, seen, _) = mockRepository(respond = ::nlb)
            val vm = searchViewModel(repo)
            vm.failedOnInvalidWords()

            vm.onQueryChange("dinosaur")
            vm.submit(tampines)

            assertIs<ResultsState.Loaded>(vm.settled().results)
            assertEquals("dinosaur", seen.last().url.parameters["Keywords"])
        }

    @Test
    fun correctedWordsSearchedWithTryAgainAlsoClearTheFailure() =
        runTest {
            val (repo, seen, _) = mockRepository(respond = ::nlb)
            val vm = searchViewModel(repo)
            vm.failedOnInvalidWords()

            vm.onQueryChange("dinosaur") // the words in the box are now right, and the screen still shows Try again
            vm.retry(tampines) // so Try again must search them, not the invalid words it failed on

            assertIs<ResultsState.Loaded>(vm.settled().results)
            assertEquals("dinosaur", seen.last().url.parameters["Keywords"])
            assertEquals("dinosaur", vm.state.value.searchedQuery)
        }

    @Test
    fun tryAgainWithTheWordsLeftAsTheyWereStillAsksAgainForThoseWords() =
        runTest {
            val (repo, seen, _) = mockRepository(respond = ::nlb)
            val vm = searchViewModel(repo)
            vm.failedOnInvalidWords()

            vm.retry(tampines) // nothing was corrected: the same words, so the same failure

            assertIs<ResultsState.Failed>(vm.settled().results)
            assertEquals(INVALID, seen.last().url.parameters["Keywords"])
        }

    @Test
    fun theFailureDoesNotComeBackAfterTheCorrectedSearch() =
        runTest {
            val (repo, _, _) = mockRepository(respond = ::nlb)
            val vm = searchViewModel(repo)
            vm.failedOnInvalidWords()
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()

            val loaded = vm.loaded()
            assertNull(loaded.onShelf.takeIf { it.failed }) // no Try again inside the list either
            assertEquals(false, loaded.onLoan?.failed)
            assertEquals(false, loaded.others?.failed)
        }

    private companion object {
        /** Words with characters the search cannot take. */
        const val INVALID = "dino!!saur??"
    }
}
