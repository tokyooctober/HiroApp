package sg.hirokids.shared.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.koin.core.module.Module
import org.koin.dsl.module
import sg.hirokids.shared.db.AppDatabase

/** JVM exists for tests only (spec section 10); the database is in memory. */
actual fun platformModule(): Module =
    module {
        single<SqlDriver> {
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { AppDatabase.Schema.create(it) }
        }
    }
