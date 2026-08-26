package com.alothmany.wa.system.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class WAAccessibilityService : AccessibilityService() {
    companion object {
        private const val MAX_NODES = 4000
        private const val SNAPSHOT_THROTTLE_MS = 120L
        private val TARGET_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
    }

    private var lastSnapshotAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityRuntime.connected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString()
        if (packageName !in TARGET_PACKAGES) return

        val now = System.currentTimeMillis()
        val shouldCountNodes = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            now - lastSnapshotAt >= SNAPSHOT_THROTTLE_MS

        val count = if (shouldCountNodes) {
            lastSnapshotAt = now
            countNodes(rootInActiveWindow)
        } else {
            AccessibilityRuntime.state.value.nodeCount
        }

        AccessibilityRuntime.event(packageName, event.eventType, count)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        AccessibilityRuntime.disconnected()
        super.onDestroy()
    }

    private fun countNodes(root: AccessibilityNodeInfo?): Int {
        if (root == null) return 0
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var count = 0
        while (stack.isNotEmpty() && count < MAX_NODES) {
            val node = stack.removeLast()
            count++
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(stack::add)
            }
        }
        return count
    }
}
