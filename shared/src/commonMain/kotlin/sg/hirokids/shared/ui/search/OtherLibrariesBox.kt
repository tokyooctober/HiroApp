package sg.hirokids.shared.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import sg.hirokids.shared.domain.Library
import sg.hirokids.shared.domain.OtherLibrary
import sg.hirokids.shared.domain.distanceLabel
import sg.hirokids.shared.resources.Res
import sg.hirokids.shared.resources.distance_km
import sg.hirokids.shared.resources.distance_m
import sg.hirokids.shared.resources.others_error
import sg.hirokids.shared.resources.others_on_shelf
import sg.hirokids.shared.resources.others_subtitle
import sg.hirokids.shared.resources.others_title
import sg.hirokids.shared.resources.others_view
import sg.hirokids.shared.resources.others_view_description
import sg.hirokids.shared.ui.theme.HiroColors

private val BoxRadius = 18.dp
private val DashedBorderWidth = 2.dp
private const val DASH_LENGTH = 16f
private const val DASH_GAP = 10f

/** "More on the shelf at other libraries": one row per library with a View button (FR-15, FR-16). */
internal fun LazyListScope.othersSection(
    others: OtherLibraries?,
    actions: OtherLibraryActions,
) {
    if (others == null || (others.items.isEmpty() && !others.failed)) return
    item(key = "others") { OtherLibrariesBox(others, actions) }
}

@Composable
private fun OtherLibrariesBox(
    others: OtherLibraries,
    actions: OtherLibraryActions,
) {
    val shape = RoundedCornerShape(BoxRadius)
    Column(
        Modifier
            .fillMaxWidth()
            .background(HiroColors.Card, shape)
            .dashedBorder(HiroColors.InputBorder, BoxRadius)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SectionTitle(stringResource(Res.string.others_title))
            Text(stringResource(Res.string.others_subtitle), style = MaterialTheme.typography.bodyMedium, color = HiroColors.TextSecondary)
        }
        if (others.failed) {
            Text(stringResource(Res.string.others_error), color = HiroColors.TextSecondary)
            RetryButton(actions.onRetry)
        }
        others.items.forEach { OtherLibraryRow(it, actions.onView) }
    }
}

@Composable
private fun OtherLibraryRow(
    item: OtherLibrary,
    onView: (Library) -> Unit,
) {
    val viewDescription = stringResource(Res.string.others_view_description, item.library.name)
    Column {
        HorizontalDivider(color = HiroColors.Divider)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.library.name, color = HiroColors.Ink, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(Res.string.others_on_shelf, item.onShelf) + " · " + distanceText(item.meters),
                    style = MaterialTheme.typography.bodySmall,
                    color = HiroColors.TextTertiary,
                )
            }
            TextButton(
                onClick = { onView(item.library) },
                modifier = Modifier.heightIn(min = MinTouchTarget).semantics { contentDescription = viewDescription },
            ) {
                Text(stringResource(Res.string.others_view), color = HiroColors.Primary, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun distanceText(meters: Double): String {
    val label = distanceLabel(meters)
    return if (label.kilometres) stringResource(Res.string.distance_km, label.text) else stringResource(Res.string.distance_m, label.text)
}

/** The dashed outline of the design (`Results.dc.html`), drawn behind the content. */
private fun Modifier.dashedBorder(
    color: Color,
    radius: Dp,
): Modifier =
    drawBehind {
        val stroke = Stroke(width = DashedBorderWidth.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_LENGTH, DASH_GAP)))
        drawRoundRect(color = color, cornerRadius = CornerRadius(radius.toPx()), style = stroke)
    }
