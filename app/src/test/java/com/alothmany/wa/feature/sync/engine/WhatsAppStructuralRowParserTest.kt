package com.alothmany.wa.feature.sync.engine

import com.alothmany.wa.system.accessibility.RectSnapshot
import com.alothmany.wa.system.accessibility.WhatsAppUiNode
import com.alothmany.wa.system.accessibility.WhatsAppUiSnapshot
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppStructuralRowParserTest {
    private val parser = WhatsAppGroupParser()

    @Test
    fun nonClickableChatContainersAreStillRead() {
        val nodes = mutableListOf<WhatsAppUiNode>()
        nodes += primaryList()
        nodes += row(240, "طلاب الجامعة", "~ Ahmed: المحاضرة", clickable = false)
        nodes += row(430, "محمد", "مرحبا", clickable = false)
        nodes += row(620, "قروب التقنية", "Ali: update", clickable = false)

        val parsed = parser.parse(snapshot(nodes))
        assertTrue(parsed.chatRowCount >= 2)
        assertTrue(parsed.groups.any { it.displayName == "طلاب الجامعة" })
    }

    private fun snapshot(nodes: List<WhatsAppUiNode>) = WhatsAppUiSnapshot(
        packageName = "com.whatsapp",
        eventType = 0,
        capturedAt = System.currentTimeMillis(),
        rootBounds = RectSnapshot(0, 0, 1080, 2400),
        nodes = nodes,
    )

    private fun primaryList() = WhatsAppUiNode(
        text = null, contentDescription = null, className = "android.view.ViewGroup", viewId = "chat_list",
        clickable = false, scrollable = true, enabled = true, depth = 2,
        bounds = RectSnapshot(0, 190, 1080, 2180),
    )

    private fun row(top: Int, title: String, preview: String, clickable: Boolean) = listOf(
        WhatsAppUiNode(
            text = null, contentDescription = null, className = "android.view.ViewGroup", viewId = null,
            clickable = clickable, scrollable = false, enabled = true, depth = 4,
            bounds = RectSnapshot(15, top, 1065, top + 170),
        ),
        WhatsAppUiNode(
            text = title, contentDescription = null, className = "android.widget.TextView", viewId = null,
            clickable = false, scrollable = false, enabled = true, depth = 5,
            bounds = RectSnapshot(220, top + 15, 960, top + 65),
        ),
        WhatsAppUiNode(
            text = preview, contentDescription = null, className = "android.widget.TextView", viewId = null,
            clickable = false, scrollable = false, enabled = true, depth = 5,
            bounds = RectSnapshot(220, top + 78, 960, top + 132),
        ),
    )
}
