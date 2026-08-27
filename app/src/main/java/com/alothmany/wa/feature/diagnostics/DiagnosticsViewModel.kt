package com.alothmany.wa.feature.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alothmany.wa.feature.sync.engine.WhatsAppGroupParser
import com.alothmany.wa.feature.sync.engine.WhatsAppScreenDetector
import com.alothmany.wa.system.accessibility.WhatsAppUiBridge
import com.alothmany.wa.system.integration.SystemIntegrationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class CaptureDiagnostics(
    val packageName: String = "-",
    val surface: String = "-",
    val chatRows: Int = 0,
    val groupCandidates: Int = 0,
    val capturedAt: Long = 0L,
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val systemIntegration: SystemIntegrationManager,
    private val parser: WhatsAppGroupParser,
    private val screenDetector: WhatsAppScreenDetector,
) : ViewModel() {
    val state = systemIntegration.state

    val capture = WhatsAppUiBridge.latest
        .map { snapshot ->
            if (snapshot == null) {
                CaptureDiagnostics()
            } else {
                val parsed = parser.parse(snapshot)
                CaptureDiagnostics(
                    packageName = snapshot.packageName,
                    surface = screenDetector.classify(snapshot, parsed).name,
                    chatRows = parsed.chatRowCount,
                    groupCandidates = parsed.groups.size,
                    capturedAt = snapshot.capturedAt,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CaptureDiagnostics())

    init {
        systemIntegration.initialize()
        systemIntegration.refresh()
    }

    fun refresh() {
        systemIntegration.refresh()
        WhatsAppUiBridge.captureNow()
    }
}
