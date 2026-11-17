package com.screenx.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.screenx.ai.domain.AppearanceSettings
import com.screenx.ai.domain.ThemeMode
import com.screenx.ai.presentation.ScreenXRoot
import com.screenx.ai.presentation.theme.ScreenXTheme
import com.screenx.ai.utils.LocaleHelper
import com.screenx.ai.utils.QuickStart

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        val settings = (application as ScreenXApp).settingsRepository
        setContent {
            val appearance by settings.appearance.collectAsStateWithLifecycle(AppearanceSettings())
            val dark = when (appearance.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            // Keep the status and navigation bar icons readable in the chosen theme.
            val view = LocalView.current
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
            ScreenXTheme(darkTheme = dark) {
                ScreenXRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_QUICK_RECORD, false) == true) {
            intent.removeExtra(EXTRA_QUICK_RECORD)
            QuickStart.request()
        }
    }

    companion object {
        const val EXTRA_QUICK_RECORD = "extra_quick_record"
    }
}
