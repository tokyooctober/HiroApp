package sg.hirokids.shared.data

import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay

/**
 * FR-12: when the proxy or NLB answers HTTP 429 the call is tried again after 1, 2 and 4 seconds. If it is still busy after
 * those three retries the error goes up and the screen offers Retry. Every other failure goes up at once.
 * [sleep] is a parameter so tests can run without waiting.
 */
class RetryPolicy(
    private val backoffMillis: List<Long> = DEFAULT_BACKOFF_MILLIS,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun <T> run(block: suspend () -> T): T {
        var retries = 0
        while (true) {
            try {
                return block()
            } catch (e: ResponseException) {
                if (e.response.status != HttpStatusCode.TooManyRequests || retries >= backoffMillis.size) throw e
                sleep(backoffMillis[retries++])
            }
        }
    }

    private companion object {
        val DEFAULT_BACKOFF_MILLIS = listOf(1_000L, 2_000L, 4_000L)
    }
}
