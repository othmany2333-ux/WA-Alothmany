package com.alothmany.wa.feature.sync.selection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight selection hand-off for Smart Sync v0.4.1.
 *
 * v0.4.1 synchronizes WhatsApp groups only, so this store keeps only
 * the selected source/run/group identifiers required by later operations.
 */
object OperationSelectionStore {

    private val _payload = MutableStateFlow(OperationSelectionPayload())

    val payload: StateFlow<OperationSelectionPayload> =
        _payload.asStateFlow()

    fun set(
        sourceId: String?,
        runId: String?,
        groupIds: Set<String>,
    ) {
        _payload.value = OperationSelectionPayload(
            sourceId = sourceId,
            runId = runId,
            groupIds = groupIds.toSet(),
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
    val updatedAt: Long = 0L,
)
