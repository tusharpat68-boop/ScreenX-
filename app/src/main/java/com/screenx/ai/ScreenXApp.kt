package com.screenx.ai

import android.app.Application
import android.content.Context
import com.screenx.ai.data.SettingsRepository
import com.screenx.ai.presentation.theme.AccentState
import com.screenx.ai.recording.RecordingSession
import com.screenx.ai.storage.RecordingStorage
import com.screenx.ai.storage.StorageConfig
import com.screenx.ai.utils.LocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Application entry point and simple app-wide holder (manual dependency injection). */
class ScreenXApp : Application() {

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        // Keep the save folder and the accent color in sync with Settings.
        appScope.launch {
            settingsRepository.generalSettings.collect { StorageConfig.folder = it.saveFolder }
        }
        appScope.launch(Dispatchers.Main) {
            settingsRepository.appearance.collect { AccentState.apply(it.accentIndex) }
        }
        // Remove partial recordings left behind by a crash or a killed process.
        appScope.launch {
            if (!RecordingSession.state.value.isActive) {
                runCatching { RecordingStorage.cleanupIncomplete(this@ScreenXApp) }
            }
        }
    }
}
