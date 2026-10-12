package sg.hirokids.shared.ui

import androidx.compose.runtime.staticCompositionLocalOf
import sg.hirokids.shared.domain.MapsLauncher
import sg.hirokids.shared.domain.NoMapsLauncher

/** The platform's way to open directions, provided once by `App` so no screen has to pass it down. */
val LocalMapsLauncher = staticCompositionLocalOf<MapsLauncher> { NoMapsLauncher }
