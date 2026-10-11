package sg.hirokids.shared.data

import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import sg.hirokids.shared.domain.BookLanguage
import sg.hirokids.shared.domain.CopyStatus
import sg.hirokids.shared.domain.LatLon
import sg.hirokids.shared.domain.Library
import sg.hirokids.shared.domain.Mode
import sg.hirokids.shared.domain.ResultGroup
import sg.hirokids.shared.domain.Title
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
        retry: RetryPolicy = RetryPolicy(sleep = {}),
        respond: (HttpRequestData) -> Pair<HttpStatusCode, String>,
    ) = mockRepository(store, retry, respond)

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

    // --- group 2: in this library, but all copies out (FR-4) -----------------------------------------------------------

    @Test
    fun theAllCopiesOutGroupAsksForTheLibraryWithoutTheAvailabilityFilter() =
        runTest {
            val (repo, seen, _) = repository { HttpStatusCode.OK to response(group("A", record(1))) }
            val page = repo.search(keywords("dinosaur"), tampines, Mode.CHILDREN, group = ResultGroup.ALL_HERE)
            val url = seen.single().url
            assertEquals("trl", url.parameters["Locations"])
            assertNull(url.parameters["Availability"])
            assertEquals("juvenile", url.parameters["IntendedAudiences"])
            assertFalse(page.hits.single().reportedOnShelfHere)
        }

    @Test
    fun anIsbnLookupHasNoAllCopiesOutGroupAndMakesNoCall() =
        runTest {
            val (repo, seen, _) = repository { HttpStatusCode.OK to response() }
            val page = repo.search(SearchQuery.Isbn("9781801041850"), tampines, Mode.CHILDREN, group = ResultGroup.ALL_HERE)
            assertEquals(emptyList(), page.hits)
            assertFalse(page.hasMore)
            assertEquals(0, seen.size)
        }

    // --- busy proxy (FR-12) ---------------------------------------------------------------------------------------------

    @Test
    fun aBusyProxyIsRetriedWithBackOffBeforeTheErrorReachesTheScreen() =
        runTest {
            val waits = mutableListOf<Long>()
            var calls = 0
            val (repo, seen, _) =
                repository(retry = RetryPolicy(sleep = { waits += it })) {
                    if (++calls <= 2) HttpStatusCode.TooManyRequests to "" else HttpStatusCode.OK to response(group("A", record(1)))
                }
            val page = repo.search(keywords("dinosaur"), tampines, Mode.CHILDREN)
            assertEquals(1, page.hits.size)
            assertEquals(3, seen.size)
            assertEquals(listOf(1_000L, 2_000L), waits)
        }

    // --- saved books while offline (FR-12, FR-14) --------------------------------------------------------------------------

    @Test
    fun savedBooksMatchingTheWordsAreAvailableWithoutTheNetwork() =
        runTest {
            val (repo, seen, _) =
                repository {
                    HttpStatusCode.OK to response(group("Dinosaur eggs", record(1)), group("Cats and kittens", record(2)))
                }
            repo.search(keywords("animals"), tampines, Mode.CHILDREN)
            seen.clear()
            assertEquals(listOf("Dinosaur eggs"), repo.saved(keywords("eggs dino"), Mode.CHILDREN).map { it.title })
            assertEquals(emptyList(), repo.saved(keywords("dragon"), Mode.CHILDREN).map { it.title })
            assertEquals(0, seen.size)
        }

    @Test
    fun savedBooksAreMatchedByIsbnToo() =
        runTest {
            val (repo, _, _) = repository { HttpStatusCode.OK to response(group("Dinosaur", record(7))) }
            repo.search(keywords("dinosaur"), tampines, Mode.CHILDREN)
            assertEquals(listOf(7L), repo.saved(SearchQuery.Isbn("978000007"), Mode.CHILDREN).map { it.brn })
        }

    @Test
    fun savedBooksAreRunThroughTheAudienceGateAgain() =
        runTest {
            val store = MemoryStore()
            // a title that should never have been stored (for example by an older version of the app)
            store.put(
                listOf(
                    sg.hirokids.shared.domain.Title(
                        9,
                        null,
                        "Dinosaur adult",
                        null,
                        null,
                        listOf("Dinosaurs"),
                        emptyList(),
                        emptyList(),
                        false,
                        0,
                    ),
                ),
            )
            val (repo, _, _) = repository(store) { HttpStatusCode.OK to response() }
            assertEquals(emptyList(), repo.saved(keywords("dinosaur"), Mode.CHILDREN))
        }

    // --- group 3: other libraries (FR-15) -------------------------------------------------------------------------------------

    @Test
    fun theOtherLibrariesCallAsksForBooksOnTheShelfAnywhereAndSendsNoBranch() =
        runTest {
            val facets = locationFacets("prpl" to 3, "prl" to 1)
            val (repo, seen, _) = repository { HttpStatusCode.OK to response(group("A", record(1)), facets = facets) }
            val counts = repo.shelfCounts(SearchQuery.Keywords("dinosaur"), Mode.CHILDREN)
            val url = seen.single().url
            assertNull(url.parameters["Locations"])
            assertEquals("true", url.parameters["Availability"])
            assertEquals("juvenile", url.parameters["IntendedAudiences"])
            assertEquals("dinosaur", url.parameters["Keywords"])
            assertEquals(mapOf("prpl" to 3, "prl" to 1), counts)
        }

    @Test
    fun aResponseWithoutALocationFacetGivesNoCounts() =
        runTest {
            val (repo, _, _) = repository { HttpStatusCode.OK to response(group("A", record(1))) }
            assertEquals(emptyMap(), repo.shelfCounts(SearchQuery.Keywords("dinosaur"), Mode.CHILDREN))
        }

    @Test
    fun theOtherLibrariesCallIsRetriedWhenBusyLikeTheOthers() =
        runTest {
            var calls = 0
            val (repo, seen, _) =
                repository {
                    if (++calls ==
                        1
                    ) {
                        HttpStatusCode.TooManyRequests to ""
                    } else {
                        HttpStatusCode.OK to response(facets = locationFacets("prpl" to 2))
                    }
                }
            assertEquals(mapOf("prpl" to 2), repo.shelfCounts(SearchQuery.Keywords("dinosaur"), Mode.CHILDREN))
            assertEquals(2, seen.size)
        }

    // --- language (FR-7) ---------------------------------------------------------------------------------------------

    @Test
    fun aLanguageFilterIsSentOnEveryKindOfSearchCallAndNoFilterSendsNothing() =
        runTest {
            val (repo, seen, _) = repository { HttpStatusCode.OK to response() }
            val chinese = SearchQuery.Keywords("dinosaur", BookLanguage.CHINESE)
            repo.search(chinese, tampines, Mode.CHILDREN)
            repo.search(chinese, tampines, Mode.CHILDREN, group = ResultGroup.ALL_HERE)
            repo.shelfCounts(chinese, Mode.CHILDREN)
            assertEquals(listOf("chi", "chi", "chi"), seen.map { it.url.parameters["Languages"] })

            seen.clear()
            repo.search(keywords("dinosaur"), tampines, Mode.CHILDREN)
            assertNull(seen.single().url.parameters["Languages"])
        }

    @Test
    fun theLanguagesOfARecordAreReadAndKept() =
        runTest {
            val (repo, _, store) =
                repository {
                    HttpStatusCode.OK to
                        response(
                            group("Dinosaur", record(1, extra = ""","language":["Chinese","English"]""")),
                            group("Cats", record(2)),
                        )
                }
            val page = repo.search(keywords("animals"), tampines, Mode.CHILDREN)
            assertEquals(listOf("Chinese", "English"), page.hits[0].title.languages)
            assertEquals(emptyList(), page.hits[1].title.languages)
            assertEquals(listOf("Chinese", "English"), store.get(1)?.languages)
        }

    @Test
    fun savedBooksAreNarrowedToTheLanguageButOnesWithNoRecordedLanguageStay() =
        runTest {
            val (repo, _, _) =
                repository {
                    HttpStatusCode.OK to
                        response(
                            group("Dinosaur english", record(1, extra = ""","language":["English"]""")),
                            group("Dinosaur chinese", record(2, extra = ""","language":["Chinese"]""")),
                            group("Dinosaur unknown", record(3)),
                        )
                }
            repo.search(keywords("dinosaur"), tampines, Mode.CHILDREN)

            fun saved(language: BookLanguage) =
                repo
                    .saved(SearchQuery.Keywords("dinosaur", language), Mode.CHILDREN)
                    .map { it.brn }
                    .sorted()

            assertEquals(listOf(1L, 3L), saved(BookLanguage.ENGLISH))
            assertEquals(listOf(2L, 3L), saved(BookLanguage.CHINESE))
            assertEquals(listOf(1L, 2L, 3L), saved(BookLanguage.ALL))
        }

    // --- book detail: copies and summary (FR-6, FR-14, FR-15) --------------------------------------------------------

    private fun title(
        brn: Long,
        summary: String? = null,
    ) = Title(
        brn = brn,
        isbn = null,
        title = "Dinosaur",
        author = null,
        coverUrl = null,
        subjects = listOf("Dinosaurs Juvenile literature."),
        audience = emptyList(),
        audienceImda = emptyList(),
        isRestricted = false,
        reservations = 0,
        summary = summary,
    )

    @Test
    fun copiesAreReadPageByPageUntilNlbSaysThereAreNoMore() =
        runTest {
            val (repo, seen, _) =
                repository { request ->
                    val offset = request.url.parameters["Offset"]
                    HttpStatusCode.OK to
                        if (offset == "0") {
                            availability(item(1, "TRL"), item(1, "PRPL"), hasMore = true, next = 2)
                        } else {
                            availability(item(1, "BEPL", state = "Out"))
                        }
                }
            val copies = repo.copies(listOf(title(1)), Mode.CHILDREN)
            assertEquals(listOf("TRL", "PRPL", "BEPL"), copies.map { it.branchCode })
            assertEquals(listOf("0", "2"), seen.map { it.url.parameters["Offset"] })
            assertEquals(listOf("1", "1"), seen.map { it.url.parameters["BRN"] })
        }

    @Test
    fun copiesNotForChildrenAreNotReturned() =
        runTest {
            val (repo, _, _) =
                repository {
                    HttpStatusCode.OK to
                        availability(item(1, "TRL"), item(1, "TRL", usage = "ADULT", usageName = "Adult"), item(1, "PRPL", usage = "TEEN"))
                }
            assertEquals(listOf("TRL"), repo.copies(listOf(title(1)), Mode.CHILDREN).map { it.branchCode })
        }

    @Test
    fun onlyACopyThatIsInAndAvailableIsOnTheShelf() =
        runTest {
            val (repo, _, _) =
                repository {
                    HttpStatusCode.OK to availability(item(1, "TRL", "In"), item(1, "TRL", "Shelving"), item(1, "TRL", "Out"))
                }
            assertEquals(
                listOf(CopyStatus.AVAILABLE, CopyStatus.IN_PROCESS, CopyStatus.ON_LOAN),
                repo.copies(listOf(title(1)), Mode.CHILDREN).map { it.status },
            )
        }

    @Test
    fun everyRecordOfATitleIsAskedFor() =
        runTest {
            val (repo, seen, _) =
                repository { request ->
                    HttpStatusCode.OK to availability(item(request.url.parameters["BRN"]!!.toLong(), "TRL"))
                }
            val copies = repo.copies(listOf(title(1), title(2)), Mode.CHILDREN)
            assertEquals(listOf(1L, 2L), copies.map { it.brn })
            assertEquals(2, seen.size)
        }

    @Test
    fun theSummaryIsFetchedOnceAndKept() =
        runTest {
            val (repo, seen, store) = repository { HttpStatusCode.OK to details(1, "Dinosaurs were incredible creatures!") }
            val first = repo.withSummary(title(1), Mode.CHILDREN)
            assertEquals("Dinosaurs were incredible creatures!", first?.summary)
            assertEquals("Dinosaurs were incredible creatures!", store.get(1)?.summary)
            assertEquals(1, seen.size)
            assertTrue(seen.single().isDetails)

            seen.clear()
            repo.withSummary(first!!, Mode.CHILDREN)
            assertEquals(0, seen.size) // already known: no call (FR-14)
        }

    @Test
    fun aBookWithNoSummaryIsRecordedAsHavingNone() =
        runTest {
            val noSummary = details(1).replace(""""summary":["A book about dinosaurs."],""", "")
            val (repo, seen, store) = repository { HttpStatusCode.OK to noSummary }
            assertEquals("", repo.withSummary(title(1), Mode.CHILDREN)?.summary)
            assertEquals("", store.get(1)?.summary)
            repo.withSummary(store.get(1)!!, Mode.CHILDREN)
            assertEquals(1, seen.size)
        }

    @Test
    fun aSearchDoesNotWipeASummaryKeptEarlier() =
        runTest {
            val (repo, _, store) = repository { HttpStatusCode.OK to response(group("Dinosaur", record(1))) }
            store.put(listOf(title(1, summary = "Kept.")))
            repo.search(keywords("dinosaur"), tampines, Mode.CHILDREN)
            assertEquals("Kept.", store.get(1)?.summary)
        }

    @Test
    fun aTitleThatNoLongerPassesTheAudienceGateWithItsFullRecordIsNotShown() =
        runTest {
            val (repo, _, store) =
                repository {
                    HttpStatusCode.OK to details(1).replace(""""isRestricted":false""", """"isRestricted":false,"audience":["Adult"]""")
                }
            assertNull(repo.withSummary(title(1), Mode.CHILDREN))
            assertNull(store.get(1)?.summary)
        }
}
