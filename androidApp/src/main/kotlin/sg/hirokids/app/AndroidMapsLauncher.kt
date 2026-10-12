package sg.hirokids.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import sg.hirokids.shared.domain.DirectionsLinks
import sg.hirokids.shared.domain.Library
import sg.hirokids.shared.domain.LinkAllowList
import sg.hirokids.shared.domain.MapsLauncher

/** Directions open in Google Maps if it is installed, otherwise in the browser. Only the destination leaves the app (NFR-5). */
class AndroidMapsLauncher(
    private val context: Context,
) : MapsLauncher {
    override fun openDirections(destination: Library) {
        val link = DirectionsLinks.google(destination)
        if (!LinkAllowList.isAllowed(link)) return
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // no maps app and no browser: nothing to open
        }
    }
}
