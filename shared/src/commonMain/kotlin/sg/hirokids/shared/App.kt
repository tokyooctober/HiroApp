package sg.hirokids.shared

import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import sg.hirokids.shared.domain.LocationProvider
import sg.hirokids.shared.ui.library.LibraryFlow
import sg.hirokids.shared.ui.library.LibraryViewModel
import sg.hirokids.shared.ui.theme.HiroTheme

/** [locationProvider] is the platform's way to read the phone's location (FusedLocationProviderClient, CLLocationManager). */
@Composable
fun App(locationProvider: LocationProvider) {
    HiroTheme {
        LibraryFlow(koinViewModel<LibraryViewModel>(parameters = { parametersOf(locationProvider) }))
    }
}
