package sg.hirokids.shared.di

import com.russhwolf.settings.Settings
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import sg.hirokids.shared.data.ProxyApi
import sg.hirokids.shared.data.createHttpClient
import sg.hirokids.shared.db.AppDatabase
import sg.hirokids.shared.ui.library.LibraryCountViewModel

/** Platform pieces (SQLite driver). Android needs a `Context` binding from the app. */
expect fun platformModule(): Module

fun sharedModule(proxyBaseUrl: String): Module =
    module {
        single { createHttpClient() }
        single { ProxyApi(get(), proxyBaseUrl) }
        single { AppDatabase(get()) }
        single<Settings> { Settings() }
        viewModelOf(::LibraryCountViewModel)
    }

/** Starts Koin for the shared code. Android adds its `Context`; iOS calls this from Swift. */
fun initKoin(
    proxyBaseUrl: String,
    extra: Module? = null,
): KoinApplication =
    startKoin {
        modules(listOfNotNull(sharedModule(proxyBaseUrl), platformModule(), extra))
    }
