package com.screenx.ai.domain

/**
 * Validates recording preferences before Android encoders and audio capture are created.
 *
 * Settings may outlive app versions or be restored from backup, so the recording pipeline
 * must not assume that persisted values are still among the current UI choices.
 */
object RecordingConfigValidator {
    fun errors(config: RecordingConfig): List<String> = buildList {
        if (config.resolutionP !in RecordingConfig.RESOLUTIONS) {
            add("Unsupported video resolution: ${config.resolutionP}")
        }
        if (config.fps !in RecordingConfig.FPS_OPTIONS) {
            add("Unsupported frame rate: ${config.fps}")
        }
        if (config.bitrateKbps < 0 || config.bitrateKbps > 100_000) {
            add("Video bitrate must be between 0 and 100000 Kbps")
        }
        if (config.audioSource != AudioSource.NONE &&
            config.audioBitrateKbps !in RecordingConfig.AUDIO_BITRATES
        ) {
            add("Unsupported audio bitrate: ${config.audioBitrateKbps}")
        }
    }

    fun isValid(config: RecordingConfig): Boolean = errors(config).isEmpty()
}
