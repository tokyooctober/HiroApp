package sg.hirokids.shared.di

import com.russhwolf.settings.Settings
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import sg.hirokids.shared.data.BookStore
import sg.hirokids.shared.data.CurrentLibraryStore
import sg.hirokids.shared.data.DirectorySource
import sg.hirokids.shared.data.LibraryRepository
import sg.hirokids.shared.data.LibrarySeedSource
import sg.hirokids.shared.data.NlbRepository
import sg.hirokids.shared.data.ProxyApi
import sg.hirokids.shared.data.ResourceSeedSource
import sg.hirokids.shared.data.SqlBookStore
import sg.hirokids.shared.data.createHttpClient
import sg.hirokids.shared.db.AppDatabase
import sg.hirokids.shared.domain.LocationProvider
import sg.hirokids.shared.ui.library.LibraryViewModel
import sg.hirokids.shared.ui.search.SearchViewModel

/** Platform pieces (SQLite driver). Android needs a `Context` binding from the app. */
expect fun platformModule(): Module

fun sharedModule(proxyBaseUrl: String): Module =
    module {
        single { createHttpClient() }
        single { ProxyApi(get(), proxyBaseUrl) }
        single { AppDatabase(get()) }
        single<Settings> { Settings() }
        single<BookStore> { SqlBookStore(get()) }
        single { NlbRepository(get(), get()) }
        single { CurrentLibraryStore(get()) }
        single<LibrarySeedSource> { ResourceSeedSource() }
        single<DirectorySource> { LibraryRepository(get(), get()) }
        // the platform supplies the location provider when the screen is created
        viewModel { (location: LocationProvider) -> LibraryViewModel(get(), get(), location) }
        viewModel { SearchViewModel(get(), get()) }
    }

/** Starts Koin for the shared code. Android adds its `Context`; iOS calls this from Swift. */
fun initKoin(
    proxyBaseUrl: String,
    extra: Module? = null,
): KoinApplication =
    startKoin {
        modules(listOfNotNull(sharedModule(proxyBaseUrl), platformModule(), extra))
    }
