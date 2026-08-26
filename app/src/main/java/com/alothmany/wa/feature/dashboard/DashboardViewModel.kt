package com.alothmany.wa.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alothmany.wa.core.logging.AppLogger
import com.alothmany.wa.core.model.*
import com.alothmany.wa.data.local.dao.GroupDao
import com.alothmany.wa.data.local.dao.LinkDao
import com.alothmany.wa.data.local.dao.SourceDao
import com.alothmany.wa.data.repository.SettingsRepository
import com.alothmany.wa.system.integration.SystemIntegrationManager
import com.alothmany.wa.system.integration.SystemIntegrationState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private data class DashboardDataState(
    val preferences: AppPreferences = AppPreferences(),
    val sources: Int = 0,
    val groups: Int = 0,
    val communities: Int = 0,
    val links: Int = 0,
)

data class DashboardUiState(
    val preferences: AppPreferences = AppPreferences(),
    val sources: Int = 0,
    val groups: Int = 0,
    val communities: Int = 0,
    val links: Int = 0,
    val system: SystemIntegrationState = SystemIntegrationState(),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val settings: SettingsRepository,
    sourceDao: SourceDao,
    groupDao: GroupDao,
    linkDao: LinkDao,
    private val logger: AppLogger,
    private val systemIntegration: SystemIntegrationManager,
) : ViewModel() {

    private val dataState: Flow<DashboardDataState> = combine(
        settings.preferences,
        sourceDao.observeCount(),
        groupDao.observeGroupCount(),
        groupDao.observeCommunityCount(),
        linkDao.observeCount(),
    ) { prefs, sources, groups, communities, links ->
        DashboardDataState(prefs, sources, groups, communities, links)
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        dataState,
        systemIntegration.state,
    ) { data, system ->
        DashboardUiState(
            preferences = data.preferences,
            sources = data.sources,
            groups = data.groups,
            communities = data.communities,
            links = data.links,
            system = system,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    init {
        systemIntegration.initialize()
        systemIntegration.refresh()
    }

    fun refreshSystem() = systemIntegration.refresh()

    fun setSource(source: WhatsAppSourceType) = viewModelScope.launch {
        if (source !in systemIntegration.state.value.availableSourceTypes) {
            logger.warning("SETTINGS", "Unavailable WhatsApp source selected: ${source.name}")
            return@launch
        }
        settings.setSource(source)
        logger.info("SETTINGS", "WhatsApp source: ${source.name}")
    }

    fun setSpeed(value: Float) = viewModelScope.launch { settings.setNavigationSpeed(value) }
    fun setWait(value: Float) = viewModelScope.launch { settings.setWaitSeconds(value) }
    fun setTurbo(value: Boolean) = viewModelScope.launch { settings.setSuperTurbo(value) }
    fun setSkip(value: Boolean) = viewModelScope.launch { settings.setSkipNonEssential(value) }
    fun setSmartRead(value: Boolean) = viewModelScope.launch { settings.setSmartLinkRead(value) }
}
