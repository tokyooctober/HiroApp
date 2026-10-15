package sg.hirokids.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import io.ktor.client.request.HttpRequestData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import sg.hirokids.shared.domain.BookLanguage

/**
 * The results screen on a real device: View at another library and Back (FR-16), and the hint when the chosen language has no
 * books but another has (FR-7). The pretend proxy answers from small answers built in the test.
 */
class ResultsFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    private fun launch(answer: (HttpRequestData) -> String) {
        PretendProxy.start(language = BookLanguage.ENGLISH, answer = answer)
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario?.close()
        PretendProxy.stop()
    }

    private fun search(words: String) {
        compose.onNodeWithText("Title, author, topic, ISBN").performTextInput(words)
        compose.onNodeWithText("Search").performClick()
    }

    private fun waitForText(text: String) =
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun viewShowsTheSearchAtAnotherLibraryAndBackBringsTheFirstResultsBackWithoutAskingAgain() {
        // a dragon book on the shelf at Tampines, and three at Pasir Ris
        launch { request ->
            val p = request.url.parameters
            when {
                request.isBranches() -> PretendProxy.fixture("library-get-branches.json")
                p["Locations"] == null -> PretendProxy.books("Dragon Kingdom", shelfCounts = mapOf("trl" to 1, "prpl" to 3))
                p["Availability"] == "true" -> PretendProxy.books("Dragon Kingdom")
                else -> PretendProxy.NOTHING
            }
        }
        search("dragon")
        waitForText("Dragon Kingdom")
        compose.onNodeWithText("At Tampines Regional Library").assertIsDisplayed()

        // the other-libraries box lists Pasir Ris with its View button
        waitForText("Pasir Ris Public Library")
        compose.onAllNodesWithText("View").onFirst().performClick()
        waitForText("At Pasir Ris Public Library")

        // Back returns to Tampines with the same results and no new request
        val asked = PretendProxy.requests.size
        compose.onNodeWithContentDescription("Back").performClick()
        waitForText("At Tampines Regional Library")
        compose.onNodeWithText("Dragon Kingdom").assertIsDisplayed()
        assertEquals("going back must not ask NLB again", asked, PretendProxy.requests.size)
    }

    @Test
    fun anEmptyLanguageOffersAllLanguagesWhenAnotherLanguageHasBooks() {
        // Chinese finds nothing for these words, but with no language chosen there is a book
        launch { request ->
            val p = request.url.parameters
            when {
                request.isBranches() -> PretendProxy.fixture("library-get-branches.json")
                p["Languages"] == "chi" -> PretendProxy.NOTHING
                p["Locations"] == null -> PretendProxy.books("Dragon Kingdom")
                p["Availability"] == "true" -> PretendProxy.books("Dragon Kingdom")
                else -> PretendProxy.NOTHING
            }
        }
        search("dragon kingdom")
        waitForText("Dragon Kingdom")

        compose.onNodeWithText("Chinese").performClick()
        waitForText("No Chinese books found for these words. Books in other languages match.")
        compose.onNodeWithText("Show all languages").assertIsDisplayed()

        compose.onNodeWithText("Show all languages").performClick()
        waitForText("Dragon Kingdom")
        compose.onNodeWithText("At Tampines Regional Library").assertIsDisplayed()
    }

    @Test
    fun anEmptySearchWithNoBooksAnywhereKeepsThePlainMessage() {
        launch { request -> if (request.isBranches()) PretendProxy.fixture("library-get-branches.json") else PretendProxy.NOTHING }
        search("zzzz qqqq")
        waitForText("No books found. Try different words.")
        compose.onAllNodesWithText("Show all languages").assertCountEquals(0)
    }
}
