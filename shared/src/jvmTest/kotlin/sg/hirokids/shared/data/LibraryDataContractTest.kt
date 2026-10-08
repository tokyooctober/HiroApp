package sg.hirokids.shared.data

import kotlinx.serialization.json.Json
import sg.hirokids.shared.domain.LibraryDirectory
import sg.hirokids.shared.domain.LibraryStatus
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contract between the real data files and the code that reads them:
 * `data/nlb_libraries.json` (the seed) and `fixtures/library-get-branches.json` (a recorded NLB response).
 * JVM only: it reads the files from the repository, which Gradle runs from the `shared` folder.
 */
class LibraryDataContractTest {
    private val seedText = File("../data/nlb_libraries.json").readText()
    private val liveText = File("../fixtures/library-get-branches.json").readText()

    private val seed = parseSeed(seedText)
    private val live =
        Json { ignoreUnknownKeys = true }
            .decodeFromString<BranchesResponse>(liveText)
            .branches
            .filter { it.branchType?.code in setOf("PL", "RL") }
            .mapNotNull { it.toLiveBranch() }

    @Test
    fun everyOpenLibraryHasAUniqueCodeThatNlbLists() {
        val open = seed.filter { it.status == LibraryStatus.OPEN }
        assertTrue(open.all { it.code != null }, "an open library has no branch code")
        val codes = open.map { it.code!!.uppercase() }
        assertEquals(codes.size, codes.toSet().size, "duplicate branch codes")
        val liveCodes = live.map { it.code.uppercase() }.toSet()
        assertEquals(emptyList(), codes.filter { it !in liveCodes }, "open libraries NLB does not list")
    }

    @Test
    fun twentyTwoLibrariesAreSelectableAndTheHiddenOnesStayHidden() {
        // Owner decision, 7 Oct 2026: 22 open; Ang Mo Kio (upcoming), Queenstown, Orchard and Marine Parade are not shown.
        val directory = LibraryDirectory.from(seed, live)
        assertEquals(22, directory.libraries.size)
        val hidden = listOf("Ang Mo Kio", "Queenstown", "Orchard", "Marine Parade")
        for (word in hidden) {
            assertTrue(directory.libraries.none { it.name.contains(word) }, "$word should be hidden")
        }
    }

    @Test
    fun theGoldenScenarioOrderHoldsWithTheRealDistances() {
        // Spec section 8 (golden scenario): from Tampines, Pasir Ris comes before Punggol. The spec's 3.1 km and 9.0 km are
        // illustrative; the real coordinates give about 2.4 km and 7.3 km.
        val directory = LibraryDirectory.from(seed, live)
        val tampines = directory.byCode("TRL")!!
        val distances = directory.closestFirst(tampines.position).associate { it.library.code to it.meters / 1000.0 }
        assertTrue(distances.getValue("PRPL") in 2.0..3.0, "Pasir Ris ${distances["PRPL"]} km")
        assertTrue(distances.getValue("PRL") in 7.0..8.0, "Punggol ${distances["PRL"]} km")
        assertTrue(distances.getValue("PRPL") < distances.getValue("PRL"))
    }

    @Test
    fun theRecordedNlbResponseParsesWithTheAppsOwnTypes() {
        assertTrue(live.size >= 26, "only ${live.size} libraries parsed from the recorded response")
        // the entries without coordinates are not libraries in our list: a kiosk, the Mobile Library (0,0) and "Others"
        val shown = seed.filter { it.status == LibraryStatus.OPEN }.mapNotNull { it.code?.uppercase() }.toSet()
        val withoutPosition = live.filter { it.code.uppercase() in shown && it.position == null }
        assertEquals(emptyList(), withoutPosition.map { it.code }, "a library we show has no usable coordinates")
    }
}
