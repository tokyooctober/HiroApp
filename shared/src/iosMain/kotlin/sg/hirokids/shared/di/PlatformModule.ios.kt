package sg.hirokids.shared.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import org.koin.core.module.Module
import org.koin.dsl.module
import sg.hirokids.shared.db.AppDatabase

actual fun platformModule(): Module =
    module {
        single<SqlDriver> { NativeSqliteDriver(AppDatabase.Schema, "hirokids.db") }
    }
