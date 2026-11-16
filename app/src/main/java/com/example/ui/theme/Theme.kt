package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ColorBorderSubtle = androidx.compose.ui.graphics.Color(0xFF1C2538)

private val AxionDarkColorScheme = darkColorScheme(
    primary = AxionCyan,
    onPrimary = StudioObsidian,
    primaryContainer = AxionCyanMuted,
    onPrimaryContainer = TextPrimary,
    secondary = AxionViolet,
    onSecondary = TextPrimary,
    secondaryContainer = AxionVioletContainer,
    onSecondaryContainer = TextPrimary,
    tertiary = AxionMagenta,
    onTertiary = StudioObsidian,
    background = StudioObsidian,
    onBackground = TextPrimary,
    surface = StudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = StudioObsidian,
    surfaceContainerLow = StudioSurface,
    surfaceContainer = StudioSurfaceElevated,
    surfaceContainerHigh = StudioSurfaceHighest,
    outline = StudioBorder,
    outlineVariant = ColorBorderSubtle,
    error = StatusError,
    onError = TextPrimary,
    errorContainer = StatusErrorContainer,
    onErrorContainer = TextPrimary
)

val AxionShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun AxionTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AxionDarkColorScheme,
        typography = AxionTypography,
        shapes = AxionShapes,
        content = content
    )
}
