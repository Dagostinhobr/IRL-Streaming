package app.streamlink.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LiveRed = Color(0xFFFF2D55)
val Mint = Color(0xFF00E0B8)
val Ink = Color(0xFF08090C)
val Panel = Color(0xFF14161C)
val PanelHigh = Color(0xFF1E2129)
val TextPrimary = Color(0xFFF5F6FA)
val TextMuted = Color(0xFF8A90A0)
val Warn = Color(0xFFFFB020)

private val StreamLinkColors = darkColorScheme(
    primary = LiveRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A1420),
    onPrimaryContainer = Color(0xFFFFD9DF),
    secondary = Mint,
    onSecondary = Color(0xFF00251E),
    background = Ink,
    onBackground = TextPrimary,
    surface = Panel,
    onSurface = TextPrimary,
    surfaceVariant = PanelHigh,
    onSurfaceVariant = TextMuted,
    outline = Color(0xFF363B47),
    error = LiveRed,
    onError = Color.White
)

@Composable
fun StreamLinkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StreamLinkColors,
        content = content
    )
}
