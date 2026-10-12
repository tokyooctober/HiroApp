package sg.hirokids.shared.domain

import kotlin.math.abs
import kotlin.math.roundToLong

/** Opens the phone's maps app with directions to a library. No routing happens in this app (Task 11). */
fun interface MapsLauncher {
    fun openDirections(destination: Library)
}

/** For previews and tests, and for a platform with no maps app: does nothing. */
object NoMapsLauncher : MapsLauncher {
    override fun openDirections(destination: Library) = Unit
}

/**
 * The only addresses the app ever asks the phone to open (NFR-7): `https` to Google Maps directions or Apple Maps, with no port,
 * no user name and no other host. A link that is not on the list is not opened, whatever built it.
 */
object LinkAllowList {
    private const val GOOGLE_HOST = "www.google.com"
    private const val GOOGLE_PATH = "/maps/dir/"
    private const val APPLE_HOST = "maps.apple.com"

    // scheme, host, optional path and query; "@" or ":" in the host part makes it not match, so user names and ports are refused
    private val shape = Regex("""^https://([a-z0-9.-]+)(/[^?#\s]*)?(\?[^#\s]*)?$""")

    fun isAllowed(url: String): Boolean {
        val match = shape.matchEntire(url) ?: return false
        val host = match.groupValues[1]
        val path = match.groupValues[2].ifEmpty { "/" }
        return when (host) {
            GOOGLE_HOST -> path == GOOGLE_PATH
            APPLE_HOST -> path == "/"
            else -> false
        }
    }
}

/** Directions links to a library. Only the destination is in them: nothing about the user (NFR-5). */
object DirectionsLinks {
    private const val MICRODEGREES = 1_000_000L
    private const val FRACTION_DIGITS = 6

    /** Google Maps' universal link: opens the Google Maps app if there is one, otherwise the browser. */
    fun google(destination: Library): String = "https://www.google.com/maps/dir/?api=1&destination=${coordinates(destination.position)}"

    /** Apple Maps. */
    fun apple(destination: Library): String = "https://maps.apple.com/?daddr=${coordinates(destination.position)}"

    private fun coordinates(position: LatLon) = "${decimal(position.lat)},${decimal(position.lon)}"

    /** Six decimals (about 10 cm) and never an exponent or a comma, whatever the phone's language. */
    private fun decimal(value: Double): String {
        val scaled = (abs(value) * MICRODEGREES).roundToLong()
        val whole = scaled / MICRODEGREES
        val fraction = (scaled % MICRODEGREES).toString().padStart(FRACTION_DIGITS, '0')
        return (if (value < 0 && scaled != 0L) "-" else "") + "$whole.$fraction"
    }
}
