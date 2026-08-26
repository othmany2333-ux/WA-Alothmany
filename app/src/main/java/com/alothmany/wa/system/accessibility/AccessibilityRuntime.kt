package com.alothmany.wa.system.accessibility

import com.alothmany.wa.system.integration.AccessibilitySnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AccessibilityRuntime {
    private val _state = MutableStateFlow(AccessibilitySnapshot())
    val state: StateFlow<AccessibilitySnapshot> = _state.asStateFlow()

    fun connected() {
        _state.value = _state.value.copy(serviceConnected = true)
    }

    fun disconnected() {
        _state.value = _state.value.copy(serviceConnected = false)
    }

    fun event(packageName: String?, eventType: Int, nodeCount: Int) {
        _state.value = _state.value.copy(
            serviceConnected = true,
            lastPackage = packageName,
            lastEventType = eventType,
            nodeCount = nodeCount,
            lastEventAt = System.currentTimeMillis(),
        )
    }
}
