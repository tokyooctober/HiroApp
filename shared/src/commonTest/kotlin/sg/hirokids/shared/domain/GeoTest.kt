package sg.hirokids.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Spec section 5: haversine distance with Earth radius R = 6371 km. */
class GeoTest {
    private val origin = LatLon(1.3, 103.8)

    @Test
    fun theSamePointIsZeroMetresAway() {
        assertEquals(0.0, distanceMeters(origin, origin))
    }

    @Test
    fun distanceIsTheSameBothWays() {
        val a = LatLon(1.352391, 103.940821)
        val b = LatLon(1.4054, 103.9021)
        assertEquals(distanceMeters(a, b), distanceMeters(b, a))
    }

    @Test
    fun oneDegreeOfLatitudeIsAbout111Kilometres() {
        val d = distanceMeters(LatLon(1.0, 103.0), LatLon(2.0, 103.0))
        assertTrue(d in 111_190.0..111_200.0, "got $d")
    }

    @Test
    fun theThreeHundredMetreEdgeIsTold299From301() {
        assertTrue(distanceMeters(origin, origin.northBy(299.0)) < DETECTION_RADIUS_METERS)
        assertTrue(distanceMeters(origin, origin.northBy(301.0)) > DETECTION_RADIUS_METERS)
    }
}

/** A point exactly [meters] due north (one degree of latitude is R * pi / 180 metres). */
fun LatLon.northBy(meters: Double): LatLon = LatLon(lat + meters / METERS_PER_DEGREE_LAT, lon)

private const val METERS_PER_DEGREE_LAT = 6_371_000.0 * 3.141592653589793 / 180.0
