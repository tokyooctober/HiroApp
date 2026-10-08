package sg.hirokids.shared.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import sg.hirokids.shared.domain.distanceLabel
import sg.hirokids.shared.resources.Res
import sg.hirokids.shared.resources.change
import sg.hirokids.shared.resources.detect_subtitle
import sg.hirokids.shared.resources.detect_title
import sg.hirokids.shared.resources.detecting
import sg.hirokids.shared.resources.distance_km
import sg.hirokids.shared.resources.distance_m
import sg.hirokids.shared.resources.home_placeholder
import sg.hirokids.shared.resources.loading
import sg.hirokids.shared.resources.notice_approximate
import sg.hirokids.shared.resources.notice_denied
import sg.hirokids.shared.resources.notice_not_at_library
import sg.hirokids.shared.resources.notice_unavailable
import sg.hirokids.shared.resources.picker_choose
import sg.hirokids.shared.resources.picker_close
import sg.hirokids.shared.resources.picker_confirm
import sg.hirokids.shared.resources.picker_empty
import sg.hirokids.shared.resources.picker_or_choose
import sg.hirokids.shared.resources.picker_search_hint
import sg.hirokids.shared.resources.picker_selected
import sg.hirokids.shared.resources.picker_title
import sg.hirokids.shared.resources.youre_at
import sg.hirokids.shared.ui.theme.HiroColors
import sg.hirokids.shared.ui.theme.HiroShapes

private val MinTouchTarget = 48.dp // NFR-8: 48 dp (Android) / 44 pt (iOS)

/** Shows either the home screen or the picker, depending on the state. */
@Composable
fun LibraryFlow(viewModel: LibraryViewModel) {
    val state by viewModel.state.collectAsState()
    when {
        state.loading -> LoadingScreen()
        state.pickerOpen ->
            LibraryPickerScreen(
                state = state,
                onDetect = viewModel::detect,
                onSearch = viewModel::search,
                onSelect = viewModel::select,
                onConfirm = viewModel::confirm,
                onClose = viewModel::closePicker,
            )
        else -> LibraryHomeScreen(state, onChange = viewModel::change)
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
        Text(stringResource(Res.string.loading), Modifier.padding(top = 96.dp))
    }
}

/** The "You're at {library}" card on top of the search home (design: `Main.dc.html`). Search itself arrives in Task 7. */
@Composable
fun LibraryHomeScreen(
    state: LibraryUiState,
    onChange: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(HiroColors.Page)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        state.current?.let { YoureAtCard(it.name, onChange) }
        Text(
            stringResource(Res.string.home_placeholder),
            style = MaterialTheme.typography.bodyLarge,
            color = HiroColors.TextSecondary,
        )
    }
}

@Composable
fun YoureAtCard(
    libraryName: String,
    onChange: () -> Unit,
) {
    val youreAt = stringResource(Res.string.youre_at)
    val change = stringResource(Res.string.change)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(HiroColors.CardTint, HiroShapes.Card)
            .clickable(onClickLabel = change, onClick = onChange)
            .semantics(mergeDescendants = true) { contentDescription = "$youreAt $libraryName. $change" }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(youreAt, style = MaterialTheme.typography.labelLarge, color = HiroColors.TextSecondary, fontWeight = FontWeight.Bold)
            Text(libraryName, style = MaterialTheme.typography.titleMedium, color = HiroColors.Ink, fontWeight = FontWeight.ExtraBold)
        }
        Text(change, color = HiroColors.Primary, fontWeight = FontWeight.ExtraBold)
    }
}

