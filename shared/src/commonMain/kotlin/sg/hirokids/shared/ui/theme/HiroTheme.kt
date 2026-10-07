package sg.hirokids.shared.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Design tokens from spec section 3 ("Design tokens"). Use these, not platform defaults. */
object HiroColors {
    val Ink = Color(0xFF14213D)
    val TextSecondary = Color(0xFF3D4555)
    val TextTertiary = Color(0xFF4A5568)

    // Primary and Adult mode
    val Primary = Color(0xFF2B59C3)
    val PrimaryPressed = Color(0xFF1E3F8F)
    val PrimaryTint = Color(0xFFE6EDFB)

    // Children mode
    val Children = Color(0xFFB4530A)
    val ChildrenInk = Color(0xFF7A3606)
    val ChildrenTint = Color(0xFFFFEAD2)

    // Status is never shown by colour alone: pair these with words ("On shelf", "On loan")
    val OnShelfBackground = Color(0xFFDDF3EA)
    val OnShelfText = Color(0xFF0B5D45)
    val OnLoanBackground = Color(0xFFFDEBD3)
    val OnLoanText = Color(0xFF8A4100)

    val Logo = Color(0xFFFFC94A)
    val BookHeader = Color(0xFFFFF4D6)

    val Page = Color(0xFFF5F7FB)
    val Card = Color(0xFFFFFFFF)
    val Border = Color(0xFFE3E7EE)
    val InputBorder = Color(0xFFC9D3E3)
}

/** Cards use a 16 px radius; chips are fully rounded. */
object HiroShapes {
    val Card = RoundedCornerShape(16.dp)
    val Chip = RoundedCornerShape(percent = 50)
}

private val HiroColorScheme =
    lightColorScheme(
        primary = HiroColors.Primary,
        onPrimary = Color.White,
        primaryContainer = HiroColors.PrimaryTint,
        onPrimaryContainer = HiroColors.PrimaryPressed,
        background = HiroColors.Page,
        onBackground = HiroColors.Ink,
        surface = HiroColors.Card,
        onSurface = HiroColors.Ink,
        onSurfaceVariant = HiroColors.TextSecondary,
        outline = HiroColors.InputBorder,
        outlineVariant = HiroColors.Border,
    )

@Composable
fun HiroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HiroColorScheme,
        shapes = Shapes(medium = HiroShapes.Card, large = HiroShapes.Card),
        content = content,
    )
}
