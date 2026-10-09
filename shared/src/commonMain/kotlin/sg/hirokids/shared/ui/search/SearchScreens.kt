package sg.hirokids.shared.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import sg.hirokids.shared.domain.Library
import sg.hirokids.shared.domain.SearchHit
import sg.hirokids.shared.resources.Res
import sg.hirokids.shared.resources.card_by
import sg.hirokids.shared.resources.card_on_shelf
import sg.hirokids.shared.resources.mode_children
import sg.hirokids.shared.resources.results_at_library
import sg.hirokids.shared.resources.results_back
import sg.hirokids.shared.resources.results_empty
import sg.hirokids.shared.resources.results_error
import sg.hirokids.shared.resources.results_found_title
import sg.hirokids.shared.resources.results_on_shelf_title
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

private val MinTouchTarget = 48.dp // NFR-8: 48 dp (Android) / 44 pt (iOS)
private val SearchBoxHeight = 56.dp
private val CoverWidth = 64.dp
private val CoverHeight = 88.dp

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
                library = library,
                state = state,
                onQueryChange = viewModel::onQueryChange,
                onSearch = { viewModel.submit(library) },
                onRetry = { viewModel.retry(library) },
                onBack = viewModel::back,
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
    library: Library,
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(HiroColors.Page).safeDrawingPadding()) {
        Column(
            Modifier.fillMaxWidth().background(HiroColors.Card).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val back = stringResource(Res.string.results_back)
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.heightIn(min = MinTouchTarget).semantics { contentDescription = back },
                ) {
                    Text("←", color = HiroColors.Ink, fontWeight = FontWeight.ExtraBold)
                }
                Box(Modifier.weight(1f)) { SearchBox(state.query, onQueryChange, onSearch) }
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
                Chip(library.name, HiroColors.PrimaryTint, HiroColors.PrimaryPressed)
            }
        }
        when (val results = state.results) {
            ResultsState.Idle -> Unit
            ResultsState.Loading -> CenteredMessage { Loading() }
            ResultsState.Empty -> CenteredMessage { Text(stringResource(Res.string.results_empty), color = HiroColors.TextSecondary) }
            ResultsState.Failed ->
                CenteredMessage {
                    Text(stringResource(Res.string.results_error), color = HiroColors.TextSecondary)
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.padding(top = 12.dp).heightIn(min = MinTouchTarget),
                        colors = ButtonDefaults.buttonColors(containerColor = HiroColors.Primary),
                    ) {
                        Text(stringResource(Res.string.results_retry), fontWeight = FontWeight.ExtraBold)
                    }
                }
            is ResultsState.Loaded -> ResultList(library, results.hits)
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
private fun CenteredMessage(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}

@Composable
private fun Loading() {
    CircularProgressIndicator()
    Text(stringResource(Res.string.results_searching), Modifier.padding(top = 12.dp), color = HiroColors.TextSecondary)
}

@Composable
private fun ResultList(
    library: Library,
    hits: List<SearchHit>,
) {
    // an ISBN lookup says nothing about the shelf, so it gets a plain heading and its cards carry no shelf claim
    val onShelf = hits.any { it.reportedOnShelfHere }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(if (onShelf) Res.string.results_on_shelf_title else Res.string.results_found_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = HiroColors.Ink,
                    fontWeight = FontWeight.SemiBold,
                )
                if (onShelf) {
                    Text(
                        stringResource(Res.string.results_at_library, library.name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = HiroColors.TextSecondary,
                    )
                }
            }
        }
        items(hits, key = { it.title.brn }) { hit -> ResultCard(hit) }
    }
}

@Composable
private fun ResultCard(hit: SearchHit) {
    val title = hit.title
    val onShelf = stringResource(Res.string.card_on_shelf)
    val by = title.author?.let { stringResource(Res.string.card_by, title.title, it) } ?: title.title
    val description = listOfNotNull(by, title.format, onShelf.takeIf { hit.reportedOnShelfHere }).joinToString(". ")
    Row(
        Modifier
            .fillMaxWidth()
            .background(HiroColors.Card, HiroShapes.Card)
            .padding(12.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Cover(title.title, title.coverUrl)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title.title, style = MaterialTheme.typography.titleMedium, color = HiroColors.Ink, fontWeight = FontWeight.SemiBold)
            val byline = listOfNotNull(title.author, title.format).joinToString(" · ")
            if (byline.isNotEmpty()) {
                Text(byline, style = MaterialTheme.typography.bodyMedium, color = HiroColors.TextTertiary)
            }
            if (hit.reportedOnShelfHere) {
                // status is never colour-only: a tick and words
                Text(
                    "✓ $onShelf",
                    modifier =
                        Modifier
                            .background(
                                HiroColors.OnShelfBackground,
                                HiroShapes.Chip,
                            ).padding(horizontal = 10.dp, vertical = 4.dp),
                    color = HiroColors.OnShelfText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

/** The cover from NLB; while it loads, or if there is none, the title on a plain cover (never a blank space). */
@Composable
private fun Cover(
    title: String,
    url: String?,
) {
    Box(
        Modifier.size(CoverWidth, CoverHeight).clip(RoundedCornerShape(10.dp)).background(HiroColors.BookHeader),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            title,
            modifier = Modifier.padding(6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = HiroColors.Ink,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.size(CoverWidth, CoverHeight),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
