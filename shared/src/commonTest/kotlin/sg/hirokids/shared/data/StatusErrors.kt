package sg.hirokids.shared.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

/** A real [ResponseException] for [status], made the way the app gets one: a request through the app's own client. */
internal suspend fun statusError(status: HttpStatusCode): ResponseException =
    try {
        createHttpClient(MockEngine { respondError(status) }).get("http://proxy.test/x")
        error("expected $status")
    } catch (e: ResponseException) {
        e
    }
