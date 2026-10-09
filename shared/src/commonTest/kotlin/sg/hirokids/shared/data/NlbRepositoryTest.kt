package sg.hirokids.shared.data

import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import sg.hirokids.shared.domain.LatLon
import sg.hirokids.shared.domain.Library
import sg.hirokids.shared.domain.Mode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Search through the proxy (FR-1, FR-2, FR-14): what is asked, what is shown, what is kept. */
class NlbRepositoryTest {
    private val tampines = Library("TRL", "Tampines Regional Library", LatLon(1.352391, 103.940821))

    private fun repository(
        store: BookStore = MemoryStore(),
        respond: (HttpRequestData) -> Pair<HttpStatusCode, String>,
    ) = mockRepository(store, respond)

    private fun keywords(text: String) = SearchQuery.Keywords(text)

    // --- what is asked -----------------------------------------------------------------------------------------------

    @Test
    fun aChildrensSearchAsksForJuvenileBooksOnTheShelfAtTheCurrentLibrary() =
        runTest {
            val (repo, seen, _) = repository { HttpStatusCode.OK to response() }
            repo.search(keywords("dinosaur"), tampines, Mode.CHILDREN)
            val url = seen.single().url
            assertEquals("/catalogue/SearchTitles", url.encodedPath)
            assertEquals("dinosaur", url.parameters["Keywords"])
            assertEquals("juvenile", url.parameters["IntendedAudiences"])
            assertEquals("bks", url.parameters["MaterialTypes"])
            assertEquals("trl", url.parameters["Locations"]) // NLB's facet ids and filters are lower case
            assertEquals("true", url.parameters["Availability"])
            assertEquals("20", url.parameters["Limit"])
            assertEquals("0", url.parameters["Offset"])
        }

    @Test
    fun theNextPageAsksForTheGivenOffset() =
        runTest {
            val (repo, seen, _) = repository { HttpStatusCode.OK to response() }
            repo.search(keywords("dinosaur"), tampines, Mode.CHILDREN, offset = 40)
            assertEquals("40", seen.single().url.parameters["Offset"])
        }

    @Test
    fun onlyTheBranchCodeLeavesThePhone() =
        runTest {
            val (repo, seen, _) = repository { HttpStatusCode.OK to response() }
            repo.search(keywords("dinosaur"), tampines, Mode.CHILDREN)
            val sent =
                seen.single().url.toString() +
                    seen
                        .single()
                        .headers
                        .entries()
                        .joinToString()
            assertFalse(sent.contains("1.3523"), sent) // no coordinates (NFR-5)
            assertFalse(sent.contains("103.94"), sent)
            assertFalse(sent.contains("Tampines"), sent) // not even the library's name
        }

    @Test
    fun anIsbnQueryAsksGetTitlesByIsbnAndNotSearchTitles() =
        runTest {
            val flat = """{"totalRecords":1,"count":1,"hasMoreRecords":false,"nextRecordsOffset":1,"titles":[${record(205717763)}]}"""
            val (repo, seen, _) = repository { HttpStatusCode.OK to flat }
            val page = repo.search(SearchQuery.Isbn("9781801041850"), tampines, Mode.CHILDREN)
            val url = seen.single().url
            assertEquals("/catalogue/GetTitles", url.encodedPath)
            assertEquals("9781801041850", url.parameters["ISBN"])
            assertNull(url.parameters["Locations"])
            assertEquals(listOf(205717763L), page.hits.map { it.title.brn })
        }

    // --- what is shown -----------------------------------------------------------------------------------------------

    @Test
    fun aTitleThatFailsTheAudienceGateIsNeverReturned() =
        runTest {
            val adult = record(2, subjects = """["Dinosaurs"]""") // no children's marker
            val restricted = record(3, extra = ""","isRestricted":true""").replace(""""isRestricted":false,""", "")
            val older = record(4, extra = ""","audience":["16+"]""")
            val ok = record(1)
            val (repo, _, _) =
                repository {
                    HttpStatusCode.OK to
                        response(group("A", ok), group("B", adult), group("C", restricted), group("D", older))
                }
            val page = repo.search(keywords("dinosaur"), tampines, Mode.CHILDREN)
            assertEquals(listOf(1L), page.hits.map { it.title.brn })
            assertEquals(3, page.hidden)
        }

    @Test
    fun aGroupShowsOnlyItsAllowedRecords() =
        runTest {
            val junior = record(10)
            val adultEdition = record(11, subjects = """["Dinosaurs"]""")
            val (repo, _, _) = repository { HttpStatusCode.OK to response(group("Dinosaur", adultEdition, junior)) }
            val hit = repo.search(keywords("dinosaur"), tampines, Mode.CHILDREN).hits.single()
            assertEquals(10L, hit.title.brn)
            assertEquals(listOf(10L), hit.records.map { it.brn })
        }

