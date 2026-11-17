package com.screenx.ai.utils

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * In-app language (English / Bengali). The choice is kept in SharedPreferences so it can be read
 * synchronously when an Activity or Service is created. An empty value means "system language".
 */
object LocaleHelper {
    private const val PREFS = "screenx_locale"
    private const val KEY = "language"

    /** The saved language tag: "", "en" or "bn". */
    fun get(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""

    /** Saves the language and updates the application's resources right away. */
    @Suppress("DEPRECATION")
    fun set(context: Context, language: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language).apply()
        val app = context.applicationContext
        val locale = if (language.isEmpty()) Locale.getDefault() else Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        val cfg = Configuration(app.resources.configuration)
        cfg.setLocale(locale)
        app.resources.updateConfiguration(cfg, app.resources.displayMetrics)
    }

    /** Use in attachBaseContext of the Application, Activity and Services. */
    fun wrap(base: Context): Context {
        val language = get(base)
        if (language.isEmpty()) return base
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(locale)
        return base.createConfigurationContext(cfg)
    }
}
