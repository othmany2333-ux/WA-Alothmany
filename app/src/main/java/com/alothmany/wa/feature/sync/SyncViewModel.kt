package com.alothmany.wa.feature.sync

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alothmany.wa.core.model.AppPreferences
import com.alothmany.wa.data.local.dao.GroupDao
import com.alothmany.wa.data.local.dao.GroupSyncMetaDao
import com.alothmany.wa.data.local.entity.GroupEntity
import com.alothmany.wa.data.local.entity.GroupSyncMetaEntity
import com.alothmany.wa.data.repository.SettingsRepository
import com.alothmany.wa.feature.sync.engine.SmartSyncEngine
import com.alothmany.wa.feature.sync.model.ContactSyncMode
import com.alothmany.wa.feature.sync.model.GroupSelectionKind
import com.alothmany.wa.feature.sync.model.SyncEngineStatus
import com.alothmany.wa.feature.sync.model.SyncRuntimeState
import com.alothmany.wa.feature.sync.service.SmartSyncService
import com.alothmany.wa.system.integration.SystemIntegrationManager
import com.alothmany.wa.system.integration.SystemIntegrationState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private data class SyncBaseState(
    val groups: List<GroupEntity>,
    val meta: List<GroupSyncMetaEntity>,
    val runtime: SyncRuntimeState,
    val preferences: AppPreferences,
    val system: SystemIntegrationState,
)

data class SyncGroupUiItem(
    val id: String,
    val name: String,
    val unread: Boolean,
    val active: Boolean,
    val locked: Boolean,
    val deleted: Boolean,
    val community: Boolean,
    val confidence: String,
    val fingerprint: String,
    val selected: Boolean,
)

data class SyncUiState(
    val groups: List<SyncGroupUiItem> = emptyList(),
    val totalMatching: Int = 0,
    val visibleLimit: Int = 4,
    val search: String = "",
    val selectedIds: Set<String> = emptySet(),
    val contactMode: ContactSyncMode = ContactSyncMode.UNSAVED_WHATSAPP_NUMBERS,
    val runtime: SyncRuntimeState = SyncRuntimeState(),
    val sourceId: String? = null,
    val sourceName: String? = null,
    val accessibilityReady: Boolean = false,
    val canStart: Boolean = false,
) {
    val hasMore: Boolean get() = totalMatching > groups.size
    val selectedCount: Int get() = selectedIds.size
}