    @Test
    fun theCardTitleIsReadFromTheGroupAndTheCoverIsTheMediumOne() =
        runTest {
            val (repo, _, _) = repository { HttpStatusCode.OK to response(group("Dinosaur", record(1), author = "Hepworth, Amelia")) }
            val title =
                repo
                    .search(keywords("dinosaur"), tampines, Mode.CHILDREN)
                    .hits
                    .single()
                    .title
            assertEquals("Dinosaur", title.title)
            assertEquals("Hepworth, Amelia", title.author)
            assertEquals("https://c/m", title.coverUrl)
            assertEquals("Book", title.format)
            assertEquals(2, title.reservations)
        }

    @Test
    fun searchResultsAreMarkedAsReportedOnTheShelfByTheSearchButIsbnResultsAreNot() =
        runTest {
            val (repo, _, _) = repository { HttpStatusCode.OK to response(group("A", record(1))) }
            assertTrue(
                repo
                    .search(keywords("a1"), tampines, Mode.CHILDREN)
                    .hits
                    .single()
                    .reportedOnShelfHere,
            )
            val flat = """{"totalRecords":1,"count":1,"hasMoreRecords":false,"nextRecordsOffset":1,"titles":[${record(1)}]}"""
            val (isbnRepo, _, _) = repository { HttpStatusCode.OK to flat }
            assertFalse(
                isbnRepo
                    .search(SearchQuery.Isbn("9781801041850"), tampines, Mode.CHILDREN)
                    .hits
                    .single()
                    .reportedOnShelfHere,
            )
        }

    @Test
    fun aRecordWithoutACoverGroupGetsTheCoverAddressForItsIsbn() =
        runTest {
            val flat = """{"totalRecords":1,"count":1,"hasMoreRecords":false,"nextRecordsOffset":1,"titles":[${record(7)}]}"""
            val (repo, _, _) = repository { HttpStatusCode.OK to flat }
            val title =
                repo
                    .search(SearchQuery.Isbn("9780000007"), tampines, Mode.CHILDREN)
                    .hits
                    .single()
                    .title
            assertEquals("https://eservice.nlb.gov.sg/bookcoverwrapper/cover/978000007?s=MD", title.coverUrl)
        }

    @Test
    fun adultModeShowsNothingUntilItIsBuilt() =
        runTest {
            val (repo, seen, _) = repository { HttpStatusCode.OK to response(group("A", record(1))) }
            val page = repo.search(keywords("dinosaur"), tampines, Mode.ADULT)
            assertEquals("adult", seen.single().url.parameters["IntendedAudiences"])
            assertEquals(emptyList(), page.hits)
        }

    // --- paging ------------------------------------------------------------------------------------------------------

    @Test
    fun thePageSaysWhetherThereIsMoreAndWhereToContinue() =
        runTest {
            val (repo, _, _) = repository { HttpStatusCode.OK to response(group("A", record(1)), hasMore = true, next = 20) }
            val page = repo.search(keywords("dinosaur"), tampines, Mode.CHILDREN)
            assertTrue(page.hasMore)
            assertEquals(20, page.nextOffset)
        }

    @Test
    fun noMoreMeansNoNextOffset() =
        runTest {
            val (repo, _, _) = repository { HttpStatusCode.OK to response(group("A", record(1)), hasMore = false, next = 20) }
            val page = repo.search(keywords("dinosaur"), tampines, Mode.CHILDREN)
            assertFalse(page.hasMore)
            assertNull(page.nextOffset)
        }

    // --- the local book store ----------------------------------------------------------------------------------------

    @Test
    fun allowedTitlesAreKeptByBrnAndHiddenOnesAreNot() =
        runTest {
            val (repo, _, store) =
                repository {
                    HttpStatusCode.OK to
                        response(group("A", record(1)), group("B", record(2, subjects = """["Dinosaurs"]""")))
                }
            repo.search(keywords("dinosaur"), tampines, Mode.CHILDREN)
            assertEquals("A", store.get(1)?.title)
            assertNull(store.get(2))
        }

    @Test
    fun aStoredTitleIsReadWithoutAnyNetworkCall() =
        runTest {
            val (repo, seen, _) = repository { HttpStatusCode.OK to response(group("A", record(1))) }
            repo.search(keywords("dinosaur"), tampines, Mode.CHILDREN)
            seen.clear()
            assertEquals("A", repo.stored(1)?.title)
            assertNull(repo.stored(99))
            assertEquals(0, seen.size)
        }

    // --- failures ----------------------------------------------------------------------------------------------------

    @Test
    fun aProxyErrorIsPassedUpSoTheScreenCanOfferRetry() =
        runTest {
            val (repo, _, _) = repository { HttpStatusCode.TooManyRequests to "" }
            val error = assertFailsWith<ResponseException> { repo.search(keywords("dinosaur"), tampines, Mode.CHILDREN) }
            assertEquals(429, error.response.status.value)
        }
}
