package com.alothmany.wa.system.integration

import com.alothmany.wa.core.model.WhatsAppSourceType

enum class CapabilityStatus {
    READY,
    NEEDS_PERMISSION,
    OFFLINE,
    LIMITED,
    UNAVAILABLE,
    ERROR,
}

data class ShizukuSnapshot(
    val status: CapabilityStatus = CapabilityStatus.OFFLINE,
    val binderAlive: Boolean = false,
    val permissionGranted: Boolean = false,
    val privilegedServiceConnected: Boolean = false,
    val serverVersion: Int? = null,
    val serverUid: Int? = null,
    val privilegedUid: Int? = null,
    val error: String? = null,
)

data class AccessibilitySnapshot(
    val enabled: Boolean = false,
    val serviceConnected: Boolean = false,
    val lastPackage: String? = null,
    val lastEventType: Int? = null,
    val nodeCount: Int = 0,
    val textNodeCount: Int = 0,
    val scrollableNodeCount: Int = 0,
    val interactiveWindowCount: Int = 0,
    val captureSource: String? = null,
    val lastSnapshotAt: Long = 0L,
    val lastEventAt: Long = 0L,
)

data class DetectedWhatsAppSource(
    val id: String,
    val packageName: String,
    val userId: Int,
    val profileType: String,
    val displayName: String,
    val sourceType: WhatsAppSourceType,
    val versionName: String? = null,
    val launchable: Boolean = false,
    val privilegedDetection: Boolean = false,
)

data class SystemIntegrationState(
    val shizuku: ShizukuSnapshot = ShizukuSnapshot(),
    val accessibility: AccessibilitySnapshot = AccessibilitySnapshot(),
    val overlayPermissionGranted: Boolean = false,
    val overlayRunning: Boolean = false,
    val sources: List<DetectedWhatsAppSource> = emptyList(),
    val lastProbeAt: Long = 0L,
    val probing: Boolean = false,
) {
    val availableSourceTypes: Set<WhatsAppSourceType>
        get() = sources.mapTo(linkedSetOf()) { it.sourceType }
}
