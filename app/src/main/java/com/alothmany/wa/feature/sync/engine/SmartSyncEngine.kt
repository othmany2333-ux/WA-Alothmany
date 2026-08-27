package com.alothmany.wa.feature.sync.engine

import android.content.Context
import android.content.Intent
import com.alothmany.wa.core.logging.AppLogger
import com.alothmany.wa.data.local.dao.GroupDao
import com.alothmany.wa.data.local.dao.GroupSyncMetaDao
import com.alothmany.wa.data.local.dao.SyncCheckpointDao
import com.alothmany.wa.data.local.dao.SyncRunDao
import com.alothmany.wa.data.local.entity.GroupEntity
import com.alothmany.wa.data.local.entity.GroupSyncMetaEntity
import com.alothmany.wa.data.local.entity.SyncCheckpointEntity
import com.alothmany.wa.data.local.entity.SyncRunEntity
import com.alothmany.wa.data.repository.SettingsRepository
import com.alothmany.wa.feature.sync.model.SyncEngineStatus
import com.alothmany.wa.feature.sync.model.SyncRuntimeState
import com.alothmany.wa.feature.sync.model.SyncStage
import com.alothmany.wa.system.accessibility.WhatsAppUiBridge
import com.alothmany.wa.system.accessibility.WhatsAppUiSnapshot
import com.alothmany.wa.system.integration.SystemIntegrationManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

