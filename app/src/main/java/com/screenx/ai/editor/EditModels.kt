package com.screenx.ai.editor

import android.net.Uri

/** Center-crop presets. [ratio] is width / height of the final video (after rotation). */
enum class CropPreset(val ratio: Float?) {
    ORIGINAL(null),
    SQUARE(1f),
    WIDE(16f / 9f),
    TALL(9f / 16f),
    CLASSIC(4f / 3f),
    PORTRAIT(3f / 4f)
}

/** [shortSide] is the target size of the shorter side in pixels (0 = keep the original size). */
enum class ExportQuality(val shortSide: Int, val bitrate: Int) {
    LOW(480, 2_000_000),
    MEDIUM(720, 5_000_000),
    HIGH(1080, 10_000_000),
    ORIGINAL(0, 0)
}

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val progress: Int, val audioOnly: Boolean) : ExportState
    data class Done(val name: String, val uri: String?, val audioOnly: Boolean) : ExportState
    data class Failed(val message: String) : ExportState
}

/**
 * One clip on the timeline: a piece of the source video ([startMs]..[endMs] in source time)
 * with its own speed and volume. Clips play one after another in list order.
 */
data class Segment(
    val id: Long,
    val startMs: Long,
    val endMs: Long,
    val speed: Float = 1f,
    val volume: Float = 1f
) {
    val sourceMs: Long get() = endMs - startMs

    /** How long the clip lasts on the timeline (faster speed = shorter). */
    val timelineMs: Long get() = maxOf(1L, (sourceMs / speed).toLong())
}

/** One-tap looks. Built from the Media3 color effects in [EditEffects]. */
enum class FilterPreset { NONE, VIVID, WARM, COOL, VINTAGE, FADE, BW, NEGATIVE }

/** Manual color tuning. Every value is -1..1 and 0 means "unchanged". */
data class Adjust(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f
) {
    val isDefault: Boolean get() = brightness == 0f && contrast == 0f && saturation == 0f
}

/**
 * A text on top of the video (shown for the whole video).
 * [size] is the text height as a fraction of the frame height.
 * [x] and [y] place the center of the text on the frame: -1..1, y = 1 is the top.
 */
data class TextItem(
    val id: Long,
    val text: String = "",
    val color: Int = 0xFFFFFFFF.toInt(),
    val size: Float = 0.07f,
    val x: Float = 0f,
    val y: Float = 0.6f,
    val bold: Boolean = true
)

data class EditorUiState(
    val uri: Uri? = null,
    val title: String = "",
    val loading: Boolean = true,
    val error: Int? = null,
    /** Length of the source file. */
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val hasAudio: Boolean = false,
    val sourceBitrate: Int = 0,
    val segments: List<Segment> = emptyList(),
    val selectedId: Long? = null,
    /** Extra tracks below the main video (additional video, overlay, image, audio). */
    val tracks: List<Track> = emptyList(),
    val selectedClipId: Long? = null,
    val filter: FilterPreset = FilterPreset.NONE,
    val adjust: Adjust = Adjust(),
    val texts: List<TextItem> = emptyList(),
    val selectedTextId: Long? = null,
    val rotationCw: Int = 0,
    val crop: CropPreset = CropPreset.ORIGINAL,
    val quality: ExportQuality = ExportQuality.HIGH,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val export: ExportState = ExportState.Idle
)

/** Maps between the edited timeline (clips back to back) and the source video. */
object EditTimeline {
    class Located(val index: Int, val sourceMs: Long)

    fun total(segments: List<Segment>): Long {
        var sum = 0L
        for (s in segments) sum += s.timelineMs
        return sum
    }

    /** Where timeline time [timelineMs] is in the source video. Times past the end land on the last frame. */
    fun locate(segments: List<Segment>, timelineMs: Long): Located? {
        if (segments.isEmpty()) return null
        var acc = 0L
        for (i in segments.indices) {
            val s = segments[i]
            val len = s.timelineMs
            if (timelineMs < acc + len || i == segments.lastIndex) {
                val within = (timelineMs - acc).coerceIn(0L, len)
                val src = s.startMs + (within * s.speed).toLong()
                return Located(i, src.coerceIn(s.startMs, s.endMs))
            }
            acc += len
        }
        return null
    }

    /** Index of the clip that contains source time [sourceMs], or -1 when it is in a removed part. */
    fun indexAtSource(segments: List<Segment>, sourceMs: Long): Int {
        for (i in segments.indices) {
            val s = segments[i]
            if (sourceMs >= s.startMs && sourceMs < s.endMs) return i
        }
        return -1
    }

    /** Timeline time for source time [sourceMs]. A removed part maps to the next clip's start. */
    fun timelineOfSource(segments: List<Segment>, sourceMs: Long): Long {
        var acc = 0L
        for (s in segments) {
            if (sourceMs < s.startMs) return acc
            if (sourceMs <= s.endMs) {
                return acc + ((sourceMs - s.startMs) / s.speed).toLong()
            }
            acc += s.timelineMs
        }
        return acc
    }
}
