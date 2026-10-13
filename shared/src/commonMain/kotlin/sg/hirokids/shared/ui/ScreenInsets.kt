package sg.hirokids.shared.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The edges the phone keeps for itself (status bar, notch, gesture bar). Null means the real ones, `WindowInsets.safeDrawing`.
 * Tests set it so a JVM test can stand in for a phone's status bar, which the JVM does not have.
 */
val LocalScreenInsets = staticCompositionLocalOf<WindowInsets?> { null }

/** Every screen pads by this, so no button sits under the status bar where a tap never reaches the app. */
@Composable
fun screenInsets(): WindowInsets = LocalScreenInsets.current ?: WindowInsets.safeDrawing
