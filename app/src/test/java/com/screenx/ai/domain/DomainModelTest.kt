package com.screenx.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainModelTest {
    @Test
    fun audioSourceFlagsMatchSelectedSource() {
        assertFalse(AudioSource.NONE.usesMic)
        assertFalse(AudioSource.NONE.usesInternal)
        assertTrue(AudioSource.MICROPHONE.usesMic)
        assertFalse(AudioSource.MICROPHONE.usesInternal)
        assertFalse(AudioSource.INTERNAL.usesMic)
        assertTrue(AudioSource.INTERNAL.usesInternal)
        assertTrue(AudioSource.BOTH.usesMic)
        assertTrue(AudioSource.BOTH.usesInternal)
    }

    @Test
    fun recordingConfigDefaultsAreSafeAndSupportedPresets() {
        val config = RecordingConfig()
        assertEquals(720, config.resolutionP)
        assertEquals(30, config.fps)
        assertEquals(0, config.bitrateKbps) // automatic bitrate
        assertEquals(AudioSource.NONE, config.audioSource)
        assertTrue(RecordingConfig.RESOLUTIONS.contains(480))
        assertTrue(RecordingConfig.RESOLUTIONS.contains(720))
        assertTrue(RecordingConfig.RESOLUTIONS.contains(1080))
        assertTrue(RecordingConfig.FPS_OPTIONS.containsAll(listOf(24, 30, 60)))
    }

    @Test
    fun recordingStateIsActiveOnlyOutsideIdle() {
        assertFalse(RecordingState().isActive)
        listOf(
            RecordingStatus.STARTING,
            RecordingStatus.RECORDING,
            RecordingStatus.PAUSED,
            RecordingStatus.STOPPING
        ).forEach { status ->
            assertTrue("$status should be active", RecordingState(status = status).isActive)
        }
    }

    @Test
    fun overlaySettingRangesAreDefined() {
        assertEquals(listOf(0, 1, 2), FloatingConfig.SIZES)
        assertEquals(listOf(50, 75, 100), FloatingConfig.OPACITIES)
        assertEquals(listOf(0, 1, 2, 3), FacecamConfig.SIZES)
    }
    @Test
    fun qualityOptionsHaveNonEmptySafeDefaults() {
        val options = QualityOptions()
        assertTrue(options.resolutions.isNotEmpty())
        assertTrue(options.fps.isNotEmpty())
        assertTrue(options.audioSources.contains(AudioSource.NONE))
        assertTrue(options.audioSources.contains(AudioSource.MICROPHONE))
        assertTrue(options.resolutions.all { it > 0 })
        assertTrue(options.fps.all { it > 0 })
    }

    @Test
    fun recordingStateStartsWithNoElapsedTimeOrError() {
        val state = RecordingState()
        assertEquals(0L, state.elapsedMs)
        assertEquals(null, state.error)
        assertEquals(null, state.notice)
        assertFalse(state.isActive)
    }

}
