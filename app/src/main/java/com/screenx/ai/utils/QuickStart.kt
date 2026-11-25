package com.screenx.ai.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** "Start recording now" requests from the quick settings tile and the reminder notification. */
object QuickStart {
    private val _request = MutableStateFlow(false)
    val request: StateFlow<Boolean> = _request.asStateFlow()

    fun request() {
        _request.value = true
    }

    fun consume() {
        _request.value = false
    }
}
