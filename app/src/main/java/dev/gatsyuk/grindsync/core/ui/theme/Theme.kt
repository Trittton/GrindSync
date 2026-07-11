package dev.gatsyuk.grindsync.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.gatsyuk.grindsync.core.model.ThemeMode

// Dark-first palette (SPEC §6.8), single accent, mirrors nav-schematic.html.
private val AccentDark = Color(0xFF4DB3FF)
private val AccentLight = Color(0xFF1F78E0)

private val DarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF06263D),
    primaryContainer = Color(0xFF122636),
    onPrimaryContainer = Color(0xFFBFE0FF),
    background = Color(0xFF0B0E13),
    onBackground = Color(0xFFE8EDF4),
    surface = Color(0xFF141A23),
    onSurface = Color(0xFFE8EDF4),
    surfaceVariant = Color(0xFF1B222D),
    onSurfaceVariant = Color(0xFF8A94A5),
    outline = Color(0xFF28303C),
)

private val LightColors = lightColorScheme(
    primary = AccentLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFB),
    onPrimaryContainer = Color(0xFF0B4F97),
    background = Color(0xFFE9EDF3),
    onBackground = Color(0xFF131922),
    surface = Color.White,
    onSurface = Color(0xFF131922),
    surfaceVariant = Color(0xFFF4F6FB),
    onSurfaceVariant = Color(0xFF5A6473),
    outline = Color(0xFFD2D9E4),
)

@Composable
fun GrindSyncTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
