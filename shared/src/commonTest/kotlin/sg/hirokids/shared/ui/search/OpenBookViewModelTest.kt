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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Opening a card and coming back (FR-6): the results are exactly as they were, with no new call. */
@OptIn(ExperimentalCoroutinesApi::class)
class OpenBookViewModelTest {
    private val tampines = searchTestDirectory.byCode("TRL")!!

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun openingACardShowsTheDetailAndBackBringsTheSameResultsWithoutAskingNlb() =
        runTest {
            val (repo, seen, _) = mockRepository(respond = searchRouter(response(group("A", record(1))), response()))
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()
            val before = vm.state.value.results
            val calls = seen.size

            val loaded = vm.loaded()
            val hit = loaded.onShelf.hits.single()
            vm.openBook(hit)
            assertEquals(SearchScreen.DETAIL, vm.state.value.screen)
            assertEquals(hit, vm.state.value.openBook)

            vm.closeBook()
            assertEquals(SearchScreen.RESULTS, vm.state.value.screen)
            assertNull(vm.state.value.openBook)
            assertEquals(before, vm.state.value.results)
            assertEquals(calls, seen.size)
        }

    @Test
    fun closingWhenNoBookIsOpenChangesNothing() =
        runTest {
            val (repo, _, _) = mockRepository(respond = searchRouter(response(), response()))
            val vm = searchViewModel(repo)
            vm.closeBook()
            assertEquals(SearchScreen.HOME, vm.state.value.screen)
        }
}
