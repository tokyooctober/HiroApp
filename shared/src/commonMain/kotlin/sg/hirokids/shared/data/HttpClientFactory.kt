package sg.hirokids.shared.data

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * The one place the app's HTTP client is configured, so tests run against the same setup as production.
 * `expectSuccess` turns HTTP 429 and 5xx from the proxy into exceptions, which the screens show as a retry (FR-12).
 * Pass an engine in tests (MockEngine); in the apps the platform engine (OkHttp, Darwin) is picked up automatically.
 */
fun createHttpClient(engine: HttpClientEngine? = null): HttpClient {
    val configure: HttpClientConfig<*>.() -> Unit = {
        expectSuccess = true
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
    return if (engine == null) HttpClient(configure) else HttpClient(engine, configure)
}
