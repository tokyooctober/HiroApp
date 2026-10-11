package sg.hirokids.shared.ui.search

import com.russhwolf.settings.MapSettings
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import sg.hirokids.shared.data.LanguageStore
import sg.hirokids.shared.data.group
import sg.hirokids.shared.data.locationFacets
import sg.hirokids.shared.data.mockRepository
import sg.hirokids.shared.data.record
import sg.hirokids.shared.data.response
import sg.hirokids.shared.domain.BookLanguage
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The language filter (FR-7): English first, one tap to another language, remembered, and never mixed up by Back. */
@OptIn(ExperimentalCoroutinesApi::class)
class LanguageViewModelTest {
    private val tampines = searchTestDirectory.byCode("TRL")!!
    private val pasirRis = searchTestDirectory.byCode("PRPL")!!

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Every call answers with a book named for the language it was asked for, and one other library has books. */
    private val byLanguage = { request: HttpRequestData ->
        val p = request.url.parameters
        val language = p["Languages"] ?: "all"
        HttpStatusCode.OK to
            if (p["Locations"] == null) {
                response(facets = locationFacets("trl" to 1, "prpl" to 1))
            } else {
                response(group("Book in $language", record(1)))
            }
    }

    @Test
    fun aFirstSearchAsksForEnglishBooksOnAllThreeCalls() =
        runTest {
            val (repo, seen, _) = mockRepository(respond = byLanguage)
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()
            assertEquals(BookLanguage.ENGLISH, vm.state.value.language)
            assertEquals(listOf("eng", "eng", "eng"), seen.map { it.url.parameters["Languages"] })
            assertEquals(listOf("Book in eng"), vm.loaded().onShelf.titles())
        }

    @Test
    fun choosingAnotherLanguageSearchesTheSameWordsAgainInThatLanguage() =
        runTest {
            val (repo, seen, _) = mockRepository(respond = byLanguage)
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()
            seen.clear()

            vm.chooseLanguage(BookLanguage.CHINESE, tampines)
            vm.settled()

            assertEquals(BookLanguage.CHINESE, vm.state.value.language)
            assertEquals("dinosaur", vm.state.value.searchedQuery)
            assertEquals(listOf("chi", "chi", "chi"), seen.map { it.url.parameters["Languages"] })
            assertEquals(listOf("Book in chi"), vm.loaded().onShelf.titles())
            assertEquals(listOf("shelf", "out", "others"), seen.groups())
        }

    @Test
    fun allSendsNoLanguageAtAll() =
        runTest {
            val (repo, seen, _) = mockRepository(respond = byLanguage)
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()
            seen.clear()

            vm.chooseLanguage(BookLanguage.ALL, tampines)
            vm.settled()

            assertTrue(seen.all { it.url.parameters["Languages"] == null })
            assertEquals(listOf("Book in all"), vm.loaded().onShelf.titles())
        }

    @Test
    fun choosingTheLanguageAlreadyShownAsksNothing() =
        runTest {
            val (repo, seen, _) = mockRepository(respond = byLanguage)
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()
            seen.clear()
            vm.chooseLanguage(BookLanguage.ENGLISH, tampines)
            assertEquals(0, seen.size)
        }

    @Test
    fun theChoiceIsRememberedForTheNextVisit() =
        runTest {
            val settings = MapSettings()
            val (repo, _, _) = mockRepository(respond = byLanguage)
            val first = searchViewModel(repo, LanguageStore(settings))
            first.onQueryChange("dinosaur")
            first.submit(tampines)
            first.settled()
            first.chooseLanguage(BookLanguage.MALAY, tampines)
            first.settled()

            val later = searchViewModel(repo, LanguageStore(settings))
            assertEquals(BookLanguage.MALAY, later.state.value.language)
        }

    @Test
    fun anIsbnLookupTakesNoLanguageAndOffersNoFilter() =
        runTest {
            val (repo, seen, _) = mockRepository(respond = { HttpStatusCode.OK to response(record(1)) })
            val vm = searchViewModel(repo)
            vm.onQueryChange("9781801041850")
            vm.submit(tampines)
            vm.settled()
            assertFalse(vm.state.value.canFilterLanguage)
            assertTrue(seen.all { it.url.parameters["Languages"] == null })
        }

    @Test
    fun aKeywordSearchOffersTheFilter() =
        runTest {
            val (repo, _, _) = mockRepository(respond = byLanguage)
            val vm = searchViewModel(repo)
            assertFalse(vm.state.value.canFilterLanguage) // nothing searched yet
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()
            assertTrue(vm.state.value.canFilterLanguage)
        }

    @Test
    fun backAfterViewBringsBackTheLanguageOfTheResultsItRestores() =
        runTest {
            val (repo, _, _) = mockRepository(respond = byLanguage)
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()

            vm.view(pasirRis, from = tampines)
            vm.settled()
            vm.chooseLanguage(BookLanguage.CHINESE, pasirRis)
            vm.settled()
            assertEquals(listOf("Book in chi"), vm.loaded().onShelf.titles())

            vm.back()
            assertEquals(BookLanguage.ENGLISH, vm.state.value.language)
            assertEquals(listOf("Book in eng"), vm.loaded().onShelf.titles())
        }
}
