package sg.hirokids.shared.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import sg.hirokids.shared.domain.BookLanguage
import sg.hirokids.shared.resources.Res
import sg.hirokids.shared.resources.language_all
import sg.hirokids.shared.resources.language_chinese
import sg.hirokids.shared.resources.language_english
import sg.hirokids.shared.resources.language_filter
import sg.hirokids.shared.resources.language_malay
import sg.hirokids.shared.resources.language_tamil
import sg.hirokids.shared.ui.theme.HiroColors
import sg.hirokids.shared.ui.theme.HiroShapes

private fun BookLanguage.label(): StringResource =
    when (this) {
        BookLanguage.ALL -> Res.string.language_all
        BookLanguage.ENGLISH -> Res.string.language_english
        BookLanguage.CHINESE -> Res.string.language_chinese
        BookLanguage.MALAY -> Res.string.language_malay
        BookLanguage.TAMIL -> Res.string.language_tamil
    }

/** English, Chinese, Malay, Tamil and All: one choice at a time, narrowing the search to that language (FR-7). */
@Composable
fun LanguageChips(
    selected: BookLanguage,
    onSelect: (BookLanguage) -> Unit,
) {
    val label = stringResource(Res.string.language_filter)
    Row(
        Modifier.horizontalScroll(rememberScrollState()).selectableGroup().semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // English first, All last: a child's first look is at English books, and a mixed list is the exception
        for (language in listOf(BookLanguage.ENGLISH, BookLanguage.CHINESE, BookLanguage.MALAY, BookLanguage.TAMIL, BookLanguage.ALL)) {
            val on = language == selected
            Box(
                Modifier
                    .heightIn(min = MinTouchTarget)
                    .selectable(selected = on, onClick = { onSelect(language) }, role = Role.RadioButton)
                    .background(if (on) HiroColors.Ink else HiroColors.PrimaryTint, HiroShapes.Chip)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(language.label()),
                    color = if (on) Color.White else HiroColors.PrimaryPressed,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
            }
        }
    }
}
