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

data class EditorUiState(
    val uri: Uri? = null,
    val title: String = "",
    val loading: Boolean = true,
    val error: Int? = null,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val hasAudio: Boolean = false,
    val sourceBitrate: Int = 0,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val rotationCw: Int = 0,
    val crop: CropPreset = CropPreset.ORIGINAL,
    val quality: ExportQuality = ExportQuality.HIGH,
    val export: ExportState = ExportState.Idle
)
