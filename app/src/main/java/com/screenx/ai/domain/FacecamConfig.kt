package com.screenx.ai.domain

enum class CameraLens { FRONT, BACK }

enum class FacecamShape { CIRCLE, RECTANGLE }

/**
 * Face camera bubble settings. [sizeLevel] 0..3 = small..extra large.
 * [x]/[y] are the last dragged position in pixels (-1 = default corner).
 */
data class FacecamConfig(
    val enabled: Boolean = false,
    val lens: CameraLens = CameraLens.FRONT,
    val shape: FacecamShape = FacecamShape.CIRCLE,
    val sizeLevel: Int = 1,
    val x: Int = -1,
    val y: Int = -1
) {
    companion object {
        val SIZES = listOf(0, 1, 2, 3)
    }
}
