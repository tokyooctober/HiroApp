package sg.hirokids.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import sg.hirokids.shared.domain.LocationProvider
import sg.hirokids.shared.domain.MapsLauncher
import sg.hirokids.shared.domain.NoMapsLauncher
import sg.hirokids.shared.ui.LocalMapsLauncher
import sg.hirokids.shared.ui.library.LibraryFlow
import sg.hirokids.shared.ui.library.LibraryViewModel
import sg.hirokids.shared.ui.search.SearchViewModel
import sg.hirokids.shared.ui.theme.HiroTheme

/**
 * [locationProvider] is the platform's way to read the phone's location (FusedLocationProviderClient, CLLocationManager);
 * [maps] opens the phone's maps app for directions.
 */
@Composable
fun App(
    locationProvider: LocationProvider,
    maps: MapsLauncher = NoMapsLauncher,
) {
    HiroTheme {
        CompositionLocalProvider(LocalMapsLauncher provides maps) {
            LibraryFlow(
                viewModel = koinViewModel<LibraryViewModel>(parameters = { parametersOf(locationProvider) }),
                search = koinViewModel<SearchViewModel>(),
            )
        }
    }
}
