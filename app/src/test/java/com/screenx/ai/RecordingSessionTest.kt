package com.screenx.ai

import com.screenx.ai.domain.RecordingStatus
import com.screenx.ai.recording.RecordingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecordingSessionTest {
    @Before fun clean() {
        RecordingSession.reset()
        RecordingSession.dismissSaved()
    }

    @Test fun startsIdle() {
        val s = RecordingSession.state.value
        assertEquals(RecordingStatus.IDLE, s.status)
        assertFalse(s.isActive)
    }

    @Test fun activeWhenRecording() {
        RecordingSession.update { it.copy(status = RecordingStatus.RECORDING) }
        assertTrue(RecordingSession.state.value.isActive)
    }

    @Test fun savedKeepsTheCardAfterReset() {
        RecordingSession.saved("a.mp4", "content://x/1", null)
        RecordingSession.update { it.copy(status = RecordingStatus.RECORDING) }
        RecordingSession.reset()
        val s = RecordingSession.state.value
        assertEquals(RecordingStatus.IDLE, s.status)
        assertEquals("a.mp4", s.lastSavedName)
    }

    @Test fun scanResultOnlyUpdatesMatchingName() {
        RecordingSession.saved("a.mp4", null, null)
        RecordingSession.updateSavedUri("other.mp4", "content://x/2")
        assertNull(RecordingSession.state.value.lastSavedUri)
        RecordingSession.updateSavedUri("a.mp4", "content://x/1")
        assertEquals("content://x/1", RecordingSession.state.value.lastSavedUri)
    }

    @Test fun failureClearsStateAndShowsError() {
        RecordingSession.fail("boom")
        val s = RecordingSession.state.value
        assertEquals("boom", s.error)
        assertFalse(s.isActive)
    }

    @Test fun dismissRemovesSavedCard() {
        RecordingSession.saved("a.mp4", "u", "n")
        RecordingSession.dismissSaved()
        assertNull(RecordingSession.state.value.lastSavedName)
        assertNotNull(RecordingSession.state)
    }
}
