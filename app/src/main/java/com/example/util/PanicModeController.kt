package com.example.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PanicModeController {
    private val _isPanicActive = MutableStateFlow(false)
    val isPanicActive: StateFlow<Boolean> = _isPanicActive.asStateFlow()

    fun triggerPanic() {
        _isPanicActive.value = true
    }

    fun dismissPanic() {
        _isPanicActive.value = false
    }
}
