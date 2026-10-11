package sg.hirokids.shared.data

import kotlinx.serialization.json.Json
import sg.hirokids.shared.domain.AudiencePolicy
import sg.hirokids.shared.domain.BookAvailability
import sg.hirokids.shared.domain.CopyStatus
import sg.hirokids.shared.domain.LatLon
import sg.hirokids.shared.domain.Library
import sg.hirokids.shared.domain.LibraryDirectory
import sg.hirokids.shared.domain.Mode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** The recorded `GetAvailabilityInfo` and `GetTitleDetails` responses, read with the app's own types. */
class NlbFixtureDetailTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val copies =
        json
            .decodeFromString<AvailabilityResponse>(File("../fixtures/get-availability-info.json").readText())
            .items
            .mapNotNull { it.toCopy() }

    @Test
    fun everyRecordedCopyIsReadWithItsBranchCallNumberAndShelfName() {
        assertEquals(9, copies.size)
        val first = copies.first()
        assertEquals("BBPL", first.branchCode)
        assertEquals("English 567.912 HEP", first.formattedCallNumber)
        assertEquals("567.912 HEP", first.callNumber)
        assertEquals("Early Literacy 0-3", first.usageLevelName)
        assertEquals("ELL 0-3", first.usageLevel)
    }

    @Test
    fun onlyCopiesThatAreInAndAvailableCountAsOnTheShelf() {
        val byBranch = copies.groupBy { it.branchCode }
        // BBPL: one in, three on loan; SKPL: available but still being shelved
        assertEquals(1, byBranch.getValue("BBPL").count { it.status == CopyStatus.AVAILABLE })
        assertEquals(3, byBranch.getValue("BBPL").count { it.status == CopyStatus.ON_LOAN })
        assertEquals(0, byBranch.getValue("SKPL").count { it.status == CopyStatus.AVAILABLE })
    }

    @Test
    fun theRecordedCopiesBuildTheDetailPanel() {
        val bukitBatok = Library("BBPL", "Bukit Batok Public Library", LatLon(1.3490, 103.7495))
        val all = copies.map { it.branchCode }.toSet().map { Library(it, "$it Library", bukitBatok.position) } + bukitBatok
        val directory = LibraryDirectory(all.distinctBy { it.code })
        val result = BookAvailability.from(AudiencePolicy(Mode.CHILDREN).countableCopies(copies), bukitBatok, directory)
        val here = assertNotNull(result.here)
        assertEquals(1, here.available)
        assertEquals(4, here.total)
        assertEquals("English 567.912 HEP", here.callNumber)
        assertEquals("Early Literacy 0-3", here.shelfType)
    }

    @Test
    fun theRecordedTitleDetailsCarryTheSummary() {
        val record = json.decodeFromString<RecordDto>(File("../fixtures/get-title-details.json").readText())
        assertEquals(
            "Dinosaurs were incredible creatures! Now even the smallest of readers can look inside the mighty T. rex, " +
                "with peep-through pages and fun facts.",
            record.summary.single(),
        )
    }
}
