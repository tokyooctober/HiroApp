package sg.hirokids.shared

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController
import sg.hirokids.shared.di.initKoin

/** Called from the SwiftUI host (iosApp). The proxy URL is passed in from the app's Info.plist. */
@Suppress("FunctionName") // Swift entry point; the name follows the Compose Multiplatform convention
fun MainViewController(proxyBaseUrl: String): UIViewController {
    initKoin(proxyBaseUrl)
    val location = IosLocationProvider()
    return ComposeUIViewController { App(location) }
}
