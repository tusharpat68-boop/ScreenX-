package com.screenx.ai.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.screenx.ai.domain.AppearanceSettings
import com.screenx.ai.domain.AudioSource
import com.screenx.ai.domain.CameraLens
import com.screenx.ai.domain.FacecamConfig
import com.screenx.ai.domain.FacecamShape
import com.screenx.ai.domain.FloatingConfig
import com.screenx.ai.domain.GeneralSettings
import com.screenx.ai.domain.RecordingConfig
import com.screenx.ai.domain.ThemeMode
import com.screenx.ai.domain.VideoOrientation
import com.screenx.ai.storage.StorageConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    private object Keys {
        val RESOLUTION = intPreferencesKey("resolution_p")
        val FPS = intPreferencesKey("fps")
        val BITRATE_KBPS = intPreferencesKey("bitrate_kbps")
        val ORIENTATION = stringPreferencesKey("orientation")
        val AUDIO_SOURCE = stringPreferencesKey("audio_source")
        val AUDIO_KBPS = intPreferencesKey("audio_kbps")
        val COUNTDOWN = intPreferencesKey("countdown_sec")
        val AUTO_STOP = intPreferencesKey("auto_stop_min")

        val FLOAT_ENABLED = booleanPreferencesKey("float_enabled")
        val FLOAT_SIZE = intPreferencesKey("float_size")
        val FLOAT_OPACITY = intPreferencesKey("float_opacity")
        val FLOAT_X = intPreferencesKey("float_x")
        val FLOAT_Y = intPreferencesKey("float_y")
        val FLOAT_START_HIDDEN = booleanPreferencesKey("float_start_hidden")

        val CAM_ENABLED = booleanPreferencesKey("cam_enabled")
        val CAM_LENS = stringPreferencesKey("cam_lens")
        val CAM_SHAPE = stringPreferencesKey("cam_shape")
        val CAM_SIZE = intPreferencesKey("cam_size")
        val CAM_X = intPreferencesKey("cam_x")
        val CAM_Y = intPreferencesKey("cam_y")

        val DRAW_ENABLED = booleanPreferencesKey("draw_enabled")
        val FAVORITES = stringSetPreferencesKey("favorite_files")
        val SHOT_DELAY = intPreferencesKey("screenshot_delay_sec")

        val SAVE_FOLDER = stringPreferencesKey("save_folder")
        val EXPORT_QUALITY = stringPreferencesKey("export_quality")
        val WM_ENABLED = booleanPreferencesKey("wm_enabled")
        val WM_TEXT = stringPreferencesKey("wm_text")
        val WM_CORNER = intPreferencesKey("wm_corner")
        val HAPTICS = booleanPreferencesKey("haptics")
        val SOUND = booleanPreferencesKey("sound")
        val SOUND_VOLUME = intPreferencesKey("sound_volume")
        val NOTIF_PAUSE = booleanPreferencesKey("notif_pause")
        val SCHEDULED_AT = longPreferencesKey("scheduled_at")

        val THEME = stringPreferencesKey("theme_mode")
        val ACCENT = intPreferencesKey("accent_index")
    }

    val recordingConfig: Flow<RecordingConfig> = store.data.map { p ->
        RecordingConfig(
            resolutionP = p[Keys.RESOLUTION] ?: 720,
            fps = p[Keys.FPS] ?: 30,
            bitrateKbps = p[Keys.BITRATE_KBPS] ?: 0,
            orientation = runCatching { VideoOrientation.valueOf(p[Keys.ORIENTATION] ?: "AUTO") }
                .getOrDefault(VideoOrientation.AUTO),
            audioSource = runCatching { AudioSource.valueOf(p[Keys.AUDIO_SOURCE] ?: "NONE") }
                .getOrDefault(AudioSource.NONE),
            audioBitrateKbps = p[Keys.AUDIO_KBPS] ?: 128,
            countdownSec = p[Keys.COUNTDOWN] ?: 0,
            autoStopMin = p[Keys.AUTO_STOP] ?: 0
        )
    }

    val floatingConfig: Flow<FloatingConfig> = store.data.map { p ->
        FloatingConfig(
            enabled = p[Keys.FLOAT_ENABLED] ?: false,
            sizeLevel = p[Keys.FLOAT_SIZE] ?: 1,
            opacityPercent = p[Keys.FLOAT_OPACITY] ?: 100,
            x = p[Keys.FLOAT_X] ?: -1,
            y = p[Keys.FLOAT_Y] ?: -1,
            startHidden = p[Keys.FLOAT_START_HIDDEN] ?: false
        )
    }

    val facecamConfig: Flow<FacecamConfig> = store.data.map { p ->
        FacecamConfig(
            enabled = p[Keys.CAM_ENABLED] ?: false,
            lens = runCatching { CameraLens.valueOf(p[Keys.CAM_LENS] ?: "FRONT") }
                .getOrDefault(CameraLens.FRONT),
            shape = runCatching { FacecamShape.valueOf(p[Keys.CAM_SHAPE] ?: "CIRCLE") }
                .getOrDefault(FacecamShape.CIRCLE),
            sizeLevel = p[Keys.CAM_SIZE] ?: 1,
            x = p[Keys.CAM_X] ?: -1,
            y = p[Keys.CAM_Y] ?: -1
        )
    }

    val drawingEnabled: Flow<Boolean> = store.data.map { p -> p[Keys.DRAW_ENABLED] ?: false }

    val generalSettings: Flow<GeneralSettings> = store.data.map { p ->
        GeneralSettings(
            saveFolder = StorageConfig.sanitize(p[Keys.SAVE_FOLDER] ?: StorageConfig.DEFAULT_FOLDER),
            exportQuality = p[Keys.EXPORT_QUALITY] ?: "HIGH",
            watermarkEnabled = p[Keys.WM_ENABLED] ?: false,
            watermarkText = p[Keys.WM_TEXT] ?: "ScreenX AI",
            watermarkCorner = p[Keys.WM_CORNER] ?: 1,
            hapticsEnabled = p[Keys.HAPTICS] ?: true,
            soundEnabled = p[Keys.SOUND] ?: false,
            soundVolume = p[Keys.SOUND_VOLUME] ?: 70,
            notifPauseAction = p[Keys.NOTIF_PAUSE] ?: true,
            scheduledAtMs = p[Keys.SCHEDULED_AT] ?: 0L
        )
    }

    val appearance: Flow<AppearanceSettings> = store.data.map { p ->
        AppearanceSettings(
            themeMode = runCatching { ThemeMode.valueOf(p[Keys.THEME] ?: "SYSTEM") }
                .getOrDefault(ThemeMode.SYSTEM),
            accentIndex = p[Keys.ACCENT] ?: 0
        )
    }

    val favorites: Flow<Set<String>> = store.data.map { p -> p[Keys.FAVORITES] ?: emptySet() }
    val screenshotDelaySec: Flow<Int> = store.data.map { p -> p[Keys.SHOT_DELAY] ?: 3 }

    suspend fun setResolution(p: Int) { store.edit { it[Keys.RESOLUTION] = p } }
    suspend fun setFps(fps: Int) { store.edit { it[Keys.FPS] = fps } }
    suspend fun setBitrateKbps(kbps: Int) { store.edit { it[Keys.BITRATE_KBPS] = kbps } }
    suspend fun setOrientation(o: VideoOrientation) { store.edit { it[Keys.ORIENTATION] = o.name } }
    suspend fun setAudioSource(s: AudioSource) { store.edit { it[Keys.AUDIO_SOURCE] = s.name } }
    suspend fun setAudioBitrateKbps(kbps: Int) { store.edit { it[Keys.AUDIO_KBPS] = kbps } }
    suspend fun setCountdown(sec: Int) { store.edit { it[Keys.COUNTDOWN] = sec } }
    suspend fun setAutoStop(min: Int) { store.edit { it[Keys.AUTO_STOP] = min } }

    // ---- floating controls ----
    suspend fun setFloatingEnabled(enabled: Boolean) { store.edit { it[Keys.FLOAT_ENABLED] = enabled } }
    suspend fun setFloatingSize(level: Int) { store.edit { it[Keys.FLOAT_SIZE] = level } }
    suspend fun setFloatingOpacity(percent: Int) { store.edit { it[Keys.FLOAT_OPACITY] = percent } }
    suspend fun setFloatingStartHidden(hidden: Boolean) { store.edit { it[Keys.FLOAT_START_HIDDEN] = hidden } }
    suspend fun setFloatingPosition(x: Int, y: Int) {
        store.edit {
            it[Keys.FLOAT_X] = x
            it[Keys.FLOAT_Y] = y
        }
    }

    // ---- face camera ----
    suspend fun setFacecamEnabled(enabled: Boolean) { store.edit { it[Keys.CAM_ENABLED] = enabled } }
    suspend fun setFacecamLens(lens: CameraLens) { store.edit { it[Keys.CAM_LENS] = lens.name } }
    suspend fun setFacecamShape(shape: FacecamShape) { store.edit { it[Keys.CAM_SHAPE] = shape.name } }
    suspend fun setFacecamSize(level: Int) { store.edit { it[Keys.CAM_SIZE] = level } }
    suspend fun setFacecamPosition(x: Int, y: Int) {
        store.edit {
            it[Keys.CAM_X] = x
            it[Keys.CAM_Y] = y
        }
    }

    suspend fun setDrawingEnabled(enabled: Boolean) { store.edit { it[Keys.DRAW_ENABLED] = enabled } }

    // ---- library ----
    suspend fun setFavorite(fileName: String, favorite: Boolean) {
        store.edit {
            val current = it[Keys.FAVORITES] ?: emptySet()
            it[Keys.FAVORITES] = if (favorite) current + fileName else current - fileName
        }
    }

    suspend fun renameFavorite(oldName: String, newName: String) {
        store.edit {
            val current = it[Keys.FAVORITES] ?: emptySet()
            if (oldName in current) it[Keys.FAVORITES] = current - oldName + newName
        }
    }

    suspend fun setScreenshotDelay(seconds: Int) { store.edit { it[Keys.SHOT_DELAY] = seconds } }

    // ---- general ----
    suspend fun setSaveFolder(name: String) { store.edit { it[Keys.SAVE_FOLDER] = StorageConfig.sanitize(name) } }
    suspend fun setExportQuality(name: String) { store.edit { it[Keys.EXPORT_QUALITY] = name } }
    suspend fun setWatermarkEnabled(on: Boolean) { store.edit { it[Keys.WM_ENABLED] = on } }
    suspend fun setWatermarkText(text: String) { store.edit { it[Keys.WM_TEXT] = text.trim().take(40) } }
    suspend fun setWatermarkCorner(corner: Int) { store.edit { it[Keys.WM_CORNER] = corner } }
    suspend fun setHaptics(on: Boolean) { store.edit { it[Keys.HAPTICS] = on } }
    suspend fun setSound(on: Boolean) { store.edit { it[Keys.SOUND] = on } }
    suspend fun setSoundVolume(percent: Int) { store.edit { it[Keys.SOUND_VOLUME] = percent } }
    suspend fun setNotifPause(on: Boolean) { store.edit { it[Keys.NOTIF_PAUSE] = on } }
    suspend fun setScheduledAt(ms: Long) { store.edit { it[Keys.SCHEDULED_AT] = ms } }

    // ---- appearance ----
    suspend fun setThemeMode(mode: ThemeMode) { store.edit { it[Keys.THEME] = mode.name } }
    suspend fun setAccent(index: Int) { store.edit { it[Keys.ACCENT] = index } }
}
