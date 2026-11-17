package com.screenx.ai.presentation.settings

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.screenx.ai.R
import com.screenx.ai.ScreenXApp
import com.screenx.ai.camera.CameraCapabilities
import com.screenx.ai.domain.AppearanceSettings
import com.screenx.ai.domain.AudioSource
import com.screenx.ai.domain.CameraLens
import com.screenx.ai.domain.FacecamConfig
import com.screenx.ai.domain.FacecamShape
import com.screenx.ai.domain.FloatingConfig
import com.screenx.ai.domain.GeneralSettings
import com.screenx.ai.domain.QualityOptions
import com.screenx.ai.domain.RecordingConfig
import com.screenx.ai.domain.ThemeMode
import com.screenx.ai.domain.VideoOrientation
import com.screenx.ai.recording.ConfigResolver
import com.screenx.ai.recording.EncoderCapabilities
import com.screenx.ai.schedule.ScheduleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val settings = (application as ScreenXApp).settingsRepository

    val config: StateFlow<RecordingConfig> = settings.recordingConfig
        .stateIn(viewModelScope, SharingStarted.Eagerly, RecordingConfig())
    val floating: StateFlow<FloatingConfig> = settings.floatingConfig
        .stateIn(viewModelScope, SharingStarted.Eagerly, FloatingConfig())
    val facecam: StateFlow<FacecamConfig> = settings.facecamConfig
        .stateIn(viewModelScope, SharingStarted.Eagerly, FacecamConfig())
    val drawingEnabled: StateFlow<Boolean> = settings.drawingEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val general: StateFlow<GeneralSettings> = settings.generalSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, GeneralSettings())
    val appearance: StateFlow<AppearanceSettings> = settings.appearance
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceSettings())

    val options: StateFlow<QualityOptions> = config
        .map { ConfigResolver.options(app, it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, QualityOptions())

    private val _capabilities = MutableStateFlow<List<String>>(emptyList())
    val capabilities: StateFlow<List<String>> = _capabilities.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.Default) { _capabilities.value = buildCapabilities() }
    }

    /** Plain-language summary of what this phone can do. */
    private fun buildCapabilities(): List<String> {
        val lines = ArrayList<String>()
        val yes = app.getString(R.string.cap_yes)
        val no = app.getString(R.string.cap_no)

        EncoderCapabilities.encoders().forEach { e ->
            lines.add(
                app.getString(
                    R.string.cap_encoder,
                    e.name,
                    app.getString(if (e.hardware) R.string.cap_hardware else R.string.cap_software),
                    e.maxWidth, e.maxHeight, e.maxFps
                )
            )
        }
        val resolutions = ConfigResolver.options(app, RecordingConfig()).resolutions
        lines.add(app.getString(R.string.cap_resolutions, resolutions.joinToString(", ") { "${it}p" }))

        lines.add(
            app.getString(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) R.string.cap_internal_yes
                else R.string.cap_internal_no
            )
        )
        val hasMic = app.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        lines.add(app.getString(R.string.cap_mic, if (hasMic) yes else no))

        val cameras = CameraCapabilities.available(app).joinToString(", ") {
            app.getString(if (it == CameraLens.FRONT) R.string.lens_front else R.string.lens_back)
        }
        lines.add(app.getString(R.string.cap_cameras, cameras.ifEmpty { app.getString(R.string.cap_none) }))
        lines.add(app.getString(R.string.cap_android, Build.VERSION.RELEASE, Build.VERSION.SDK_INT))
        return lines
    }

    // ---- recording ----
    fun setResolution(p: Int) = launch { settings.setResolution(p) }
    fun setFps(fps: Int) = launch { settings.setFps(fps) }
    fun setBitrate(kbps: Int) = launch { settings.setBitrateKbps(kbps) }
    fun setOrientation(o: VideoOrientation) = launch { settings.setOrientation(o) }
    fun setAudioSource(s: AudioSource) = launch { settings.setAudioSource(s) }
    fun setAudioBitrate(kbps: Int) = launch { settings.setAudioBitrateKbps(kbps) }
    fun setCountdown(sec: Int) = launch { settings.setCountdown(sec) }
    fun setAutoStop(min: Int) = launch { settings.setAutoStop(min) }

    // ---- floating controls ----
    fun setFloatingEnabled(on: Boolean) = launch { settings.setFloatingEnabled(on) }
    fun setFloatingSize(level: Int) = launch { settings.setFloatingSize(level) }
    fun setFloatingOpacity(percent: Int) = launch { settings.setFloatingOpacity(percent) }
    fun setFloatingStartHidden(hidden: Boolean) = launch { settings.setFloatingStartHidden(hidden) }
    fun resetFloatingPosition() = launch { settings.setFloatingPosition(-1, -1) }

    // ---- face camera ----
    fun setFacecamEnabled(on: Boolean) = launch { settings.setFacecamEnabled(on) }
    fun setFacecamLens(lens: CameraLens) = launch { settings.setFacecamLens(lens) }
    fun setFacecamShape(shape: FacecamShape) = launch { settings.setFacecamShape(shape) }
    fun setFacecamSize(level: Int) = launch { settings.setFacecamSize(level) }
    fun resetFacecamPosition() = launch { settings.setFacecamPosition(-1, -1) }

    fun setDrawingEnabled(on: Boolean) = launch { settings.setDrawingEnabled(on) }

    // ---- video and saving ----
    fun setExportQuality(name: String) = launch { settings.setExportQuality(name) }
    fun setSaveFolder(name: String) = launch { settings.setSaveFolder(name) }
    fun setWatermarkEnabled(on: Boolean) = launch { settings.setWatermarkEnabled(on) }
    fun setWatermarkText(text: String) = launch { settings.setWatermarkText(text) }
    fun setWatermarkCorner(corner: Int) = launch { settings.setWatermarkCorner(corner) }

    // ---- appearance ----
    fun setThemeMode(mode: ThemeMode) = launch { settings.setThemeMode(mode) }
    fun setAccent(index: Int) = launch { settings.setAccent(index) }

    // ---- feel and sound ----
    fun setHaptics(on: Boolean) = launch { settings.setHaptics(on) }
    fun setSound(on: Boolean) = launch { settings.setSound(on) }
    fun setSoundVolume(percent: Int) = launch { settings.setSoundVolume(percent) }
    fun setNotifPause(on: Boolean) = launch { settings.setNotifPause(on) }

    // ---- reminder ----
    fun scheduleReminder(minutes: Int) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        ScheduleManager.schedule(app, at)
        launch { settings.setScheduledAt(at) }
    }

    fun cancelReminder() {
        ScheduleManager.cancel(app)
        launch { settings.setScheduledAt(0L) }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
