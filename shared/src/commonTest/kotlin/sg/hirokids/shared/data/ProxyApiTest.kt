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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ProxyApiTest {
    private val twoBranches =
        """{"totalRecords":48,"branches":[{"branchCode":"TRL","branchName":"Tampines Library","extra":1},""" +
            """{"branchCode":"PRL","branchName":"Punggol Library"}]}"""

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
    fun readsTheBranchesAndIgnoresUnknownFields() =
        runTest {
            val (api, _) = api { HttpStatusCode.OK to twoBranches }
            val branches = api.publicLibraries()
            assertEquals(listOf("TRL", "PRL"), branches.map { it.branchCode })
        }

    @Test
    fun callsOnlyTheProxyGetBranchesPathWithTheDocumentedParameters() =
        runTest {
            val (api, seen) = api(baseUrl = "http://proxy.test/") { HttpStatusCode.OK to twoBranches }
            api.publicLibraries()
            val url = seen.single().url
            assertEquals("proxy.test", url.host)
            assertEquals("/library/GetBranches", url.encodedPath) // trailing slash of the base URL is not doubled
            assertEquals("active", url.parameters["ListType"])
            assertEquals("PL", url.parameters["LibraryTypes"])
        }

    @Test
    fun theAppNeverSendsNlbCredentials() =
        runTest {
            // NFR-4: the key and app code live only in the proxy
            val (api, seen) = api { HttpStatusCode.OK to twoBranches }
            api.publicLibraries()
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
                assertFailsWith<ResponseException> { api.publicLibraries() }
            }
        }
}
