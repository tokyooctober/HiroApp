package sg.hirokids.shared

import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import sg.hirokids.shared.domain.DirectionsLinks
import sg.hirokids.shared.domain.Library
import sg.hirokids.shared.domain.LinkAllowList
import sg.hirokids.shared.domain.MapsLauncher

/** Directions open in Apple Maps. Only the destination leaves the app (NFR-5). */
class IosMapsLauncher : MapsLauncher {
    override fun openDirections(destination: Library) {
        val link = DirectionsLinks.apple(destination)
        if (!LinkAllowList.isAllowed(link)) return
        val url = NSURL.URLWithString(link) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }
}
