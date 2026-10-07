package sg.hirokids.shared

import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel
import sg.hirokids.shared.ui.library.LibraryCountScreen
import sg.hirokids.shared.ui.theme.HiroTheme

@Composable
fun App() {
    HiroTheme {
        LibraryCountScreen(koinViewModel())
    }
}