@HiltViewModel
class SyncViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val groupDao: GroupDao,
    private val metaDao: GroupSyncMetaDao,
    private val settings: SettingsRepository,
    private val integration: SystemIntegrationManager,
    private val engine: SmartSyncEngine,
) : ViewModel() {
    private val search = MutableStateFlow("")
    private val visibleLimit = MutableStateFlow(4)
    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val contactMode = MutableStateFlow(ContactSyncMode.UNSAVED_WHATSAPP_NUMBERS)

    private val base: Flow<SyncBaseState> = combine(
        groupDao.observeAll(),
        metaDao.observeAll(),
        engine.state,
        settings.preferences,
        integration.state,
    ) { groups, meta, runtime, preferences, system ->
        SyncBaseState(groups, meta, runtime, preferences, system)
    }

    val uiState: StateFlow<SyncUiState> = combine(
        base,
        search,
        visibleLimit,
        selectedIds,
        contactMode,
    ) { baseState, query, limit, selection, contacts ->
        val source = baseState.system.sources.firstOrNull { it.sourceType == baseState.preferences.selectedSource }
            ?: baseState.system.sources.firstOrNull()
        val sourceId = source?.id
        val metaMap = baseState.meta.associateBy { it.groupId }

        val sourceGroups = if (sourceId == null) emptyList() else baseState.groups.filter { it.sourceId == sourceId }
        val normalizedQuery = query.trim().lowercase()
        val matching = sourceGroups
            .asSequence()
            .filter { group -> normalizedQuery.isBlank() || group.displayName.lowercase().contains(normalizedQuery) }
            .sortedWith(compareByDescending<GroupEntity> { metaMap[it.id]?.isActive == true }.thenBy { it.displayName.lowercase() })
            .toList()

        val visible = matching.take(limit).map { group ->
            val meta = metaMap[group.id]
            SyncGroupUiItem(
                id = group.id,
                name = group.displayName,
                unread = meta?.isUnread == true,
                active = meta?.isActive != false && meta?.isDeleted != true,
                locked = meta?.isLocked == true,
                deleted = meta?.isDeleted == true,
                community = meta?.isCommunity == true || group.isCommunity,
                confidence = meta?.confidence ?: "UNKNOWN",
                fingerprint = group.fingerprint,
                selected = group.id in selection,
            )
        }

        SyncUiState(
            groups = visible,
            totalMatching = matching.size,
            visibleLimit = limit,
            search = query,
            selectedIds = selection,
            contactMode = contacts,
            runtime = baseState.runtime,
            sourceId = sourceId,
            sourceName = source?.displayName,
            accessibilityReady = baseState.system.accessibility.enabled || baseState.system.accessibility.serviceConnected,
            canStart = source?.launchable == true &&
                (baseState.system.accessibility.enabled || baseState.system.accessibility.serviceConnected),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState())

    init {
        integration.initialize()
        integration.refresh()
        integration.probeSources()
    }

    fun search(value: String) {
        search.value = value
        visibleLimit.value = 4
    }

    fun showMore() {
        visibleLimit.value = (visibleLimit.value + 20).coerceAtMost(2000)
    }

    fun collapsePreview() {
        visibleLimit.value = 4
    }

    fun toggleGroup(id: String) {
        selectedIds.value = selectedIds.value.toMutableSet().apply {
            if (!add(id)) remove(id)
        }
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun select(kind: GroupSelectionKind) = viewModelScope.launch {
        val current = uiState.value
        val allVisibleForSource = combineGroupSnapshot(current.sourceId)
        selectedIds.value = when (kind) {
            GroupSelectionKind.ALL -> allVisibleForSource.mapTo(linkedSetOf()) { it.first.id }
            GroupSelectionKind.UNREAD -> allVisibleForSource.filter { it.second?.isUnread == true }.mapTo(linkedSetOf()) { it.first.id }
            GroupSelectionKind.ACTIVE -> allVisibleForSource.filter { it.second?.isActive != false && it.second?.isDeleted != true }.mapTo(linkedSetOf()) { it.first.id }
            GroupSelectionKind.LOCKED -> allVisibleForSource.filter { it.second?.isLocked == true }.mapTo(linkedSetOf()) { it.first.id }
            GroupSelectionKind.DELETED -> allVisibleForSource.filter { it.second?.isDeleted == true }.mapTo(linkedSetOf()) { it.first.id }
            GroupSelectionKind.COMMUNITIES -> allVisibleForSource.filter { it.first.isCommunity || it.second?.isCommunity == true }.mapTo(linkedSetOf()) { it.first.id }
        }
    }

    fun setContactMode(mode: ContactSyncMode) {
        contactMode.value = mode
    }

    fun startSync() {
        ContextCompat.startForegroundService(
            context,
            SmartSyncService.intent(context, SmartSyncService.ACTION_START),
        )
    }

    fun pauseOrResume() {
        val action = if (uiState.value.runtime.status == SyncEngineStatus.PAUSED) {
            SmartSyncService.ACTION_RESUME
        } else {
            SmartSyncService.ACTION_PAUSE
        }
        context.startService(SmartSyncService.intent(context, action))
    }

    fun stopSync() {
        context.startService(SmartSyncService.intent(context, SmartSyncService.ACTION_STOP))
    }

    private suspend fun combineGroupSnapshot(sourceId: String?): List<Pair<GroupEntity, GroupSyncMetaEntity?>> {
        if (sourceId == null) return emptyList()
        val groups = groupSnapshot.first().filter { it.sourceId == sourceId }
        val meta = metaSnapshot.first().associateBy { it.groupId }
        return groups.map { it to meta[it.id] }
    }

    private val groupSnapshot = groupDao.observeAll()
    private val metaSnapshot = metaDao.observeAll()
}
