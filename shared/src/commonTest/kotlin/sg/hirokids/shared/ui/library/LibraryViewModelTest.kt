package sg.hirokids.shared.ui.library

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import sg.hirokids.shared.data.CurrentLibraryStore
import sg.hirokids.shared.domain.LibraryDirectory
import sg.hirokids.shared.domain.LocationResult
import sg.hirokids.shared.domain.northBy
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** FR-3: current library, detection, picker. Covers the acceptance criteria of Task 5. */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(
        settings: Settings = MapSettings(),
        provider: FakeLocationProvider = FakeLocationProvider(),
        directory: LibraryDirectory = Spots.directory(),
    ): LibraryViewModel {
        val vm = LibraryViewModel(FakeDirectorySource(directory), CurrentLibraryStore(settings), provider)
        advanceUntilIdle()
        return vm
    }

    private fun LibraryViewModel.names() = state.value.items.map { it.library.name }

    // ---- first run, saved choice, restart

    @Test
    fun firstRunOpensThePickerWithNoLibrarySelectedAndOnlyOpenLibraries() =
        runTest(dispatcher) {
            val vm = viewModel()
            val s = vm.state.value
            assertTrue(s.pickerOpen)
            assertNull(s.current)
            // Ang Mo Kio (upcoming) and Queenstown (closed) never appear
            assertEquals(listOf("Pasir Ris Public Library", "Punggol Regional Library", "Tampines Regional Library"), vm.names())
        }

    @Test
    fun aSavedLibraryIsShownAndThePickerStaysClosed() =
        runTest(dispatcher) {
            val settings = MapSettings()
            CurrentLibraryStore(settings).code = "TRL"
            val s = viewModel(settings).state.value
            assertEquals("Tampines Regional Library", s.current?.name)
            assertFalse(s.pickerOpen)
        }

    @Test
    fun theChoiceSurvivesARestart() =
        runTest(dispatcher) {
            val settings = MapSettings()
            val first = viewModel(settings)
            first.select("PRPL")
            first.confirm()
            advanceUntilIdle()
            val afterRestart = viewModel(settings) // a new view model on the same storage
            assertEquals(
                "PRPL",
                afterRestart.state.value.current
                    ?.code,
            )
            assertFalse(afterRestart.state.value.pickerOpen)
        }

    @Test
    fun aSavedLibraryThatCannotBeChosenAnymoreIsForgottenAndThePickerOpens() =
        runTest(dispatcher) {
            val settings = MapSettings()
            CurrentLibraryStore(settings).code = "QUPL" // Queenstown is closed
            val s = viewModel(settings).state.value
            assertNull(s.current)
            assertTrue(s.pickerOpen)
            assertNull(CurrentLibraryStore(settings).code)
        }

    // ---- detection

    @Test
    fun withinThreeHundredMetresTheNearestLibraryIsSetAndSaved() =
        runTest(dispatcher) {
            val settings = MapSettings()
            val provider = FakeLocationProvider(LocationResult.Precise(Spots.tampines.northBy(120.0)))
            val vm = viewModel(settings, provider)
            assertEquals(
                "TRL",
                vm.state.value.current
                    ?.code,
            )
            assertFalse(vm.state.value.pickerOpen)
            assertEquals("TRL", CurrentLibraryStore(settings).code)
        }

    @Test
    fun theDetectButtonAsksForThePermissionButStartupDoesNot() =
        runTest(dispatcher) {
            val provider = FakeLocationProvider(LocationResult.Denied)
            val vm = viewModel(provider = provider)
            assertEquals(listOf(false), provider.prompts) // silent attempt on open: never a permission dialog
            vm.detect()
            advanceUntilIdle()
            assertEquals(listOf(false, true), provider.prompts)
        }

    @Test
    fun preciseButOutsideTheRadiusKeepsThePickerOpenSortedClosestFirstWithAnotice() =
        runTest(dispatcher) {
            val provider = FakeLocationProvider(LocationResult.Precise(Spots.tampines.northBy(2_000.0)))
            val vm = viewModel(provider = provider)
            vm.detect()
            advanceUntilIdle()
            val s = vm.state.value
            assertNull(s.current)
            assertTrue(s.pickerOpen)
            assertEquals(DetectNotice.NOT_AT_A_LIBRARY, s.notice)
            assertEquals(listOf("Pasir Ris Public Library", "Tampines Regional Library", "Punggol Regional Library"), vm.names())
            assertNotNull(s.items.first().meters)
        }

    @Test
    fun anApproximateLocationNeverAutoSelectsOrShowsDistancesButStillOrdersTheListClosestFirst() =
        runTest(dispatcher) {
            // even exactly on top of a library: an approximate fix cannot be trusted to 300 m
            val provider = FakeLocationProvider(LocationResult.Approximate(Spots.tampines))
            val vm = viewModel(provider = provider)
            vm.detect()
            advanceUntilIdle()
            val s = vm.state.value
            assertNull(s.current)
            assertTrue(s.pickerOpen)
            assertEquals(DetectNotice.APPROXIMATE_ONLY, s.notice)
            assertEquals("Tampines Regional Library", vm.names().first())
            // the fix is blurred by the phone, so a distance such as "250 m away" would claim precision it does not have
            assertTrue(s.items.all { it.meters == null })
        }

    @Test
    fun aDeniedPermissionLeavesTheListByNameWithANoticeAndNoDistances() =
        runTest(dispatcher) {
            val vm = viewModel(provider = FakeLocationProvider(LocationResult.Denied))
            vm.detect()
            advanceUntilIdle()
            val s = vm.state.value
            assertEquals(DetectNotice.DENIED, s.notice)
            assertTrue(s.pickerOpen)
            assertTrue(s.items.all { it.meters == null })
            assertEquals(vm.names().sorted(), vm.names())
        }

    @Test
    fun anUnavailableLocationShowsItsOwnNotice() =
        runTest(dispatcher) {
            val vm = viewModel(provider = FakeLocationProvider(LocationResult.Unavailable))
            vm.detect()
            advanceUntilIdle()
            assertEquals(DetectNotice.UNAVAILABLE, vm.state.value.notice)
        }

    @Test
    fun startupDetectionStaysQuietWhenItFindsNothing() =
        runTest(dispatcher) {
            val vm = viewModel(provider = FakeLocationProvider(LocationResult.Denied))
            assertNull(vm.state.value.notice) // notices appear only after the user taps Detect
        }

    // ---- change, choose, search

    @Test
    fun changeReopensThePickerWithTheCurrentLibraryTicked() =
        runTest(dispatcher) {
            val settings = MapSettings()
            CurrentLibraryStore(settings).code = "PRL"
            val vm = viewModel(settings)
            assertFalse(vm.state.value.pickerOpen)
            vm.change()
            val s = vm.state.value
            assertTrue(s.pickerOpen)
            assertEquals("PRL", s.selectedCode)
        }

    @Test
    fun selectingAndConfirmingSetsAndSavesTheLibrary() =
        runTest(dispatcher) {
            val settings = MapSettings()
            val vm = viewModel(settings)
            vm.select("PRPL")
            assertNull(vm.state.value.current) // ticking alone changes nothing yet
            vm.confirm()
            assertEquals(
                "PRPL",
                vm.state.value.current
                    ?.code,
            )
            assertFalse(vm.state.value.pickerOpen)
            assertEquals("PRPL", CurrentLibraryStore(settings).code)
        }

    @Test
    fun confirmingWithNothingTickedDoesNothing() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.confirm()
            assertNull(vm.state.value.current)
            assertTrue(vm.state.value.pickerOpen)
        }

    @Test
    fun aLibraryThatIsNotInTheDirectoryCannotBeTicked() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.select("AMKPL") // upcoming, hidden
            assertNull(vm.state.value.selectedCode)
        }

    @Test
    fun searchFiltersByNameIgnoringCaseAndKeepsTheOrder() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.search("  REGIONAL ")
            assertEquals(listOf("Punggol Regional Library", "Tampines Regional Library"), vm.names())
            vm.search("zzz")
            assertTrue(
                vm.state.value.items
                    .isEmpty(),
            )
        }

    @Test
    fun thePickerCanOnlyBeClosedWhenThereIsALibraryToReturnTo() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.closePicker()
            assertTrue(vm.state.value.pickerOpen) // no current library yet: stays open
            vm.select("TRL")
            vm.confirm()
            vm.change()
            vm.closePicker()
            assertFalse(vm.state.value.pickerOpen)
            assertEquals(
                "TRL",
                vm.state.value.current
                    ?.code,
            )
        }

    @Test
    fun closingThePickerDropsASearchAndTheTickBackToTheCurrentLibrary() =
        runTest(dispatcher) {
            val settings = MapSettings()
            CurrentLibraryStore(settings).code = "TRL"
            val vm = viewModel(settings)
            vm.change()
            vm.search("pasir")
            vm.select("PRPL")
            vm.closePicker()
            vm.change()
            assertEquals("", vm.state.value.query)
            assertEquals("TRL", vm.state.value.selectedCode)
        }
}
