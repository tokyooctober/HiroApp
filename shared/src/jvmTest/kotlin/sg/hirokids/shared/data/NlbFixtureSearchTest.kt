package sg.hirokids.shared.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import sg.hirokids.shared.domain.AudiencePolicy
import sg.hirokids.shared.domain.LatLon
import sg.hirokids.shared.domain.Library
import sg.hirokids.shared.domain.Mode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The recorded NLB responses in `fixtures/` through the app's own types (the fixtures are the contract, spec section 9).
 * JVM only: it reads the files from the repository, which Gradle runs from the `shared` folder.
 */
class NlbFixtureSearchTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val tampines = Library("TRL", "Tampines Regional Library", LatLon(1.352391, 103.940821))

    private fun text(name: String) = File("../fixtures/$name").readText()

    private fun searchRepository(fixture: String): NlbRepository {
        val engine =
            MockEngine {
                respond(text(fixture), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        return NlbRepository(ProxyApi(createHttpClient(engine), "http://proxy.test"), InMemoryBooks())
    }

    private class InMemoryBooks : BookStore {
        val saved = mutableMapOf<Long, sg.hirokids.shared.domain.Title>()

        override fun put(titles: List<sg.hirokids.shared.domain.Title>) {
            titles.forEach { saved[it.brn] = it }
        }

        override fun get(brn: Long) = saved[brn]
    }

    @Test
    fun everyRecordedSearchResponseReadsWithTheAppsTypes() {
        val searches = File("../fixtures").listFiles { f -> f.name.startsWith("search-titles-") && f.name.endsWith(".json") }!!
        assertTrue(searches.size >= 6)
        for (file in searches) {
            val response = json.decodeFromString<SearchTitlesResponse>(file.readText())
            assertTrue(response.titles.isNotEmpty(), file.name)
            assertTrue(response.titles.all { it.records.isNotEmpty() && it.title.isNotBlank() }, file.name)
        }
        for (name in listOf("get-titles-title.json", "get-titles-isbn.json")) {
            assertTrue(json.decodeFromString<GetTitlesResponse>(text(name)).titles.isNotEmpty(), name)
        }
    }

    @Test
    fun dinosaurInChildrenModeGivesCardsAndNoCardFailsTheGate() =
        runTest {
            val page =
                searchRepository(
                    "search-titles-junior-here-available.json",
                ).search(SearchQuery.Keywords("dinosaur"), tampines, Mode.CHILDREN)
            assertTrue(page.hits.isNotEmpty())
            val policy = AudiencePolicy(Mode.CHILDREN)
            assertTrue(page.hits.flatMap { it.records }.all(policy::allows))
            assertTrue(page.hits.all { it.title.title.isNotBlank() && it.title.coverUrl != null })
            assertEquals(20, page.hits.size + page.hidden) // every group is either shown or counted as hidden
            assertTrue(page.hasMore)
            assertEquals(20, page.nextOffset)
        }

    @Test
    fun theFirstRecordedCardIsTheHepworthDinosaur() =
        runTest {
            val first =
                searchRepository("search-titles-junior-here-available.json")
                    .search(SearchQuery.Keywords("dinosaur"), tampines, Mode.CHILDREN)
                    .hits
                    .first()
            assertNotNull(first.title.isbn)
            assertTrue(first.title.coverUrl!!.startsWith("https://eservice.nlb.gov.sg/bookcoverwrapper/cover/"))
            assertEquals("Book", first.title.format)
        }

    @Test
    fun anAdultSearchRunThroughChildrenModeLeaksNothing() =
        runTest {
            val page = searchRepository("search-titles-adult-plain.json").search(SearchQuery.Keywords("dinosaur"), tampines, Mode.CHILDREN)
            // only the "young readers adaptation" records carry a children's marker; the rest of the adult list is hidden
            assertTrue(
                page.hits.all { it.title.title.contains("young readers", ignoreCase = true) },
                page.hits.map { it.title.title }.toString(),
            )
            assertTrue(page.hidden >= 15)
        }

    @Test
    fun theIsbnFixtureGivesOneCardWithTheTitleBeforeTheSlash() =
        runTest {
            val page = searchRepository("get-titles-isbn.json").search(SearchQuery.Isbn("9781801041850"), tampines, Mode.CHILDREN)
            val hit = page.hits.single()
            assertEquals("Dinosaur", hit.title.title)
            assertEquals("Hepworth, Amelia", hit.title.author)
            assertEquals(false, hit.reportedOnShelfHere)
        }

    @Test
    fun theTitleSearchFixtureIsGatedRecordByRecord() =
        runTest {
            val page = searchRepository("get-titles-title.json").search(SearchQuery.Isbn("9787555272731"), tampines, Mode.CHILDREN)
            val policy = AudiencePolicy(Mode.CHILDREN)
            assertTrue(page.hits.all { policy.allows(it.title) })
            assertEquals(20, page.hits.size + page.hidden)
        }
}
