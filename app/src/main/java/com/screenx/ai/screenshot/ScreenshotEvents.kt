package com.screenx.ai.screenshot

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lets the gallery know a new screenshot was saved. */
object ScreenshotEvents {
    private val _saved = MutableStateFlow(0)
    val saved: StateFlow<Int> = _saved.asStateFlow()

    fun notifySaved() {
        _saved.value = _saved.value + 1
    }
}
