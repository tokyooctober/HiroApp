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
import sg.hirokids.shared.data.locationFacets
import sg.hirokids.shared.data.mockRepository
import sg.hirokids.shared.data.record
import sg.hirokids.shared.data.response
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * "Dragon Kingdom" (two words) must not end in "No books found" when NLB has matching children's books: not on the shelf here,
 * not at this library at all, or hidden behind a language the user has not chosen are all different from nothing found.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NoFalseEmptyTest {
    private val tampines = searchTestDirectory.byCode("TRL")!!
    private val dragon = response(group("The Dragon Kingdom", record(1)))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun search(respond: (HttpRequestData) -> Pair<HttpStatusCode, String>): SearchViewModel {
        val (repo, _, _) = mockRepository(respond = respond)
        val vm = searchViewModel(repo)
        vm.onQueryChange("Dragon Kingdom")
        vm.submit(tampines)
        vm.settled()
        return vm
    }

    @Test
    fun bothWordsGoToNlbAsOneSearch() =
        runTest {
            val (repo, seen, _) = mockRepository(respond = searchRouter(onShelf = dragon, allHere = dragon))
            val vm = searchViewModel(repo)
            vm.onQueryChange("  Dragon   Kingdom ")
            vm.submit(tampines)
            vm.settled()
            assertEquals(setOf("Dragon Kingdom"), seen.map { it.url.parameters["Keywords"] }.toSet())
        }

    @Test
    fun aBookOnTheShelfHereIsListed() =
        runTest {
            val vm = search(searchRouter(onShelf = dragon, allHere = dragon))
            assertEquals(listOf("The Dragon Kingdom"), vm.loaded().onShelf.titles())
        }

    @Test
    fun aBookWithAllCopiesOutIsListedNotTheEmptyState() =
        runTest {
            val vm = search(searchRouter(onShelf = response(), allHere = dragon))
            val loaded = assertIs<ResultsState.Loaded>(vm.state.value.results)
            assertEquals(listOf("The Dragon Kingdom"), loaded.onLoan!!.titles())
        }

    @Test
    fun aBookOnlyAtOtherLibrariesIsListedNotTheEmptyState() =
        runTest {
            val others = response(facets = locationFacets("prpl" to 2))
            val vm = search(searchRouter(onShelf = response(), allHere = response(), others = others))
            val loaded = assertIs<ResultsState.Loaded>(vm.state.value.results)
            assertEquals(listOf("PRPL"), loaded.others!!.items.map { it.library.code })
        }

    @Test
    fun nothingAnywhereIsTheEmptyState() =
        runTest {
            val vm = search(searchRouter(onShelf = response(), allHere = response()))
            assertIs<ResultsState.Empty>(vm.state.value.results)
        }
}
