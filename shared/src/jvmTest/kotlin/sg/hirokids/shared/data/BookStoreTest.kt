package sg.hirokids.shared.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import sg.hirokids.shared.db.AppDatabase
import sg.hirokids.shared.domain.Title
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The SQLDelight book store on an in-memory database (FR-14). */
class BookStoreTest {
    private val dinosaur =
        Title(
            brn = 205717763,
            isbn = "9781801041850",
            title = "Dinosaur",
            author = "Hepworth, Amelia",
            coverUrl = "https://c/m",
            format = "Book",
            subjects = listOf("Tyrannosaurus rex Pictorial works Juvenile literature.", "Fossils; \"quoted\", comma"),
            audience = listOf("9-12."),
            audienceImda = emptyList(),
            isRestricted = false,
            reservations = 3,
        )

    private fun store(): SqlBookStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        return SqlBookStore(AppDatabase(driver))
    }

    @Test
    fun aTitleSurvivesTheRoundTripExactly() {
        val store = store()
        store.put(listOf(dinosaur))
        assertEquals(dinosaur, store.get(dinosaur.brn))
    }

    @Test
    fun anUnknownBrnIsNull() {
        assertNull(store().get(1))
    }

    @Test
    fun savingAgainReplacesTheEarlierCopyAndNothingExpires() {
        val store = store()
        store.put(listOf(dinosaur))
        store.put(listOf(dinosaur.copy(reservations = 7, author = null)))
        assertEquals(7, store.get(dinosaur.brn)?.reservations)
        assertNull(store.get(dinosaur.brn)?.author)
    }

    @Test
    fun severalTitlesAreSavedTogether() {
        val store = store()
        store.put(listOf(dinosaur, dinosaur.copy(brn = 2, isbn = null, coverUrl = null, format = null)))
        assertEquals(205717763L, store.get(205717763)?.brn)
        assertEquals(2L, store.get(2)?.brn)
    }

    @Test
    fun aPhoneThatAlreadyHasTheEarlierDatabaseGainsTheBookTable() {
        // Version 1 had only the meta table (Tasks 4 and 5). An installed app must upgrade without losing it.
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "CREATE TABLE meta (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)", 0)
        driver.execute(null, "INSERT INTO meta(key, value) VALUES ('currentLibraryCode', 'TRL')", 0)
        driver.execute(null, "PRAGMA user_version = 1", 0)

        AppDatabase.Schema.migrate(driver, 1, AppDatabase.Schema.version)

        val database = AppDatabase(driver)
        assertEquals("TRL", database.metaQueries.selectValue("currentLibraryCode").executeAsOne())
        val store = SqlBookStore(database)
        store.put(listOf(dinosaur))
        assertEquals(dinosaur, store.get(dinosaur.brn))
    }

    @Test
    fun searchFindsSavedTitlesByWordsInTitleAuthorOrIsbnWithoutRegardToCase() {
        val store = store()
        store.put(
            listOf(
                dinosaur,
                dinosaur.copy(
                    brn = 2,
                    isbn = "111",
                    title = "Cats and kittens",
                    author = "Smith, Jo",
                    subjects = listOf("Cats Juvenile fiction."),
                ),
            ),
        )
        assertEquals(listOf(205717763L), store.search("DINOSAUR", 20).map { it.brn })
        assertEquals(listOf(2L), store.search("smith kittens", 20).map { it.brn })
        assertEquals(listOf(205717763L), store.search("9781801041850", 20).map { it.brn })
        assertEquals(emptyList(), store.search("dragon", 20).map { it.brn })
        assertEquals(emptyList(), store.search("   ", 20).map { it.brn })
    }

    @Test
    fun searchTreatsPercentAndUnderscoreAsPlainCharacters() {
        val store = store()
        store.put(listOf(dinosaur))
        assertEquals(emptyList(), store.search("%", 20).map { it.brn })
        assertEquals(emptyList(), store.search("_", 20).map { it.brn })
    }

    @Test
    fun searchReturnsAtMostTheLimit() {
        val store = store()
        store.put((1L..5L).map { dinosaur.copy(brn = it) })
        assertEquals(3, store.search("dinosaur", 3).size)
    }

    @Test
    fun searchNeedsEveryWordNotJustOne() {
        val store = store()
        store.put(listOf(dinosaur.copy(brn = 1, title = "Dinosaur bones"), dinosaur.copy(brn = 2, title = "Dinosaur eggs")))
        assertEquals(emptyList(), store.search("bones eggs", 20).map { it.brn })
        assertEquals(listOf(1L), store.search("dinosaur bones", 20).map { it.brn })
    }
}
