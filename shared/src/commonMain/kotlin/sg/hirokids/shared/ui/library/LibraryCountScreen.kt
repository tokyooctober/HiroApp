package sg.hirokids.shared.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import sg.hirokids.shared.resources.Res
import sg.hirokids.shared.resources.libraries_count
import sg.hirokids.shared.resources.libraries_error
import sg.hirokids.shared.resources.libraries_loading
import sg.hirokids.shared.resources.retry

@Composable
fun LibraryCountScreen(viewModel: LibraryCountViewModel) {
    val state by viewModel.state.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val s = state) {
            LibraryCountState.Loading -> {
                CircularProgressIndicator()
                Text(stringResource(Res.string.libraries_loading))
            }
            is LibraryCountState.Loaded ->
                Text(
                    pluralStringResource(Res.plurals.libraries_count, s.count, s.count),
                    style = MaterialTheme.typography.headlineMedium,
                )
            LibraryCountState.Error -> {
                Text(stringResource(Res.string.libraries_error))
                Button(onClick = viewModel::load) { Text(stringResource(Res.string.retry)) }
            }
        }
    }
}
