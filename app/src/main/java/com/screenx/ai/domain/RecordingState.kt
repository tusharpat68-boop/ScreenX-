package com.screenx.ai.domain

enum class RecordingStatus { IDLE, STARTING, RECORDING, PAUSED, STOPPING }

data class RecordingState(
    val status: RecordingStatus = RecordingStatus.IDLE,
    val elapsedMs: Long = 0L,
    val error: String? = null,
    val notice: String? = null,
    val lastSavedName: String? = null,
    val lastSavedUri: String? = null,
    val countdownLeft: Int = 0
) {
    val isActive: Boolean get() = status != RecordingStatus.IDLE
}
