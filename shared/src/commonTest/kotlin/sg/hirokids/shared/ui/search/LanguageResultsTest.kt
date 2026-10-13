package sg.hirokids.shared.ui.search

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import sg.hirokids.shared.data.LanguageStore
import sg.hirokids.shared.data.group
import sg.hirokids.shared.data.mockRepository
import sg.hirokids.shared.data.record
import sg.hirokids.shared.data.response
import sg.hirokids.shared.domain.BookLanguage
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The results shown are only for the language chosen: a book that is in English is not listed again under Chinese, Malay or Tamil,
 * and a Chinese book is not listed under English. The pretend NLB here answers with every language mixed whatever it is asked for,
 * the worst case, so the check is the app's own and does not rest on NLB honouring `Languages`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LanguageResultsTest {
    private val tampines = searchTestDirectory.byCode("TRL")!!

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun inLanguage(
        brn: Long,
        language: String,
    ) = record(brn, extra = ""","language":["$language"]""")

    /** The same dinosaur book in each language (a separate record each), all in one answer. */
    private val mixed =
        response(
            group("Dinosaur", inLanguage(1, "English")),
            group("恐龙", inLanguage(2, "Chinese")),
            group("Dinosaur Malay", inLanguage(3, "Malay")),
            group("Dinosaur Tamil", inLanguage(4, "Tamil")),
            group("Dinosaur and friends", record(5, extra = ""","language":["Chinese","English"]""")), // in two languages
            group("Dinosaur unknown", record(6)), // NLB gave no language
        )

    private suspend fun shown(language: BookLanguage): List<String> {
        val (repo, _, _) = mockRepository(respond = searchRouter(onShelf = mixed, allHere = mixed))
        val vm = searchViewModel(repo, languages = LanguageStore(MapSettings()).also { it.language = language })
        vm.onQueryChange("dinosaur")
        vm.submit(tampines)
        vm.settled()
        return vm.loaded().onShelf.titles()
    }

    @Test
    fun englishShowsOnlyEnglishBooks() =
        runTest {
            assertEquals(listOf("Dinosaur", "Dinosaur and friends", "Dinosaur unknown"), shown(BookLanguage.ENGLISH))
        }

    @Test
    fun chineseShowsOnlyChineseBooksAndNoEnglishOnes() =
        runTest {
            assertEquals(listOf("恐龙", "Dinosaur and friends", "Dinosaur unknown"), shown(BookLanguage.CHINESE))
        }

    @Test
    fun malayAndTamilShowOnlyTheirOwn() =
        runTest {
            assertEquals(listOf("Dinosaur Malay", "Dinosaur unknown"), shown(BookLanguage.MALAY))
            assertEquals(listOf("Dinosaur Tamil", "Dinosaur unknown"), shown(BookLanguage.TAMIL))
        }

    @Test
    fun allShowsEveryBookExactlyOnce() =
        runTest {
            val titles = shown(BookLanguage.ALL)
            assertEquals(6, titles.size)
            assertEquals(titles.toSet().size, titles.size)
        }

    @Test
    fun aBookIsNotListedInTwoLanguageListsUnlessItIsInBoth() =
        runTest {
            val english = shown(BookLanguage.ENGLISH).toSet()
            val malay = shown(BookLanguage.MALAY).toSet()
            val tamil = shown(BookLanguage.TAMIL).toSet()
            // only the book NLB gives no language for can be in more than one list, because nothing says which it is
            assertEquals(setOf("Dinosaur unknown"), english intersect malay)
            assertEquals(setOf("Dinosaur unknown"), malay intersect tamil)
        }

    @Test
    fun switchingTheLanguageReplacesTheListInsteadOfAddingToIt() =
        runTest {
            val (repo, _, _) = mockRepository(respond = searchRouter(onShelf = mixed, allHere = mixed))
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()
            assertEquals(false, "恐龙" in vm.loaded().onShelf.titles()) // English first

            vm.chooseLanguage(BookLanguage.CHINESE, tampines)
            vm.settled()

            val titles = vm.loaded().onShelf.titles()
            assertEquals(listOf("恐龙", "Dinosaur and friends", "Dinosaur unknown"), titles)
            assertEquals(false, "Dinosaur" in titles)
        }
}
