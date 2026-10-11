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
import sg.hirokids.shared.data.isAvailability
import sg.hirokids.shared.data.isDetails
import sg.hirokids.shared.data.item
import sg.hirokids.shared.data.mockRepository
import sg.hirokids.shared.domain.SearchHit
import sg.hirokids.shared.domain.Title
import sg.hirokids.shared.ui.library.FakeDirectorySource
import sg.hirokids.shared.ui.search.searchTestDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {
    private val tampines = searchTestDirectory.byCode("TRL")!!

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val dinosaur =
        Title(
            brn = 1,
            isbn = "9780000001",
            title = "Dinosaur",
            author = "Hepworth, Amelia",
            coverUrl = null,
            subjects = listOf("Dinosaurs Juvenile literature."),
            audience = emptyList(),
            audienceImda = emptyList(),
            isRestricted = false,
            reservations = 0,
        )
    private val hit = SearchHit(dinosaur, listOf(dinosaur), true)

    private fun pretendProxy(
        copies: () -> String = { availability(item(1, "TRL"), item(1, "TRL", "Out"), item(1, "PRPL")) },
    ): (HttpRequestData) -> Pair<HttpStatusCode, String> =
        { request ->
            when {
                request.isAvailability -> HttpStatusCode.OK to copies()
                request.isDetails -> HttpStatusCode.OK to details(1, "Big lizards.")
                else -> HttpStatusCode.NotFound to ""
            }
        }

    private fun viewModel(respond: (HttpRequestData) -> Pair<HttpStatusCode, String>) =
        mockRepository(respond = respond).let { (repo, seen, store) ->
            Triple(DetailViewModel(repo, FakeDirectorySource(searchTestDirectory)), seen, store)
        }

    private suspend fun DetailViewModel.loaded() = state.first { it.copies !is CopiesState.Loading }

    /** The summary arrives after the copies. */
    private suspend fun DetailViewModel.withSummary() = state.first { it.title?.summary != null }

    @Test
    fun theTitleIsThereFromTheFirstFrameAndTheCopiesFollow() =
        runTest {
            val (vm, _, _) = viewModel(pretendProxy())
            vm.open(hit, tampines)
            val state = vm.loaded()
            assertEquals("Dinosaur", state.title?.title)
            val availability = assertIs<CopiesState.Loaded>(state.copies).availability
            assertEquals(1, availability.here?.available)
            assertEquals(2, availability.here?.total)
            assertEquals("English JP TAN", availability.here?.callNumber)
            assertEquals(listOf("PRPL"), availability.others.map { it.copies.library.code })
        }

    @Test
    fun theSummaryIsFetchedAfterTheCopiesWithOneMoreCall() =
        runTest {
            val (vm, seen, _) = viewModel(pretendProxy())
            vm.open(hit, tampines)
            assertEquals("Big lizards.", vm.withSummary().title?.summary)
            assertEquals(listOf(true, false), seen.map { it.isAvailability })
        }

    @Test
    fun aSummaryKeptOnThePhoneNeedsNoDetailsCall() =
        runTest {
            val (vm, seen, store) = viewModel(pretendProxy())
            store.put(listOf(dinosaur.copy(summary = "Kept.")))
            vm.open(hit, tampines)
            val shown = vm.state.value.title
            assertEquals("Kept.", shown?.summary) // already there, before any answer
            vm.loaded()
            assertTrue(seen.none { it.isDetails })
        }

    @Test
    fun whenTheShelfCannotBeCheckedTheRestStaysAndTryAgainWorks() =
        runTest {
            var up = false
            val (vm, _, _) =
                viewModel { request ->
                    if (request.isAvailability) {
                        if (up) HttpStatusCode.OK to availability(item(1, "TRL")) else HttpStatusCode.InternalServerError to ""
                    } else {
                        HttpStatusCode.OK to details(1)
                    }
                }
            vm.open(hit, tampines)
            assertEquals(CopiesState.Failed, vm.loaded().copies)
            val shown = vm.state.value.title
            assertEquals("Dinosaur", shown?.title)

            up = true
            vm.retry()
            assertIs<CopiesState.Loaded>(vm.loaded().copies)
        }

    @Test
    fun openingTheSameBookAgainAsksNothingMore() =
        runTest {
            val (vm, seen, _) = viewModel(pretendProxy())
            vm.open(hit, tampines)
            vm.loaded()
            val calls = seen.size
            vm.open(hit, tampines)
            assertEquals(calls, seen.size)
        }

    @Test
    fun aBookReopenedAfterClosingChecksTheShelfAgain() =
        runTest {
            val (vm, seen, _) = viewModel(pretendProxy())
            vm.open(hit, tampines)
            vm.loaded()
            vm.close()
            assertNull(vm.state.value.title)
            seen.clear()
            vm.open(hit, tampines)
            vm.loaded()
            assertTrue(seen.any { it.isAvailability })
        }

    @Test
    fun noCopyAtThisLibraryIsSaidSoAndOthersStillList() =
        runTest {
            val (vm, _, _) = viewModel(pretendProxy { availability(item(1, "PRPL"), item(1, "PRL", "Out")) })
            vm.open(hit, tampines)
            val availability = assertIs<CopiesState.Loaded>(vm.loaded().copies).availability
            assertNull(availability.here)
            assertEquals(listOf("PRPL", "PRL"), availability.others.map { it.copies.library.code })
        }
}
