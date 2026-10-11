package sg.hirokids.shared.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryMatchTest {
    private val harbourfront =
        Library(
            code = "HBPL",
            name = "library@harbourfront",
            position = LatLon(1.2656, 103.8222),
            aliases = listOf("Harbourfront Library", "1 HarbourFront Walk, VivoCity"),
        )

    @Test
    fun aBlankQueryMatchesEverything() {
        assertTrue(harbourfront.matches(""))
        assertTrue(harbourfront.matches("   "))
    }

    @Test
    fun theAppsOwnNameStillMatches() {
        assertTrue(harbourfront.matches("library@harbour"))
    }

    @Test
    fun nlbsNameMatchesEvenWhenTheAppNameDiffers() {
        assertTrue(harbourfront.matches("Harbourfront Library"))
    }

    @Test
    fun theMallInTheAddressMatchesWhateverTheSpacingOrCase() {
        assertTrue(harbourfront.matches("vivocity"))
        assertTrue(harbourfront.matches("Vivo City Library"))
    }

    @Test
    fun everyWordMustBeFound() {
        assertFalse(harbourfront.matches("Harbourfront Tampines"))
    }

    @Test
    fun wordsInAnyOrderMatch() {
        assertTrue(harbourfront.matches("library harbourfront"))
    }
}
