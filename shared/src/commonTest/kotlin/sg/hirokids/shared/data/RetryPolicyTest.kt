package sg.hirokids.shared.data

import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** FR-12: HTTP 429 is retried after 1, 2 and 4 seconds, at most 3 times, before the error is shown. */
class RetryPolicyTest {
    private val delays = mutableListOf<Long>()
    private val policy = RetryPolicy(sleep = { delays += it })

    private suspend fun <T> request(
        vararg statuses: HttpStatusCode,
        ok: T,
    ): Pair<T, Int> {
        var calls = 0
        val result =
            policy.run {
                val status = statuses.getOrNull(calls++) ?: HttpStatusCode.OK
                if (status == HttpStatusCode.OK) ok else throw statusError(status)
            }
        return result to calls
    }

    @Test
    fun aSuccessfulCallIsNotDelayed() =
        runTest {
            val (value, calls) = request(ok = "fine")
            assertEquals("fine", value)
            assertEquals(1, calls)
            assertEquals(emptyList(), delays)
        }

    @Test
    fun busyOnceWaitsOneSecondThenSucceeds() =
        runTest {
            val (value, calls) = request(HttpStatusCode.TooManyRequests, ok = "fine")
            assertEquals("fine", value)
            assertEquals(2, calls)
            assertEquals(listOf(1_000L), delays)
        }

    @Test
    fun busyThreeTimesWaitsOneTwoAndFourSecondsThenSucceeds() =
        runTest {
            val (value, calls) =
                request(
                    HttpStatusCode.TooManyRequests,
                    HttpStatusCode.TooManyRequests,
                    HttpStatusCode.TooManyRequests,
                    ok = "fine",
                )
            assertEquals("fine", value)
            assertEquals(4, calls)
            assertEquals(listOf(1_000L, 2_000L, 4_000L), delays)
        }

    @Test
    fun stillBusyAfterThreeRetriesPassesTheErrorUp() =
        runTest {
            var calls = 0
            val error =
                assertFailsWith<ResponseException> {
                    policy.run<String> {
                        calls++
                        throw statusError(HttpStatusCode.TooManyRequests)
                    }
                }
            assertEquals(429, error.response.status.value)
            assertEquals(4, calls) // the first try and three retries
            assertEquals(listOf(1_000L, 2_000L, 4_000L), delays)
        }

    @Test
    fun otherErrorsAreNotRetried() =
        runTest {
            for (status in listOf(HttpStatusCode.InternalServerError, HttpStatusCode.BadGateway, HttpStatusCode.NotFound)) {
                var calls = 0
                assertFailsWith<ResponseException> {
                    policy.run<String> {
                        calls++
                        throw statusError(status)
                    }
                }
                assertEquals(1, calls, status.toString())
            }
            assertEquals(emptyList(), delays)
        }

    @Test
    fun aFailureThatIsNotAnHttpErrorIsNotRetried() =
        runTest {
            var calls = 0
            assertFailsWith<IllegalStateException> {
                policy.run<String> {
                    calls++
                    error("offline")
                }
            }
            assertEquals(1, calls)
        }
}
