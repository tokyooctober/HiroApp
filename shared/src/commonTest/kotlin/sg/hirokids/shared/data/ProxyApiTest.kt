package sg.hirokids.shared.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import sg.hirokids.shared.domain.LatLon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ProxyApiTest {
    private val twoBranches =
        """{"totalRecords":30,"branches":[""" +
            """{"branchCode":"TRL","branchName":"Tampines Library","extra":1,""" +
            """"coordinates":{"geoLatitude":"1.352391","geoLongitude":"103.940821"}},""" +
            """{"branchCode":"MOLLY","branchName":"Mobile Library"}]}"""

    private fun api(
        baseUrl: String = "http://proxy.test",
        respond: (HttpRequestData) -> Pair<HttpStatusCode, String>,
    ): Pair<ProxyApi, MutableList<HttpRequestData>> {
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
        return ProxyApi(createHttpClient(engine), baseUrl) to seen
    }

    @Test
    fun readsCodesAndPositionsAndIgnoresUnknownFields() =
        runTest {
            val (api, _) = api { HttpStatusCode.OK to twoBranches }
            val branches = api.libraryBranches()
            assertEquals(listOf("TRL", "MOLLY"), branches.map { it.code })
            assertEquals(LatLon(1.352391, 103.940821), branches.first().position)
        }

    @Test
    fun aBranchWithoutCoordinatesHasNoPosition() =
        runTest {
            val (api, _) = api { HttpStatusCode.OK to twoBranches }
            assertNull(api.libraryBranches().last().position)
        }

    @Test
    fun emptyOrZeroCoordinatesAreTreatedAsNoPosition() =
        runTest {
            val json =
                """{"branches":[""" +
                    """{"branchCode":"MOLLY","coordinates":{"geoLatitude":"0.000000","geoLongitude":"0.000000"}},""" +
                    """{"branchCode":"OTHERS","coordinates":{"geoLatitude":"","geoLongitude":""}}]}"""
            val (api, _) = api { HttpStatusCode.OK to json }
            assertEquals(listOf(null, null), api.libraryBranches().map { it.position })
        }

    @Test
    fun callsOnlyTheProxyGetBranchesPathForPublicAndRegionalLibraries() =
        runTest {
            val (api, seen) = api(baseUrl = "http://proxy.test/") { HttpStatusCode.OK to twoBranches }
            api.libraryBranches()
            val url = seen.single().url
            assertEquals("proxy.test", url.host)
            assertEquals("/library/GetBranches", url.encodedPath) // trailing slash of the base URL is not doubled
            assertEquals("active", url.parameters["ListType"])
            assertEquals("PL,RL", url.parameters["LibraryTypes"]) // regional libraries (Tampines, Jurong, ...) are type RL
        }

    @Test
    fun theAppNeverSendsNlbCredentials() =
        runTest {
            // NFR-4: the key and app code live only in the proxy
            val (api, seen) = api { HttpStatusCode.OK to twoBranches }
            api.libraryBranches()
            val headers = seen.single().headers
            assertNull(headers["X-Api-Key"])
            assertNull(headers["X-App-Code"])
        }

    @Test
    fun tooManyRequestsAndServerErrorsSurfaceAsExceptions() =
        runTest {
            // FR-12: 429 and 5xx must reach the screen as errors so it can offer Retry
            for (status in listOf(HttpStatusCode.TooManyRequests, HttpStatusCode.InternalServerError, HttpStatusCode.BadGateway)) {
                val (api, _) = api { status to "" }
                assertFailsWith<ResponseException> { api.libraryBranches() }
            }
        }
}
