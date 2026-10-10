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
import sg.hirokids.shared.domain.ResultGroup
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Search home and results (FR-1, FR-4, FR-5, FR-11, FR-12). */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val tampines = searchTestDirectory.byCode("TRL")!!

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val onShelfHere = response(group("Dinosaur", record(1)), group("Dinosaur eggs", record(2)))
    private val allOut = response(group("Dinosaur tracks", record(3, waiting = 4)), group("Dinosaur names", record(4, waiting = 1)))

    private fun router(
        onShelf: String = onShelfHere,
        allHere: String = allOut,
        others: String = response(),
    ) = searchRouter(onShelf, allHere, others)

    // --- the home screen ---------------------------------------------------------------------------------------------------

    @Test
    fun theHomeScreenStartsEmpty() {
        val (repo, seen, _) = mockRepository(respond = router())
        val vm = searchViewModel(repo)
        assertEquals(SearchScreen.HOME, vm.state.value.screen)
        assertEquals("", vm.state.value.query)
        assertEquals(ResultsState.Idle, vm.state.value.results)
        assertEquals(0, seen.size)
    }

    @Test
    fun typingKeepsTheTextAndClearsTheTooShortHint() {
        val (repo, _, _) = mockRepository(respond = router())
        val vm = searchViewModel(repo)
        vm.onQueryChange("d")
        vm.submit(tampines)
        assertTrue(vm.state.value.tooShort)
        vm.onQueryChange("di")
        assertEquals("di", vm.state.value.query)
        assertEquals(false, vm.state.value.tooShort)
    }

    @Test
    fun oneCharacterIsNotSentAndStaysOnHome() {
        val (repo, seen, _) = mockRepository(respond = router())
        val vm = searchViewModel(repo)
        vm.onQueryChange(" d ")
        vm.submit(tampines)
        assertTrue(vm.state.value.tooShort)
        assertEquals(SearchScreen.HOME, vm.state.value.screen)
        assertEquals(0, seen.size)
    }

    // --- the two groups -----------------------------------------------------------------------------------------------------

    @Test
    fun aSearchAsksTheShelfFirstThenAllCopiesOutAtTheCurrentLibrary() =
        runTest {
            val (repo, seen, _) = mockRepository(respond = router())
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            val done = vm.settled()
            assertEquals(SearchScreen.RESULTS, done.screen)
            assertEquals(listOf("shelf", "out", "others"), seen.groups())
            assertTrue(seen.all { it.url.parameters["IntendedAudiences"] == "juvenile" })
            assertTrue(seen.take(2).all { it.url.parameters["Locations"] == "trl" })
            assertEquals("dinosaur", done.searchedQuery)
            val loaded = vm.loaded()
            assertEquals(listOf("Dinosaur", "Dinosaur eggs"), loaded.onShelf.titles())
            assertTrue(loaded.onShelf.hits.all { it.reportedOnShelfHere })
            assertTrue(loaded.onLoan!!.hits.none { it.reportedOnShelfHere })
        }

    @Test
    fun allCopiesOutAreOrderedByFewestWaitingFirst() =
        runTest {
            val (repo, _, _) = mockRepository(respond = router())
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()
            // "Dinosaur names" has 1 waiting and "Dinosaur tracks" 4, although tracks came first from NLB
            assertEquals(listOf("Dinosaur names", "Dinosaur tracks"), vm.loaded().onLoan!!.titles())
            assertEquals(
                listOf(1, 4),
                vm
                    .loaded()
                    .onLoan!!
                    .hits
                    .map { it.title.reservations },
            )
        }

    @Test
    fun aTitleOnTheShelfNeverRepeatsInAllCopiesOut() =
        runTest {
            val overlapping = response(group("Dinosaur", record(1)), group("Dinosaur tracks", record(3)))
            val (repo, _, _) = mockRepository(respond = router(allHere = overlapping))
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()
            assertEquals(listOf("Dinosaur tracks"), vm.loaded().onLoan!!.titles())
        }

    @Test
    fun nothingOnTheShelfStillShowsTheBooksThatAreOut() =
        runTest {
            val (repo, _, _) = mockRepository(respond = router(onShelf = response()))
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()
            val loaded = vm.loaded()
            assertEquals(emptyList(), loaded.onShelf.hits)
            assertNull(loaded.onShelf.nextOffset)
            assertEquals(2, loaded.onLoan!!.hits.size)
        }

    @Test
    fun nothingInEitherGroupIsEmpty() =
        runTest {
            val (repo, _, _) = mockRepository(respond = router(onShelf = response(), allHere = response()))
            val vm = searchViewModel(repo)
            vm.onQueryChange("zzzz")
            vm.submit(tampines)
            assertEquals(ResultsState.Empty, vm.settled().results)
        }

    @Test
    fun resultsThatAllFailTheAudienceGateAreEmpty() =
        runTest {
            val adultOnly = response(group("Adult book", record(9, subjects = """["Dinosaurs"]""")))
            val (repo, _, _) = mockRepository(respond = router(onShelf = adultOnly, allHere = adultOnly))
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            assertEquals(ResultsState.Empty, vm.settled().results)
        }

    @Test
    fun anIsbnIsOneLookupWithNoShelfGroups() =
        runTest {
            val flat = """{"totalRecords":1,"count":1,"hasMoreRecords":false,"nextRecordsOffset":1,"titles":[${record(5)}]}"""
            val (repo, seen, _) = mockRepository { HttpStatusCode.OK to flat }
            val vm = searchViewModel(repo)
            vm.onQueryChange("978-1-80104-185-0")
            vm.submit(tampines)
            vm.settled()
            assertEquals(1, seen.size)
            assertEquals("/catalogue/GetTitles", seen.single().url.encodedPath)
            val loaded = vm.loaded()
            assertNull(loaded.onLoan)
            assertEquals(
                false,
                loaded.onShelf.hits
                    .single()
                    .reportedOnShelfHere,
            )
        }

    // --- paging (FR-11) -----------------------------------------------------------------------------------------------------

    private val firstPage = response(group("Dinosaur", record(1)), group("Dinosaur eggs", record(2)), hasMore = true, next = 20)

    private fun pager(
        second: String,
        allHere: String = response(),
    ): (HttpRequestData) -> Pair<HttpStatusCode, String> =
        { request ->
            val shelf = request.url.parameters["Availability"] == "true"
            val offset = request.url.parameters["Offset"]
            HttpStatusCode.OK to
                when {
                    request.url.parameters["Locations"] == null -> response()
                    !shelf -> allHere
                    offset == "0" -> firstPage
                    else -> second
                }
        }

    @Test
    fun reachingTheEndOfAGroupLoadsTheNextTwentyFromTheOffsetNlbGave() =
        runTest {
            val (repo, seen, _) = mockRepository(respond = pager(response(group("Dinosaur bones", record(10)))))
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()
            assertEquals(20, vm.loaded().onShelf.nextOffset)
            vm.loadMore(tampines, ResultGroup.ON_SHELF)
            vm.settled()
            assertEquals("20", seen.last().url.parameters["Offset"])
            assertEquals(listOf("Dinosaur", "Dinosaur eggs", "Dinosaur bones"), vm.loaded().onShelf.titles())
        }

    @Test
    fun theLastPageEndsTheGroupAndFurtherRequestsAreIgnored() =
        runTest {
            val (repo, seen, _) = mockRepository(respond = pager(response(group("Dinosaur bones", record(10)), hasMore = false, next = 40)))
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()
            vm.loadMore(tampines, ResultGroup.ON_SHELF)
            vm.settled()
            assertNull(vm.loaded().onShelf.nextOffset)
            val calls = seen.size
            vm.loadMore(tampines, ResultGroup.ON_SHELF)
            vm.loadMore(tampines, ResultGroup.ON_SHELF)
            vm.settled() // give any wrongly started request time to reach the pretend proxy
            assertEquals(calls, seen.size)
        }

    @Test
    fun aBookThatComesBackOnALaterPageIsNotShownTwice() =
        runTest {
            val (repo, _, _) = mockRepository(respond = pager(response(group("Dinosaur", record(1)), group("Dinosaur bones", record(10)))))
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()
            vm.loadMore(tampines, ResultGroup.ON_SHELF)
            vm.settled()
            assertEquals(listOf("Dinosaur", "Dinosaur eggs", "Dinosaur bones"), vm.loaded().onShelf.titles())
        }

    @Test
    fun aBookFoundOnTheShelfOnALaterPageLeavesAllCopiesOut() =
        runTest {
            val outHasBones = response(group("Dinosaur bones", record(10)), group("Dinosaur tracks", record(3)))
            val (repo, _, _) = mockRepository(respond = pager(response(group("Dinosaur bones", record(10))), allHere = outHasBones))
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()
            assertTrue("Dinosaur bones" in vm.loaded().onLoan!!.titles())
            vm.loadMore(tampines, ResultGroup.ON_SHELF)
            vm.settled()
            assertEquals(listOf("Dinosaur tracks"), vm.loaded().onLoan!!.titles())
        }

    @Test
    fun theSecondGroupPagesTooAndIsOrderedWithinEachPage() =
        runTest {
            val outFirst =
                response(group("Out A", record(20, waiting = 5)), group("Out B", record(21, waiting = 0)), hasMore = true, next = 20)
            val outSecond = response(group("Out C", record(22, waiting = 3)), group("Out D", record(23, waiting = 1)))
            val (repo, seen, _) =
                mockRepository { request ->
                    HttpStatusCode.OK to
                        when {
                            request.url.parameters["Locations"] == null || request.url.parameters["Availability"] == "true" -> response()
                            request.url.parameters["Offset"] == "0" -> outFirst
                            else -> outSecond
                        }
                }
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()
            assertEquals(listOf("Out B", "Out A"), vm.loaded().onLoan!!.titles())
            vm.loadMore(tampines, ResultGroup.ALL_HERE)
            vm.settled()
            assertEquals(listOf("Out B", "Out A", "Out D", "Out C"), vm.loaded().onLoan!!.titles()) // earlier cards never move
            assertEquals("20", seen.last().url.parameters["Offset"])
        }

    // --- failures and retry (FR-12) -------------------------------------------------------------------------------------------

    @Test
    fun aFailureOffersRetryAndRetryAsksAgainForTheSameWords() =
        runTest {
            var failing = true
            val (repo, seen, _) = mockRepository { request -> if (failing) HttpStatusCode.ServiceUnavailable to "" else router()(request) }
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            assertIs<ResultsState.Failed>(vm.settled().results)

            failing = false
            vm.onQueryChange("something else typed meanwhile")
            vm.retry(tampines)
            assertIs<ResultsState.Loaded>(vm.settled().results)
            assertEquals("dinosaur", seen.last().url.parameters["Keywords"])
        }

    @Test
    fun aProxyThatStaysBusyEndsInTheSameRetryAction() =
        runTest {
            val (repo, seen, _) = mockRepository { HttpStatusCode.TooManyRequests to "" }
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            assertIs<ResultsState.Failed>(vm.settled().results)
            assertEquals(4, seen.size) // the first try and three retries
        }

    @Test
    fun whenTheNetworkIsDownTheBooksSavedOnThePhoneAreShownBesideRetry() =
        runTest {
            var offline = false
            val (repo, _, _) = mockRepository { request -> if (offline) HttpStatusCode.ServiceUnavailable to "" else router()(request) }
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()

            offline = true
            vm.onQueryChange("dinosaur eggs")
            vm.submit(tampines)
            val failed = assertIs<ResultsState.Failed>(vm.settled().results)
            assertEquals(listOf("Dinosaur eggs"), failed.saved.map { it.title })
        }

    @Test
    fun aFailureOnTheSecondGroupKeepsTheFirstAndCanBeRetriedOnItsOwn() =
        runTest {
            var outFails = true
            val (repo, seen, _) =
                mockRepository { request ->
                    if (request.url.parameters["Availability"] != "true" &&
                        outFails
                    ) {
                        HttpStatusCode.ServiceUnavailable to ""
                    } else {
                        router()(request)
                    }
                }
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()
            val loaded = vm.loaded()
            assertEquals(2, loaded.onShelf.hits.size)
            assertTrue(loaded.onLoan!!.failed)

            outFails = false
            vm.retryGroup(tampines, ResultGroup.ALL_HERE)
            vm.settled()
            assertEquals(false, vm.loaded().onLoan!!.failed)
            assertEquals(
                2,
                vm
                    .loaded()
                    .onLoan!!
                    .hits.size,
            )
            assertEquals(
                2,
                vm
                    .loaded()
                    .onShelf.hits.size,
            )
            assertEquals(listOf("shelf", "out", "others", "out"), seen.groups())
        }

    @Test
    fun aFailureWhileLoadingMoreKeepsTheBooksAndRetryContinuesFromTheSameOffset() =
        runTest {
            var failing = false
            val second = response(group("Dinosaur bones", record(10)))
            val pages = pager(second)
            val (repo, seen, _) =
                mockRepository { request ->
                    if (failing &&
                        request.url.parameters["Offset"] == "20"
                    ) {
                        HttpStatusCode.BadGateway to ""
                    } else {
                        pages(request)
                    }
                }
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()

            failing = true
            vm.loadMore(tampines, ResultGroup.ON_SHELF)
            vm.settled()
            assertTrue(vm.loaded().onShelf.failed)
            assertEquals(
                2,
                vm
                    .loaded()
                    .onShelf.hits.size,
            )
            assertEquals(20, vm.loaded().onShelf.nextOffset)
            val calls = seen.size
            vm.loadMore(tampines, ResultGroup.ON_SHELF) // a failed group waits for an explicit retry
            vm.settled()
            assertEquals(calls, seen.size)
            assertTrue(vm.loaded().onShelf.failed)

            failing = false
            vm.retryGroup(tampines, ResultGroup.ON_SHELF)
            vm.settled()
            assertEquals(false, vm.loaded().onShelf.failed)
            assertEquals(listOf("Dinosaur", "Dinosaur eggs", "Dinosaur bones"), vm.loaded().onShelf.titles())
        }

    // --- navigation -------------------------------------------------------------------------------------------------------------

    @Test
    fun backReturnsToHomeAndKeepsTheWordsTyped() =
        runTest {
            val (repo, _, _) = mockRepository(respond = router())
            val vm = searchViewModel(repo)
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
            val (repo, seen, _) = mockRepository(respond = router())
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()
            vm.onQueryChange("eggs")
            vm.submit(tampines)
            assertEquals("eggs", vm.settled().searchedQuery)
            assertEquals(listOf("dinosaur", "dinosaur", "dinosaur", "eggs", "eggs", "eggs"), seen.map { it.url.parameters["Keywords"] })
        }
}
