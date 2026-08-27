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

/**
 * Smart Sync v0.3.1
 *
 * Independent sync path:
 * 1) Open the selected original WhatsApp app.
 * 2) Recover to the Chats tab using conservative exact/prefix navigation.
 * 3) Rewind the normal chat list to the top and scan it to the end.
 * 4) Detect group rows from chat-row evidence without clicking the Groups search filter.
 * 5) If Archived exists, return to top, open Archived and scan it to the end.
 * 6) Persist detected group names to Room; the Sync UI observes Room and shows them.
 *
 * No extract/publish/join/delete engine is invoked from this class.
 */
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
        private const val MAX_CHAT_HOME_RECOVERY_ATTEMPTS = 7

        private val CHAT_TAB_LABELS = setOf(
            "Chats", "Chat", "المحادثات", "الدردشات", "محادثات", "دردشات"
        )
        private val ARCHIVED_LABELS = setOf(
            "Archived", "Archived chats", "مؤرشفة", "المؤرشفة", "المؤرشف", "الدردشات المؤرشفة"
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(SyncRuntimeState())
    val state: StateFlow<SyncRuntimeState> = _state.asStateFlow()

    private val pauseRequested = MutableStateFlow(false)
    private val stopRequested = AtomicBoolean(false)
    private var runJob: Job? = null

    private data class RunAccumulator(
        val existingIds: Set<String>,
        val existingMeta: Map<String, GroupSyncMetaEntity>,
        val seenIds: LinkedHashSet<String> = linkedSetOf(),
        var newCount: Int = 0,
        var processedScreens: Int = 0,
    )

    private data class SectionResult(
        val lastSnapshot: WhatsAppUiSnapshot,
        val sawArchivedEntry: Boolean,
    )

    fun start() {
        if (runJob?.isActive == true) return
        stopRequested.set(false)
        pauseRequested.value = false
        runJob = scope.launch { executeRun() }
    }

    fun pause() {
        if (!_state.value.running) return
        pauseRequested.value = true
        _state.update {
            it.copy(
                status = SyncEngineStatus.PAUSED,
                message = "تم إيقاف المزامنة مؤقتًا",
                updatedAt = System.currentTimeMillis(),
            )
        }
        persistRuntimeAsync()
        logger.info("SYNC", "Smart Sync paused")
    }

    fun resume() {
        if (_state.value.status != SyncEngineStatus.PAUSED) return
        pauseRequested.value = false
        _state.update {
            it.copy(
                status = SyncEngineStatus.RECOVERING,
                message = "جاري استكمال المزامنة",
                updatedAt = System.currentTimeMillis(),
            )
        }
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
                    message = "جاري تجهيز المزامنة",
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
            if (!integrationState.accessibility.enabled ||
                !integrationState.accessibility.serviceConnected ||
                !WhatsAppUiBridge.serviceConnected()
            ) {
                fail("خدمة Accessibility غير متصلة")
                return
            }

            val source = integrationState.sources.firstOrNull { it.sourceType == prefs.selectedSource }
                ?: integrationState.sources.firstOrNull()
            if (source == null) {
                fail("لم يتم العثور على مصدر واتساب متاح")
                return
            }
            sourceId = source.id
            if (!source.launchable) {
                fail("مصدر واتساب المحدد غير قابل للفتح مباشرة في هذا الملف الشخصي")
                return
            }

            updateState(
                _state.value.copy(
                    sourceId = source.id,
                    sourceName = source.displayName,
                    status = SyncEngineStatus.OPENING_WHATSAPP,
                    stage = SyncStage.NORMAL_GROUPS,
                    message = "جاري فتح واتساب",
                    updatedAt = System.currentTimeMillis(),
                )
            )
            upsertRun()

            val launchIntent = context.packageManager.getLaunchIntentForPackage(source.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            if (launchIntent == null) {
                fail("تعذر الحصول على أمر فتح واتساب")
                return
            }
            context.startActivity(launchIntent)
            logger.info("SYNC", "Opened ${source.packageName} for independent chat-list sync")

            val initialSnapshot = awaitSnapshot(
                packageName = source.packageName,
                after = System.currentTimeMillis() - 500,
                timeoutMs = 5_000,
            )
            if (initialSnapshot == null) {
                fail("واجهة واتساب غير ظاهرة لخدمة Accessibility")
                return
            }

            updateStatus(SyncEngineStatus.NAVIGATING, "جاري الانتقال إلى الدردشات")
            var snapshot = ensureChatsHome(source.packageName, initialSnapshot)
            if (snapshot == null) {
                fail("تعذر الوصول إلى شاشة الدردشات بأمان")
                return
            }

            updateStatus(SyncEngineStatus.RECOVERING, "جاري الرجوع إلى بداية المحادثات")
            snapshot = rewindToTop(source.packageName, snapshot)

            val existingMeta = metaDao.getForSource(source.id).associateBy { it.groupId }
            val accumulator = RunAccumulator(
                existingIds = existingMeta.keys,
                existingMeta = existingMeta,
            )

            updateStage(SyncStage.NORMAL_GROUPS, SyncEngineStatus.SCANNING, "جاري قراءة المحادثات العادية")
            val normalResult = scanSection(
                sourceId = source.id,
                packageName = source.packageName,
                runId = runId,
                initialSnapshot = snapshot,
                archived = false,
                accumulator = accumulator,
            )

            if (stopRequested.get()) {
                finishStopped()
                return
            }

            if (normalResult.sawArchivedEntry) {
                updateStage(SyncStage.ARCHIVED, SyncEngineStatus.RECOVERING, "تم العثور على المؤرشف - جاري فتحه")
                val topSnapshot = rewindToTop(source.packageName, normalResult.lastSnapshot)
                val beforeOpen = topSnapshot.capturedAt
                val opened = WhatsAppUiBridge.clickSafeMatching(ARCHIVED_LABELS)
                if (!opened) {
                    fail("تم اكتشاف المؤرشف ولكن تعذر فتحه بأمان")
                    return
                }

                val archivedSnapshot = awaitSnapshot(source.packageName, beforeOpen, 2_800)
                    ?: WhatsAppUiBridge.latest.value?.takeIf { it.packageName == source.packageName }
                if (archivedSnapshot == null) {
                    fail("تم فتح المؤرشف ولكن تعذر قراءة واجهته")
                    return
                }

                val archivedTop = rewindToTop(source.packageName, archivedSnapshot)
                updateStage(SyncStage.ARCHIVED, SyncEngineStatus.SCANNING, "جاري قراءة المحادثات المؤرشفة")
                scanSection(
                    sourceId = source.id,
                    packageName = source.packageName,
                    runId = runId,
                    initialSnapshot = archivedTop,
                    archived = true,
                    accumulator = accumulator,
                )
            } else {
                logger.info("SYNC", "No Archived entry detected; archived scan skipped")
            }

            if (stopRequested.get()) {
                finishStopped()
                return
            }
            if (accumulator.processedScreens >= MAX_SCREENS_PER_RUN) {
                fail("تم بلوغ حد الأمان قبل تأكيد نهاية القوائم")
                return
            }

            updateStage(SyncStage.FINAL_VERIFY, SyncEngineStatus.VERIFYING_END, "تم تأكيد نهاية القوائم")

            // Only a fully completed normal + archived scan is allowed to age missing groups.
            metaDao.markMissingAfterSuccessfulRun(source.id, runId)
            metaDao.promoteVerifiedMissingToDeleted(source.id)

            val completedAt = System.currentTimeMillis()
            _state.update {
                it.copy(
                    status = SyncEngineStatus.COMPLETED,
                    stage = SyncStage.SAVING,
                    discoveredCount = accumulator.seenIds.size,
                    newCount = accumulator.newCount,
                    processedScreens = accumulator.processedScreens,
                    consecutiveEndPasses = END_CONFIRMATION_PASSES,
                    message = "اكتملت مزامنة القروبات",
                    updatedAt = completedAt,
                )
            }
            upsertRun(completedAt = completedAt)
            checkpointDao.delete(runId)
            logger.success(
                "SYNC",
                "Independent Smart Sync completed: ${accumulator.seenIds.size} group(s), ${accumulator.newCount} new"
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            fail(t.message ?: t.javaClass.simpleName)
        } finally {
            if (_state.value.status !in setOf(
                    SyncEngineStatus.COMPLETED,
                    SyncEngineStatus.STOPPED,
                    SyncEngineStatus.ERROR,
                )
            ) {
                sourceId?.let { logger.warning("SYNC", "Sync run ended without terminal state for $it") }
            }
        }
    }

    /**
     * Recover from Search, an open chat, Updates/Channels, or another restored
     * WhatsApp surface. We never search for or click the Groups filter here.
     */
    private suspend fun ensureChatsHome(
        packageName: String,
        initial: WhatsAppUiSnapshot,
    ): WhatsAppUiSnapshot? {
        var current = initial
        repeat(MAX_CHAT_HOME_RECOVERY_ATTEMPTS) {
            if (stopRequested.get()) return null
            awaitResumeIfPaused()

            val before = current.capturedAt
            if (WhatsAppUiBridge.clickSafeMatching(CHAT_TAB_LABELS)) {
                delay(120)
                val changed = awaitSnapshot(packageName, before, 1_500)
                val latest = WhatsAppUiBridge.latest.value
                return changed
                    ?: latest?.takeIf { it.packageName == packageName }
                    ?: current
            }

            if (!WhatsAppUiBridge.performBack()) return null
            current = awaitSnapshot(packageName, before, 1_500)
                ?: WhatsAppUiBridge.latest.value?.takeIf { it.packageName == packageName }
                ?: current
        }
        return null
    }

    private suspend fun scanSection(
        sourceId: String,
        packageName: String,
        runId: String,
        initialSnapshot: WhatsAppUiSnapshot,
        archived: Boolean,
        accumulator: RunAccumulator,
    ): SectionResult {
        var snapshot = initialSnapshot
        var endPasses = 0
        var eventTimeoutMs = 1_250L
        var sawArchivedEntry = false

        while (!stopRequested.get() && accumulator.processedScreens < MAX_SCREENS_PER_RUN) {
            awaitResumeIfPaused()
            if (stopRequested.get()) break

            val parsed = parser.parse(snapshot)
            accumulator.processedScreens++
            if (!archived && parsed.hasArchivedEntry) sawArchivedEntry = true

            val now = System.currentTimeMillis()
            val groupEntities = ArrayList<GroupEntity>(parsed.groups.size)
            val metaEntities = ArrayList<GroupSyncMetaEntity>(parsed.groups.size)
            var discoveredThisScreen = 0
            var newInDatabaseThisScreen = 0
            var lastName: String? = null

            parsed.groups.forEach { candidate ->
                val groupId = stableGroupId(sourceId, candidate.normalizedName)
                val firstTimeThisRun = accumulator.seenIds.add(groupId)
                if (firstTimeThisRun) discoveredThisScreen++
                if (firstTimeThisRun && groupId !in accumulator.existingIds) {
                    accumulator.newCount++
                    newInDatabaseThisScreen++
                }
                lastName = candidate.displayName
                val oldMeta = accumulator.existingMeta[groupId]

                groupEntities += GroupEntity(
                    id = groupId,
                    sourceId = sourceId,
                    displayName = candidate.displayName,
                    archived = archived,
                    isCommunity = false,
                    memberCount = null,
                    status = "AVAILABLE",
                    fingerprint = candidate.rowFingerprint,
                    lastSyncedAt = now,
                )
                metaEntities += GroupSyncMetaEntity(
                    groupId = groupId,
                    sourceId = sourceId,
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
                    stage = if (archived) SyncStage.ARCHIVED else SyncStage.NORMAL_GROUPS,
                    discoveredCount = accumulator.seenIds.size,
                    newCount = accumulator.newCount,
                    processedScreens = accumulator.processedScreens,
                    currentGroupName = lastName ?: it.currentGroupName,
                    consecutiveEndPasses = endPasses,
                    lastScreenFingerprint = currentFingerprint,
                    message = when {
                        discoveredThisScreen > 0 && archived -> "تم اكتشاف $discoveredThisScreen قروب مؤرشف"
                        discoveredThisScreen > 0 -> "تم اكتشاف $discoveredThisScreen قروب في هذه الشاشة"
                        newInDatabaseThisScreen > 0 -> "تم العثور على $newInDatabaseThisScreen قروب جديد"
                        else -> "جاري قراءة بقية المحادثات"
                    },
                    updatedAt = now,
                )
            }
            saveCheckpoint()
            upsertRun()

            val beforeScrollAt = snapshot.capturedAt
            val scrollStartedAt = System.currentTimeMillis()
            val scrollAccepted = WhatsAppUiBridge.scrollPrimaryListForward()
            val nextSnapshot = if (scrollAccepted) {
                awaitChangedSnapshot(
                    packageName = packageName,
                    after = beforeScrollAt,
                    currentFingerprint = currentFingerprint,
                    timeoutMs = eventTimeoutMs,
                )
            } else {
                null
            }

            val changed = nextSnapshot != null
            if (!scrollAccepted || !changed) {
                endPasses++
                _state.update {
                    it.copy(
                        status = SyncEngineStatus.VERIFYING_END,
                        consecutiveEndPasses = endPasses,
                        message = "جاري التحقق من نهاية القائمة $endPasses/$END_CONFIRMATION_PASSES",
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
                eventTimeoutMs = (latency * 2.10).roundToLong().coerceIn(600L, 2_200L)
                snapshot = nextSnapshot
            } else {
                eventTimeoutMs = (eventTimeoutMs + 220).coerceAtMost(2_200L)
            }
        }

        return SectionResult(
            lastSnapshot = snapshot,
            sawArchivedEntry = sawArchivedEntry,
        )
    }

    private suspend fun awaitResumeIfPaused() {
        if (!pauseRequested.value) return
        _state.update {
            it.copy(
                status = SyncEngineStatus.PAUSED,
                message = "المزامنة متوقفة مؤقتًا وتم حفظ التقدم",
                updatedAt = System.currentTimeMillis(),
            )
        }
        pauseRequested.first { paused -> !paused || stopRequested.get() }
        if (!stopRequested.get()) {
            updateStatus(SyncEngineStatus.RECOVERING, "جاري الاستكمال من نقطة الحفظ")
        }
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
            val accepted = WhatsAppUiBridge.scrollPrimaryListBackward()
            val previous = if (accepted) {
                awaitChangedSnapshot(
                    packageName = packageName,
                    after = before,
                    currentFingerprint = fingerprint,
                    timeoutMs = 850L,
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

    private suspend fun awaitSnapshot(
        packageName: String,
        after: Long,
        timeoutMs: Long,
    ): WhatsAppUiSnapshot? {
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
                message = "تم إيقاف المزامنة مع الاحتفاظ بنقطة الاستكمال",
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

    private fun updateStage(stage: SyncStage, status: SyncEngineStatus, message: String) {
        _state.update {
            it.copy(
                stage = stage,
                status = status,
                message = message,
                updatedAt = System.currentTimeMillis(),
            )
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
