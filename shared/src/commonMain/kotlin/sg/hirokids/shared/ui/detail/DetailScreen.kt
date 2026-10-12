package sg.hirokids.shared.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import sg.hirokids.shared.domain.BookAvailability
import sg.hirokids.shared.domain.BranchCopies
import sg.hirokids.shared.domain.Library
import sg.hirokids.shared.domain.OtherBranchCopies
import sg.hirokids.shared.domain.SearchHit
import sg.hirokids.shared.domain.Title
import sg.hirokids.shared.resources.Res
import sg.hirokids.shared.resources.detail_about
import sg.hirokids.shared.resources.detail_all_out
import sg.hirokids.shared.resources.detail_all_out_title
import sg.hirokids.shared.resources.detail_back
import sg.hirokids.shared.resources.detail_checking
import sg.hirokids.shared.resources.detail_copies
import sg.hirokids.shared.resources.detail_directions
import sg.hirokids.shared.resources.detail_error
import sg.hirokids.shared.resources.detail_here_title
import sg.hirokids.shared.resources.detail_look_for
import sg.hirokids.shared.resources.detail_not_held
import sg.hirokids.shared.resources.detail_other_all_out
import sg.hirokids.shared.resources.detail_other_on_shelf
import sg.hirokids.shared.resources.detail_other_row
import sg.hirokids.shared.resources.detail_others_none
import sg.hirokids.shared.resources.detail_others_note
import sg.hirokids.shared.resources.detail_others_title
import sg.hirokids.shared.resources.detail_shelf_at
import sg.hirokids.shared.resources.detail_subjects
import sg.hirokids.shared.resources.others_view
import sg.hirokids.shared.resources.others_view_description
import sg.hirokids.shared.ui.LocalMapsLauncher
import sg.hirokids.shared.ui.search.Cover
import sg.hirokids.shared.ui.search.MinTouchTarget
import sg.hirokids.shared.ui.search.RetryButton
import sg.hirokids.shared.ui.search.SectionTitle
import sg.hirokids.shared.ui.search.distanceText
import sg.hirokids.shared.ui.theme.HiroColors
import sg.hirokids.shared.ui.theme.HiroShapes

private val DetailCoverWidth = 130.dp
private val DetailCoverHeight = 178.dp
private const val MAX_SUBJECTS = 5
private const val CALL_NUMBER_SIZE = 34
private const val PANEL_PADDING = 18

/** Book detail (design: `Detail.dc.html`). [library] is the one on screen; View on another library goes back to the results there. */
@Composable
fun DetailScreen(
    hit: SearchHit,
    library: Library,
    viewModel: DetailViewModel,
    onBack: () -> Unit,
    onView: (Library) -> Unit,
) {
    val maps = LocalMapsLauncher.current
    val onDirections: (Library) -> Unit = maps::openDirections
    val state by viewModel.state.collectAsState()
    LaunchedEffect(hit.title.brn, library.code) { viewModel.open(hit, library) }
    val title = state.title ?: hit.title
    Column(Modifier.fillMaxSize().background(HiroColors.Page).verticalScroll(rememberScrollState())) {
        Header(title, onBack)
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
            when (val copies = state.copies) {
                CopiesState.Loading -> CheckingPanel()
                CopiesState.Failed -> ErrorPanel(viewModel::retry)
                is CopiesState.Loaded -> {
                    HerePanel(copies.availability.here, library)
                    OthersSection(copies.availability, onView, onDirections)
                }
            }
            About(title)
        }
    }
}

