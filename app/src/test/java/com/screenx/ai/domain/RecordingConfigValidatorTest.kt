package com.screenx.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingConfigValidatorTest {
    @Test
    fun defaultConfigIsValid() {
        assertTrue(RecordingConfigValidator.errors(RecordingConfig()).isEmpty())
    }

    @Test
    fun supportedBoundaryValuesAreAccepted() {
        RecordingConfig.RESOLUTIONS.forEach { resolution ->
            assertTrue(
                "resolution $resolution",
                RecordingConfigValidator.isValid(RecordingConfig(resolutionP = resolution))
            )
        }
        RecordingConfig.FPS_OPTIONS.forEach { fps ->
            assertTrue("fps $fps", RecordingConfigValidator.isValid(RecordingConfig(fps = fps)))
        }
        RecordingConfig.AUDIO_BITRATES.forEach { bitrate ->
            assertTrue(
                "audio bitrate $bitrate",
                RecordingConfigValidator.isValid(
                    RecordingConfig(audioSource = AudioSource.MICROPHONE, audioBitrateKbps = bitrate)
                )
            )
        }
    }

    @Test
    fun rejectsUnsupportedResolutionAndFrameRate() {
        val errors = RecordingConfigValidator.errors(
            RecordingConfig(resolutionP = 999, fps = 120)
        )
        assertEquals(2, errors.size)
        assertTrue(errors.any { it.contains("resolution") })
        assertTrue(errors.any { it.contains("frame rate") })
    }

    @Test
    fun rejectsNegativeAndExcessiveVideoBitrate() {
        assertFalse(RecordingConfigValidator.isValid(RecordingConfig(bitrateKbps = -1)))
        assertFalse(RecordingConfigValidator.isValid(RecordingConfig(bitrateKbps = 100_001)))
        assertTrue(RecordingConfigValidator.isValid(RecordingConfig(bitrateKbps = 0)))
        assertTrue(RecordingConfigValidator.isValid(RecordingConfig(bitrateKbps = 100_000)))
    }

    @Test
    fun audioBitrateIsCheckedOnlyWhenAudioIsEnabled() {
        assertTrue(
            RecordingConfigValidator.isValid(
                RecordingConfig(audioSource = AudioSource.NONE, audioBitrateKbps = 777)
            )
        )
        assertFalse(
            RecordingConfigValidator.isValid(
                RecordingConfig(audioSource = AudioSource.BOTH, audioBitrateKbps = 777)
            )
        )
    }

    @Test
    fun reportsEveryInvalidPersistedValueTogether() {
        val errors = RecordingConfigValidator.errors(
            RecordingConfig(
                resolutionP = -1,
                fps = 0,
                bitrateKbps = -20,
                audioSource = AudioSource.MICROPHONE,
                audioBitrateKbps = 1
            )
        )
        assertEquals(4, errors.size)
    }
}
