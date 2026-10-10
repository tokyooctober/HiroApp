package sg.hirokids.shared.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import sg.hirokids.shared.domain.Library
import sg.hirokids.shared.domain.ResultGroup
import sg.hirokids.shared.resources.Res
import sg.hirokids.shared.resources.mode_children
import sg.hirokids.shared.resources.results_back
import sg.hirokids.shared.resources.results_empty
import sg.hirokids.shared.resources.results_retry
import sg.hirokids.shared.resources.results_searching
import sg.hirokids.shared.resources.search_button
import sg.hirokids.shared.resources.search_heading
import sg.hirokids.shared.resources.search_hint
import sg.hirokids.shared.resources.search_label
import sg.hirokids.shared.resources.search_too_short
import sg.hirokids.shared.ui.library.YoureAtCard
import sg.hirokids.shared.ui.theme.HiroColors
import sg.hirokids.shared.ui.theme.HiroShapes

internal val MinTouchTarget = 48.dp // NFR-8: 48 dp (Android) / 44 pt (iOS)
private val SearchBoxHeight = 56.dp

/** What the results screen can ask for; one object so the screen's parameter list stays short. */
class ResultsActions(
    val onQueryChange: (String) -> Unit,
    val onSearch: () -> Unit,
    val onRetry: () -> Unit,
    val onBack: () -> Unit,
    val onLoadMore: (ResultGroup) -> Unit,
    val onRetryGroup: (ResultGroup) -> Unit,
)

/** Search home and results for the current library (design: `Main.dc.html`, `Results.dc.html`). */
@Composable
fun SearchFlow(
    library: Library,
    viewModel: SearchViewModel,
    onChangeLibrary: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    when (state.screen) {
        SearchScreen.HOME ->
            SearchHomeScreen(
                library = library,
                state = state,
                onQueryChange = viewModel::onQueryChange,
                onSearch = { viewModel.submit(library) },
                onChangeLibrary = onChangeLibrary,
            )
        SearchScreen.RESULTS ->
            ResultsScreen(
                libraryName = library.name,
                state = state,
                actions =
                    ResultsActions(
                        onQueryChange = viewModel::onQueryChange,
                        onSearch = { viewModel.submit(library) },
                        onRetry = { viewModel.retry(library) },
                        onBack = viewModel::back,
                        onLoadMore = { viewModel.loadMore(library, it) },
                        onRetryGroup = { viewModel.retryGroup(library, it) },
                    ),
            )
    }
}

@Composable
fun SearchHomeScreen(
    library: Library,
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onChangeLibrary: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(HiroColors.Page)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        YoureAtCard(library.name, onChangeLibrary)
        Text(
            stringResource(Res.string.search_heading),
            style = MaterialTheme.typography.headlineMedium,
            color = HiroColors.Ink,
            fontWeight = FontWeight.SemiBold,
        )
        SearchBox(state.query, onQueryChange, onSearch)
        if (state.tooShort) {
            Text(stringResource(Res.string.search_too_short), color = HiroColors.OnLoanText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ResultsScreen(
    libraryName: String,
    state: SearchUiState,
    actions: ResultsActions,
) {
    Column(Modifier.fillMaxSize().background(HiroColors.Page).safeDrawingPadding()) {
        ResultsHeader(libraryName, state, actions)
        when (val results = state.results) {
            ResultsState.Idle -> Unit
            ResultsState.Loading -> CenteredMessage { Loading() }
            ResultsState.Empty -> CenteredMessage { Text(stringResource(Res.string.results_empty), color = HiroColors.TextSecondary) }
            is ResultsState.Failed -> FailedList(results, actions.onRetry)
            is ResultsState.Loaded -> ResultList(libraryName, results, actions)
        }
    }
}

@Composable
private fun ResultsHeader(
    libraryName: String,
    state: SearchUiState,
    actions: ResultsActions,
) {
    Column(
        Modifier.fillMaxWidth().background(HiroColors.Card).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val back = stringResource(Res.string.results_back)
            TextButton(
                onClick = actions.onBack,
                modifier = Modifier.heightIn(min = MinTouchTarget).semantics { contentDescription = back },
            ) {
                Text("←", color = HiroColors.Ink, fontWeight = FontWeight.ExtraBold)
            }
            Box(Modifier.weight(1f)) { SearchBox(state.query, actions.onQueryChange, actions.onSearch) }
        }
        if (state.tooShort) {
            Text(
                stringResource(Res.string.search_too_short),
                color = HiroColors.OnLoanText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 8.dp)) {
            Chip(stringResource(Res.string.mode_children), HiroColors.Ink, Color.White)
            Chip(libraryName, HiroColors.PrimaryTint, HiroColors.PrimaryPressed)
        }
    }
}

@Composable
private fun SearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val label = stringResource(Res.string.search_label)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text(stringResource(Res.string.search_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.weight(1f).heightIn(min = SearchBoxHeight).semantics { contentDescription = label },
            shape = RoundedCornerShape(16.dp),
        )
        Button(
            onClick = onSearch,
            modifier = Modifier.heightIn(min = SearchBoxHeight),
            colors = ButtonDefaults.buttonColors(containerColor = HiroColors.Children),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(stringResource(Res.string.search_button), fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun Chip(
    text: String,
    background: Color,
    foreground: Color,
) {
    Text(
        text,
        modifier = Modifier.background(background, HiroShapes.Chip).padding(horizontal = 12.dp, vertical = 8.dp),
        color = foreground,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun CenteredMessage(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}

@Composable
internal fun Loading() {
    CircularProgressIndicator()
    Text(stringResource(Res.string.results_searching), Modifier.padding(top = 12.dp), color = HiroColors.TextSecondary)
}

@Composable
internal fun RetryButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.heightIn(min = MinTouchTarget),
        colors = ButtonDefaults.buttonColors(containerColor = HiroColors.Primary),
    ) {
        Text(stringResource(Res.string.results_retry), fontWeight = FontWeight.ExtraBold)
    }
}
