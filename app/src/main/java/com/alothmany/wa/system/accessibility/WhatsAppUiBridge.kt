package com.alothmany.wa.system.accessibility

import android.graphics.Rect
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

/**
 * In-process bridge between WAAccessibilityService and automation engines.
 * It only keeps the current WhatsApp UI snapshot in memory. Persisted data is
 * limited to the group/contact results explicitly produced by an engine.
 */
object WhatsAppUiBridge {
    private var serviceRef = WeakReference<WAAccessibilityService>(null)

    private val _latest = MutableStateFlow<WhatsAppUiSnapshot?>(null)
    val latest: StateFlow<WhatsAppUiSnapshot?> = _latest.asStateFlow()

    private val _events = MutableSharedFlow<WhatsAppUiSnapshot>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<WhatsAppUiSnapshot> = _events.asSharedFlow()

    internal fun attach(service: WAAccessibilityService) {
        serviceRef = WeakReference(service)
    }

    internal fun detach(service: WAAccessibilityService) {
        if (serviceRef.get() === service) serviceRef.clear()
    }

    internal fun publish(snapshot: WhatsAppUiSnapshot) {
        _latest.value = snapshot
        _events.tryEmit(snapshot)
    }

    fun serviceConnected(): Boolean = serviceRef.get() != null

    /** Legacy fuzzy click kept for other modules. Smart Sync v0.3.1 does not use it. */
    fun clickFirstMatching(labels: Set<String>): Boolean =
        serviceRef.get()?.clickFirstMatching(labels).orFalse()

    /**
     * Conservative click for navigation tabs / Archived. It only accepts an exact
     * label or a label-prefixed accessibility description, preventing a request
     * for "Chats" from clicking "Search chats".
     */
    fun clickSafeMatching(labels: Set<String>): Boolean =
        serviceRef.get()?.clickSafeMatching(labels).orFalse()

    fun scrollForward(): Boolean = serviceRef.get()?.scrollForward().orFalse()

    fun scrollBackward(): Boolean = serviceRef.get()?.scrollBackward().orFalse()

    /** Scroll the primary large vertical list instead of an arbitrary carousel/filter. */
    fun scrollPrimaryListForward(): Boolean =
        serviceRef.get()?.scrollPrimaryListForward().orFalse()

    fun scrollPrimaryListBackward(): Boolean =
        serviceRef.get()?.scrollPrimaryListBackward().orFalse()

    fun performBack(): Boolean = serviceRef.get()?.performBack().orFalse()

    private fun Boolean?.orFalse(): Boolean = this == true
}

data class WhatsAppUiSnapshot(
    val packageName: String,
    val eventType: Int,
    val capturedAt: Long,
    val rootBounds: RectSnapshot,
    val nodes: List<WhatsAppUiNode>,
) {
    val width: Int get() = rootBounds.width
    val height: Int get() = rootBounds.height
}

data class WhatsAppUiNode(
    val text: String?,
    val contentDescription: String?,
    val className: String?,
    val viewId: String?,
    val clickable: Boolean,
    val scrollable: Boolean,
    val enabled: Boolean,
    val depth: Int,
    val bounds: RectSnapshot,
)

data class RectSnapshot(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)

    fun contains(other: RectSnapshot): Boolean =
        other.left >= left && other.right <= right && other.top >= top && other.bottom <= bottom

    companion object {
        fun from(rect: Rect) = RectSnapshot(rect.left, rect.top, rect.right, rect.bottom)
    }
}
