package sg.hirokids.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookAvailabilityTest {
    private val tampines = LatLon(1.352391, 103.940821)
    private val here = Library("TRL", "Tampines Regional Library", tampines)
    private val pasirRis = Library("PRPL", "Pasir Ris Public Library", tampines.northBy(3_100.0))
    private val punggol = Library("PRL", "Punggol Regional Library", tampines.northBy(9_000.0))
    private val bedok = Library("BEPL", "Bedok Public Library", tampines.northBy(-5_600.0))
    private val directory = LibraryDirectory(listOf(here, pasirRis, punggol, bedok))

    private fun copy(
        branch: String,
        status: CopyStatus = CopyStatus.AVAILABLE,
        call: String? = "English JP TAN",
        shelf: String? = "Junior Picture Book",
    ) = Copy(
        brn = 1,
        branchCode = branch,
        usageLevel = "JUNIOR PB",
        status = status,
        callNumber = "JP TAN",
        minAgeLimit = 0,
        media = "BOOK",
        usageLevelName = shelf,
        formattedCallNumber = call,
    )

    private fun build(vararg copies: Copy) = BookAvailability.from(copies.toList(), here, directory)

    @Test
    fun hereCountsAvailableAndAllCopiesAndTellsWhereToLook() {
        val result = build(copy("TRL"), copy("TRL"), copy("TRL", CopyStatus.ON_LOAN))
        val panel = result.here!!
        assertEquals(2, panel.available)
        assertEquals(3, panel.total)
        assertEquals("English JP TAN", panel.callNumber)
        assertEquals("Junior Picture Book", panel.shelfType)
        assertTrue(panel.onShelf)
    }

    @Test
    fun theCallNumberAndShelfComeFromACopyOnTheShelfWhenThereIsOne() {
        val out = copy("TRL", CopyStatus.ON_LOAN, call = "English OUT", shelf = "Out shelf")
        val onShelf = copy("TRL", call = "English ON", shelf = "On shelf")
        val panel = build(out, onShelf).here!!
        assertEquals("English ON", panel.callNumber)
        assertEquals("On shelf", panel.shelfType)
    }

    @Test
    fun withNoFormattedNumberThePlainCallNumberIsUsedAndWithNoShelfNameTheUsageLevel() {
        val panel = build(copy("TRL", call = null, shelf = null)).here!!
        assertEquals("JP TAN", panel.callNumber)
        assertEquals("JUNIOR PB", panel.shelfType)
    }

    @Test
    fun allCopiesOutStillGivesTheCountsAndNoShelf() {
        val panel = build(copy("TRL", CopyStatus.ON_LOAN), copy("TRL", CopyStatus.IN_PROCESS)).here!!
        assertEquals(0, panel.available)
        assertEquals(2, panel.total)
        assertEquals(false, panel.onShelf)
    }

    @Test
    fun aLibraryWithNoCopyIsNotHeld() {
        assertNull(build(copy("PRPL")).here)
    }

    @Test
    fun otherLibrariesWithCopiesOnTheShelfComeFirstThenTheNearest() {
        val result =
            build(
                copy("BEPL", CopyStatus.ON_LOAN), // nearest after Pasir Ris but nothing on the shelf
                copy("PRL"),
                copy("PRPL", CopyStatus.ON_LOAN),
                copy("PRPL"),
            )
        assertEquals(listOf("PRPL", "PRL", "BEPL"), result.others.map { it.copies.library.code })
        val pasir = result.others.first().copies
        assertEquals(1, pasir.available)
        assertEquals(2, pasir.total)
    }

    @Test
    fun anOtherLibraryWithNoCopiesOnTheShelfSortsByDistanceAmongTheEmpty() {
        val result = build(copy("PRL", CopyStatus.ON_LOAN), copy("BEPL", CopyStatus.ON_LOAN))
        assertEquals(listOf("BEPL", "PRL"), result.others.map { it.copies.library.code })
    }

    @Test
    fun librariesTheAppDoesNotShowAreLeftOut() {
        val result = build(copy("TRL"), copy("LD"), copy("QUPL")) // not in the directory
        assertTrue(result.others.isEmpty())
    }

    @Test
    fun theCurrentLibraryIsNeverInTheOthers() {
        assertTrue(build(copy("TRL")).others.isEmpty())
    }

    @Test
    fun branchCodesMatchWhateverTheLetterCase() {
        assertEquals(1, build(copy("trl")).here!!.total)
    }
}
