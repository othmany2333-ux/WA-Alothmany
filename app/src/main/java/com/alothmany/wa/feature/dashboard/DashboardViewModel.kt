package com.alothmany.wa.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alothmany.wa.core.logging.AppLogger
import com.alothmany.wa.core.model.*
import com.alothmany.wa.data.local.dao.GroupDao
import com.alothmany.wa.data.local.dao.LinkDao
import com.alothmany.wa.data.local.dao.SourceDao
import com.alothmany.wa.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val preferences: AppPreferences = AppPreferences(),
    val sources: Int = 0,
    val groups: Int = 0,
    val communities: Int = 0,
    val links: Int = 0,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val settings: SettingsRepository,
    sourceDao: SourceDao,
    groupDao: GroupDao,
    linkDao: LinkDao,
    private val logger: AppLogger,
) : ViewModel() {
    val uiState: StateFlow<DashboardUiState> = combine(
        settings.preferences,
        sourceDao.observeCount(),
        groupDao.observeGroupCount(),
        groupDao.observeCommunityCount(),
        linkDao.observeCount(),
    ) { prefs, sources, groups, communities, links ->
        DashboardUiState(prefs, sources, groups, communities, links)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun setSource(source: WhatsAppSourceType) = viewModelScope.launch {
        settings.setSource(source)
        logger.info("SETTINGS", "WhatsApp source: ${source.name}")
    }
    fun setSpeed(value: Float) = viewModelScope.launch { settings.setNavigationSpeed(value) }
    fun setWait(value: Float) = viewModelScope.launch { settings.setWaitSeconds(value) }
    fun setTurbo(value: Boolean) = viewModelScope.launch { settings.setSuperTurbo(value) }
    fun setSkip(value: Boolean) = viewModelScope.launch { settings.setSkipNonEssential(value) }
    fun setSmartRead(value: Boolean) = viewModelScope.launch { settings.setSmartLinkRead(value) }
}
