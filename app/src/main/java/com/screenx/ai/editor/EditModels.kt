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

/** One-tap looks. Built from the Media3 color effects in [EditEffects]. */
enum class FilterPreset {
    NONE, VIVID, WARM, COOL, VINTAGE, FADE, BW, NEGATIVE,
    CINEMATIC, SUNSET, MINT, NOIR, DRAMA, SOFT
}

/** Manual color tuning. Every value is -1..1 and 0 means "unchanged". */
data class Adjust(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    /** Cooler (-1) to warmer (+1). */
    val warmth: Float = 0f,
    /** Greener (-1) to more magenta (+1). */
    val tint: Float = 0f,
    /** Turns the colors around the color wheel: -1..1 = -180..180 degrees. */
    val hue: Float = 0f,
    /** How strongly the chosen filter is applied (0..1). It is not part of the manual tuning. */
    val filterStrength: Float = 1f
) {
    val isDefault: Boolean
        get() = brightness == 0f && contrast == 0f && saturation == 0f && warmth == 0f && tint == 0f && hue == 0f
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
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val crop: CropPreset = CropPreset.ORIGINAL,
    val quality: ExportQuality = ExportQuality.HIGH,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val export: ExportState = ExportState.Idle
)