@Singleton
class SmartSyncEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val integration: SystemIntegrationManager,
    private val groupDao: GroupDao,
    private val metaDao: GroupSyncMetaDao,
    private val runDao: SyncRunDao,
    private val checkpointDao: SyncCheckpointDao,
    private val parser: WhatsAppGroupParser,
    private val logger: AppLogger,
) {
    companion object {
        private const val MAX_SCREENS_PER_RUN = 3500
        private const val END_CONFIRMATION_PASSES = 2
        private val GROUP_FILTER_LABELS = setOf(
            "Groups", "Group", "المجموعات", "القروبات", "مجموعات", "قروبات"
        )
        private val CHAT_TAB_LABELS = setOf(
            "Chats", "Chat", "المحادثات", "الدردشات", "محادثات"
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(SyncRuntimeState())
    val state: StateFlow<SyncRuntimeState> = _state.asStateFlow()

    private val pauseRequested = MutableStateFlow(false)
    private val stopRequested = AtomicBoolean(false)
    private var runJob: Job? = null

    fun start() {
        if (runJob?.isActive == true) return
        stopRequested.set(false)
        pauseRequested.value = false
        runJob = scope.launch { executeRun() }
    }

    fun pause() {
        if (!_state.value.running) return
        pauseRequested.value = true
        _state.update { it.copy(status = SyncEngineStatus.PAUSED, message = "Paused", updatedAt = System.currentTimeMillis()) }
        persistRuntimeAsync()
        logger.info("SYNC", "Smart Sync paused")
    }

    fun resume() {
        if (_state.value.status != SyncEngineStatus.PAUSED) return
        pauseRequested.value = false
        _state.update { it.copy(status = SyncEngineStatus.RECOVERING, message = "Resuming", updatedAt = System.currentTimeMillis()) }
        logger.info("SYNC", "Smart Sync resumed")
    }

    fun stop() {
        stopRequested.set(true)
        pauseRequested.value = false
        logger.warning("SYNC", "Smart Sync stop requested")
    }

    private suspend fun executeRun() {
        val startedAt = System.currentTimeMillis()
        val runId = UUID.randomUUID().toString()
        var sourceId: String? = null
        try {
            updateState(
                SyncRuntimeState(
                    runId = runId,
                    status = SyncEngineStatus.PREPARING,
                    stage = SyncStage.NORMAL_GROUPS,
                    message = "Preparing capabilities",
                    startedAt = startedAt,
                    updatedAt = startedAt,
                )
            )

            integration.initialize()
            integration.refresh()
            integration.probeSources()
            withTimeoutOrNull(3_500) {
                integration.state.first { !it.probing && it.sources.isNotEmpty() }
            }

            val prefs = settings.preferences.first()
            val integrationState = integration.state.value
            if (!integrationState.accessibility.enabled || !integrationState.accessibility.serviceConnected || !WhatsAppUiBridge.serviceConnected()) {
                fail("Accessibility service is not connected")
                return
            }

            val source = integrationState.sources.firstOrNull { it.sourceType == prefs.selectedSource }
                ?: integrationState.sources.firstOrNull()
            if (source == null) {
                fail("No accessible WhatsApp source was detected")
                return
            }
            sourceId = source.id
            if (!source.launchable) {
                fail("Selected source is detected but is not directly launchable in this Android profile yet")
                return
            }

            updateState(
                _state.value.copy(
                    sourceId = source.id,
                    sourceName = source.displayName,
                    status = SyncEngineStatus.OPENING_WHATSAPP,
                    message = "Opening WhatsApp",
                    updatedAt = System.currentTimeMillis(),
                )
            )
            upsertRun()

            val launchIntent = context.packageManager.getLaunchIntentForPackage(source.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            if (launchIntent == null) {
                fail("WhatsApp launch intent is unavailable")
                return
            }
            context.startActivity(launchIntent)
            logger.info("SYNC", "Opened ${source.packageName} for group sync")

            val initialSnapshot = awaitSnapshot(
                packageName = source.packageName,
                after = System.currentTimeMillis() - 500,
                timeoutMs = 5_000,
            )
            if (initialSnapshot == null) {
                fail("WhatsApp UI was not visible to Accessibility")
                return
            }
            var snapshot: WhatsAppUiSnapshot = initialSnapshot

            updateStatus(SyncEngineStatus.NAVIGATING, "Opening Groups filter")
            var groupsReady = false
            var navigationAttempts = 0
            while (!groupsReady && navigationAttempts < 5) {
                navigationAttempts++
                if (WhatsAppUiBridge.clickFirstMatching(GROUP_FILTER_LABELS)) {
                    snapshot = awaitSnapshot(source.packageName, snapshot.capturedAt, 2_800) ?: snapshot
                    groupsReady = true
                    break
                }

                // Recover from an open chat / Calls / Updates screen without blind tapping.
                val before = snapshot.capturedAt
                val movedToChats = WhatsAppUiBridge.clickFirstMatching(CHAT_TAB_LABELS)
                if (!movedToChats) WhatsAppUiBridge.performBack()
                snapshot = awaitSnapshot(source.packageName, before, 1_800) ?: snapshot
            }
            if (!groupsReady) {
                fail("Could not locate the Groups filter after safe navigation recovery")
                return
            }

            // Always align the list to its beginning before collecting rows. This is
            // essential when WhatsApp restores a previous scroll position. Two
            // no-change passes are required so a delayed UI frame is not mistaken for top.
            updateStatus(SyncEngineStatus.RECOVERING, "Aligning to first group")
            snapshot = rewindToTop(source.packageName, snapshot)

            updateStatus(SyncEngineStatus.SCANNING, "Scanning groups")
            logger.info("SYNC", "Group list scan started")

            val existingMeta = metaDao.getForSource(source.id).associateBy { it.groupId }
            val existingIds = existingMeta.keys
            val seenIds = linkedSetOf<String>()
            var newCount = 0
            var processedScreens = 0
            var endPasses = 0
            var previousScreenFingerprint: String? = null
            var eventTimeoutMs = 1_350L

            while (!stopRequested.get() && processedScreens < MAX_SCREENS_PER_RUN) {
                awaitResumeIfPaused()
                if (stopRequested.get()) break

                val parsed = parser.parse(snapshot)
                processedScreens++
                val now = System.currentTimeMillis()
                val groupEntities = ArrayList<GroupEntity>(parsed.groups.size)
                val metaEntities = ArrayList<GroupSyncMetaEntity>(parsed.groups.size)
                var discoveredThisScreen = 0
                var newInDatabaseThisScreen = 0
                var lastName: String? = null

                parsed.groups.forEach { candidate ->
                    val groupId = stableGroupId(source.id, candidate.normalizedName)
                    val firstTimeThisRun = seenIds.add(groupId)
                    if (firstTimeThisRun) discoveredThisScreen++
                    if (firstTimeThisRun && groupId !in existingIds) {
                        newCount++
                        newInDatabaseThisScreen++
                    }
                    lastName = candidate.displayName
                    val oldMeta = existingMeta[groupId]
                    groupEntities += GroupEntity(
                        id = groupId,
                        sourceId = source.id,
                        displayName = candidate.displayName,
                        archived = false,
                        isCommunity = false,
                        memberCount = null,
                        status = "AVAILABLE",
                        fingerprint = candidate.rowFingerprint,
                        lastSyncedAt = now,
                    )
                    metaEntities += GroupSyncMetaEntity(
                        groupId = groupId,
                        sourceId = source.id,
                        isUnread = candidate.isUnread,
                        isActive = true,
                        isLocked = candidate.isLocked,
                        isDeleted = false,
                        isCommunity = false,
                        confidence = candidate.confidence,
                        lastSeenRunId = runId,
                        missingStreak = 0,
                        firstSeenAt = oldMeta?.firstSeenAt ?: now,
                        lastSeenAt = now,
                    )
                }

                if (groupEntities.isNotEmpty()) {
                    groupDao.upsertAll(groupEntities)
                    metaDao.upsertAll(metaEntities)
                }

                val currentFingerprint = parsed.screenFingerprint
                _state.update {
                    it.copy(
                        status = SyncEngineStatus.SCANNING,
                        discoveredCount = seenIds.size,
                        newCount = newCount,
                        processedScreens = processedScreens,
                        currentGroupName = lastName ?: it.currentGroupName,
                        consecutiveEndPasses = endPasses,
                        lastScreenFingerprint = currentFingerprint,
                        message = when {
                            discoveredThisScreen > 0 -> "Discovered $discoveredThisScreen group(s) on this screen"
                            newInDatabaseThisScreen > 0 -> "Found $newInDatabaseThisScreen new group(s)"
                            else -> "Checking for more groups"
                        },
                        updatedAt = now,
                    )
                }
                saveCheckpoint()
                upsertRun()

                val beforeScrollAt = snapshot.capturedAt
                val scrollStartedAt = System.currentTimeMillis()
                val scrollAccepted = WhatsAppUiBridge.scrollForward()
                val nextSnapshot = if (scrollAccepted) {
                    awaitChangedSnapshot(
                        packageName = source.packageName,
                        after = beforeScrollAt,
                        currentFingerprint = currentFingerprint,
                        timeoutMs = eventTimeoutMs,
                    )
                } else {
                    null
                }

                val nextFingerprint = nextSnapshot?.let(parser::parse)?.screenFingerprint
                val changed = nextSnapshot != null && nextFingerprint != currentFingerprint
                val noNewOnScreen = discoveredThisScreen == 0

                if (!scrollAccepted || (noNewOnScreen && !changed && currentFingerprint == previousScreenFingerprint)) {
                    endPasses++
                    _state.update {
                        it.copy(
                            status = SyncEngineStatus.VERIFYING_END,
                            consecutiveEndPasses = endPasses,
                            message = "Verifying end $endPasses/$END_CONFIRMATION_PASSES",
                            updatedAt = System.currentTimeMillis(),
                        )
                    }
                    saveCheckpoint()
                    if (endPasses >= END_CONFIRMATION_PASSES) break
                    delay(180)
                } else {
                    endPasses = 0
                }

                if (nextSnapshot != null) {
                    val latency = (System.currentTimeMillis() - scrollStartedAt).coerceAtLeast(120)
                    eventTimeoutMs = (latency * 2.15).roundToLong().coerceIn(650L, 2_200L)
                    snapshot = nextSnapshot
                } else {
                    eventTimeoutMs = (eventTimeoutMs + 250).coerceAtMost(2_200L)
                }
                previousScreenFingerprint = currentFingerprint
            }

            if (stopRequested.get()) {
                finishStopped()
                return
            }
            if (processedScreens >= MAX_SCREENS_PER_RUN) {
                fail("Safety limit reached before the end of the group list was confirmed")
                return
            }

            updateStatus(SyncEngineStatus.VERIFYING_END, "End confirmed twice")
            // Only a fully completed scan is allowed to age missing groups. Two completed
            // runs are required before an item becomes 'deleted'. This prevents false loss.
            metaDao.markMissingAfterSuccessfulRun(source.id, runId)
            metaDao.promoteVerifiedMissingToDeleted(source.id)

            val completedAt = System.currentTimeMillis()
            _state.update {
                it.copy(
                    status = SyncEngineStatus.COMPLETED,
                    stage = SyncStage.SAVING,
                    consecutiveEndPasses = END_CONFIRMATION_PASSES,
                    message = "Group synchronization completed",
                    updatedAt = completedAt,
                )
            }
            upsertRun(completedAt = completedAt)
            checkpointDao.delete(runId)
            logger.success("SYNC", "Smart Sync completed: ${_state.value.discoveredCount} group(s), ${_state.value.newCount} new")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            fail(t.message ?: t.javaClass.simpleName)
        } finally {
            if (_state.value.status !in setOf(SyncEngineStatus.COMPLETED, SyncEngineStatus.STOPPED, SyncEngineStatus.ERROR)) {
                sourceId?.let { logger.warning("SYNC", "Sync run ended without terminal state for $it") }
            }
        }
    }

    private suspend fun awaitResumeIfPaused() {
        if (!pauseRequested.value) return
        _state.update { it.copy(status = SyncEngineStatus.PAUSED, message = "Paused safely", updatedAt = System.currentTimeMillis()) }
        pauseRequested.first { paused -> !paused || stopRequested.get() }
        if (!stopRequested.get()) updateStatus(SyncEngineStatus.RECOVERING, "Resuming from checkpoint")
    }


    private suspend fun rewindToTop(
        packageName: String,
        initial: WhatsAppUiSnapshot,
    ): WhatsAppUiSnapshot {
        var current = initial
        var noChangePasses = 0
        var attempts = 0
        while (!stopRequested.get() && noChangePasses < END_CONFIRMATION_PASSES && attempts < 1200) {
            awaitResumeIfPaused()
            if (stopRequested.get()) break
            attempts++
            val fingerprint = parser.parse(current).screenFingerprint
            val before = current.capturedAt
            val accepted = WhatsAppUiBridge.scrollBackward()
            val previous = if (accepted) {
                awaitChangedSnapshot(
                    packageName = packageName,
                    after = before,
                    currentFingerprint = fingerprint,
                    timeoutMs = 900L,
                )
            } else {
                null
            }
            if (previous == null) {
                noChangePasses++
                delay(100)
            } else {
                current = previous
                noChangePasses = 0
            }
        }
        return current
    }

    private suspend fun awaitChangedSnapshot(
        packageName: String,
        after: Long,
        currentFingerprint: String,
        timeoutMs: Long,
    ): WhatsAppUiSnapshot? {
        val latest = WhatsAppUiBridge.latest.value
        if (latest != null && latest.packageName == packageName && latest.capturedAt > after) {
            if (parser.parse(latest).screenFingerprint != currentFingerprint) return latest
        }
        return withTimeoutOrNull(timeoutMs) {
            WhatsAppUiBridge.events.first { candidate ->
                candidate.packageName == packageName &&
                    candidate.capturedAt > after &&
                    parser.parse(candidate).screenFingerprint != currentFingerprint
            }
        }
    }

    private suspend fun awaitSnapshot(packageName: String, after: Long, timeoutMs: Long): WhatsAppUiSnapshot? {
        val latest = WhatsAppUiBridge.latest.value
        if (latest != null && latest.packageName == packageName && latest.capturedAt > after) return latest
        return withTimeoutOrNull(timeoutMs) {
            WhatsAppUiBridge.events.first { snapshot ->
                snapshot.packageName == packageName && snapshot.capturedAt > after
            }
        }
    }

    private suspend fun saveCheckpoint() {
        val state = _state.value
        val runId = state.runId ?: return
        val sourceId = state.sourceId ?: return
        checkpointDao.upsert(
            SyncCheckpointEntity(
                runId = runId,
                sourceId = sourceId,
                stage = state.stage.name,
                lastAnchor = state.currentGroupName,
                lastScreenFingerprint = state.lastScreenFingerprint,
                consecutiveEndPasses = state.consecutiveEndPasses,
                processedScreens = state.processedScreens,
                discoveredCount = state.discoveredCount,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    private fun persistRuntimeAsync() {
        scope.launch {
            runCatching { saveCheckpoint(); upsertRun() }
        }
    }

    private suspend fun upsertRun(completedAt: Long? = null) {
        val state = _state.value
        val runId = state.runId ?: return
        val sourceId = state.sourceId ?: return
        runDao.upsert(
            SyncRunEntity(
                id = runId,
                sourceId = sourceId,
                status = state.status.name,
                stage = state.stage.name,
                discoveredCount = state.discoveredCount,
                newCount = state.newCount,
                processedScreens = state.processedScreens,
                currentGroupName = state.currentGroupName,
                errorMessage = state.errorMessage,
                startedAt = state.startedAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                completedAt = completedAt,
            )
        )
    }

    private suspend fun finishStopped() {
        val now = System.currentTimeMillis()
        _state.update {
            it.copy(
                status = SyncEngineStatus.STOPPED,
                message = "Stopped; checkpoint retained",
                updatedAt = now,
            )
        }
        saveCheckpoint()
        upsertRun()
        logger.warning("SYNC", "Smart Sync stopped with checkpoint retained")
    }

    private suspend fun fail(message: String) {
        val now = System.currentTimeMillis()
        _state.update {
            it.copy(
                status = SyncEngineStatus.ERROR,
                errorMessage = message,
                message = message,
                updatedAt = now,
            )
        }
        runCatching { saveCheckpoint(); upsertRun() }
        logger.error("SYNC", message)
    }

    private fun updateStatus(status: SyncEngineStatus, message: String) {
        _state.update {
            it.copy(status = status, message = message, updatedAt = System.currentTimeMillis())
        }
    }

    private fun updateState(newState: SyncRuntimeState) {
        _state.value = newState
    }

    private fun stableGroupId(sourceId: String, normalizedName: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$sourceId|$normalizedName".toByteArray(Charsets.UTF_8))
        return "grp_" + digest.take(12).joinToString("") { "%02x".format(it) }
    }
}
