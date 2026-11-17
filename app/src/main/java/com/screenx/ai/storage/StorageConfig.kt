package com.screenx.ai.storage

/** The folder name (inside Movies, Pictures and Music) where ScreenX saves files. */
object StorageConfig {
    const val DEFAULT_FOLDER = "ScreenX"

    @Volatile
    var folder: String = DEFAULT_FOLDER

    /** Keeps letters, digits, space, underscore and dash. Falls back to the default. */
    fun sanitize(input: String): String {
        val clean = input.filter { it.isLetterOrDigit() || it == ' ' || it == '_' || it == '-' }
            .trim()
            .take(30)
        return clean.ifEmpty { DEFAULT_FOLDER }
    }
}
