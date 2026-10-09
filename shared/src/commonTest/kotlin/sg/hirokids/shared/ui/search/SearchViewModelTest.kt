package sg.hirokids.shared.ui.search

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import sg.hirokids.shared.data.group
import sg.hirokids.shared.data.mockRepository
import sg.hirokids.shared.data.record
import sg.hirokids.shared.data.response
import sg.hirokids.shared.domain.LatLon
import sg.hirokids.shared.domain.Library
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Task 7b: the search home and results state (FR-1, FR-5, FR-12 retry). */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val tampines = Library("TRL", "Tampines Regional Library", LatLon(1.352391, 103.940821))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val twoCards = response(group("Dinosaur", record(1)), group("Dinosaur eggs", record(2)))

    private suspend fun SearchViewModel.settled(): SearchUiState = state.first { it.results !is ResultsState.Loading }

    @Test
    fun theHomeScreenStartsEmpty() {
        val (repo, seen, _) = mockRepository { HttpStatusCode.OK to twoCards }
        val vm = SearchViewModel(repo)
        assertEquals(SearchScreen.HOME, vm.state.value.screen)
        assertEquals("", vm.state.value.query)
        assertEquals(ResultsState.Idle, vm.state.value.results)
        assertEquals(0, seen.size)
    }

    @Test
    fun typingKeepsTheTextAndClearsTheTooShortHint() {
        val (repo, _, _) = mockRepository { HttpStatusCode.OK to twoCards }
        val vm = SearchViewModel(repo)
        vm.onQueryChange("d")
        vm.submit(tampines)
        assertTrue(vm.state.value.tooShort)
        vm.onQueryChange("di")
        assertEquals("di", vm.state.value.query)
        assertEquals(false, vm.state.value.tooShort)
    }

    @Test
    fun oneCharacterIsNotSentAndStaysOnHome() {
        val (repo, seen, _) = mockRepository { HttpStatusCode.OK to twoCards }
        val vm = SearchViewModel(repo)
        vm.onQueryChange(" d ")
        vm.submit(tampines)
        assertTrue(vm.state.value.tooShort)
        assertEquals(SearchScreen.HOME, vm.state.value.screen)
        assertEquals(0, seen.size)
    }

    @Test
    fun aSearchShowsTheResultsScreenWithCardsFromTheCurrentLibrary() =
        runTest {
            val (repo, seen, _) = mockRepository { HttpStatusCode.OK to twoCards }
            val vm = SearchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            val done = vm.settled()
            assertEquals(SearchScreen.RESULTS, done.screen)
            val loaded = assertIs<ResultsState.Loaded>(done.results)
            assertEquals(listOf("Dinosaur", "Dinosaur eggs"), loaded.hits.map { it.title.title })
            assertEquals("trl", seen.single().url.parameters["Locations"])
            assertEquals("juvenile", seen.single().url.parameters["IntendedAudiences"])
            assertEquals("dinosaur", done.searchedQuery)
        }

    @Test
    fun anIsbnIsLookedUpByIsbnAndItsCardMakesNoShelfClaim() =
        runTest {
            val flat = """{"totalRecords":1,"count":1,"hasMoreRecords":false,"nextRecordsOffset":1,"titles":[${record(5)}]}"""
            val (repo, seen, _) = mockRepository { HttpStatusCode.OK to flat }
            val vm = SearchViewModel(repo)
            vm.onQueryChange("978-1-80104-185-0")
            vm.submit(tampines)
            val loaded = assertIs<ResultsState.Loaded>(vm.settled().results)
            assertEquals("/catalogue/GetTitles", seen.single().url.encodedPath)
            assertEquals(false, loaded.hits.single().reportedOnShelfHere)
        }

    @Test
    fun aSearchWithNothingToShowSaysSo() =
        runTest {
            val (repo, _, _) = mockRepository { HttpStatusCode.OK to response() }
            val vm = SearchViewModel(repo)
            vm.onQueryChange("zzzz")
            vm.submit(tampines)
            assertEquals(ResultsState.Empty, vm.settled().results)
        }

    @Test
    fun resultsThatAllFailTheAudienceGateCountAsNothingToShow() =
        runTest {
            val adultOnly = response(group("Adult book", record(9, subjects = """["Dinosaurs"]""")))
            val (repo, _, _) = mockRepository { HttpStatusCode.OK to adultOnly }
            val vm = SearchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            assertEquals(ResultsState.Empty, vm.settled().results)
        }

    @Test
    fun aFailureOffersRetryAndRetryAsksAgainForTheSameWords() =
        runTest {
            var failing = true
            val (repo, seen, _) =
                mockRepository {
                    if (failing) HttpStatusCode.TooManyRequests to "" else HttpStatusCode.OK to twoCards
                }
            val vm = SearchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            assertEquals(ResultsState.Failed, vm.settled().results)

            failing = false
            vm.onQueryChange("something else typed meanwhile")
            vm.retry(tampines)
            assertIs<ResultsState.Loaded>(vm.settled().results)
            assertEquals(listOf("dinosaur", "dinosaur"), seen.map { it.url.parameters["Keywords"] })
        }

    @Test
    fun backReturnsToHomeAndKeepsTheWordsTyped() =
        runTest {
            val (repo, _, _) = mockRepository { HttpStatusCode.OK to twoCards }
            val vm = SearchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()
            vm.back()
            assertEquals(SearchScreen.HOME, vm.state.value.screen)
            assertEquals("dinosaur", vm.state.value.query)
            assertEquals(ResultsState.Idle, vm.state.value.results)
        }

    @Test
    fun aSecondSearchReplacesTheFirst() =
        runTest {
            val (repo, seen, _) = mockRepository { HttpStatusCode.OK to twoCards }
            val vm = SearchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()
            vm.onQueryChange("eggs")
            vm.submit(tampines)
            assertEquals("eggs", vm.settled().searchedQuery)
            assertEquals(listOf("dinosaur", "eggs"), seen.map { it.url.parameters["Keywords"] })
        }
}
