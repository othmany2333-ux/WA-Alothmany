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
 * Smart Sync v0.3.7 CLEAN
 *
 * One job only:
 * WhatsApp -> Chats -> scan to end -> Archived (if present) -> scan to end
 * -> persist detected groups -> expose them to the Sync UI.
 *
 * It intentionally DOES NOT invoke Groups filter, Channels, extraction,
 * publishing, joining, deleting, communities, or contact-sync engines.
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
    private val screenDetector: WhatsAppScreenDetector,
    private val logger: AppLogger,
) {
    companion object {
        private const val MAX_SCREENS_PER_RUN = 3500
        private const val END_CONFIRMATION_PASSES = 2
        private const val MAX_CHAT_HOME_RECOVERY_ATTEMPTS = 7

        private val CHAT_TAB_LABELS = setOf(
            "Chats", "Chat", "الدردشات", "المحادثات", "دردشات", "محادثات"
        )

        private val ARCHIVED_LABELS = setOf(
            "Archived", "Archived chats",
            "مؤرشفة", "المؤرشفة", "المؤرشف",
            "مؤرشفه", "المؤرشفه",
            "الدردشات المؤرشفة", "الدردشات المؤرشفه"
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
        logger.info("SYNC", "Smart Sync v0.3.7 paused")
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
        logger.info("SYNC", "Smart Sync v0.3.7 resumed")
    }

    fun stop() {
        stopRequested.set(true)
        pauseRequested.value = false
        logger.warning("SYNC", "Smart Sync v0.3.7 stop requested")
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
                    message = "جاري تجهيز المزامنة النظيفة",
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

            if (!integrationState.accessibility.enabled && !WhatsAppUiBridge.serviceConnected()) {
                fail("خدمة Accessibility غير مفعلة")
                return
            }

            if (!awaitAccessibilityBridge()) {
                fail("خدمة Accessibility مفعلة ولكن محرك قراءة واتساب غير متصل")
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

            val launchIntent = context.packageManager
                .getLaunchIntentForPackage(source.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

            if (launchIntent == null) {
                fail("تعذر الحصول على أمر فتح واتساب")
                return
            }

            val launchAt = System.currentTimeMillis()
            context.startActivity(launchIntent)
            logger.info("SYNC", "Opened ${source.packageName} for Smart Sync v0.3.7 CLEAN")

            val initialSnapshot = awaitInitialWhatsAppSnapshot(
                packageName = source.packageName,
                launchAt = launchAt,
            )

            if (initialSnapshot == null) {
                fail("لم تصل واجهة واتساب إلى Accessibility")
                return
            }

            updateStatus(SyncEngineStatus.NAVIGATING, "جاري الوصول إلى شاشة الدردشات")
            var snapshot = ensureChatsHome(
                packageName = source.packageName,
                initial = initialSnapshot,
            )

            if (snapshot == null) {
                fail("تعذر الوصول إلى شاشة الدردشات")
                return
            }

            updateStatus(
                SyncEngineStatus.RECOVERING,
                "جاري الرجوع إلى بداية قائمة الدردشات",
            )
            snapshot = rewindToTop(source.packageName, snapshot)

            val existingMeta = metaDao.getForSource(source.id).associateBy { it.groupId }
            val accumulator = RunAccumulator(
                existingIds = existingMeta.keys,
                existingMeta = existingMeta,
            )

            updateStage(
                SyncStage.NORMAL_GROUPS,
                SyncEngineStatus.SCANNING,
                "جاري قراءة الدردشات واكتشاف القروبات",
            )

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
                updateStage(
                    SyncStage.ARCHIVED,
                    SyncEngineStatus.RECOVERING,
                    "تم العثور على المؤرشف - جاري فتحه",
                )

                val topSnapshot = rewindToTop(source.packageName, normalResult.lastSnapshot)
                val beforeOpen = topSnapshot.capturedAt

                if (!WhatsAppUiBridge.clickSafeMatching(ARCHIVED_LABELS)) {
                    fail("تم اكتشاف المؤرشف ولكن تعذر فتحه")
                    return
                }

                val archivedSnapshot = awaitSnapshot(
                    packageName = source.packageName,
                    after = beforeOpen,
                    timeoutMs = 2_800,
                    allowCaptureFallback = true,
                )

                if (archivedSnapshot == null) {
                    fail("تم فتح المؤرشف ولكن تعذر قراءة واجهته")
                    return
                }

                val archivedTop = rewindToTop(source.packageName, archivedSnapshot)

                updateStage(
                    SyncStage.ARCHIVED,
                    SyncEngineStatus.SCANNING,
                    "جاري قراءة القروبات المؤرشفة",
                )

                scanSection(
                    sourceId = source.id,
                    packageName = source.packageName,
                    runId = runId,
                    initialSnapshot = archivedTop,
                    archived = true,
                    accumulator = accumulator,
                )
            } else {
                logger.info("SYNC", "No Archived entry detected")
            }

            if (stopRequested.get()) {
                finishStopped()
                return
            }

            if (accumulator.processedScreens >= MAX_SCREENS_PER_RUN) {
                fail("تم بلوغ حد الأمان قبل تأكيد نهاية القوائم")
                return
            }

            updateStage(
                SyncStage.FINAL_VERIFY,
                SyncEngineStatus.VERIFYING_END,
                "تم تأكيد نهاية قوائم واتساب",
            )

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
                "Smart Sync v0.3.7 CLEAN completed: ${accumulator.seenIds.size} group(s), ${accumulator.newCount} new",
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
                sourceId?.let {
                    logger.warning("SYNC", "Sync run ended without terminal state for $it")
                }
            }
        }
    }

    /**
     * Never presses the Groups filter.
     * Known non-chat surfaces are moved back to Chats before any scroll command.
     */
    private suspend fun ensureChatsHome(
        packageName: String,
        initial: WhatsAppUiSnapshot,
    ): WhatsAppUiSnapshot? {
        var current = initial

        repeat(MAX_CHAT_HOME_RECOVERY_ATTEMPTS) { attempt ->
            if (stopRequested.get()) return null
            awaitResumeIfPaused()

            val surface = screenDetector.classify(current, parser.parse(current))

            when (surface) {
                WhatsAppSurface.CHAT_LIST -> return current

                WhatsAppSurface.ARCHIVED_LIST,
                WhatsAppSurface.SEARCH,
                WhatsAppSurface.OPEN_CHAT -> {
                    val before = current.capturedAt
                    if (!WhatsAppUiBridge.performBack()) return null
                    current = awaitSnapshot(
                        packageName = packageName,
                        after = before,
                        timeoutMs = 1_700,
                        allowCaptureFallback = true,
                    ) ?: current
                }

                WhatsAppSurface.CHANNELS_OR_UPDATES,
                WhatsAppSurface.COMMUNITIES,
                WhatsAppSurface.CALLS -> {
                    val before = current.capturedAt
                    if (!WhatsAppUiBridge.clickSafeMatching(CHAT_TAB_LABELS)) {
                        return null
                    }
                    current = awaitSnapshot(
                        packageName = packageName,
                        after = before,
                        timeoutMs = 1_700,
                        allowCaptureFallback = true,
                    ) ?: current
                }

                WhatsAppSurface.UNKNOWN -> {
                    val before = current.capturedAt
                    val clickedChats = WhatsAppUiBridge.clickSafeMatching(CHAT_TAB_LABELS)

                    if (clickedChats) {
                        current = awaitSnapshot(
                            packageName = packageName,
                            after = before,
                            timeoutMs = 1_500,
                            allowCaptureFallback = true,
                        ) ?: current
                    } else if (attempt >= 1) {
                        if (!WhatsAppUiBridge.performBack()) return null
                        current = awaitSnapshot(
                            packageName = packageName,
                            after = before,
                            timeoutMs = 1_500,
                            allowCaptureFallback = true,
                        ) ?: current
                    } else {
                        delay(180)
                        current = WhatsAppUiBridge.captureNow(packageName)
                            ?: WhatsAppUiBridge.latest.value
                                ?.takeIf { it.packageName == packageName }
                            ?: current
                    }
                }
            }
        }

        return current.takeIf {
            screenDetector.classify(it, parser.parse(it)) == WhatsAppSurface.CHAT_LIST
        }
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

            snapshot = ensureReadableListSnapshot(
                packageName = packageName,
                snapshot = snapshot,
                archived = archived,
            ) ?: throw IllegalStateException(
                if (archived) {
                    "غادرت واجهة القروبات المؤرشفة أثناء المزامنة"
                } else {
                    "غادرت واجهة الدردشات أثناء المزامنة"
                }
            )

            val parsed = parser.parse(snapshot)
            accumulator.processedScreens++

            if (!archived && parsed.hasArchivedEntry) {
                sawArchivedEntry = true
            }

            val now = System.currentTimeMillis()
            val groupEntities = ArrayList<GroupEntity>(parsed.groups.size)
            val metaEntities = ArrayList<GroupSyncMetaEntity>(parsed.groups.size)

            var discoveredThisScreen = 0
            var lastName: String? = null

            parsed.groups.forEach { candidate ->
                val groupId = stableGroupId(sourceId, candidate.identityFingerprint)
                val firstTimeThisRun = accumulator.seenIds.add(groupId)

                if (firstTimeThisRun) {
                    discoveredThisScreen++
                    if (groupId !in accumulator.existingIds) {
                        accumulator.newCount++
                    }
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
                    fingerprint = candidate.identityFingerprint,
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
                        discoveredThisScreen > 0 && archived ->
                            "تم اكتشاف $discoveredThisScreen قروب مؤرشف"
                        discoveredThisScreen > 0 ->
                            "تم اكتشاف $discoveredThisScreen قروب في هذه الشاشة"
                        archived ->
                            "جاري قراءة بقية المؤرشف"
                        else ->
                            "جاري قراءة بقية الدردشات"
                    },
                    updatedAt = now,
                )
            }

            saveCheckpoint()
            upsertRun()

            /*
             * This is the scrolling mechanism that worked in the original v0.3:
             * use the largest scrollable element exposed by WhatsApp.
             * The critical difference is that we call it ONLY after the surface
             * guard above confirms Chats/Archived, so it cannot scroll Channels.
             */
            val beforeScrollAt = snapshot.capturedAt
            val scrollStartedAt = System.currentTimeMillis()
            val scrollAccepted = WhatsAppUiBridge.scrollForward()

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

            if (!scrollAccepted || nextSnapshot == null) {
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

                if (endPasses >= END_CONFIRMATION_PASSES) {
                    break
                }

                delay(220)
            } else {
                endPasses = 0
                val latency = (System.currentTimeMillis() - scrollStartedAt).coerceAtLeast(120)
                eventTimeoutMs = (latency * 2.10)
                    .roundToLong()
                    .coerceIn(650L, 2_300L)
                snapshot = nextSnapshot
            }
        }

        return SectionResult(
            lastSnapshot = snapshot,
            sawArchivedEntry = sawArchivedEntry,
        )
    }

    private suspend fun ensureReadableListSnapshot(
        packageName: String,
        snapshot: WhatsAppUiSnapshot,
        archived: Boolean,
    ): WhatsAppUiSnapshot? {
        fun allowed(candidate: WhatsAppUiSnapshot): Boolean {
            val surface = screenDetector.classify(candidate, parser.parse(candidate))
            return if (archived) {
                surface == WhatsAppSurface.ARCHIVED_LIST ||
                    surface == WhatsAppSurface.CHAT_LIST
            } else {
                surface == WhatsAppSurface.CHAT_LIST
            }
        }

        if (allowed(snapshot)) return snapshot

        delay(90)
        val fresh = WhatsAppUiBridge.captureNow(packageName)
            ?: WhatsAppUiBridge.latest.value?.takeIf { it.packageName == packageName }

        return fresh?.takeIf(::allowed)
    }

    private suspend fun rewindToTop(
        packageName: String,
        initial: WhatsAppUiSnapshot,
    ): WhatsAppUiSnapshot {
        var current = initial
        var noChangePasses = 0
        var attempts = 0

        while (
            !stopRequested.get() &&
            noChangePasses < END_CONFIRMATION_PASSES &&
            attempts < 1200
        ) {
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
                delay(110)
            } else {
                current = previous
                noChangePasses = 0
            }
        }

        return current
    }

    private suspend fun awaitAccessibilityBridge(timeoutMs: Long = 3_000L): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (WhatsAppUiBridge.serviceConnected()) return true
            delay(80)
        }
        return WhatsAppUiBridge.serviceConnected()
    }

    private suspend fun awaitInitialWhatsAppSnapshot(
        packageName: String,
        launchAt: Long,
    ): WhatsAppUiSnapshot? {
        awaitSnapshot(
            packageName = packageName,
            after = launchAt - 700,
            timeoutMs = 5_000,
            allowCaptureFallback = false,
        )?.takeIf { it.nodes.isNotEmpty() }?.let { return it }

        repeat(4) { attempt ->
            delay(180L + attempt * 140L)

            val captured = WhatsAppUiBridge.captureNow(packageName)
            if (
                captured != null &&
                captured.packageName == packageName &&
                captured.nodes.isNotEmpty()
            ) {
                return captured
            }

            val latest = WhatsAppUiBridge.latest.value
            if (
                latest != null &&
                latest.packageName == packageName &&
                latest.nodes.isNotEmpty()
            ) {
                return latest
            }
        }

        return null
    }

    private suspend fun awaitChangedSnapshot(
        packageName: String,
        after: Long,
        currentFingerprint: String,
        timeoutMs: Long,
    ): WhatsAppUiSnapshot? {
        fun changed(candidate: WhatsAppUiSnapshot): Boolean =
            candidate.packageName == packageName &&
                candidate.capturedAt > after &&
                parser.parse(candidate).screenFingerprint != currentFingerprint

        val latest = WhatsAppUiBridge.latest.value
        if (latest != null && changed(latest)) {
            return latest
        }

        val eventSnapshot = withTimeoutOrNull(timeoutMs) {
            WhatsAppUiBridge.events.first(::changed)
        }
        if (eventSnapshot != null) {
            return eventSnapshot
        }

        delay(80)
        val captured = WhatsAppUiBridge.captureNow(packageName)
        return captured?.takeIf { candidate ->
            candidate.packageName == packageName &&
                parser.parse(candidate).screenFingerprint != currentFingerprint
        }
    }

    private suspend fun awaitSnapshot(
        packageName: String,
        after: Long,
        timeoutMs: Long,
        allowCaptureFallback: Boolean,
    ): WhatsAppUiSnapshot? {
        val latest = WhatsAppUiBridge.latest.value
        if (
            latest != null &&
            latest.packageName == packageName &&
            latest.capturedAt > after
        ) {
            return latest
        }

        val eventSnapshot = withTimeoutOrNull(timeoutMs) {
            WhatsAppUiBridge.events.first { snapshot ->
                snapshot.packageName == packageName &&
                    snapshot.capturedAt > after
            }
        }
        if (eventSnapshot != null) return eventSnapshot

        if (!allowCaptureFallback) return null

        delay(90)
        return WhatsAppUiBridge.captureNow(packageName)
            ?: WhatsAppUiBridge.latest.value
                ?.takeIf { it.packageName == packageName }
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
            updateStatus(
                SyncEngineStatus.RECOVERING,
                "جاري الاستكمال من نقطة الحفظ",
            )
        }
    }

    private suspend fun saveCheckpoint() {
        val state = _state.value
        val currentRunId = state.runId ?: return
        val currentSourceId = state.sourceId ?: return

        checkpointDao.upsert(
            SyncCheckpointEntity(
                runId = currentRunId,
                sourceId = currentSourceId,
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
            runCatching {
                saveCheckpoint()
                upsertRun()
            }
        }
    }

    private suspend fun upsertRun(completedAt: Long? = null) {
        val state = _state.value
        val currentRunId = state.runId ?: return
        val currentSourceId = state.sourceId ?: return

        runDao.upsert(
            SyncRunEntity(
                id = currentRunId,
                sourceId = currentSourceId,
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
        logger.warning("SYNC", "Smart Sync v0.3.7 stopped with checkpoint retained")
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

        runCatching {
            saveCheckpoint()
            upsertRun()
        }

        logger.error("SYNC", message)
    }

    private fun updateStatus(
        status: SyncEngineStatus,
        message: String,
    ) {
        _state.update {
            it.copy(
                status = status,
                message = message,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    private fun updateStage(
        stage: SyncStage,
        status: SyncEngineStatus,
        message: String,
    ) {
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

    private fun stableGroupId(
        sourceId: String,
        identityFingerprint: String,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$sourceId|$identityFingerprint".toByteArray(Charsets.UTF_8))

        return "grp_" + digest
            .take(12)
            .joinToString("") { "%02x".format(it) }
    }
}
