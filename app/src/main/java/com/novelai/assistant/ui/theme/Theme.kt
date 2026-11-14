package com.novelai.assistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.novelai.assistant.ui.components.ProvideAdaptiveMetrics

private val LightColors = lightColorScheme(
    primary = NovelColors.Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E4FF),
    onPrimaryContainer = Color(0xFF1C1A6E),
    secondary = Color(0xFF4E7E8C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6EBF0),
    onSecondaryContainer = Color(0xFF12333B),
    tertiary = Color(0xFFB0578D),
    tertiaryContainer = Color(0xFFFBDFEF),
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1C22),
    surfaceVariant = Color(0xFFE7E7EE),
    onSurfaceVariant = Color(0xFF5B5B66),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F7FA),
    surfaceContainer = Color(0xFFF0F0F5),
    surfaceContainerHigh = Color(0xFFE9E9F0),
    surfaceContainerHighest = Color(0xFFE2E2EA),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDEDEE5),
    outline = Color(0xFFC9C9D1),
    outlineVariant = Color(0xFFE2E2E9),
    error = Color(0xFFE5484D)
)

private val DarkColors = darkColorScheme(
    primary = NovelColors.IndigoLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A2866),
    onPrimaryContainer = Color(0xFFE5E4FF),
    secondary = Color(0xFF8AC5D4),
    secondaryContainer = Color(0xFF2A4A53),
    onSecondaryContainer = Color(0xFFD6EBF0),
    tertiary = Color(0xFFEDA0C8),
    tertiaryContainer = Color(0xFF6E3156),
    background = NovelColors.GlassCanvasDark,
    onBackground = NovelColors.GlassOnDarkText,
    surface = NovelColors.GlassSurfaceDark,
    onSurface = NovelColors.GlassOnDarkText,
    surfaceVariant = Color(0xFF1C1C21),
    onSurfaceVariant = Color(0xFFA8A8B3),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0A0A0C),
    surfaceContainer = Color(0xFF121215),
    surfaceContainerHigh = Color(0xFF1A1A1F),
    surfaceContainerHighest = Color(0xFF23232A),
    surfaceBright = Color(0xFF2C2C34),
    surfaceDim = Color(0xFF000000),
    outline = Color(0xFF42424A),
    outlineVariant = Color(0xFF23232A),
    error = Color(0xFFFF7B81)
)

@Composable
fun NovelAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    ProvideAdaptiveMetrics {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = NovelShapes,
            content = content
        )
    }
}