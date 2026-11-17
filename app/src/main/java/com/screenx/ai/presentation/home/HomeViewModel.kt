package com.screenx.ai.presentation.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.screenx.ai.ScreenXApp
import com.screenx.ai.domain.AudioSource
import com.screenx.ai.domain.CameraLens
import com.screenx.ai.domain.FacecamConfig
import com.screenx.ai.domain.FacecamShape
import com.screenx.ai.domain.FloatingConfig
import com.screenx.ai.domain.QualityOptions
import com.screenx.ai.domain.RecordingConfig
import com.screenx.ai.domain.RecordingState
import com.screenx.ai.recording.ConfigResolver
import com.screenx.ai.recording.RecordingSession
import com.screenx.ai.storage.StorageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val freeBytes: Long = 0L,
    val totalBytes: Long = 0L
) {
    val usedFraction: Float
        get() = if (totalBytes > 0) 1f - (freeBytes.toFloat() / totalBytes.toFloat()) else 0f
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = (application as ScreenXApp).settingsRepository

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** Live session state written by RecordingService. */
    val recording: StateFlow<RecordingState> = RecordingSession.state

    val config: StateFlow<RecordingConfig> = settings.recordingConfig
        .stateIn(viewModelScope, SharingStarted.Eagerly, RecordingConfig())

    val floating: StateFlow<FloatingConfig> = settings.floatingConfig
        .stateIn(viewModelScope, SharingStarted.Eagerly, FloatingConfig())

    val facecam: StateFlow<FacecamConfig> = settings.facecamConfig
        .stateIn(viewModelScope, SharingStarted.Eagerly, FacecamConfig())

    val drawingEnabled: StateFlow<Boolean> = settings.drawingEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Resolutions, frame rates and audio sources this device supports. */
    val options: StateFlow<QualityOptions> = config
        .map { ConfigResolver.options(getApplication(), it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, QualityOptions())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val info = StorageInfo.read()
            _state.value = HomeUiState(info.freeBytes, info.totalBytes)
        }
    }

    fun setResolution(p: Int) {
        viewModelScope.launch { settings.setResolution(p) }
    }

    fun setFps(fps: Int) {
        viewModelScope.launch { settings.setFps(fps) }
    }

    fun setAudioSource(source: AudioSource) {
        viewModelScope.launch { settings.setAudioSource(source) }
    }

    fun setAudioBitrate(kbps: Int) {
        viewModelScope.launch { settings.setAudioBitrateKbps(kbps) }
    }

    fun setFloatingEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setFloatingEnabled(enabled) }
    }

    fun setFloatingSize(level: Int) {
        viewModelScope.launch { settings.setFloatingSize(level) }
    }

    fun setFloatingOpacity(percent: Int) {
        viewModelScope.launch { settings.setFloatingOpacity(percent) }
    }

    fun setDrawingEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setDrawingEnabled(enabled) }
    }

    fun setFacecamEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setFacecamEnabled(enabled) }
    }

    fun setFacecamLens(lens: CameraLens) {
        viewModelScope.launch { settings.setFacecamLens(lens) }
    }

    fun setFacecamShape(shape: FacecamShape) {
        viewModelScope.launch { settings.setFacecamShape(shape) }
    }

    fun setFacecamSize(level: Int) {
        viewModelScope.launch { settings.setFacecamSize(level) }
    }

    fun dismissSaved() = RecordingSession.dismissSaved()
}
