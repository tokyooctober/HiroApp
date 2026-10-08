package sg.hirokids.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import sg.hirokids.shared.domain.LatLon
import sg.hirokids.shared.domain.LocationProvider
import sg.hirokids.shared.domain.LocationResult
import kotlin.coroutines.resume

/**
 * Reads the phone's location once with FusedLocationProviderClient. The fix stays in the app: nothing is logged or sent.
 *
 * One instance lives as long as the app. The view model that uses it outlives any single Activity, so the permission dialog
 * is attached to whichever Activity is on screen ([attach], called from `onCreate`) and detached when that Activity is destroyed.
 */
class AndroidLocationProvider(
    private val context: Context,
) : LocationProvider {
    private var permissionDialog: ActivityResultLauncher<Array<String>>? = null
    private var permissionAnswer: CompletableDeferred<Unit>? = null

    /** Call from `onCreate`: the permission dialog must be registered before the Activity starts. */
    fun attach(activity: ComponentActivity) {
        val launcher =
            activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                permissionAnswer?.complete(Unit)
            }
        permissionDialog = launcher
        activity.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    if (permissionDialog === launcher) permissionDialog = null
                }
            },
        )
    }

    override suspend fun locate(allowPrompt: Boolean): LocationResult =
        if (permissionGranted(allowPrompt)) readFix(precise = hasFine()) else LocationResult.Denied

    /** True when the app may read the location; asks the user first when [allowPrompt] and nothing is allowed yet. */
    private suspend fun permissionGranted(allowPrompt: Boolean): Boolean {
        if (hasFine() || hasCoarse()) return true
        val dialog = permissionDialog
        if (allowPrompt && dialog != null) askUser(dialog) // no dialog when no screen is attached
        return hasFine() || hasCoarse()
    }

    /** Shows the system permission dialog and waits for the answer. A dialog that cannot be shown must never crash the app. */
    private suspend fun askUser(dialog: ActivityResultLauncher<Array<String>>) {
        val answer = CompletableDeferred<Unit>().also { permissionAnswer = it }
        val shown =
            runCatching { dialog.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
        if (shown.isSuccess) answer.await()
    }

    /** One fix. [precise] is false when the user allowed approximate location only. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun readFix(precise: Boolean): LocationResult {
        val priority = if (precise) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val cancel = CancellationTokenSource()
            val fix =
                try {
                    withTimeoutOrNull(FIX_TIMEOUT_MS) { client.getCurrentLocation(priority, cancel.token).await() }
                } finally {
                    cancel.cancel()
                }
            when {
                fix == null -> LocationResult.Unavailable
                precise -> LocationResult.Precise(LatLon(fix.latitude, fix.longitude))
                else -> LocationResult.Approximate(LatLon(fix.latitude, fix.longitude))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            LocationResult.Unavailable // no Google Play services, location switched off, or a security error
        }
    }

    private fun hasFine() = granted(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun hasCoarse() = granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val FIX_TIMEOUT_MS = 15_000L
    }
}

/** Waits for a Play services task without another library; a null result is treated as "no fix". */
private suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (result != null) continuation.resume(result) else continuation.cancel(IllegalStateException("no location"))
        }
        addOnFailureListener { continuation.cancel(it) }
    }
