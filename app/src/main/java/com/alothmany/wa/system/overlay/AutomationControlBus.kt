package com.alothmany.wa.system.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AutomationControlState(
    val paused: Boolean = false,
    val stopRequestId: Long = 0L,
)

object AutomationControlBus {
    private val _state = MutableStateFlow(AutomationControlState())
    val state: StateFlow<AutomationControlState> = _state.asStateFlow()

    fun togglePause() {
        _state.value = _state.value.copy(paused = !_state.value.paused)
    }

    fun requestStop() {
        _state.value = AutomationControlState(
            paused = false,
            stopRequestId = System.currentTimeMillis(),
        )
    }
}
