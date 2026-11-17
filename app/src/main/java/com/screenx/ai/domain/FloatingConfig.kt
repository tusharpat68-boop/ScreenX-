package com.screenx.ai.domain

/**
 * Floating controller settings. [sizeLevel] 0=small, 1=medium, 2=large.
 * [x]/[y] are the last dragged position in pixels (-1 = default corner).
 * [startHidden] starts recordings with the controller hidden (show it from the notification).
 */
data class FloatingConfig(
    val enabled: Boolean = false,
    val sizeLevel: Int = 1,
    val opacityPercent: Int = 100,
    val x: Int = -1,
    val y: Int = -1,
    val startHidden: Boolean = false
) {
    companion object {
        val SIZES = listOf(0, 1, 2)
        val OPACITIES = listOf(50, 75, 100)
    }
}