/** "Which library are you at?" (design: `Location.dc.html`). */
@Composable
fun LibraryPickerScreen(
    state: LibraryUiState,
    onDetect: () -> Unit,
    onSearch: (String) -> Unit,
    onSelect: (String) -> Unit,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(HiroColors.Card).safeDrawingPadding()) {
        Row(Modifier.padding(start = 12.dp, end = 20.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.picker_title),
                style = MaterialTheme.typography.headlineSmall,
                color = HiroColors.Ink,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            if (state.current != null) {
                TextButton(onClick = onClose, modifier = Modifier.heightIn(min = MinTouchTarget)) {
                    Text(stringResource(Res.string.picker_close))
                }
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DetectButton(detecting = state.detecting, onClick = onDetect)
            state.notice?.let { NoticeText(it) }
            OrDivider()
            OutlinedTextField(
                value = state.query,
                onValueChange = onSearch,
                singleLine = true,
                placeholder = { Text(stringResource(Res.string.picker_search_hint)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
                shape = RoundedCornerShape(14.dp),
            )
            LibraryList(state, onSelect)
        }
        val selectedName = state.selectedName
        Button(
            onClick = onConfirm,
            enabled = selectedName != null,
            modifier = Modifier.fillMaxWidth().padding(20.dp).heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = HiroColors.Primary),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                selectedName?.let { stringResource(Res.string.picker_confirm, it) } ?: stringResource(Res.string.picker_choose),
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun DetectButton(
    detecting: Boolean,
    onClick: () -> Unit,
) {
    val title = stringResource(Res.string.detect_title)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(HiroColors.Card, HiroShapes.Card)
            .clickable(enabled = !detecting, onClickLabel = title, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.background(HiroColors.PrimaryTint, RoundedCornerShape(12.dp)).padding(12.dp)) {
            if (detecting) CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp) else Text("◎", color = HiroColors.Primary)
        }
        Column {
            Text(if (detecting) stringResource(Res.string.detecting) else title, fontWeight = FontWeight.ExtraBold, color = HiroColors.Ink)
            Text(stringResource(Res.string.detect_subtitle), style = MaterialTheme.typography.bodySmall, color = HiroColors.TextTertiary)
        }
    }
}

@Composable
private fun NoticeText(notice: DetectNotice) {
    val text =
        when (notice) {
            DetectNotice.NOT_AT_A_LIBRARY -> stringResource(Res.string.notice_not_at_library)
            DetectNotice.APPROXIMATE_ONLY -> stringResource(Res.string.notice_approximate)
            DetectNotice.DENIED -> stringResource(Res.string.notice_denied)
            DetectNotice.UNAVAILABLE -> stringResource(Res.string.notice_unavailable)
        }
    Text(
        text,
        modifier = Modifier.fillMaxWidth().background(HiroColors.OnLoanBackground, RoundedCornerShape(12.dp)).padding(12.dp),
        style = MaterialTheme.typography.bodySmall,
        color = HiroColors.OnLoanText,
    )
}

@Composable
private fun OrDivider() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(Modifier.weight(1f), color = HiroColors.Border)
        Text(stringResource(Res.string.picker_or_choose), style = MaterialTheme.typography.bodySmall, color = HiroColors.TextTertiary)
        HorizontalDivider(Modifier.weight(1f), color = HiroColors.Border)
    }
}

@Composable
private fun LibraryList(
    state: LibraryUiState,
    onSelect: (String) -> Unit,
) {
    if (state.items.isEmpty()) {
        Text(stringResource(Res.string.picker_empty), color = HiroColors.TextSecondary, modifier = Modifier.padding(vertical = 12.dp))
        return
    }
    // Compose keeps the scroll anchored on the item that was first visible, so a re-sort (after Detect, or a search) would
    // jump past the closest libraries. Start from the top whenever the order changes.
    val listState = rememberLazyListState()
    val order = state.items.map { it.library.code }
    LaunchedEffect(order) { listState.scrollToItem(0) }
    LazyColumn(Modifier.fillMaxWidth(), state = listState) {
        items(state.items, key = { it.library.code }) { item ->
            val selected = item.library.code == state.selectedCode
            val selectedLabel = stringResource(Res.string.picker_selected)
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable { onSelect(item.library.code) }
                        .semantics(mergeDescendants = true) {
                            this.selected = selected
                            role = Role.RadioButton
                            if (selected) contentDescription = "${item.library.name}. $selectedLabel"
                        }.padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.library.name, color = HiroColors.Ink, fontWeight = FontWeight.Bold)
                        item.meters?.let {
                            Text(
                                distanceText(it),
                                style = MaterialTheme.typography.bodySmall,
                                color = HiroColors.TextTertiary,
                            )
                        }
                    }
                    if (selected) Text("✓", color = HiroColors.Primary, fontWeight = FontWeight.ExtraBold) // status is never colour-only
                }
                HorizontalDivider(color = HiroColors.Divider)
            }
        }
    }
}

@Composable
private fun distanceText(meters: Double): String {
    val label = distanceLabel(meters)
    return if (label.kilometres) stringResource(Res.string.distance_km, label.text) else stringResource(Res.string.distance_m, label.text)
}
