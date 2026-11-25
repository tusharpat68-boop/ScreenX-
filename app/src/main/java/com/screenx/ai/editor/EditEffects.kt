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
    val texts: List<TextItem>
)

@OptIn(UnstableApi::class)
object EditEffects {

    /** Effects for the live preview: no downscaling. */
    fun preview(width: Int, height: Int, look: LookSettings): List<Effect> =
        build(width, height, look, null).effects

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
        when (filter) {
            FilterPreset.NONE -> Unit
            FilterPreset.VIVID -> {
                out.add(HslAdjustment.Builder().adjustSaturation(35f).build())
                out.add(Contrast(0.12f))
            }
            FilterPreset.WARM -> {
                out.add(RgbAdjustment.Builder().setRedScale(1.12f).setBlueScale(0.88f).build())
            }
            FilterPreset.COOL -> {
                out.add(RgbAdjustment.Builder().setRedScale(0.9f).setBlueScale(1.12f).build())
            }
            FilterPreset.VINTAGE -> {
                out.add(HslAdjustment.Builder().adjustSaturation(-30f).build())
                out.add(RgbAdjustment.Builder().setRedScale(1.08f).setBlueScale(0.85f).build())
                out.add(Contrast(-0.05f))
            }
            FilterPreset.FADE -> {
                out.add(HslAdjustment.Builder().adjustSaturation(-15f).build())
                out.add(Brightness(0.06f))
                out.add(Contrast(-0.18f))
            }
            FilterPreset.BW -> out.add(RgbFilter.createGrayscaleFilter())
            FilterPreset.NEGATIVE -> out.add(RgbFilter.createInvertedFilter())
        }
        if (adjust.brightness != 0f) out.add(Brightness(adjust.brightness.coerceIn(-1f, 1f) * 0.5f))
        if (adjust.contrast != 0f) out.add(Contrast(adjust.contrast.coerceIn(-1f, 1f)))
        if (adjust.saturation != 0f) {
            out.add(HslAdjustment.Builder().adjustSaturation(adjust.saturation.coerceIn(-1f, 1f) * 100f).build())
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
