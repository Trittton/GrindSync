package dev.gatsyuk.soloranking.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import dev.gatsyuk.soloranking.core.model.ThemeMode

// "System window" art direction (Solo Leveling): deep blue-black canvas, one
// electric-blue signature accent + violet partner used ONLY in gradients/glows.
// Dark-first (SPEC §6.8); light mode is the same structure without the glow.
private val AccentDark = Color(0xFF4DA8FF)
private val VioletDark = Color(0xFF8B7CFF)
private val AccentLight = Color(0xFF1F78E0)
private val VioletLight = Color(0xFF6C5CE7)

/**
 * Colors that Material's [androidx.compose.material3.ColorScheme] has no slot
 * for: the panel treatment (gradient border + fill) and the glow intensity.
 * Consumed by SystemPanel / RankBadge / GlowProgressBar in core/ui.
 */
@Immutable
data class SystemColors(
    val accent: Color,
    val accentAlt: Color,
    val panelFill: Color,
    val panelBorderTop: Color,
    val panelBorderBottom: Color,
    /** 0f disables the radial top-glow (light theme). */
    val glowAlpha: Float,
)

val LocalSystemColors = staticCompositionLocalOf {
    DarkSystemColors // sane default; SoloRankingTheme always provides one
}

private val DarkSystemColors = SystemColors(
    accent = AccentDark,
    accentAlt = VioletDark,
    panelFill = Color(0xFF0F1624),
    panelBorderTop = Color(0x804DA8FF),
    panelBorderBottom = Color(0x268B7CFF),
    glowAlpha = 0.10f,
)

private val LightSystemColors = SystemColors(
    accent = AccentLight,
    accentAlt = VioletLight,
    panelFill = Color.White,
    panelBorderTop = Color(0x4D1F78E0),
    panelBorderBottom = Color(0x1A6C5CE7),
    glowAlpha = 0f,
)

private val DarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF06263D),
    primaryContainer = Color(0xFF12263F),
    onPrimaryContainer = Color(0xFFBFE0FF),
    secondary = VioletDark,
    onSecondary = Color(0xFF1A1145),
    secondaryContainer = Color(0xFF241C4D),
    onSecondaryContainer = Color(0xFFD9D2FF),
    background = Color(0xFF070B14),
    onBackground = Color(0xFFE8EDF4),
    surface = Color(0xFF0F1624),
    onSurface = Color(0xFFE8EDF4),
    surfaceVariant = Color(0xFF182338),
    onSurfaceVariant = Color(0xFFA3AFC6),
    // Bright enough to read as an input border on dark cards (user feedback).
    outline = Color(0xFF4E5A73),
    outlineVariant = Color(0xFF2C3A54),
    // Container family drives NavigationBar / elevated surfaces — keep the blue cast.
    surfaceContainerLowest = Color(0xFF070B12),
    surfaceContainerLow = Color(0xFF0B1119),
    surfaceContainer = Color(0xFF0D1420),
    surfaceContainerHigh = Color(0xFF121B2C),
    surfaceContainerHighest = Color(0xFF16213A),
    surfaceDim = Color(0xFF070B14),
    surfaceBright = Color(0xFF1C2942),
)

private val LightColors = lightColorScheme(
    primary = AccentLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFB),
    onPrimaryContainer = Color(0xFF0B4F97),
    secondary = VioletLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E1FB),
    onSecondaryContainer = Color(0xFF2F2478),
    background = Color(0xFFE9EDF3),
    onBackground = Color(0xFF131922),
    surface = Color.White,
    onSurface = Color(0xFF131922),
    surfaceVariant = Color(0xFFF4F6FB),
    onSurfaceVariant = Color(0xFF5A6473),
    outline = Color(0xFFAEB9C9),
    outlineVariant = Color(0xFFD2D9E4),
)

// Slightly larger secondary text than M3 defaults — gym readability (user feedback).
private val AppTypography = Typography().let { base ->
    base.copy(
        bodySmall = base.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
        labelSmall = base.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
        labelMedium = base.labelMedium.copy(fontSize = 13.sp),
    )
}

@Composable
fun SoloRankingTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(
        LocalSystemColors provides if (dark) DarkSystemColors else LightSystemColors,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}
