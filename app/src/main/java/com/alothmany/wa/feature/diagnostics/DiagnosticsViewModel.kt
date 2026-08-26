package com.alothmany.wa.feature.diagnostics

import androidx.lifecycle.ViewModel
import com.alothmany.wa.system.integration.SystemIntegrationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val systemIntegration: SystemIntegrationManager,
) : ViewModel() {
    val state = systemIntegration.state

    init {
        systemIntegration.initialize()
        systemIntegration.refresh()
    }

    fun refresh() = systemIntegration.refresh()
}
