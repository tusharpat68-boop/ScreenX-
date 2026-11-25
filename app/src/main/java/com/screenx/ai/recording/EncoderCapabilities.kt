package com.screenx.ai.recording

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build

data class EncoderInfo(
    val name: String,
    val hardware: Boolean,
    val maxWidth: Int,
    val maxHeight: Int,
    val maxFps: Int
)

/** Asks the device's H.264 encoders what they can really do. */
object EncoderCapabilities {
    private const val MIME = "video/avc"

    private val h264Encoders: List<MediaCodecInfo> by lazy {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { info -> info.isEncoder && info.supportedTypes.any { it.equals(MIME, true) } }
    }

    private val videoCaps: List<MediaCodecInfo.VideoCapabilities> by lazy {
        h264Encoders.mapNotNull { info ->
            runCatching { info.getCapabilitiesForType(MIME).videoCapabilities }.getOrNull()
        }
    }

    fun supports(width: Int, height: Int, fps: Int): Boolean = videoCaps.any {
        it.areSizeAndRateSupported(width, height, fps.toDouble()) ||
            it.areSizeAndRateSupported(height, width, fps.toDouble())
    }

    /** One entry per H.264 encoder on this device, for the Settings screen. */
    fun encoders(): List<EncoderInfo> = h264Encoders.mapNotNull { info ->
        val caps = runCatching { info.getCapabilitiesForType(MIME).videoCapabilities }.getOrNull()
            ?: return@mapNotNull null
        val hardware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.isHardwareAccelerated
        } else {
            !(info.name.startsWith("OMX.google.") || info.name.startsWith("c2.android."))
        }
        EncoderInfo(
            name = info.name,
            hardware = hardware,
            maxWidth = caps.supportedWidths.upper,
            maxHeight = caps.supportedHeights.upper,
            maxFps = caps.supportedFrameRates.upper
        )
    }
}
