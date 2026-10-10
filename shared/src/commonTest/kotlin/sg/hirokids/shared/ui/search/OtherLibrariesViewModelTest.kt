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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Other libraries and View (FR-15, FR-16), including the golden scenario of spec section 8. */
@OptIn(ExperimentalCoroutinesApi::class)
class OtherLibrariesViewModelTest {
    private val tampines = searchTestDirectory.byCode("TRL")!!
    private val pasirRis = searchTestDirectory.byCode("PRPL")!!

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun router(
        onShelf: String = response(),
        allHere: String = response(),
        others: String = response(),
    ) = searchRouter(onShelf, allHere, others)

    /** Spec section 8: A on the shelf at Tampines, B there but all out, C on the shelf only at Pasir Ris and Punggol. */
    private fun goldenScenario(): (HttpRequestData) -> Pair<HttpStatusCode, String> =
        { request ->
            val p = request.url.parameters
            val onShelf = p["Availability"] == "true"
            HttpStatusCode.OK to
                when {
                    p["Locations"] == null ->
                        response(
                            facets =
                                locationFacets("trl" to 5, "prpl" to 1, "prl" to 1, "ld" to 3, "amkpl" to 9, "qupl" to 2),
                        )
                    p["Locations"] == "trl" && onShelf -> response(group("A", record(1)))
                    p["Locations"] == "trl" -> response(group("A", record(1)), group("B", record(2, waiting = 3)))
                    p["Locations"] == "prpl" -> response(group("C", record(3)))
                    else -> response()
                }
        }

    @Test
    fun goldenScenarioAIsOnTheShelfBIsOutAndCIsOnlyListedAtPasirRisThenPunggol() =
        runTest {
            val (repo, seen, _) = mockRepository(respond = goldenScenario())
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()
            val loaded = vm.loaded()
            assertEquals(listOf("A"), loaded.onShelf.titles())
            assertEquals(listOf("B"), loaded.onLoan!!.titles())
            assertEquals(listOf("PRPL", "PRL"), loaded.others!!.items.map { it.library.code })
            assertEquals(3, seen.size) // at most three NLB calls for a search
            assertEquals(listOf("shelf", "out", "others"), seen.groups())
        }

    @Test
    fun theOtherLibrariesCallAsksForTheShelfEverywhereWithNoBranch() =
        runTest {
            val (repo, seen, _) = mockRepository(respond = goldenScenario())
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()
            val call = seen.last().url.parameters
            assertNull(call["Locations"])
            assertEquals("true", call["Availability"])
        }

    @Test
    fun viewOnPasirRisMakesItTheCurrentLibraryAndMovesCToTheShelf() =
        runTest {
            val (repo, seen, _) = mockRepository(respond = goldenScenario())
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()

            vm.view(pasirRis, from = tampines)
            vm.settled()

            assertEquals(pasirRis, vm.state.value.viewing)
            assertEquals("dinosaur", vm.state.value.searchedQuery) // same words, same mode
            assertEquals(listOf("C"), vm.loaded().onShelf.titles())
            assertEquals(6, seen.size)
            assertTrue(seen.drop(3).filter { it.url.parameters["Locations"] != null }.all { it.url.parameters["Locations"] == "prpl" })
            // Tampines is now one of the other libraries
            assertTrue(
                vm
                    .loaded()
                    .others!!
                    .items
                    .any { it.library.code == "TRL" },
            )
            assertTrue(
                vm
                    .loaded()
                    .others!!
                    .items
                    .none { it.library.code == "PRPL" },
            )
        }

    @Test
    fun backAfterViewRestoresTheEarlierLibraryAndItsResultsWithoutAskingNlbAgain() =
        runTest {
            val (repo, seen, _) = mockRepository(respond = goldenScenario())
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()
            val before = vm.state.value.results

            vm.view(pasirRis, from = tampines)
            vm.settled()
            val calls = seen.size
            vm.back()

            assertNull(vm.state.value.viewing)
            assertEquals(SearchScreen.RESULTS, vm.state.value.screen)
            assertEquals(before, vm.state.value.results)
            assertEquals(calls, seen.size)

            vm.back() // nothing left to go back to: now it is the search home
            assertEquals(SearchScreen.HOME, vm.state.value.screen)
        }

    @Test
    fun viewsStackSoBackWalksBackOneLibraryAtATime() =
        runTest {
            val punggol = searchTestDirectory.byCode("PRL")!!
            val (repo, _, _) = mockRepository(respond = goldenScenario())
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()
            vm.view(pasirRis, from = tampines)
            vm.settled()
            vm.view(punggol, from = pasirRis)
            vm.settled()
            assertEquals(punggol, vm.state.value.viewing)
            vm.back()
            assertEquals(pasirRis, vm.state.value.viewing)
            vm.back()
            assertNull(vm.state.value.viewing)
        }

