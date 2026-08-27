package com.alothmany.wa.feature.sync.selection

import com.alothmany.wa.feature.sync.model.ContactSyncMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shared local hand-off from Sync to Extract/Publish/Join/Delete. */
object OperationSelectionStore {
    private val _payload = MutableStateFlow(OperationSelectionPayload())
    val payload: StateFlow<OperationSelectionPayload> = _payload.asStateFlow()

    fun set(
        sourceId: String?,
        runId: String?,
        groupIds: Set<String>,
        contactMode: ContactSyncMode,
    ) {
        _payload.value = OperationSelectionPayload(
            sourceId = sourceId,
            runId = runId,
            groupIds = groupIds.toSet(),
            contactMode = contactMode,
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun clear() {
        _payload.value = OperationSelectionPayload()
    }
}

data class OperationSelectionPayload(
    val sourceId: String? = null,
    val runId: String? = null,
    val groupIds: Set<String> = emptySet(),
    val contactMode: ContactSyncMode = ContactSyncMode.UNSAVED_WHATSAPP_NUMBERS,
    val updatedAt: Long = 0L,
)
