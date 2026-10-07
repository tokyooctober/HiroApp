package sg.hirokids.shared.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** SQLDelight round trip on an in-memory driver (spec section 10 testing strategy). */
class AppDatabaseTest {
    private fun database(): AppDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        return AppDatabase(driver)
    }

    @Test
    fun aValueIsStoredReadAndReplaced() {
        val queries = database().metaQueries
        assertNull(queries.selectValue("k").executeAsOneOrNull())
        queries.upsert("k", "one")
        assertEquals("one", queries.selectValue("k").executeAsOne())
        queries.upsert("k", "two")
        assertEquals("two", queries.selectValue("k").executeAsOne())
    }
}
