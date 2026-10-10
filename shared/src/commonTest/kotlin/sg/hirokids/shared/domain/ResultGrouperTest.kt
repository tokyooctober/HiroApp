package sg.hirokids.shared.domain

import sg.hirokids.shared.ui.library.Spots
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** FR-15: the "More on the shelf at other libraries" list. Table-driven, with the golden scenario of spec section 8. */
class ResultGrouperTest {
    private val directory = Spots.directory() // Tampines (TRL), Pasir Ris (PRPL, 3.1 km north), Punggol (PRL, 9 km north)
    private val tampines = directory.byCode("TRL")!!

    private fun codes(counts: Map<String, Int>) = ResultGrouper.otherLibraries(counts, tampines, directory).map { it.library.code }

    @Test
    fun goldenScenarioPasirRisComesBeforePunggolWhenTheyHoldTheSameNumber() {
        // Book C is on the shelf only at Pasir Ris and Punggol; Pasir Ris is nearer to Tampines.
        val others = ResultGrouper.otherLibraries(mapOf("prpl" to 1, "prl" to 1), tampines, directory)
        assertEquals(listOf("PRPL", "PRL"), others.map { it.library.code })
        assertEquals(listOf(1, 1), others.map { it.onShelf })
        assertTrue(others[0].meters in 3_000.0..3_200.0, "Pasir Ris ${others[0].meters} m")
        assertTrue(others[1].meters in 8_900.0..9_100.0, "Punggol ${others[1].meters} m")
    }

    @Test
    fun theLibraryWithMostBooksComesFirstWhateverTheDistance() {
        assertEquals(listOf("PRL", "PRPL"), codes(mapOf("prpl" to 2, "prl" to 5)))
    }

    @Test
    fun theCurrentLibraryIsNeverListed() {
        assertEquals(listOf("PRPL"), codes(mapOf("trl" to 50, "prpl" to 1)))
    }

    @Test
    fun aLibraryWithNoBooksIsLeftOut() {
        assertEquals(listOf("PRL"), codes(mapOf("prpl" to 0, "prl" to 3)))
    }

    @Test
    fun libraryCodesAreMatchedWithoutRegardToCase() {
        assertEquals(listOf("PRPL", "PRL"), codes(mapOf("PRPL" to 1, "Prl" to 1)))
    }

    @Test
    fun aCodeNobodyHasHeardOfIsLeftOut() {
        // the recorded facet has "ld", which is not in NLB's branch list
        assertEquals(listOf("PRPL"), codes(mapOf("ld" to 159, "prpl" to 1)))
    }

    @Test
    fun librariesThatAreNotOpenNeverAppear() {
        // Ang Mo Kio (upcoming) and Queenstown (closed) are in the seed list but not in the directory
        assertEquals(listOf("PRPL"), codes(mapOf("amkpl" to 9, "qupl" to 9, "prpl" to 1)))
    }

    @Test
    fun nothingOnTheShelfElsewhereGivesAnEmptyList() {
        assertEquals(emptyList(), codes(emptyMap()))
        assertEquals(emptyList(), codes(mapOf("trl" to 3)))
    }

    @Test
    fun equalCountsAndDistancesAreOrderedByNameSoTheListNeverShuffles() {
        val here = Library("AAA", "Here", LatLon(1.30, 103.80))
        val twin = LatLon(1.31, 103.80)
        val crowd =
            LibraryDirectory(
                listOf(here, Library("ZED", "Zed Library", twin), Library("ABE", "Abe Library", twin), Library("MID", "Mid Library", twin)),
            )
        val order = ResultGrouper.otherLibraries(mapOf("zed" to 4, "abe" to 4, "mid" to 4), here, crowd).map { it.library.name }
        assertEquals(listOf("Abe Library", "Mid Library", "Zed Library"), order)
    }
}
