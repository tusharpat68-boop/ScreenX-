package com.screenx.ai.recording

import com.screenx.ai.domain.RecordingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Single source of truth for the current recording session.
 * The service writes to it; ViewModels and the UI read from it.
 */
object RecordingSession {
    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    fun update(transform: (RecordingState) -> RecordingState) = _state.update(transform)

    /** Back to idle, keeping the "last saved" card. */
    fun reset() {
        _state.update {
            RecordingState(lastSavedName = it.lastSavedName, lastSavedUri = it.lastSavedUri)
        }
    }

    fun saved(name: String, uri: String?, notice: String?) {
        _state.value = RecordingState(lastSavedName = name, lastSavedUri = uri, notice = notice)
    }

    /** Called when the media scanner finishes (Android 8-9). */
    fun updateSavedUri(name: String, uri: String?) {
        _state.update { if (it.lastSavedName == name) it.copy(lastSavedUri = uri) else it }
    }

    fun dismissSaved() {
        _state.update { it.copy(lastSavedName = null, lastSavedUri = null, notice = null) }
    }

    fun fail(message: String) {
        _state.value = RecordingState(error = message)
    }
}
