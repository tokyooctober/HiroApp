package sg.hirokids.app

import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import sg.hirokids.shared.data.CurrentLibraryStore
import sg.hirokids.shared.data.LanguageStore
import sg.hirokids.shared.data.ProxyApi
import sg.hirokids.shared.data.createHttpClient
import sg.hirokids.shared.domain.BookLanguage
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The proxy for the UI tests: answers every call the app makes from the test's own [answer], so no test needs the real proxy or
 * the network. The app's HTTP client is swapped for one that talks to this, once per process; each test sets [answer] and starts at
 * a known library and language.
 */
object PretendProxy {
    @Volatile
    var answer: (HttpRequestData) -> String = { "{}" }

    /** Every request the app has made since [start]. */
    val requests = CopyOnWriteArrayList<HttpRequestData>()

    private var installed = false

    fun start(
        library: String = "TRL",
        language: BookLanguage = BookLanguage.ENGLISH,
        answer: (HttpRequestData) -> String,
    ) {
        this.answer = answer
        requests.clear()
        if (!installed) {
            val engine =
                MockEngine { request ->
                    requests += request
                    respond(this@PretendProxy.answer(request), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            loadKoinModules(
                module {
                    single { createHttpClient(engine) }
                    single { ProxyApi(get(), "http://proxy.test") }
                },
            )
            installed = true
        }
        val koin = GlobalContext.get()
        koin.get<CurrentLibraryStore>().code = library // so no location permission or library picker is in the way
        koin.get<LanguageStore>().language = language
    }

    fun stop() {
        GlobalContext.get().get<CurrentLibraryStore>().clear()
    }

    /** A response recorded in `fixtures/`. */
    fun fixture(name: String): String =
        InstrumentationRegistry
            .getInstrumentation()
            .context.assets
            .open(name)
            .bufferedReader()
            .use { it.readText() }

    /** What NLB says when nothing matches. */
    const val NOTHING = """{"totalRecords":0,"count":0,"hasMoreRecords":false,"nextRecordsOffset":0,"titles":[],"facets":[]}"""

    /** A `SearchTitles` answer with one children's book per title given, and the `location` facet of [shelfCounts] (branch code to books). */
    fun books(
        vararg titles: String,
        shelfCounts: Map<String, Int> = emptyMap(),
    ): String {
        val groups =
            titles.withIndex().joinToString(",") { (i, title) ->
                """{"title":"$title","author":"Quinn, Jordan","coverUrl":{"small":"","medium":"","large":""},"records":[""" +
                    """{"brn":${i + 1},"isbns":["978000000${i + 1}"],"format":{"code":"1","name":"Book"},""" +
                    """"subjects":["Dragons Juvenile fiction."],"isRestricted":false,"activeReservationsCount":0,"availability":true}]}"""
            }
        val facets =
            if (shelfCounts.isEmpty()) {
                "[]"
            } else {
                """[{"id":"location","name":"Location","values":[""" +
                    shelfCounts.entries.joinToString(",") { (code, n) -> """{"id":"$code","data":"$code","count":$n}""" } + "]}]"
            }
        return """{"totalRecords":${titles.size},"count":${titles.size},"hasMoreRecords":false,"nextRecordsOffset":0,""" +
            """"titles":[$groups],"facets":$facets}"""
    }
}

fun HttpRequestData.isBranches() = url.encodedPath.endsWith("GetBranches")
