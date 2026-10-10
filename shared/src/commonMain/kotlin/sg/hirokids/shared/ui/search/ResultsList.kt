package sg.hirokids.shared.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import sg.hirokids.shared.domain.ResultGroup
import sg.hirokids.shared.domain.SearchHit
import sg.hirokids.shared.resources.Res
import sg.hirokids.shared.resources.card_by
import sg.hirokids.shared.resources.card_on_loan
import sg.hirokids.shared.resources.card_on_loan_waiting
import sg.hirokids.shared.resources.card_on_shelf
import sg.hirokids.shared.resources.results_at_library
import sg.hirokids.shared.resources.results_end
import sg.hirokids.shared.resources.results_error
import sg.hirokids.shared.resources.results_found_title
import sg.hirokids.shared.resources.results_more_error
import sg.hirokids.shared.resources.results_none_on_shelf
import sg.hirokids.shared.resources.results_on_loan_title
import sg.hirokids.shared.resources.results_on_shelf_title
import sg.hirokids.shared.resources.results_saved_note
import sg.hirokids.shared.resources.results_saved_title
import sg.hirokids.shared.ui.theme.HiroColors
import sg.hirokids.shared.ui.theme.HiroShapes

private val CoverWidth = 64.dp
private val CoverHeight = 88.dp

/** What a card says about the book: on the shelf, on loan (with the number waiting), or nothing (saved, or an ISBN lookup). */
private enum class CardStatus { ON_SHELF, ON_LOAN, NONE }

/** The search could not be made: say so, offer Try again, and list any saved books that match (FR-12). */
@Composable
internal fun FailedList(
    failed: ResultsState.Failed,
    onRetry: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.results_error), color = HiroColors.TextSecondary)
                RetryButton(onRetry)
            }
        }
        if (failed.saved.isNotEmpty()) {
            item {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SectionTitle(stringResource(Res.string.results_saved_title))
                    Text(
                        stringResource(Res.string.results_saved_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = HiroColors.TextSecondary,
                    )
                }
            }
            items(failed.saved, key = { it.brn }) { title -> ResultCard(SearchHit(title, listOf(title), false), CardStatus.NONE) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = HiroColors.Ink, fontWeight = FontWeight.SemiBold)
}

@Composable
internal fun ResultList(
    libraryName: String,
    results: ResultsState.Loaded,
    actions: ResultsActions,
) {
    // nothing to list yet and the second group is still on its way: keep the spinner rather than a blank page
    if (results.hasNothingToListYet()) {
        CenteredMessage { Loading() }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        onShelfSection(libraryName, results, actions)
        onLoanSection(results.onLoan, actions)
    }
}

private fun ResultsState.Loaded.hasNothingToListYet(): Boolean {
    val waiting = onShelf.loading || onLoan?.loading == true
    return waiting && onShelf.hits.isEmpty() && onLoan?.hits.isNullOrEmpty()
}

/** "On the shelf here" (or, for an ISBN lookup, a plain "Books found" whose cards carry no shelf claim). */
private fun LazyListScope.onShelfSection(
    libraryName: String,
    results: ResultsState.Loaded,
    actions: ResultsActions,
) {
    val onShelf = results.onShelf
    val claimsShelf = results.onLoan != null
    when {
        onShelf.hits.isNotEmpty() -> {
            item(key = "shelf-title") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SectionTitle(stringResource(if (claimsShelf) Res.string.results_on_shelf_title else Res.string.results_found_title))
                    if (claimsShelf) {
                        Text(
                            stringResource(Res.string.results_at_library, libraryName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = HiroColors.TextSecondary,
                        )
                    }
                }
            }
            val status = if (claimsShelf) CardStatus.ON_SHELF else CardStatus.NONE
            items(onShelf.hits, key = { "shelf-${it.title.brn}" }) { ResultCard(it, status) }
        }
        onShelf.nextOffset == null && !results.onLoan?.hits.isNullOrEmpty() ->
            item(key = "none-on-shelf") {
                Text(stringResource(Res.string.results_none_on_shelf), color = HiroColors.TextSecondary)
            }
    }
    groupFooter("shelf", onShelf, endOfResults = !claimsShelf, group = ResultGroup.ON_SHELF, actions = actions)
}

/** "In this library, but all copies out", the last section of the list. */
private fun LazyListScope.onLoanSection(
    onLoan: Shelf?,
    actions: ResultsActions,
) {
    if (onLoan == null) return
    if (onLoan.hits.isNotEmpty() || onLoan.failed) {
        item(key = "loan-title") { SectionTitle(stringResource(Res.string.results_on_loan_title)) }
        items(onLoan.hits, key = { "loan-${it.title.brn}" }) { ResultCard(it, CardStatus.ON_LOAN) }
    }
    groupFooter("loan", onLoan, endOfResults = true, group = ResultGroup.ALL_HERE, actions = actions)
}

/**
 * The row under a group's cards. When it scrolls into view and the group has more, it asks for the next page (FR-11); a failed
 * page shows Try again instead of asking again; the last group ends with "End of results".
 */
private fun LazyListScope.groupFooter(
    key: String,
    shelf: Shelf,
    endOfResults: Boolean,
    group: ResultGroup,
    actions: ResultsActions,
) {
    val hasCards = shelf.hits.isNotEmpty()
    when {
        shelf.failed ->
            item(key = "$key-error") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.results_more_error), color = HiroColors.TextSecondary)
                    RetryButton { actions.onRetryGroup(group) }
                }
            }
        shelf.loading ->
            item(key = "$key-loading") {
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            }
        shelf.nextOffset != null ->
            item(key = "$key-more") {
                // composing this row means the user has scrolled to the end of the group
                LaunchedEffect(shelf.nextOffset, shelf.hits.size) { actions.onLoadMore(group) }
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            }
        endOfResults && hasCards ->
            item(key = "$key-end") {
                Text(
                    stringResource(Res.string.results_end),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    color = HiroColors.TextTertiary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
    }
}

@Composable
private fun ResultCard(
    hit: SearchHit,
    status: CardStatus,
) {
    val title = hit.title
    val statusText =
        when (status) {
            CardStatus.ON_SHELF -> stringResource(Res.string.card_on_shelf)
            CardStatus.ON_LOAN ->
                if (title.reservations > 0) {
                    stringResource(Res.string.card_on_loan_waiting, title.reservations)
                } else {
                    stringResource(Res.string.card_on_loan)
                }
            CardStatus.NONE -> null
        }
    val by = title.author?.let { stringResource(Res.string.card_by, title.title, it) } ?: title.title
    val description = listOfNotNull(by, title.format, statusText).joinToString(". ")
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
            if (statusText != null) StatusPill(status, statusText)
        }
    }
}

/** Status is never colour-only: a tick or words as well. */
@Composable
private fun StatusPill(
    status: CardStatus,
    text: String,
) {
    val onShelf = status == CardStatus.ON_SHELF
    Text(
        if (onShelf) "✓ $text" else text,
        modifier =
            Modifier
                .background(if (onShelf) HiroColors.OnShelfBackground else HiroColors.OnLoanBackground, HiroShapes.Chip)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        color = if (onShelf) HiroColors.OnShelfText else HiroColors.OnLoanText,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.ExtraBold,
    )
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
