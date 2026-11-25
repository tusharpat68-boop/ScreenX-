package com.screenx.ai.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// Built inside the composable so they follow the accent color.
private fun darkColors(): ColorScheme = darkColorScheme(
    primary = ScreenXPrimary,
    secondary = ScreenXSecondary,
    tertiary = ScreenXAccent,
    background = ScreenXBackground,
    surface = ScreenXCard,
    surfaceVariant = ScreenXCard,
    onPrimary = ScreenXTextPrimary,
    onSecondary = ScreenXTextPrimary,
    onBackground = ScreenXTextPrimary,
    onSurface = ScreenXTextPrimary,
    onSurfaceVariant = ScreenXTextSecondary
)

private fun lightColors(): ColorScheme = lightColorScheme(
    primary = ScreenXPrimary,
    secondary = ScreenXSecondary,
    tertiary = ScreenXAccent,
    background = ScreenXLightBackground,
    surface = ScreenXLightCard,
    onPrimary = ScreenXTextPrimary,
    onSecondary = ScreenXTextPrimary,
    onBackground = ScreenXLightText,
    onSurface = ScreenXLightText
)

private val ScreenXShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun ScreenXTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkColors() else lightColors(),
        typography = ScreenXTypography,
        shapes = ScreenXShapes,
        content = content
    )
}
