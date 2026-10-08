package sg.hirokids.shared.data

import sg.hirokids.shared.domain.LatLon
import sg.hirokids.shared.domain.LibraryStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LibrarySeedTest {
    private val json =
        """
        {"version":1,"updatedAt":"2026-10-07","source":"x","libraries":[
          {"id":"tampines","branchCode":"TRL","name":"Tampines Regional Library","address":"1 Tampines","postal":"1",
           "lat":1.352391,"lng":103.940821,"status":"open","statusNote":"","geofenceRadiusM":300},
          {"id":"amk","branchCode":null,"name":"Ang Mo Kio Public Library","lat":1.369,"lng":103.848,"status":"upcoming"},
          {"id":"orchard","branchCode":"OCPL","name":"Orchard Library","lat":1.3,"lng":103.8,"status":"closed"},
          {"id":"odd","branchCode":"ODD","name":"Odd","lat":1.0,"lng":103.0,"status":"verify"}
        ]}
        """.trimIndent()

    @Test
    fun parsesNamesCodesPositionsAndIgnoresUnknownFields() {
        val tampines = parseSeed(json).first()
        assertEquals("TRL", tampines.code)
        assertEquals("Tampines Regional Library", tampines.name)
        assertEquals(LatLon(1.352391, 103.940821), tampines.position)
    }

    @Test
    fun aNullBranchCodeIsKeptAsNull() {
        assertNull(parseSeed(json).first { it.name.startsWith("Ang Mo Kio") }.code)
    }

    @Test
    fun statusesAreMappedAndAnythingUnrecognisedIsUnknown() {
        val byName = parseSeed(json).associate { it.name to it.status }
        assertEquals(LibraryStatus.OPEN, byName["Tampines Regional Library"])
        assertEquals(LibraryStatus.UPCOMING, byName["Ang Mo Kio Public Library"])
        assertEquals(LibraryStatus.CLOSED, byName["Orchard Library"])
        assertEquals(LibraryStatus.UNKNOWN, byName["Odd"]) // "verify" is not open: hide when ambiguous
    }
}
