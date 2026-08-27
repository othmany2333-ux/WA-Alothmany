package com.alothmany.wa.feature.sync.engine

import com.alothmany.wa.system.accessibility.RectSnapshot
import com.alothmany.wa.system.accessibility.WhatsAppUiNode
import com.alothmany.wa.system.accessibility.WhatsAppUiSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class WhatsAppScreenDetectorTest {
    private val parser = WhatsAppGroupParser()
    private val detector = WhatsAppScreenDetector()

    @Test
    fun normalChatListIsAcceptedBeforeAnyBackAction() {
        val snapshot = snapshot(
            listOf(
                label("الدردشات", 2250),
                row(220, "محمد", "مرحبا"),
                row(400, "طلاب الجامعة", "~ Ahmed: المحاضرة"),
                row(580, "خالد", "رسالة"),
            ).flatten()
        )
        assertEquals(WhatsAppSurface.CHAT_LIST, detector.classify(snapshot, parser.parse(snapshot)))
    }

    @Test
    fun searchSurfaceIsNotChatList() {
        val edit = WhatsAppUiNode(
            text = "بحث...", contentDescription = "Search chats", className = "android.widget.EditText",
            viewId = "search", clickable = true, scrollable = false, enabled = true, depth = 3,
            bounds = RectSnapshot(50, 80, 1000, 180),
        )
        val snapshot = snapshot(listOf(edit) + row(260, "طلاب الجامعة", "~ Ahmed: hi"))
        assertEquals(WhatsAppSurface.SEARCH, detector.classify(snapshot, parser.parse(snapshot)))
    }

    @Test
    fun channelsSurfaceIsRejectedEvenWhenRowsExist() {
        val snapshot = snapshot(
            listOf(
                label("القنوات", 90),
                label("الدردشات", 2250),
                row(250, "قناة تقنية", "آخر تحديث"),
                row(440, "قناة أخبار", "خبر جديد"),
                row(630, "قناة ثالثة", "تحديث"),
            ).flatten()
        )
        assertEquals(WhatsAppSurface.CHANNELS_OR_UPDATES, detector.classify(snapshot, parser.parse(snapshot)))
    }

    private fun snapshot(nodes: List<WhatsAppUiNode>) = WhatsAppUiSnapshot(
        packageName = "com.whatsapp",
        eventType = 0,
        capturedAt = System.currentTimeMillis(),
        rootBounds = RectSnapshot(0, 0, 1080, 2400),
        nodes = nodes,
    )

    private fun label(text: String, top: Int) = listOf(
        WhatsAppUiNode(
            text = text, contentDescription = text, className = "android.widget.TextView", viewId = "label",
            clickable = false, scrollable = false, enabled = true, depth = 3,
            bounds = RectSnapshot(100, top, 900, top + 60),
        )
    )

    private fun row(top: Int, title: String, preview: String) = listOf(
        WhatsAppUiNode(
            text = null, contentDescription = null, className = "android.view.ViewGroup", viewId = "chat_row",
            clickable = true, scrollable = false, enabled = true, depth = 4,
            bounds = RectSnapshot(20, top, 1060, top + 170),
        ),
        WhatsAppUiNode(
            text = title, contentDescription = null, className = "android.widget.TextView", viewId = "title",
            clickable = false, scrollable = false, enabled = true, depth = 5,
            bounds = RectSnapshot(240, top + 12, 950, top + 62),
        ),
        WhatsAppUiNode(
            text = preview, contentDescription = null, className = "android.widget.TextView", viewId = "preview",
            clickable = false, scrollable = false, enabled = true, depth = 5,
            bounds = RectSnapshot(240, top + 75, 950, top + 128),
        )
    )
}
