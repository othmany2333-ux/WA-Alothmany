package com.alothmany.wa.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alothmany.wa.core.logging.AppLogger
import com.alothmany.wa.core.model.*
import com.alothmany.wa.data.repository.SettingsRepository
import com.alothmany.wa.system.integration.SystemIntegrationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val logger: AppLogger,
    private val systemIntegration: SystemIntegrationManager,
) : ViewModel() {
    val preferences = repo.preferences.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppPreferences(),
    )
    val systemState = systemIntegration.state

    init {
        systemIntegration.initialize()
        systemIntegration.refresh()
    }

    private fun log() = logger.info("SETTINGS", "Settings updated")
    fun language(v: AppLanguage) = viewModelScope.launch { repo.setLanguage(v); log() }
    fun theme(v: AppTheme) = viewModelScope.launch { repo.setTheme(v); log() }
    fun performance(v: PerformanceMode) = viewModelScope.launch { repo.setPerformance(v); log() }
    fun autoResume(v: Boolean) = viewModelScope.launch { repo.setAutoResume(v); log() }
    fun notifications(v: Boolean) = viewModelScope.launch { repo.setNotifications(v); log() }
    fun syncArchived(v: Boolean) = viewModelScope.launch { repo.setSyncArchived(v); log() }
    fun syncCommunities(v: Boolean) = viewModelScope.launch { repo.setSyncCommunities(v); log() }
    fun saveProgress(v: Boolean) = viewModelScope.launch { repo.setSaveProgress(v); log() }

    fun configureShizuku() = systemIntegration.configureShizuku()
    fun configureAccessibility() = systemIntegration.configureAccessibility()
    fun toggleOverlay() = systemIntegration.toggleOverlay()
    fun probeSources() = systemIntegration.probeSources()
    fun refreshSystem() = systemIntegration.refresh()
}
