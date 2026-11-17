package com.screenx.ai.domain

enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** Everything in Settings that is not about the recording itself. */
data class GeneralSettings(
    val saveFolder: String = "ScreenX",
    val exportQuality: String = "HIGH",
    val watermarkEnabled: Boolean = false,
    val watermarkText: String = "ScreenX AI",
    val watermarkCorner: Int = 1, // 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right
    val hapticsEnabled: Boolean = true,
    val soundEnabled: Boolean = false,
    val soundVolume: Int = 70,
    val notifPauseAction: Boolean = true,
    val scheduledAtMs: Long = 0L
)

data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentIndex: Int = 0
)