    @Test
    fun viewingTheLibraryAlreadyShownDoesNothing() =
        runTest {
            val (repo, seen, _) = mockRepository(respond = goldenScenario())
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()
            vm.view(tampines, from = tampines)
            vm.settled()
            assertEquals(3, seen.size)
            assertNull(vm.state.value.viewing)
        }

    @Test
    fun aNewSearchWhileViewingStaysAtTheViewedLibraryAndBackStillReturnsToTheFirst() =
        runTest {
            val (repo, seen, _) = mockRepository(respond = goldenScenario())
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()
            vm.view(pasirRis, from = tampines)
            vm.settled()

            vm.onQueryChange("eggs")
            vm.submit(pasirRis) // the screen passes the library being viewed
            vm.settled()
            assertEquals(pasirRis, vm.state.value.viewing)
            assertEquals("prpl", seen.last { it.url.parameters["Locations"] != null }.url.parameters["Locations"])

            vm.back()
            assertNull(vm.state.value.viewing)
            assertEquals("dinosaur", vm.state.value.searchedQuery)
        }

    @Test
    fun booksOnlyAvailableElsewhereStillShowTheOtherLibrariesRatherThanNoResults() =
        runTest {
            val (repo, _, _) =
                mockRepository(
                    respond = router(onShelf = response(), allHere = response(), others = response(facets = locationFacets("prpl" to 2))),
                )
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()
            val loaded = vm.loaded()
            assertEquals(emptyList(), loaded.onShelf.hits)
            assertEquals(listOf("PRPL"), loaded.others!!.items.map { it.library.code })
        }

    @Test
    fun nothingAnywhereIsStillEmptyWhenNoOtherLibraryHasIt() =
        runTest {
            val others = response(facets = locationFacets("trl" to 4, "ld" to 1)) // only the current library and a code we do not show
            val (repo, _, _) = mockRepository(respond = router(onShelf = response(), allHere = response(), others = others))
            val vm = searchViewModel(repo)
            vm.onQueryChange("zzzz")
            vm.submit(tampines)
            assertEquals(ResultsState.Empty, vm.settled().results)
        }

    @Test
    fun anIsbnLookupHasNoOtherLibraries() =
        runTest {
            val flat = """{"totalRecords":1,"count":1,"hasMoreRecords":false,"nextRecordsOffset":1,"titles":[${record(5)}]}"""
            val (repo, seen, _) = mockRepository { HttpStatusCode.OK to flat }
            val vm = searchViewModel(repo)
            vm.onQueryChange("9781801041850")
            vm.submit(tampines)
            vm.settled()
            assertNull(vm.loaded().others)
            assertEquals(1, seen.size)
        }

    @Test
    fun aFailureOnTheOtherLibrariesKeepsTheRestAndCanBeRetriedOnItsOwn() =
        runTest {
            var failing = true
            val golden = goldenScenario()
            val (repo, seen, _) =
                mockRepository { request ->
                    if (failing &&
                        request.url.parameters["Locations"] == null
                    ) {
                        HttpStatusCode.BadGateway to ""
                    } else {
                        golden(request)
                    }
                }
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()
            assertTrue(vm.loaded().others!!.failed)
            assertEquals(listOf("A"), vm.loaded().onShelf.titles())

            failing = false
            vm.retryOthers(tampines)
            vm.settled()
            assertEquals(false, vm.loaded().others!!.failed)
            assertEquals(
                listOf("PRPL", "PRL"),
                vm
                    .loaded()
                    .others!!
                    .items
                    .map { it.library.code },
            )
            assertEquals(listOf("shelf", "out", "others", "others"), seen.groups())
        }

    @Test
    fun resultsSavedForBackNeverComeBackStuckOnASpinner() {
        val spinning = Shelf(nextOffset = 0, loading = true)
        val loaded =
            ResultsState.Loaded(
                Shelf(hits = emptyList(), nextOffset = 20, loading = true),
                spinning,
                OtherLibraries(loading = true),
            )
        val saved = assertIs<ResultsState.Loaded>(loaded.forSnapshot())
        assertTrue(saved.onShelf.failed && !saved.onShelf.loading)
        assertTrue(saved.onLoan!!.failed && !saved.onLoan.loading)
        assertTrue(saved.others!!.failed && !saved.others.loading)
        assertEquals(20, saved.onShelf.nextOffset) // where to resume
        assertEquals(ResultsState.Failed(emptyList()), ResultsState.Loading.forSnapshot())
        assertEquals(ResultsState.Empty, ResultsState.Empty.forSnapshot())
    }
}
