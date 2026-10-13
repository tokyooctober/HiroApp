package sg.hirokids.shared.ui.search

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import sg.hirokids.shared.data.LibraryRepository
import sg.hirokids.shared.data.LibrarySeedSource
import sg.hirokids.shared.data.ProxyApi
import sg.hirokids.shared.data.createHttpClient
import sg.hirokids.shared.data.group
import sg.hirokids.shared.data.mockRepository
import sg.hirokids.shared.data.record
import sg.hirokids.shared.data.response
import sg.hirokids.shared.domain.ResultGroup
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The proxy is not always there: it may not have been started when the app opened, or it may be restarted while the app is in use.
 * The app must show the failure, offer Try again, and carry on as soon as the proxy answers, with no restart of the app.
 * "Connection refused" is simulated by the pretend proxy throwing, as the real HTTP engine does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProxyComesAndGoesTest {
    private val tampines = searchTestDirectory.byCode("TRL")!!
    private var proxyIsUp = false

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun proxy(request: HttpRequestData): Pair<HttpStatusCode, String> {
        if (!proxyIsUp) error("Connection refused")
        return searchRouter(onShelf = response(group("Dinosaur", record(1))), allHere = response())(request)
    }

    @Test
    fun aProxyThatWasNotRunningWhenTheAppOpenedWorksOnceItIsStarted() =
        runTest {
            val (repo, _, _) = mockRepository(respond = ::proxy)
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            assertIs<ResultsState.Failed>(vm.settled().results) // the error and Try again

            proxyIsUp = true // the owner starts the proxy; the app is not restarted
            vm.retry(tampines)

            vm.settled()
            assertEquals(listOf("Dinosaur"), vm.loaded().onShelf.titles())
        }

    @Test
    fun theSearchButtonWorksAsWellAsTryAgain() =
        runTest {
            val (repo, _, _) = mockRepository(respond = ::proxy)
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            assertIs<ResultsState.Failed>(vm.settled().results)

            proxyIsUp = true
            vm.submit(tampines)

            assertIs<ResultsState.Loaded>(vm.settled().results)
        }

    @Test
    fun aProxyRestartedWhileTheAppIsOpenCostsOneFailedSearchAndNothingMore() =
        runTest {
            proxyIsUp = true
            val (repo, _, _) = mockRepository(respond = ::proxy)
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            assertIs<ResultsState.Loaded>(vm.settled().results)

            proxyIsUp = false // being restarted
            vm.onQueryChange("dragons")
            vm.submit(tampines)
            assertIs<ResultsState.Failed>(vm.settled().results)

            proxyIsUp = true // back up
            vm.retry(tampines)

            assertIs<ResultsState.Loaded>(vm.settled().results)
            assertEquals("dragons", vm.state.value.searchedQuery)
        }

    @Test
    fun theProxyGoingDownWhileMoreResultsLoadKeepsTheBooksAndRetryContinues() =
        runTest {
            proxyIsUp = true
            val page = response(group("A", record(1)), group("B", record(2)), hasMore = true, next = 20)
            val (repo, _, _) =
                mockRepository { request ->
                    val offset = request.url.parameters["Offset"]
                    if (!proxyIsUp && offset == "20") error("Connection refused")
                    if (offset == "20") {
                        HttpStatusCode.OK to response(group("C", record(3)))
                    } else {
                        searchRouter(onShelf = page, allHere = response())(request)
                    }
                }
            val vm = searchViewModel(repo)
            vm.onQueryChange("dinosaur")
            vm.submit(tampines)
            vm.settled()

            proxyIsUp = false
            vm.loadMore(tampines, ResultGroup.ON_SHELF)
            vm.settled()
            assertEquals(listOf("A", "B"), vm.loaded().onShelf.titles()) // nothing lost
            assertTrue(vm.loaded().onShelf.failed)

            proxyIsUp = true
            vm.retryGroup(tampines, ResultGroup.ON_SHELF)
            vm.settled()
            assertEquals(listOf("A", "B", "C"), vm.loaded().onShelf.titles())
        }

    // ---- the library list (it comes from the proxy too)

    private val seedJson =
        """{"libraries":[{"branchCode":"TRL","name":"Tampines Regional Library","lat":1.0,"lng":103.0,"status":"open"},""" +
            """{"branchCode":"GONE","name":"Not Listed Library","lat":1.1,"lng":103.1,"status":"open"}]}"""
    private val liveJson =
        """{"totalRecords":1,"branches":[{"branchCode":"TRL","branchName":"Tampines Library",""" +
            """"coordinates":{"geoLatitude":"1.352391","geoLongitude":"103.940821"}}]}"""

    private fun libraries(calls: MutableList<HttpRequestData>): LibraryRepository {
        val engine =
            MockEngine { request ->
                calls += request
                if (!proxyIsUp) error("Connection refused")
                respond(liveJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        return LibraryRepository(LibrarySeedSource { seedJson }, ProxyApi(createHttpClient(engine), "http://proxy.test"))
    }

    @Test
    fun theLibraryListFallsBackToTheBundledOneThenUsesNlbsOnceTheProxyIsUp() =
        runTest {
            val calls = mutableListOf<HttpRequestData>()
            val libraries = libraries(calls)

            val offline = libraries.directory() // proxy not started: the bundled list, so the picker still works
            assertEquals(listOf("GONE", "TRL"), offline.libraries.map { it.code }.sorted())

            proxyIsUp = true
            val online = libraries.directory() // asked again: the live list now applies
            assertEquals(listOf("TRL"), online.libraries.map { it.code })
            assertEquals(1.352391, online.byCode("TRL")!!.position.lat)

            libraries.directory() // and a live list is kept: no further request
            assertEquals(2, calls.size)
        }
}
