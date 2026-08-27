package com.alothmany.wa.feature.sync.model

enum class SyncEngineStatus {
    IDLE,
    PREPARING,
    OPENING_WHATSAPP,
    NAVIGATING,
    SCANNING,
    VERIFYING_END,
    PAUSED,
    RECOVERING,
    COMPLETED,
    STOPPED,
    ERROR,
}

enum class SyncStage {
    NORMAL_GROUPS,
    ARCHIVED,
    COMMUNITIES,
    FINAL_VERIFY,
    SAVING,
}

enum class GroupSelectionKind {
    ALL,
    UNREAD,
    ACTIVE,
    LOCKED,
    DELETED,
    COMMUNITIES,
}

enum class ContactSyncMode {
    UNSAVED_WHATSAPP_NUMBERS,
    SAVED_WHATSAPP_NUMBERS,
    WHATSAPP_NUMBERS_AND_CONTACTS,
    CONTACTS_ONLY,
}

data class SyncRuntimeState(
    val runId: String? = null,
    val sourceId: String? = null,
    val sourceName: String? = null,
    val status: SyncEngineStatus = SyncEngineStatus.IDLE,
    val stage: SyncStage = SyncStage.NORMAL_GROUPS,
    val discoveredCount: Int = 0,
    val newCount: Int = 0,
    val processedScreens: Int = 0,
    val currentGroupName: String? = null,
    val consecutiveEndPasses: Int = 0,
    val lastScreenFingerprint: String? = null,
    val message: String? = null,
    val errorMessage: String? = null,
    val startedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val running: Boolean
        get() = status in setOf(
            SyncEngineStatus.PREPARING,
            SyncEngineStatus.OPENING_WHATSAPP,
            SyncEngineStatus.NAVIGATING,
            SyncEngineStatus.SCANNING,
            SyncEngineStatus.VERIFYING_END,
            SyncEngineStatus.RECOVERING,
        )
}

data class ParsedGroupCandidate(
    val displayName: String,
    val normalizedName: String,
    val isUnread: Boolean,
    val isLocked: Boolean,
    val confidence: String,
    val rowFingerprint: String,
)

data class ParsedGroupScreen(
    val groups: List<ParsedGroupCandidate>,
    val screenFingerprint: String,
    val looksLikeGroupList: Boolean,
)
