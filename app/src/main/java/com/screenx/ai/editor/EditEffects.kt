package com.screenx.ai.editor

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.Crop
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import com.google.common.collect.ImmutableList
import kotlin.math.min
import kotlin.math.roundToInt

/** What an export will produce: the effects and the size of the result. */
class ExportPlan(val effects: List<Effect>, val outWidth: Int, val outHeight: Int)

/** The look of the edit (everything except speed and volume, which belong to each clip). */
class LookSettings(
    val rotationCw: Int,
    val crop: CropPreset,
    val filter: FilterPreset,
    val adjust: Adjust,
    val texts: List<TextItem>,
    val flipH: Boolean = false,
    val flipV: Boolean = false
)

@OptIn(UnstableApi::class)
object EditEffects {

    /** Effects for the live preview: no downscaling. */
    fun preview(width: Int, height: Int, look: LookSettings, fitToCanvas: Boolean = false): List<Effect> =
        (if (fitToCanvas) listOf(fitToCanvas(width, height)) else emptyList()) + build(width, height, look, null).effects

    /** Scales a clip of another size to fit inside the project's picture size (black bars, no stretching). */
    fun fitToCanvas(width: Int, height: Int): Effect =
        Presentation.createForWidthAndHeight(width, height, Presentation.LAYOUT_SCALE_TO_FIT)

    /** The picture size of the result (after rotation and crop, before any downscaling). */
    fun outputSize(width: Int, height: Int, look: LookSettings): Pair<Int, Int> {
        val plan = build(width, height, look, null)
        return plan.outWidth to plan.outHeight
    }

    fun exportPlan(width: Int, height: Int, look: LookSettings, quality: ExportQuality): ExportPlan =
        build(width, height, look, quality)

    private fun build(
        width: Int,
        height: Int,
        look: LookSettings,
        quality: ExportQuality?
    ): ExportPlan {
        val rotationCw = look.rotationCw
        val rotated = rotationCw == 90 || rotationCw == 270
        // Frame size as the viewer sees it, before cropping.
        val viewW = (if (rotated) height else width).toFloat()
        val viewH = (if (rotated) width else height).toFloat()

        var keepW = viewW
        var keepH = viewH
        val ratio = look.crop.ratio
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

        if (look.flipH || look.flipV) {
            val sx = if (look.flipH) -1f else 1f
            val sy = if (look.flipV) -1f else 1f
            effects.add(MatrixTransformation { android.graphics.Matrix().apply { postScale(sx, sy) } })
        }

        effects.addAll(colorEffects(look.filter, look.adjust))

        // Text is drawn at the size of the cropped frame, before any downscaling.
        if (keepH > 0f) {
            val overlay = textOverlay(look.texts, keepH)
            if (overlay != null) effects.add(overlay)
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

    private fun colorEffects(filter: FilterPreset, adjust: Adjust): List<Effect> {
        val out = ArrayList<Effect>()
        val p = FilterMath.params(filter, adjust.filterStrength)
        if (p.satPct != 0f) out.add(HslAdjustment.Builder().adjustSaturation(p.satPct).build())
        if (p.r != 1f || p.g != 1f || p.b != 1f) {
            out.add(RgbAdjustment.Builder().setRedScale(p.r).setGreenScale(p.g).setBlueScale(p.b).build())
        }
        if (p.brightness != 0f) out.add(Brightness(p.brightness))
        if (p.contrast != 0f) out.add(Contrast(p.contrast))
        if (p.invert) out.add(RgbFilter.createInvertedFilter())

        if (adjust.brightness != 0f) out.add(Brightness(adjust.brightness.coerceIn(-1f, 1f) * 0.5f))
        if (adjust.contrast != 0f) out.add(Contrast(adjust.contrast.coerceIn(-1f, 1f)))
        if (adjust.saturation != 0f || adjust.hue != 0f) {
            val hsl = HslAdjustment.Builder()
            if (adjust.hue != 0f) hsl.adjustHue(adjust.hue.coerceIn(-1f, 1f) * 180f)
            if (adjust.saturation != 0f) hsl.adjustSaturation(adjust.saturation.coerceIn(-1f, 1f) * 100f)
            out.add(hsl.build())
        }
        if (adjust.warmth != 0f || adjust.tint != 0f) {
            val k = FilterMath.warmthScales(adjust.warmth, adjust.tint)
            out.add(RgbAdjustment.Builder().setRedScale(k[0]).setGreenScale(k[1]).setBlueScale(k[2]).build())
        }
        return out
    }

    private fun textOverlay(texts: List<TextItem>, frameHeight: Float): OverlayEffect? {
        val overlays = ArrayList<TextureOverlay>()
        for (t in texts) {
            if (t.text.isBlank()) continue
            val spannable = SpannableString(t.text)
            val px = (t.size * frameHeight).toInt().coerceAtLeast(12)
            val end = spannable.length
            spannable.setSpan(AbsoluteSizeSpan(px, false), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(t.color), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (t.bold) {
                spannable.setSpan(StyleSpan(Typeface.BOLD), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val settings = OverlaySettings.Builder()
                .setBackgroundFrameAnchor(t.x.coerceIn(-1f, 1f), t.y.coerceIn(-1f, 1f))
                .setOverlayFrameAnchor(0f, 0f)
                .build()
            overlays.add(TextOverlay.createStaticTextOverlay(spannable, settings))
        }
        if (overlays.isEmpty()) return null
        return OverlayEffect(ImmutableList.copyOf(overlays))
    }

    private fun Int.evenSize(): Int = if (this < 2) 2 else this - (this % 2)
}
