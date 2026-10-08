package sg.hirokids.shared.ui.library

import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import sg.hirokids.shared.data.CurrentLibraryStore
import sg.hirokids.shared.data.LibraryRepository
import sg.hirokids.shared.data.LibrarySeedSource
import sg.hirokids.shared.data.ProxyApi
import sg.hirokids.shared.data.createHttpClient
import sg.hirokids.shared.domain.LatLon
import sg.hirokids.shared.domain.LocationResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** NFR-5: coordinates never leave the device; only the library list is requested through the proxy. */
@OptIn(ExperimentalCoroutinesApi::class)
class LocationPrivacyTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val seedJson =
        """{"libraries":[{"branchCode":"TRL","name":"Tampines Regional Library","lat":1.352391,"lng":103.940821,"status":"open"}]}"""
    private val liveJson =
        """{"totalRecords":1,"branches":[{"branchCode":"TRL","branchName":"Tampines Library",""" +
            """"coordinates":{"geoLatitude":"1.352391","geoLongitude":"103.940821"}}]}"""

    @Test
    fun detectingTheLibraryRequestsOnlyTheBranchListAndSendsNoCoordinates() =
        runTest(dispatcher) {
            val seen = mutableListOf<HttpRequestData>()
            val engine =
                MockEngine { request ->
                    seen += request
                    respond(liveJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val repository =
                LibraryRepository(
                    seed = LibrarySeedSource { seedJson },
                    api = ProxyApi(createHttpClient(engine), "http://proxy.test"),
                )
            val here = LatLon(1.352391, 103.940821)
            val vm =
                LibraryViewModel(repository, CurrentLibraryStore(MapSettings()), FakeLocationProvider(LocationResult.Precise(here)))
            // the mock server answers on a background thread: wait for the library to be set instead of advancing virtual time
            vm.state.first { it.current != null }
            vm.detect()
            advanceUntilIdle()

            assertEquals(
                "TRL",
                vm.state.value.current
                    ?.code,
            ) // detection worked...
            assertEquals(1, seen.size) // ...with a single branch-list request, reused (no per-detect request)
            assertEquals("/library/GetBranches", seen.single().url.encodedPath)
            assertEquals(
                setOf("ListType", "LibraryTypes"),
                seen
                    .single()
                    .url.parameters
                    .names(),
            )
            val everything = seen.joinToString { it.url.toString() + it.headers.entries().toString() }
            assertFalse(everything.contains("1.35"))
            assertFalse(everything.contains("103.94"))
            assertTrue(seen.none { it.method.value != "GET" })
        }
}
