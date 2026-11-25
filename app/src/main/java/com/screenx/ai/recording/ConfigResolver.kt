package com.screenx.ai.recording

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import com.screenx.ai.domain.AudioSource
import com.screenx.ai.domain.QualityOptions
import com.screenx.ai.domain.RecordingConfig
import com.screenx.ai.domain.VideoOrientation
import kotlin.math.min

data class ResolvedConfig(
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrate: Int,
    val dpi: Int
)

class UnsupportedConfigException : Exception()

/** Turns the user's wishes into a size, frame rate and bitrate this device can encode. */
object ConfigResolver {

    fun resolve(context: Context, config: RecordingConfig): ResolvedConfig {
        val (sw, sh) = realScreenSize(context)
        val portrait = isPortrait(config.orientation, sw, sh)
        val dpi = context.resources.displayMetrics.densityDpi

        val presets = RecordingConfig.RESOLUTIONS.filter { it <= config.resolutionP }.sortedDescending()
        val fpsCandidates = listOf(config.fps, 30, 24).filter { it <= config.fps }.distinct()

        // Prefer lowering the frame rate before lowering the resolution.
        for (preset in presets) {
            val (w, h) = sizeFor(preset, sw, sh, portrait)
            for (fps in fpsCandidates) {
                if (EncoderCapabilities.supports(w, h, fps)) {
                    return ResolvedConfig(w, h, fps, bitrateFor(config, w, h, fps), dpi)
                }
            }
        }
        throw UnsupportedConfigException()
    }

    fun options(context: Context, config: RecordingConfig): QualityOptions {
        val (sw, sh) = realScreenSize(context)
        val portrait = isPortrait(config.orientation, sw, sh)
        val short = min(sw, sh)

        val resolutions = RecordingConfig.RESOLUTIONS.filter { p ->
            val (w, h) = sizeFor(p, sw, sh, portrait)
            p <= short && EncoderCapabilities.supports(w, h, 24)
        }
        val (bw, bh) = sizeFor(config.resolutionP, sw, sh, portrait)
        val fps = RecordingConfig.FPS_OPTIONS.filter { EncoderCapabilities.supports(bw, bh, it) }

        val hasMic = context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        val canCaptureInternal = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val audio = buildList {
            add(AudioSource.NONE)
            if (hasMic) add(AudioSource.MICROPHONE)
            if (canCaptureInternal) add(AudioSource.INTERNAL)
            if (canCaptureInternal && hasMic) add(AudioSource.BOTH)
        }

        return QualityOptions(
            resolutions = resolutions.ifEmpty { listOf(480) },
            fps = fps.ifEmpty { listOf(30) },
            audioSources = audio
        )
    }

    private fun bitrateFor(config: RecordingConfig, w: Int, h: Int, fps: Int): Int =
        if (config.bitrateKbps > 0) {
            (config.bitrateKbps.toLong() * 1000L).coerceIn(500_000L, 100_000_000L).toInt()
        } else {
            (w.toLong() * h * fps * 0.1).toLong().coerceIn(1_000_000L, 40_000_000L).toInt()
        }

    private fun isPortrait(orientation: VideoOrientation, sw: Int, sh: Int): Boolean =
        when (orientation) {
            VideoOrientation.PORTRAIT -> true
            VideoOrientation.LANDSCAPE -> false
            VideoOrientation.AUTO -> sh >= sw
        }

    /** Output size whose short side is [p] (capped at the screen), with the screen's aspect ratio. */
    private fun sizeFor(p: Int, sw: Int, sh: Int, portrait: Boolean): Pair<Int, Int> {
        val short = min(sw, sh)
        val long = maxOf(sw, sh)
        val s = min(p, short).even()
        val l = ((s.toLong() * long) / short).toInt().even()
        return if (portrait) s to l else l to s
    }

    private fun Int.even(): Int = this - (this % 2)

    @Suppress("DEPRECATION")
    private fun realScreenSize(context: Context): Pair<Int, Int> {
        val dm = DisplayMetrics()
        val display = context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
        if (display != null) {
            display.getRealMetrics(dm)
        } else {
            return context.resources.displayMetrics.let { it.widthPixels to it.heightPixels }
        }
        return dm.widthPixels to dm.heightPixels
    }
}
