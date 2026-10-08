package sg.hirokids.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryDirectoryTest {
    private val tampines = LatLon(1.352391, 103.940821)

    private fun seed(
        code: String?,
        name: String,
        position: LatLon = tampines,
        status: LibraryStatus = LibraryStatus.OPEN,
    ) = SeedLibrary(code, name, position, status)

    private fun live(
        code: String,
        position: LatLon? = null,
    ) = LiveBranch(code, position)

    // ---- which libraries can be chosen (spec FR-3, owner decision 7 Oct 2026)

    @Test
    fun onlyOpenLibrariesWithACodeAreSelectable() {
        val directory =
            LibraryDirectory.from(
                seed =
                    listOf(
                        seed("AAA", "Open Library"),
                        seed("BBB", "Upcoming Library", status = LibraryStatus.UPCOMING),
                        seed("CCC", "Closed Library", status = LibraryStatus.CLOSED),
                        seed("DDD", "Unknown Status Library", status = LibraryStatus.UNKNOWN),
                        seed(null, "No Code Library"),
                    ),
                live = null,
            )
        assertEquals(listOf("Open Library"), directory.libraries.map { it.name })
    }

    @Test
    fun anOpenLibraryMissingFromTheLiveListIsHidden() {
        val directory =
            LibraryDirectory.from(
                seed = listOf(seed("AAA", "Alpha Library"), seed("BBB", "Beta Library")),
                live = listOf(live("AAA")),
            )
        assertEquals(listOf("Alpha Library"), directory.libraries.map { it.name })
    }

    @Test
    fun theLiveCoordinatesWinAndCodesMatchIgnoringCase() {
        val moved = LatLon(1.5, 103.9)
        val directory =
            LibraryDirectory.from(
                seed = listOf(seed("trl", "Tampines Library", position = tampines)),
                live = listOf(live("TRL", position = moved)),
            )
        assertEquals(moved, directory.libraries.single().position)
    }

    @Test
    fun aLiveBranchWithoutCoordinatesKeepsTheSeedPosition() {
        val directory = LibraryDirectory.from(listOf(seed("TRL", "Tampines Library")), listOf(live("TRL", position = null)))
        assertEquals(tampines, directory.libraries.single().position)
    }

    @Test
    fun whenTheLiveListIsUnavailableOrEmptyTheOpenSeedLibrariesStayUsable() {
        val seeds = listOf(seed("AAA", "Alpha Library"), seed("BBB", "Closed", status = LibraryStatus.CLOSED))
        assertEquals(listOf("Alpha Library"), LibraryDirectory.from(seeds, live = null).libraries.map { it.name })
        assertEquals(listOf("Alpha Library"), LibraryDirectory.from(seeds, live = emptyList()).libraries.map { it.name })
    }

    @Test
    fun librariesAreListedByNameAndFoundByCodeIgnoringCase() {
        val directory = LibraryDirectory.from(listOf(seed("BBB", "Beta Library"), seed("AAA", "Alpha Library")), null)
        assertEquals(listOf("Alpha Library", "Beta Library"), directory.libraries.map { it.name })
        assertEquals("Beta Library", directory.byCode("bbb")?.name)
        assertNull(directory.byCode("ZZZ"))
        assertNull(directory.byCode(null))
    }

    // ---- ordering and detection

    @Test
    fun closestFirstOrdersByDistanceAndReportsIt() {
        val here = LatLon(1.3, 103.8)
        val directory =
            LibraryDirectory.from(
                listOf(
                    seed("FAR", "Far Library", here.northBy(5_000.0)),
                    seed("NEAR", "Near Library", here.northBy(200.0)),
                    seed("MID", "Mid Library", here.northBy(1_500.0)),
                ),
                null,
            )
        val ordered = directory.closestFirst(here)
        assertEquals(listOf("Near Library", "Mid Library", "Far Library"), ordered.map { it.library.name })
        assertTrue(ordered.first().meters in 199.0..201.0)
    }

    @Test
    fun equalDistancesAreBrokenByNameThenCode() {
        val here = LatLon(1.3, 103.8)
        val spot = here.northBy(1_000.0)
        val directory =
            LibraryDirectory.from(
                listOf(seed("B2", "Beta Library", spot), seed("A1", "Alpha Library", spot), seed("A0", "Alpha Library", spot)),
                null,
            )
        assertEquals(listOf("A0", "A1", "B2"), directory.closestFirst(here).map { it.library.code })
    }

    @Test
    fun detectionAcceptsWithinThreeHundredMetresAndNotBeyond() {
        val here = LatLon(1.3, 103.8)

        fun detect(metres: Double) = LibraryDirectory.from(listOf(seed("AAA", "Alpha Library", here.northBy(metres))), null).detect(here)
        assertNotNull(detect(299.0))
        assertNull(detect(301.0))
    }

    @Test
    fun detectionPicksTheNearestWhenTwoAreInRange() {
        val here = LatLon(1.3, 103.8)
        val directory =
            LibraryDirectory.from(
                listOf(seed("FAR", "Far Library", here.northBy(250.0)), seed("NEAR", "Near Library", here.northBy(40.0))),
                null,
            )
        assertEquals("NEAR", directory.detect(here)?.code)
    }

    @Test
    fun anEmptyDirectoryDetectsNothing() {
        assertNull(LibraryDirectory.from(emptyList(), null).detect(tampines))
    }
}
