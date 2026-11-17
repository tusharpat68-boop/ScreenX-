package com.screenx.ai.presentation.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.screenx.ai.R

class AccentPreset(
    @StringRes val nameRes: Int,
    val primary: Color,
    val secondary: Color,
    val accent: Color
)

val AccentPresets = listOf(
    AccentPreset(R.string.accent_blue, Color(0xFF246BFD), Color(0xFF8B5CF6), Color(0xFF22D3EE)),
    AccentPreset(R.string.accent_purple, Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFFC4B5FD)),
    AccentPreset(R.string.accent_teal, Color(0xFF14B8A6), Color(0xFF246BFD), Color(0xFF22D3EE)),
    AccentPreset(R.string.accent_orange, Color(0xFFF97316), Color(0xFFEF4444), Color(0xFFFACC15)),
    AccentPreset(R.string.accent_green, Color(0xFF22C55E), Color(0xFF14B8A6), Color(0xFFA3E635)),
    AccentPreset(R.string.accent_pink, Color(0xFFEC4899), Color(0xFF8B5CF6), Color(0xFFF9A8D4))
)

/** The current accent colors. Reading them inside a composable makes it update when they change. */
object AccentState {
    var primary by mutableStateOf(AccentPresets[0].primary)
    var secondary by mutableStateOf(AccentPresets[0].secondary)
    var accent by mutableStateOf(AccentPresets[0].accent)

    fun apply(index: Int) {
        val preset = AccentPresets.getOrElse(index) { AccentPresets[0] }
        primary = preset.primary
        secondary = preset.secondary
        accent = preset.accent
    }
}

val ScreenXPrimary: Color get() = AccentState.primary
val ScreenXSecondary: Color get() = AccentState.secondary
val ScreenXAccent: Color get() = AccentState.accent

val ScreenXBackground = Color(0xFF080B14)
val ScreenXCard = Color(0xFF121827)
val ScreenXTextPrimary = Color(0xFFFFFFFF)
val ScreenXTextSecondary = Color(0xFFB4BAC8)

val ScreenXLightBackground = Color(0xFFF5F7FB)
val ScreenXLightCard = Color(0xFFFFFFFF)
val ScreenXLightText = Color(0xFF0B1020)
