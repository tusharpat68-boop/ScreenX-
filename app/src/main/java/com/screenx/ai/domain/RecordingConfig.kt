package com.screenx.ai.domain

enum class VideoOrientation { AUTO, PORTRAIT, LANDSCAPE }

/**
 * User-chosen recording settings.
 * [resolutionP] is the short side in pixels (480, 720, 1080...). [bitrateKbps] 0 means automatic.
 * [countdownSec] and [autoStopMin] of 0 mean off.
 */
data class RecordingConfig(
    val resolutionP: Int = 720,
    val fps: Int = 30,
    val bitrateKbps: Int = 0,
    val orientation: VideoOrientation = VideoOrientation.AUTO,
    val audioSource: AudioSource = AudioSource.NONE,
    val audioBitrateKbps: Int = 128,
    val countdownSec: Int = 0,
    val autoStopMin: Int = 0
) {
    companion object {
        val RESOLUTIONS = listOf(480, 720, 1080, 1440, 2160)
        val FPS_OPTIONS = listOf(24, 30, 60)
        val AUDIO_BITRATES = listOf(64, 128, 192)
        val BITRATES_KBPS = listOf(0, 2000, 4000, 8000, 12000, 20000)
        val COUNTDOWNS = listOf(0, 3, 5, 10)
        val AUTO_STOPS = listOf(0, 1, 5, 10, 30, 60)
    }
}

/** Qualities this device and its encoder can actually do. */
data class QualityOptions(
    val resolutions: List<Int> = listOf(480, 720, 1080),
    val fps: List<Int> = listOf(24, 30, 60),
    val audioSources: List<AudioSource> = listOf(AudioSource.NONE, AudioSource.MICROPHONE)
)
