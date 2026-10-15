package sg.hirokids.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import io.ktor.client.request.HttpRequestData
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The search flow on a real device, from the search box to the results (FR-1, FR-5), against a pretend proxy that answers
 * with the responses recorded in `fixtures/`, so it needs neither the proxy nor the network.
 */
class SearchFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    /** Which recorded response the pretend proxy sends for this request (the same split the app makes: here / everywhere / on loan). */
    private fun HttpRequestData.answer(): String {
        val params = url.parameters
        val here = params["Locations"] != null
        val onShelf = params["Availability"] == "true"
        return PretendProxy.fixture(
            when {
                isBranches() -> "library-get-branches.json"
                here && onShelf -> "search-titles-junior-here-available.json"
                here -> "search-titles-junior-here-any.json"
                onShelf -> "search-titles-junior-everywhere-available.json"
                else -> "search-titles-junior-plain.json"
            },
        )
    }

    @Before
    fun setUp() {
        PretendProxy.start { it.answer() }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario?.close()
        PretendProxy.stop()
    }

    @Test
    fun searchingShowsBooksOnTheShelfAtTheChosenLibrary() {
        compose.onNodeWithText("Find a book").assertIsDisplayed()
        compose.onNodeWithText("Tampines Regional Library").assertIsDisplayed()

        compose.onNodeWithText("Title, author, topic, ISBN").performTextInput("dinosaur")
        compose.onNodeWithText("Search").performClick()

        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText("On the shelf here").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("At Tampines Regional Library").assertIsDisplayed()
        compose.onNodeWithText("Dinosaur", substring = false).assertIsDisplayed()
    }
}
