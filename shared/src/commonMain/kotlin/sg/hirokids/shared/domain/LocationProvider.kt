package sg.hirokids.shared.domain

sealed interface LocationResult {
    /** A precise fix: trusted for the 300 m "you are in this library" test. */
    data class Precise(
        val position: LatLon,
    ) : LocationResult

    /** The user allowed approximate location only: good for ordering the picker, never for auto-selecting. */
    data class Approximate(
        val position: LatLon,
    ) : LocationResult

    /** No permission (or the user said no). */
    data object Denied : LocationResult

    /** Permission is there but no fix could be had (location services off, timeout, error). */
    data object Unavailable : LocationResult
}

/**
 * The phone's location, taken once and kept on the phone (NFR-5). Implemented per platform:
 * FusedLocationProviderClient on Android, CLLocationManager on iOS.
 */
interface LocationProvider {
    /** [allowPrompt] false: never show a permission dialog, answer [LocationResult.Denied] if not already allowed. */
    suspend fun locate(allowPrompt: Boolean): LocationResult
}
