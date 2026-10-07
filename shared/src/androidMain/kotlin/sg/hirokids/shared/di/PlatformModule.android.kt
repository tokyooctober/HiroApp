package sg.hirokids.shared.di

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import org.koin.core.module.Module
import org.koin.dsl.module
import sg.hirokids.shared.db.AppDatabase

actual fun platformModule(): Module =
    module {
        single<SqlDriver> { AndroidSqliteDriver(AppDatabase.Schema, get<Context>(), "hirokids.db") }
    }
