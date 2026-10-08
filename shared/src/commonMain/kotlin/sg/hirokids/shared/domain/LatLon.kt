package sg.hirokids.shared.domain

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(
    val lat: Double,
    val lon: Double,
)

private const val EARTH_RADIUS_METERS = 6_371_000.0 // R = 6371 km (spec section 5)

private const val HALF_TURN_DEGREES = 180.0

private fun radians(degrees: Double) = degrees * PI / HALF_TURN_DEGREES

/** Great-circle distance in metres (haversine, spec section 5). */
fun distanceMeters(
    a: LatLon,
    b: LatLon,
): Double {
    val dLat = radians(b.lat - a.lat)
    val dLon = radians(b.lon - a.lon)
    val h = sin(dLat / 2).pow(2) + cos(radians(a.lat)) * cos(radians(b.lat)) * sin(dLon / 2).pow(2)
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(h))
}
