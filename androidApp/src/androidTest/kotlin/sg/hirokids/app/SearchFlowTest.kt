package sg.hirokids.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import sg.hirokids.shared.data.CurrentLibraryStore
import sg.hirokids.shared.data.ProxyApi
import sg.hirokids.shared.data.createHttpClient

/**
 * The search flow on a real device, from the search box to the results (FR-1, FR-5), against a pretend proxy that answers
 * with the responses recorded in `fixtures/`, so it needs neither the proxy nor the network.
 */
class SearchFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    private fun fixture(name: String) =
        InstrumentationRegistry
            .getInstrumentation()
            .context.assets
            .open(name)
            .bufferedReader()
            .use { it.readText() }

    /** Which recorded response the pretend proxy sends for this request (the same split the app makes: here / everywhere / on loan). */
    private fun HttpRequestData.answer(): String {
        val params = url.parameters
        val here = params["Locations"] != null
        val onShelf = params["Availability"] == "true"
        return when {
            url.encodedPath.endsWith("GetBranches") -> "library-get-branches.json"
            here && onShelf -> "search-titles-junior-here-available.json"
            here -> "search-titles-junior-here-any.json"
            onShelf -> "search-titles-junior-everywhere-available.json"
            else -> "search-titles-junior-plain.json"
        }.let(::fixture)
    }

    @Before
    fun setUp() {
        val engine =
            MockEngine { request ->
                respond(request.answer(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        loadKoinModules(
            module {
                single { createHttpClient(engine) }
                single { ProxyApi(get(), "http://proxy.test") }
            },
        )
        // Start at Tampines Regional Library, so no location permission or library picker is in the way.
        GlobalContext.get().get<CurrentLibraryStore>().code = "TRL"
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario?.close()
        GlobalContext.get().get<CurrentLibraryStore>().clear()
    }

    @Test
    fun searchingShowsBooksOnTheShelfAtTheChosenLibrary() {
        compose.onNodeWithText("Find a book").assertIsDisplayed()
        compose.onNodeWithText("Tampines Regional Library").assertIsDisplayed()

        compose.onNodeWithText("Title, author, topic, ISBN").performTextInput("dinosaur")
        compose.onNodeWithText("Search").performClick()

        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTextCount("On the shelf here") > 0
        }
        compose.onNodeWithText("At Tampines Regional Library").assertIsDisplayed()
        compose.onNodeWithText("Dinosaur", substring = false).assertIsDisplayed()
    }
}

private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTextCount(text: String) =
    onAllNodesWithText(text).fetchSemanticsNodes().size