@Composable
private fun Header(
    title: Title,
    onBack: () -> Unit,
) {
    val back = stringResource(Res.string.detail_back)
    Column(
        Modifier.fillMaxWidth().background(HiroColors.BookHeader).padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = MinTouchTarget).semantics { contentDescription = back }) {
                Text("←", color = HiroColors.Ink, fontWeight = FontWeight.ExtraBold)
            }
        }
        Cover(title.title, title.coverUrl, DetailCoverWidth, DetailCoverHeight)
        Text(
            title.title,
            style = MaterialTheme.typography.headlineSmall,
            color = HiroColors.Ink,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        val byline = (listOfNotNull(title.author, title.format) + title.languages).joinToString(" · ")
        if (byline.isNotEmpty()) Text(byline, color = HiroColors.TextSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CheckingPanel() {
    Row(
        Modifier.fillMaxWidth().background(HiroColors.Card, HiroShapes.Card).padding(PANEL_PADDING.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text(stringResource(Res.string.detail_checking), color = HiroColors.TextSecondary)
    }
}

@Composable
private fun ErrorPanel(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(HiroColors.Card, HiroShapes.Card).padding(PANEL_PADDING.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(Res.string.detail_error), color = HiroColors.TextSecondary)
        RetryButton(onRetry)
    }
}

/** FR-6: "{available} of {total} copies", the call number in large type and the shelf type; or, if none is on the shelf, that. */
@Composable
private fun HerePanel(
    here: BranchCopies?,
    library: Library,
) {
    when {
        here == null -> MessagePanel(Res.string.detail_all_out_title, stringResource(Res.string.detail_not_held, library.name))
        !here.onShelf -> MessagePanel(Res.string.detail_all_out_title, stringResource(Res.string.detail_all_out, here.total))
        else -> ShelfPanel(here)
    }
}

@Composable
private fun ShelfPanel(here: BranchCopies) {
    Column(
        Modifier.fillMaxWidth().background(HiroColors.OnShelfBackground, HiroShapes.Card).padding(PANEL_PADDING.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(Res.string.detail_here_title),
                style = MaterialTheme.typography.titleLarge,
                color = HiroColors.OnShelfText,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                "✓ " + stringResource(Res.string.detail_copies, here.available, here.total),
                modifier = Modifier.background(HiroColors.Card, HiroShapes.Chip).padding(horizontal = 10.dp, vertical = 4.dp),
                color = HiroColors.OnShelfText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(Res.string.detail_look_for),
                style = MaterialTheme.typography.labelLarge,
                color = HiroColors.OnShelfText,
                fontWeight = FontWeight.Bold,
            )
            here.callNumber?.let {
                Text(it, fontSize = CALL_NUMBER_SIZE.sp, color = HiroColors.OnShelfText, fontWeight = FontWeight.SemiBold)
            }
            val where = here.shelfType?.let { stringResource(Res.string.detail_shelf_at, it, here.library.name) } ?: here.library.name
            Text(where, color = HiroColors.OnShelfText)
        }
    }
}

@Composable
private fun MessagePanel(
    title: StringResource,
    message: String,
) {
    Column(
        Modifier.fillMaxWidth().background(HiroColors.OnLoanBackground, HiroShapes.Card).padding(PANEL_PADDING.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleLarge,
            color = HiroColors.OnLoanText,
            fontWeight = FontWeight.SemiBold,
        )
        Text(message, color = HiroColors.OnLoanText)
    }
}

/** FR-15: each other library as "{available} of {total} on shelf", libraries with copies on the shelf first, then the nearest. */
@Composable
private fun OthersSection(
    availability: BookAvailability,
    onView: (Library) -> Unit,
    onDirections: (Library) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            SectionTitle(stringResource(Res.string.detail_others_title))
            Text(
                stringResource(Res.string.detail_others_note),
                style = MaterialTheme.typography.bodySmall,
                color = HiroColors.TextSecondary,
            )
        }
        if (availability.others.isEmpty()) {
            Text(stringResource(Res.string.detail_others_none), color = HiroColors.TextSecondary)
        }
        availability.others.forEach { OtherBranchRow(it, onView, onDirections) }
    }
}

@Composable
private fun OtherBranchRow(
    item: OtherBranchCopies,
    onView: (Library) -> Unit,
    onDirections: (Library) -> Unit,
) {
    val copies = item.copies
    val status =
        if (copies.onShelf) {
            stringResource(Res.string.detail_other_on_shelf, copies.available, copies.total)
        } else {
            stringResource(Res.string.detail_other_all_out, copies.total)
        }
    val distance = distanceText(item.meters)
    val viewDescription = stringResource(Res.string.others_view_description, copies.library.name)
    val directionsDescription = stringResource(Res.string.detail_directions, copies.library.name)
    val rowDescription = stringResource(Res.string.detail_other_row, copies.library.name, status)
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, HiroColors.InputBorder, HiroShapes.Card)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = rowDescription },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(copies.library.name, color = HiroColors.Ink, fontWeight = FontWeight.ExtraBold)
            Text(distance, style = MaterialTheme.typography.bodySmall, color = HiroColors.TextTertiary)
            Text(
                status,
                modifier =
                    Modifier
                        .background(if (copies.onShelf) HiroColors.OnShelfBackground else HiroColors.OnLoanBackground, HiroShapes.Chip)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                color = if (copies.onShelf) HiroColors.OnShelfText else HiroColors.OnLoanText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        TextButton(
            onClick = { onView(copies.library) },
            modifier = Modifier.heightIn(min = MinTouchTarget).semantics { contentDescription = viewDescription },
        ) {
            Text(stringResource(Res.string.others_view), color = HiroColors.Primary, fontWeight = FontWeight.ExtraBold)
        }
        TextButton(
            onClick = { onDirections(copies.library) },
            modifier = Modifier.heightIn(min = MinTouchTarget).semantics { contentDescription = directionsDescription },
        ) {
            Text("↗", color = HiroColors.Ink, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun About(title: Title) {
    val summary = title.summary.orEmpty()
    if (summary.isEmpty() && title.subjects.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(Res.string.detail_about))
        if (summary.isNotEmpty()) Text(summary, color = HiroColors.Ink, style = MaterialTheme.typography.bodyLarge)
        if (title.subjects.isNotEmpty()) {
            Text(stringResource(Res.string.detail_subjects), style = MaterialTheme.typography.labelLarge, color = HiroColors.TextSecondary)
            title.subjects.take(MAX_SUBJECTS).forEach {
                Text("• $it", style = MaterialTheme.typography.bodyMedium, color = HiroColors.TextTertiary)
            }
        }
    }
}
