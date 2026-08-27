package com.alothmany.wa.system.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WAAccessibilityService : AccessibilityService() {
    companion object {
        private const val MAX_NODES = 4000
        private const val SNAPSHOT_THROTTLE_MS = 70L
        private const val COMMAND_TIMEOUT_MS = 1200L
        private val TARGET_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
    }

    private data class RootSelection(
        val root: AccessibilityNodeInfo,
        val source: String,
        val interactiveWindowCount: Int,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastSnapshotAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()

        // Apply the critical flags dynamically as well as in XML. This is useful
        // on OEM builds that restore an older cached AccessibilityServiceInfo.
        serviceInfo?.let { info ->
            info.flags = info.flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            setServiceInfo(info)
        }

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
            publishSnapshot(event.eventType, force = force, expectedPackage = packageName)
        } else {
            null
        }

        if (snapshot == null) {
            AccessibilityRuntime.event(
                packageName = packageName,
                eventType = event.eventType,
                nodeCount = AccessibilityRuntime.state.value.nodeCount,
            )
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        WhatsAppUiBridge.detach(this)
        AccessibilityRuntime.disconnected()
        super.onDestroy()
    }

    internal fun captureNow(expectedPackage: String? = null): WhatsAppUiSnapshot? = onServiceThreadSnapshot {
        publishSnapshot(
            eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            force = true,
            expectedPackage = expectedPackage,
        )
    }

    internal fun clickFirstMatching(labels: Set<String>): Boolean = onServiceThread {
        val normalizedLabels = labels.map(::normalize).filter { it.isNotBlank() }
        if (normalizedLabels.isEmpty()) return@onServiceThread false

        val root = resolveWhatsAppRoot()?.root ?: return@onServiceThread false
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

        val root = resolveWhatsAppRoot()?.root ?: return@onServiceThread false
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
        scrollAny(resolveWhatsAppRoot()?.root, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    internal fun scrollBackward(): Boolean = onServiceThread {
        scrollAny(resolveWhatsAppRoot()?.root, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    internal fun scrollPrimaryListForward(): Boolean = onServiceThread {
        scrollPrimary(resolveWhatsAppRoot()?.root, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    internal fun scrollPrimaryListBackward(): Boolean = onServiceThread {
        scrollPrimary(resolveWhatsAppRoot()?.root, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    internal fun performBack(): Boolean = onServiceThread {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var target: AccessibilityNodeInfo? = node
        var hops = 0
        while (target != null && !target.isClickable && hops++ < 6) {
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
        val minWidth = (rootRect.width() * 0.50f).toInt()
        val minHeight = (rootRect.height() * 0.26f).toInt()
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

    private fun publishSnapshot(
        eventType: Int,
        force: Boolean,
        expectedPackage: String? = null,
    ): WhatsAppUiSnapshot? {
        val now = System.currentTimeMillis()
        if (!force && now - lastSnapshotAt < SNAPSHOT_THROTTLE_MS) return null

        val selection = resolveWhatsAppRoot(expectedPackage) ?: return null
        val root = selection.root
        val packageName = root.packageName?.toString() ?: return null
        if (packageName !in TARGET_PACKAGES) return null

        lastSnapshotAt = now
        val rootRect = Rect().also { root.getBoundsInScreen(it) }
        val nodes = ArrayList<WhatsAppUiNode>(384)
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.add(root to 0)

        while (stack.isNotEmpty() && nodes.size < MAX_NODES) {
            val (node, depth) = stack.removeLast()
            val rect = Rect().also { node.getBoundsInScreen(it) }
            nodes += WhatsAppUiNode(
                text = node.text?.toString()?.take(220),
                contentDescription = node.contentDescription?.toString()?.take(220),
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

        val snapshot = WhatsAppUiSnapshot(
            packageName = packageName,
            eventType = eventType,
            capturedAt = now,
            rootBounds = RectSnapshot.from(rootRect),
            nodes = nodes,
        )

        AccessibilityRuntime.capture(
            packageName = packageName,
            eventType = eventType,
            nodeCount = nodes.size,
            textNodeCount = nodes.count { !it.text.isNullOrBlank() || !it.contentDescription.isNullOrBlank() },
            scrollableNodeCount = nodes.count { it.scrollable },
            interactiveWindowCount = selection.interactiveWindowCount,
            captureSource = selection.source,
            capturedAt = now,
        )
        WhatsAppUiBridge.publish(snapshot)
        return snapshot
    }

    private fun resolveWhatsAppRoot(expectedPackage: String? = null): RootSelection? {
        val interactiveWindows = runCatching { windows.orEmpty() }.getOrDefault(emptyList())
        val candidates = ArrayList<Triple<AccessibilityNodeInfo, String, Long>>()

        rootInActiveWindow?.let { root ->
            val pkg = root.packageName?.toString()
            if (pkg in TARGET_PACKAGES) {
                candidates += Triple(root, "ACTIVE_ROOT", rootScore(root, pkg, expectedPackage, active = true, focused = true))
            }
        }

        interactiveWindows.forEachIndexed { index, window ->
            val root = window.root ?: return@forEachIndexed
            val pkg = root.packageName?.toString()
            if (pkg !in TARGET_PACKAGES) return@forEachIndexed
            candidates += Triple(
                root,
                "WINDOW[$index]:${windowTypeName(window.type)}",
                rootScore(root, pkg, expectedPackage, window.isActive, window.isFocused),
            )
        }

        if (candidates.isEmpty()) return null
        val expectedMatches = if (expectedPackage.isNullOrBlank()) {
            candidates
        } else {
            candidates.filter { (root, _, _) -> root.packageName?.toString() == expectedPackage }
                .ifEmpty { candidates }
        }
        val best = expectedMatches.maxByOrNull { it.third } ?: return null
        return RootSelection(best.first, best.second, interactiveWindows.size)
    }

    private fun rootScore(
        root: AccessibilityNodeInfo,
        packageName: String?,
        expectedPackage: String?,
        active: Boolean,
        focused: Boolean,
    ): Long {
        val rect = Rect().also { root.getBoundsInScreen(it) }
        var score = rect.width().toLong() * rect.height().toLong()
        if (packageName == expectedPackage && expectedPackage != null) score += 4_000_000_000L
        if (active) score += 2_000_000_000L
        if (focused) score += 1_000_000_000L
        return score
    }

    private fun windowTypeName(type: Int): String = when (type) {
        AccessibilityWindowInfo.TYPE_APPLICATION -> "APP"
        AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "IME"
        AccessibilityWindowInfo.TYPE_SYSTEM -> "SYSTEM"
        AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "A11Y_OVERLAY"
        else -> type.toString()
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

    private fun onServiceThreadSnapshot(block: () -> WhatsAppUiSnapshot?): WhatsAppUiSnapshot? {
        if (Looper.myLooper() == Looper.getMainLooper()) return runCatching(block).getOrNull()
        val latch = CountDownLatch(1)
        var result: WhatsAppUiSnapshot? = null
        mainHandler.post {
            result = runCatching(block).getOrNull()
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
