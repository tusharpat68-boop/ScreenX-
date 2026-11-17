package com.screenx.ai.editor

import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import kotlin.math.min
import kotlin.math.roundToInt

/** What an export will produce: the effects and the size of the result. */
class ExportPlan(val effects: List<Effect>, val outWidth: Int, val outHeight: Int)

@OptIn(UnstableApi::class)
object EditEffects {

    /** Effects for the live preview: crop and rotation only (no downscaling). */
    fun preview(width: Int, height: Int, rotationCw: Int, crop: CropPreset): List<Effect> =
        build(width, height, rotationCw, crop, null).effects

    fun exportPlan(
        width: Int,
        height: Int,
        rotationCw: Int,
        crop: CropPreset,
        quality: ExportQuality
    ): ExportPlan = build(width, height, rotationCw, crop, quality)

    private fun build(
        width: Int,
        height: Int,
        rotationCw: Int,
        crop: CropPreset,
        quality: ExportQuality?
    ): ExportPlan {
        val rotated = rotationCw == 90 || rotationCw == 270
        // Frame size as the viewer sees it, before cropping.
        val viewW = (if (rotated) height else width).toFloat()
        val viewH = (if (rotated) width else height).toFloat()

        var keepW = viewW
        var keepH = viewH
        val ratio = crop.ratio
        if (ratio != null && viewW > 0f && viewH > 0f) {
            if (viewW / viewH > ratio) {
                keepH = viewH
                keepW = viewH * ratio
            } else {
                keepW = viewW
                keepH = viewW / ratio
            }
        }

        val effects = ArrayList<Effect>()

        // Crop is applied to the original frame, so convert the fractions back through the rotation.
        if (viewW > 0f && viewH > 0f && (keepW < viewW - 0.5f || keepH < viewH - 0.5f)) {
            val fx = keepW / viewW
            val fy = keepH / viewH
            val xFrac = if (rotated) fy else fx
            val yFrac = if (rotated) fx else fy
            effects.add(Crop(-xFrac, xFrac, -yFrac, yFrac)) // left, right, bottom, top (centered)
        }

        if (rotationCw != 0) {
            // Media3 rotates counterclockwise, the editor's buttons turn clockwise.
            val ccw = ((360 - rotationCw) % 360).toFloat()
            effects.add(ScaleAndRotateTransformation.Builder().setRotationDegrees(ccw).build())
        }

        var outW = keepW.roundToInt().evenSize()
        var outH = keepH.roundToInt().evenSize()

        // Downscale for lower export qualities (never upscale).
        if (quality != null && quality.shortSide > 0 && outW > 0 && outH > 0) {
            val short = min(outW, outH)
            if (short > quality.shortSide) {
                val scale = quality.shortSide.toFloat() / short
                outW = (outW * scale).roundToInt().evenSize()
                outH = (outH * scale).roundToInt().evenSize()
                effects.add(Presentation.createForHeight(outH))
            }
        }
        return ExportPlan(effects, outW, outH)
    }

    private fun Int.evenSize(): Int = if (this < 2) 2 else this - (this % 2)
}
