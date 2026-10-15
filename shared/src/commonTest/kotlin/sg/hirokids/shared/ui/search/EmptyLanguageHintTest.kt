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
import sg.hirokids.shared.domain.BookLanguage
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * FR-7: "No books found" under one language must not read as "nothing exists" when the same words match books in another
 * language (a Chinese chip left on, "Dragon Kingdom" found in English only). The screen then says so and offers All.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EmptyLanguageHintTest {
    private val tampines = searchTestDirectory.byCode("TRL")!!

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Books exist only when no language is asked for: every call that names a language finds nothing. */
    private val onlyInOtherLanguages = { request: HttpRequestData ->
        HttpStatusCode.OK to
            if (request.url.parameters["Languages"] == null) response(group("Dragon Kingdom", record(1))) else response()
    }

    private fun HttpRequestData.isProbe() = url.parameters["Locations"] == null && url.parameters["Languages"] == null

    @Test
    fun noBooksInTheChosenLanguageButSomeInOthersSetsTheHint() =
        runTest {
            val (repo, seen, _) = mockRepository(respond = onlyInOtherLanguages)
            val vm = searchViewModel(repo)
            vm.chooseLanguage(BookLanguage.CHINESE, tampines)
            vm.onQueryChange("dragon kingdom")
            vm.submit(tampines)
            vm.settled()
            assertEquals(ResultsState.Empty, vm.state.value.results)
            assertTrue(vm.state.value.otherLanguagesMatch)
            // one extra call, with no language and no branch, asked only because the result was empty
            assertEquals(1, seen.count { it.isProbe() })
            assertEquals(4, seen.size)
        }

    @Test
    fun noBooksInAnyLanguageHasNoHint() =
        runTest {
            val (repo, _, _) = mockRepository { HttpStatusCode.OK to response() }
            val vm = searchViewModel(repo)
            vm.chooseLanguage(BookLanguage.CHINESE, tampines)
            vm.onQueryChange("zzzz qqqq")
            vm.submit(tampines)
            vm.settled()
            assertEquals(ResultsState.Empty, vm.state.value.results)
            assertFalse(vm.state.value.otherLanguagesMatch)
        }

    @Test
    fun allLanguagesNeverAsksTheExtraQuestion() =
        runTest {
            val (repo, seen, _) = mockRepository { HttpStatusCode.OK to response() }
            val vm = searchViewModel(repo)
            vm.chooseLanguage(BookLanguage.ALL, tampines)
            vm.onQueryChange("zzzz qqqq")
            vm.submit(tampines)
            vm.settled()
            assertEquals(ResultsState.Empty, vm.state.value.results)
            assertFalse(vm.state.value.otherLanguagesMatch)
            assertEquals(3, seen.size)
        }

    @Test
    fun resultsThatAreNotEmptyNeverAskTheExtraQuestion() =
        runTest {
            val (repo, seen, _) =
                mockRepository(
                    respond = searchRouter(onShelf = response(group("Dragon Kingdom", record(1))), allHere = response()),
                )
            val vm = searchViewModel(repo)
            vm.onQueryChange("dragon kingdom")
            vm.submit(tampines)
            vm.settled()
            assertIs<ResultsState.Loaded>(vm.state.value.results)
            assertFalse(vm.state.value.otherLanguagesMatch)
            assertEquals(3, seen.size)
        }

    @Test
    fun anIsbnLookupNeverAsksTheExtraQuestion() =
        runTest {
            val (repo, seen, _) =
                mockRepository {
                    HttpStatusCode.OK to
                        """{"totalRecords":0,"count":0,"hasMoreRecords":false,"nextRecordsOffset":0,"titles":[]}"""
                }
            val vm = searchViewModel(repo)
            vm.chooseLanguage(BookLanguage.CHINESE, tampines)
            vm.onQueryChange("9780000000001")
            vm.submit(tampines)
            vm.settled()
            assertEquals(ResultsState.Empty, vm.state.value.results)
            assertFalse(vm.state.value.otherLanguagesMatch)
            assertEquals(1, seen.size)
        }

    @Test
    fun aFailedExtraQuestionLeavesTheEmptyResultWithoutAHint() =
        runTest {
            val (repo, _, _) =
                mockRepository(
                    retry =
                        sg.hirokids.shared.data
                            .RetryPolicy(sleep = {}),
                ) { request ->
                    if (request.isProbe()) HttpStatusCode.InternalServerError to "" else HttpStatusCode.OK to response()
                }
            val vm = searchViewModel(repo)
            vm.chooseLanguage(BookLanguage.CHINESE, tampines)
            vm.onQueryChange("dragon kingdom")
            vm.submit(tampines)
            vm.settled()
            assertEquals(ResultsState.Empty, vm.state.value.results)
            assertFalse(vm.state.value.otherLanguagesMatch)
        }

    @Test
    fun choosingAllShowsTheBooksAndClearsTheHint() =
        runTest {
            val (repo, _, _) = mockRepository(respond = onlyInOtherLanguages)
            val vm = searchViewModel(repo)
            vm.chooseLanguage(BookLanguage.CHINESE, tampines)
            vm.onQueryChange("dragon kingdom")
            vm.submit(tampines)
            vm.settled()
            assertTrue(vm.state.value.otherLanguagesMatch)
            vm.chooseLanguage(BookLanguage.ALL, tampines)
            vm.settled()
            assertFalse(vm.state.value.otherLanguagesMatch)
            assertEquals(listOf("Dragon Kingdom"), vm.loaded().onShelf.titles())
        }

    @Test
    fun theHintIsAskedAfreshForEachEmptySearch() =
        runTest {
            val (repo, _, _) = mockRepository(respond = onlyInOtherLanguages)
            val vm = searchViewModel(repo)
            vm.chooseLanguage(BookLanguage.CHINESE, tampines)
            vm.onQueryChange("dragon kingdom")
            vm.submit(tampines)
            vm.settled()
            assertTrue(vm.state.value.otherLanguagesMatch)
            vm.submit(tampines) // the same empty search again: asked afresh, still true
            vm.settled()
            assertTrue(vm.state.value.otherLanguagesMatch)
            vm.onQueryChange("dragon")
            vm.chooseLanguage(BookLanguage.ENGLISH, tampines) // English finds nothing here either, with its own answer
            vm.settled()
            assertEquals(ResultsState.Empty, vm.state.value.results)
            assertTrue(vm.state.value.otherLanguagesMatch)
        }
}
