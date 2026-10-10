package sg.hirokids.shared.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import sg.hirokids.shared.domain.Title

/** Helpers for tests that run the repository against a pretend proxy. They build the JSON shapes recorded in `fixtures/`. */
internal fun record(
    brn: Long,
    subjects: String = """["Dinosaurs Juvenile literature."]""",
    extra: String = "",
    waiting: Int = 2,
) = """{"brn":$brn,"isbns":["97800000$brn","00000$brn"],"format":{"code":"1","name":"Book"},"subjects":$subjects,""" +
    """"isRestricted":false,"activeReservationsCount":$waiting,"availability":true$extra}"""

internal fun group(
    title: String,
    vararg records: String,
    author: String = "Hepworth, Amelia",
) = """{"title":"$title","author":"$author","coverUrl":{"small":"https://c/s","medium":"https://c/m","large":"https://c/l"},""" +
    """"records":[${records.joinToString(",")}]}"""

internal fun response(
    vararg groups: String,
    hasMore: Boolean = false,
    next: Int = 20,
) = """{"totalRecords":99,"count":${groups.size},"hasMoreRecords":$hasMore,"nextRecordsOffset":$next,""" +
    """"titles":[${groups.joinToString(",")}],"facets":[]}"""

internal class MemoryStore : BookStore {
    val saved = mutableMapOf<Long, Title>()

    override fun put(titles: List<Title>) {
        titles.forEach { saved[it.brn] = it }
    }

    override fun get(brn: Long): Title? = saved[brn]

    override fun search(
        words: String,
        limit: Int,
    ): List<Title> {
        val terms = words.lowercase().split(' ').filter { it.isNotBlank() }
        return saved.values
            .filter { t -> "${t.title} ${t.author.orEmpty()} ${t.isbn.orEmpty()}".lowercase().let { hay -> terms.all { it in hay } } }
            .take(limit)
    }
}

/** A repository whose proxy answers with [respond]; every request it receives is appended to the returned list. */
internal fun mockRepository(
    store: BookStore = MemoryStore(),
    retry: RetryPolicy = RetryPolicy(sleep = {}),
    respond: (HttpRequestData) -> Pair<HttpStatusCode, String>,
): Triple<NlbRepository, MutableList<HttpRequestData>, BookStore> {
    val seen = mutableListOf<HttpRequestData>()
    val engine =
        MockEngine { request ->
            seen += request
            val (status, body) = respond(request)
            if (status == HttpStatusCode.OK) {
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respondError(status)
            }
        }
    return Triple(NlbRepository(ProxyApi(createHttpClient(engine), "http://proxy.test"), store, retry), seen, store)
}
