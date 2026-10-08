package sg.hirokids.app

import android.app.Application
import android.content.Context
import org.koin.dsl.module
import sg.hirokids.shared.di.initKoin

class HiroApplication : Application() {
    /** One for the whole app: the view model that uses it outlives any single Activity. */
    val location: AndroidLocationProvider by lazy { AndroidLocationProvider(this) }

    override fun onCreate() {
        super.onCreate()
        val app = this
        initKoin(
            proxyBaseUrl = BuildConfig.PROXY_BASE_URL,
            extra = module { single<Context> { app } },
        )
    }
}
