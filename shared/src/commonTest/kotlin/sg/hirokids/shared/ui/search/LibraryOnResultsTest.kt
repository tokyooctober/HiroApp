package sg.hirokids.shared.ui.search

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
import sg.hirokids.shared.domain.LatLon
import sg.hirokids.shared.domain.Library
import sg.hirokids.shared.domain.LibraryDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The library the results are for is the library the front page shows ("You're at VivoCity"), never one left over from earlier:
 * not a library last viewed with View, and not the one the user was at before changing it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryOnResultsTest {
    private val tampines = searchTestDirectory.byCode("TRL")!!
    private val pasirRis = searchTestDirectory.byCode("PRPL")!!
    private val harbourfront = Library("HBPL", "library@harbourfront", LatLon(1.2656, 103.8222), listOf("VivoCity"))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val shelf = response(group("Dinosaur", record(1)))

    private fun viewModel() =
        mockRepository(respond = searchRouter(onShelf = shelf, allHere = shelf)).let { (repo, seen, _) ->
            Triple(searchViewModel(repo), seen, LibraryDirectory(listOf(tampines, pasirRis, harbourfront)))
        }

    @Test
    fun aSearchFromTheFrontPageIsForTheLibraryOnTheFrontPage() =
        runTest {
            val (vm, seen, _) = viewModel()
            vm.useLibrary(harbourfront)
            vm.onQueryChange("dinosaur")
            vm.submit(harbourfront)
            vm.settled()
            assertEquals(setOf("hbpl"), seen.map { it.url.parameters["Locations"] }.filterNotNull().toSet())
            assertNull(vm.state.value.viewing)
        }

    @Test
    fun changingTheLibraryDropsTheResultsAndTheViewedLibraryOfTheOldOne() =
        runTest {
            val (vm, _, _) = viewModel()
            vm.useLibrary(tampines)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()
            vm.view(pasirRis, from = tampines)
            vm.settled()
            assertEquals(pasirRis, vm.state.value.viewing)

            vm.useLibrary(harbourfront) // the front page now says VivoCity

            assertEquals(SearchScreen.HOME, vm.state.value.screen)
            assertEquals(ResultsState.Idle, vm.state.value.results)
            assertNull(vm.state.value.viewing)
        }

    @Test
    fun theNextSearchAfterTheChangeAsksForTheNewLibraryOnly() =
        runTest {
            val (vm, seen, _) = viewModel()
            vm.useLibrary(tampines)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()
            vm.useLibrary(harbourfront)
            seen.clear()

            vm.onQueryChange("dragon")
            vm.submit(harbourfront)
            vm.settled()

            assertEquals(setOf("hbpl"), seen.map { it.url.parameters["Locations"] }.filterNotNull().toSet())
        }

    @Test
    fun backAfterTheChangeDoesNotBringBackTheOldLibrarysResults() =
        runTest {
            val (vm, _, _) = viewModel()
            vm.useLibrary(tampines)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()
            vm.view(pasirRis, from = tampines)
            vm.settled()
            vm.useLibrary(harbourfront)

            vm.back()

            assertEquals(SearchScreen.HOME, vm.state.value.screen)
            assertEquals(ResultsState.Idle, vm.state.value.results)
        }

    @Test
    fun usingTheSameLibraryAgainKeepsTheResults() =
        runTest {
            val (vm, _, _) = viewModel()
            vm.useLibrary(tampines)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()
            val results = vm.state.value.results

            vm.useLibrary(tampines) // e.g. the screen is shown again

            assertEquals(results, vm.state.value.results)
            assertEquals(SearchScreen.RESULTS, vm.state.value.screen)
        }
}
