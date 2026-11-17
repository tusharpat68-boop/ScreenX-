package com.screenx.ai.presentation.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object ScreenXGradients {
    val brand: Brush get() = Brush.linearGradient(listOf(ScreenXPrimary, ScreenXSecondary))
    val brandWithAccent: Brush
        get() = Brush.linearGradient(listOf(ScreenXPrimary, ScreenXSecondary, ScreenXAccent))
    val glow: Brush
        get() = Brush.radialGradient(listOf(ScreenXPrimary.copy(alpha = 0.30f), Color.Transparent))
}
