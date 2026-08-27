package com.alothmany.wa.feature.sync.engine

import com.alothmany.wa.system.accessibility.RectSnapshot
import com.alothmany.wa.system.accessibility.WhatsAppUiNode
import com.alothmany.wa.system.accessibility.WhatsAppUiSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppGroupParserTest {
    private val parser = WhatsAppGroupParser()

    @Test
    fun namedSenderPrefixDetectsGroupButPersonalChatIsIgnored() {
        val snapshot = snapshot(
            rows = listOf(
                row(180, "طلاب الجامعة", "~ Ahmed: المحاضرة الساعة 10"),
                row(380, "محمد", "مرحبا كيف حالك"),
            )
        )

        val parsed = parser.parse(snapshot)
        assertEquals(1, parsed.groups.size)
        assertEquals("طلاب الجامعة", parsed.groups.single().displayName)
        assertTrue("SENDER_PREFIX" in parsed.groups.single().evidenceTags)
        assertEquals(2, parsed.chatRowCount)
    }

    @Test
    fun youPrefixAloneDoesNotTurnPersonalChatIntoGroup() {
        val parsed = parser.parse(snapshot(rows = listOf(row(180, "محمد", "أنت: تم الإرسال"))))
        assertTrue(parsed.groups.isEmpty())
    }

    @Test
    fun multipleNumbersAndParticipantListAreGroupEvidence() {
        val parsed = parser.parse(
            snapshot(rows = listOf(row(180, "لجنة العمل", "+967 777 111 222، +967 733 444 555، أحمد")))
        )
        assertEquals(1, parsed.groups.size)
        assertTrue(parsed.groups.single().evidenceScore >= 4)
    }

    @Test
    fun othersMarkerIsStrongGroupEvidence() {
        val parsed = parser.parse(snapshot(rows = listOf(row(180, "فريق المشروع", "أحمد، خالد وآخرون"))))
        assertEquals(1, parsed.groups.size)
        assertTrue("OTHERS" in parsed.groups.single().evidenceTags)
    }

    @Test
    fun archivedEntryIsDetected() {
        val nodes = mutableListOf<WhatsAppUiNode>()
        nodes += labelNode("المؤرشفة", 100)
        nodes += rowNodes(220, "قروب العمل", "~ Ali: تم الإرسال")
        val parsed = parser.parse(snapshot(nodes = nodes))
        assertTrue(parsed.hasArchivedEntry)
    }

    @Test
    fun identityFingerprintStaysStableWhenLastMessageChanges() {
        val first = parser.parse(snapshot(rows = listOf(row(180, "طلاب الجامعة", "~ Ahmed: الرسالة الأولى"))))
        val second = parser.parse(snapshot(rows = listOf(row(180, "طلاب الجامعة", "~ Khaled: رسالة جديدة"))))
        assertEquals(first.groups.single().identityFingerprint, second.groups.single().identityFingerprint)
        assertNotEquals(first.groups.single().observationFingerprint, second.groups.single().observationFingerprint)
    }

    @Test
    fun screenFingerprintUsesAllRowsNotOnlyGroups() {
        val first = parser.parse(snapshot(rows = listOf(row(180, "محمد", "رسالة عادية"))))
        val second = parser.parse(snapshot(rows = listOf(row(180, "خالد", "رسالة عادية"))))
        assertTrue(first.groups.isEmpty())
        assertTrue(second.groups.isEmpty())
        assertNotEquals(first.screenFingerprint, second.screenFingerprint)
    }

    @Test
    fun channelRowIsNotClassifiedAsGroup() {
        val nodes = rowNodes(180, "قناة الأخبار", "آخر تحديث")
            .map { node -> if (node.viewId == "chat_row") node.copy(contentDescription = "Channel") else node }
        val parsed = parser.parse(snapshot(nodes = nodes))
        assertFalse(parsed.groups.isNotEmpty())
    }

    private fun snapshot(
        rows: List<List<WhatsAppUiNode>> = emptyList(),
        nodes: List<WhatsAppUiNode> = rows.flatten(),
    ): WhatsAppUiSnapshot = WhatsAppUiSnapshot(
        packageName = "com.whatsapp",
        eventType = 0,
        capturedAt = System.currentTimeMillis(),
        rootBounds = RectSnapshot(0, 0, 1080, 2400),
        nodes = nodes,
    )

    private fun row(top: Int, title: String, preview: String): List<WhatsAppUiNode> = rowNodes(top, title, preview)

    private fun rowNodes(top: Int, title: String, preview: String): List<WhatsAppUiNode> {
        val row = WhatsAppUiNode(
            text = null,
            contentDescription = null,
            className = "android.view.ViewGroup",
            viewId = "chat_row",
            clickable = true,
            scrollable = false,
            enabled = true,
            depth = 4,
            bounds = RectSnapshot(20, top, 1060, top + 170),
        )
        val titleNode = WhatsAppUiNode(
            text = title,
            contentDescription = null,
            className = "android.widget.TextView",
            viewId = "title",
            clickable = false,
            scrollable = false,
            enabled = true,
            depth = 5,
            bounds = RectSnapshot(240, top + 12, 950, top + 62),
        )
        val previewNode = WhatsAppUiNode(
            text = preview,
            contentDescription = null,
            className = "android.widget.TextView",
            viewId = "preview",
            clickable = false,
            scrollable = false,
            enabled = true,
            depth = 5,
            bounds = RectSnapshot(240, top + 75, 950, top + 128),
        )
        return listOf(row, titleNode, previewNode)
    }

    private fun labelNode(text: String, top: Int): WhatsAppUiNode = WhatsAppUiNode(
        text = text,
        contentDescription = text,
        className = "android.widget.TextView",
        viewId = "label",
        clickable = false,
        scrollable = false,
        enabled = true,
        depth = 3,
        bounds = RectSnapshot(100, top, 800, top + 60),
    )
}
