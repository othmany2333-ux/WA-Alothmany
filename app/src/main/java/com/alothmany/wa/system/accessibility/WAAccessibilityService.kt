package com.alothmany.wa.system.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WAAccessibilityService : AccessibilityService() {
    companion object {
        private const val MAX_NODES = 2400
        private const val SNAPSHOT_THROTTLE_MS = 95L
        private const val COMMAND_TIMEOUT_MS = 900L
        private val TARGET_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastSnapshotAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        WhatsAppUiBridge.attach(this)
        AccessibilityRuntime.connected()
        publishSnapshot(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, force = true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString()
        if (packageName !in TARGET_PACKAGES) return

        val now = System.currentTimeMillis()
        val force = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED

        val snapshot = if (force || now - lastSnapshotAt >= SNAPSHOT_THROTTLE_MS) {
            publishSnapshot(event.eventType, force = force)
        } else {
            null
        }

        AccessibilityRuntime.event(
            packageName = packageName,
            eventType = event.eventType,
            nodeCount = snapshot?.nodes?.size ?: AccessibilityRuntime.state.value.nodeCount,
        )
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        WhatsAppUiBridge.detach(this)
        AccessibilityRuntime.disconnected()
        super.onDestroy()
    }

    internal fun clickFirstMatching(labels: Set<String>): Boolean = onServiceThread {
        val normalizedLabels = labels.map(::normalize).filter { it.isNotBlank() }
        if (normalizedLabels.isEmpty()) return@onServiceThread false

        val root = rootInActiveWindow ?: return@onServiceThread false
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var visited = 0
        while (stack.isNotEmpty() && visited++ < MAX_NODES) {
            val node = stack.removeLast()
            val text = normalize(node.text?.toString())
            val description = normalize(node.contentDescription?.toString())
            val matches = normalizedLabels.any { label ->
                text == label || description == label ||
                    (label.length >= 4 && (text.contains(label) || description.contains(label)))
            }
            if (matches && clickNodeOrParent(node)) return@onServiceThread true
            for (index in 0 until node.childCount) node.getChild(index)?.let(stack::add)
        }
        false
    }

    internal fun clickSafeMatching(labels: Set<String>): Boolean = onServiceThread {
        val normalizedLabels = labels.map(::normalize).filter { it.isNotBlank() }
        if (normalizedLabels.isEmpty()) return@onServiceThread false

        val root = rootInActiveWindow ?: return@onServiceThread false
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var visited = 0
        while (stack.isNotEmpty() && visited++ < MAX_NODES) {
            val node = stack.removeLast()
            val text = normalize(node.text?.toString())
            val description = normalize(node.contentDescription?.toString())
            val matches = normalizedLabels.any { label ->
                safeLabelMatch(text, label) || safeLabelMatch(description, label)
            }
            if (matches && clickNodeOrParent(node)) return@onServiceThread true
            for (index in 0 until node.childCount) node.getChild(index)?.let(stack::add)
        }
        false
    }

    internal fun scrollForward(): Boolean = onServiceThread {
        scrollAny(rootInActiveWindow, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    internal fun scrollBackward(): Boolean = onServiceThread {
        scrollAny(rootInActiveWindow, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    internal fun scrollPrimaryListForward(): Boolean = onServiceThread {
        scrollPrimary(rootInActiveWindow, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    internal fun scrollPrimaryListBackward(): Boolean = onServiceThread {
        scrollPrimary(rootInActiveWindow, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    internal fun performBack(): Boolean = onServiceThread {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var target: AccessibilityNodeInfo? = node
        var hops = 0
        while (target != null && !target.isClickable && hops++ < 5) {
            target = target.parent
        }
        return target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun safeLabelMatch(candidate: String, label: String): Boolean {
        if (candidate == label) return true

        if (candidate.startsWith(label)) {
            val suffix = candidate.substring(label.length).trimStart()
            if (suffix.isEmpty()) return true
            if (suffix.first() in setOf(',', '،', '-', '·', '•', ':') ||
                suffix.startsWith("tab") ||
                suffix.startsWith("علامة تبويب") ||
                suffix.startsWith("علامه تبويب") ||
                suffix.startsWith("غير مقرو") ||
                suffix.startsWith("unread")) return true
        }

        val tabDescription = candidate.contains("tab") ||
            candidate.contains("علامة تبويب") ||
            candidate.contains("علامه تبويب")
        if (tabDescription && (candidate.endsWith(label) || candidate.contains(" $label"))) return true
        return false
    }

    private fun scrollPrimary(root: AccessibilityNodeInfo?, action: Int): Boolean {
        root ?: return false
        val rootRect = Rect().also { root.getBoundsInScreen(it) }
        val minWidth = (rootRect.width() * 0.55f).toInt()
        val minHeight = (rootRect.height() * 0.32f).toInt()
        val candidates = ArrayList<Pair<AccessibilityNodeInfo, Rect>>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var visited = 0
        while (stack.isNotEmpty() && visited++ < MAX_NODES) {
            val node = stack.removeLast()
            if (node.isScrollable) {
                val rect = Rect().also { node.getBoundsInScreen(it) }
                if (rect.width() >= minWidth && rect.height() >= minHeight) {
                    candidates += node to rect
                }
            }
            for (index in 0 until node.childCount) node.getChild(index)?.let(stack::add)
        }

        val primaryWorked = candidates
            .sortedWith(
                compareByDescending<Pair<AccessibilityNodeInfo, Rect>> { it.second.height() }
                    .thenByDescending { it.second.width().toLong() * it.second.height().toLong() }
            )
            .any { (node, _) -> node.performAction(action) }

        return primaryWorked || scrollAny(root, action)
    }

    private fun scrollAny(root: AccessibilityNodeInfo?, action: Int): Boolean {
        root ?: return false
        val candidates = ArrayList<AccessibilityNodeInfo>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var visited = 0
        while (stack.isNotEmpty() && visited++ < MAX_NODES) {
            val node = stack.removeLast()
            if (node.isScrollable) candidates += node
            for (index in 0 until node.childCount) node.getChild(index)?.let(stack::add)
        }
        return candidates
            .sortedByDescending { node ->
                val r = Rect().also { node.getBoundsInScreen(it) }
                r.width().toLong() * r.height().toLong()
            }
            .any { it.performAction(action) } || root.performAction(action)
    }

    private fun publishSnapshot(eventType: Int, force: Boolean): WhatsAppUiSnapshot? {
        val now = System.currentTimeMillis()
        if (!force && now - lastSnapshotAt < SNAPSHOT_THROTTLE_MS) return null
        lastSnapshotAt = now

        val root = rootInActiveWindow ?: return null
        val packageName = root.packageName?.toString() ?: return null
        if (packageName !in TARGET_PACKAGES) return null

        val rootRect = Rect().also { root.getBoundsInScreen(it) }
        val nodes = ArrayList<WhatsAppUiNode>(256)
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.add(root to 0)

        while (stack.isNotEmpty() && nodes.size < MAX_NODES) {
            val (node, depth) = stack.removeLast()
            val rect = Rect().also { node.getBoundsInScreen(it) }
            nodes += WhatsAppUiNode(
                text = node.text?.toString()?.take(180),
                contentDescription = node.contentDescription?.toString()?.take(180),
                className = node.className?.toString(),
                viewId = node.viewIdResourceName,
                clickable = node.isClickable,
                scrollable = node.isScrollable,
                enabled = node.isEnabled,
                depth = depth,
                bounds = RectSnapshot.from(rect),
                focused = node.isFocused,
                editable = node.isEditable,
            )
            for (index in node.childCount - 1 downTo 0) {
                node.getChild(index)?.let { child -> stack.add(child to (depth + 1)) }
            }
        }

        return WhatsAppUiSnapshot(
            packageName = packageName,
            eventType = eventType,
            capturedAt = now,
            rootBounds = RectSnapshot.from(rootRect),
            nodes = nodes,
        ).also(WhatsAppUiBridge::publish)
    }

    private fun onServiceThread(block: () -> Boolean): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) return runCatching(block).getOrDefault(false)
        val latch = CountDownLatch(1)
        var result = false
        mainHandler.post {
            result = runCatching(block).getOrDefault(false)
            latch.countDown()
        }
        latch.await(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return result
    }

    private fun normalize(value: String?): String = value
        .orEmpty()
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
}
