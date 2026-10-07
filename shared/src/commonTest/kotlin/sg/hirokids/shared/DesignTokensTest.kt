package sg.hirokids.shared

import androidx.compose.ui.graphics.toArgb
import sg.hirokids.shared.ui.theme.HiroColors
import kotlin.test.Test
import kotlin.test.assertEquals

/** Guards the design tokens of spec section 3 against drift. */
class DesignTokensTest {
    @Test
    fun adultAndChildrenModeColoursMatchTheSpec() {
        assertEquals(0xFF2B59C3.toInt(), HiroColors.Primary.toArgb()) // Primary / Adult mode
        assertEquals(0xFFB4530A.toInt(), HiroColors.Children.toArgb()) // Children mode
        assertEquals(0xFF14213D.toInt(), HiroColors.Ink.toArgb())
    }

    @Test
    fun shelfStatusColoursMatchTheSpec() {
        assertEquals(0xFFDDF3EA.toInt(), HiroColors.OnShelfBackground.toArgb())
        assertEquals(0xFF0B5D45.toInt(), HiroColors.OnShelfText.toArgb())
        assertEquals(0xFFFDEBD3.toInt(), HiroColors.OnLoanBackground.toArgb())
        assertEquals(0xFF8A4100.toInt(), HiroColors.OnLoanText.toArgb())
    }
}
