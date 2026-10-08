package sg.hirokids.shared

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLAccuracyAuthorization
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSError
import platform.darwin.NSObject
import sg.hirokids.shared.domain.LatLon
import sg.hirokids.shared.domain.LocationProvider
import sg.hirokids.shared.domain.LocationResult

/**
 * Reads the phone's location once with CLLocationManager (when-in-use). The fix stays in the app (NFR-5).
 * Must be created and used on the main thread, which is where the view model's coroutines run.
 */
@OptIn(ExperimentalForeignApi::class)
class IosLocationProvider : LocationProvider {
    private var authorizationAnswer: CompletableDeferred<Unit>? = null
    private var fixAnswer: CompletableDeferred<LocationResult>? = null

    // CLLocationManager keeps only a weak reference to its delegate, so this property must hold it.
    private val delegate =
        object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
                if (manager.authorizationStatus != kCLAuthorizationStatusNotDetermined) authorizationAnswer?.complete(Unit)
            }

            override fun locationManager(
                manager: CLLocationManager,
                didUpdateLocations: List<*>,
            ) {
                val location = didUpdateLocations.lastOrNull() as? CLLocation ?: return
                val position = location.coordinate.useContents { LatLon(latitude, longitude) }
                val precise = manager.accuracyAuthorization != CLAccuracyAuthorization.CLAccuracyAuthorizationReducedAccuracy
                fixAnswer?.complete(if (precise) LocationResult.Precise(position) else LocationResult.Approximate(position))
            }

            override fun locationManager(
                manager: CLLocationManager,
                didFailWithError: NSError,
            ) {
                fixAnswer?.complete(LocationResult.Unavailable)
            }
        }

    private val manager =
        CLLocationManager().also {
            it.delegate = delegate
            it.desiredAccuracy = kCLLocationAccuracyBest
        }

    override suspend fun locate(allowPrompt: Boolean): LocationResult =
        when {
            !CLLocationManager.locationServicesEnabled() -> LocationResult.Unavailable
            !isAuthorized(allowPrompt) -> LocationResult.Denied
            else -> readFix()
        }

    /** True when location is allowed; asks the user first when [allowPrompt] and no answer has been given yet. */
    private suspend fun isAuthorized(allowPrompt: Boolean): Boolean {
        if (isAllowed(manager.authorizationStatus)) return true
        if (!allowPrompt || manager.authorizationStatus != kCLAuthorizationStatusNotDetermined) return false
        val answer = CompletableDeferred<Unit>().also { authorizationAnswer = it }
        manager.requestWhenInUseAuthorization()
        answer.await()
        return isAllowed(manager.authorizationStatus)
    }

    private fun isAllowed(status: CLAuthorizationStatus) =
        status == kCLAuthorizationStatusAuthorizedWhenInUse || status == kCLAuthorizationStatusAuthorizedAlways

    private suspend fun readFix(): LocationResult {
        val fix = CompletableDeferred<LocationResult>().also { fixAnswer = it }
        manager.requestLocation()
        return withTimeoutOrNull(FIX_TIMEOUT_MS) { fix.await() } ?: LocationResult.Unavailable
    }

    private companion object {
        const val FIX_TIMEOUT_MS = 15_000L
    }
}
