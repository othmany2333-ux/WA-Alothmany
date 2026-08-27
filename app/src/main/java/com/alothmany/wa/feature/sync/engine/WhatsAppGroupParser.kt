package com.alothmany.wa.feature.sync.engine

import com.alothmany.wa.feature.sync.model.ParsedGroupCandidate
import com.alothmany.wa.feature.sync.model.ParsedGroupScreen
import com.alothmany.wa.system.accessibility.WhatsAppUiNode
import com.alothmany.wa.system.accessibility.WhatsAppUiSnapshot
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhatsAppGroupParser @Inject constructor() {
    private val groupLabels = setOf(
        "groups", "group", "المجموعات", "القروبات", "مجموعات", "قروبات"
    )

    private val ignoredExact = setOf(
        "chats", "chat", "المحادثات", "الدردشات",
        "groups", "المجموعات", "القروبات", "مجموعات", "قروبات",
        "unread", "غير مقروءة", "غير المقروءة",
        "favourites", "favorites", "المفضلة",
        "archived", "مؤرشفة", "المؤرشفة", "المؤرشف",
        "communities", "المجتمعات", "community", "مجتمع",
        "search", "بحث", "new chat", "محادثة جديدة",
    )

    private val ignoredContains = setOf(
        "ask meta ai", "meta ai", "search chats", "search",
        "اسال meta ai", "اسأل meta ai", "ابحث", "البحث"
    )

    private val timeRegex = Regex("^\\d{1,2}[:.]\\d{2}(?:\\s*[ap]m)?$", RegexOption.IGNORE_CASE)
    private val countRegex = Regex("^[+]?\\d{1,4}$")
    private val dateRegex = Regex("^(today|yesterday|اليوم|أمس|امس|\\d{1,2}[/.-]\\d{1,2}(?:[/.-]\\d{2,4})?)$", RegexOption.IGNORE_CASE)

    fun parse(snapshot: WhatsAppUiSnapshot): ParsedGroupScreen {
        if (snapshot.width <= 0 || snapshot.height <= 0) {
            return ParsedGroupScreen(emptyList(), fingerprint(emptyList()), false)
        }

        val allLabels = snapshot.nodes
            .asSequence()
            .flatMap { sequenceOf(it.text, it.contentDescription) }
            .mapNotNull(::cleanLabel)
            .map(::normalize)
            .toSet()

        val looksLikeGroupList = allLabels.any { label -> groupLabels.any(label::contains) }

        val minRowHeight = (snapshot.height * 0.045f).toInt().coerceAtLeast(44)
        val maxRowHeight = (snapshot.height * 0.19f).toInt().coerceAtLeast(minRowHeight + 1)
        val minRowWidth = (snapshot.width * 0.68f).toInt()
        val contentTop = (snapshot.height * 0.08f).toInt()

        val rowNodes = snapshot.nodes.asSequence()
            .filter { it.enabled && it.clickable }
            .filter { it.bounds.width >= minRowWidth }
            .filter { it.bounds.height in minRowHeight..maxRowHeight }
            .filter { it.bounds.top >= contentTop && it.bounds.bottom <= snapshot.height }
            .distinctBy { listOf(it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom) }
            .sortedBy { it.bounds.top }
            .toList()

        val groups = rowNodes.mapNotNull { row -> parseRow(snapshot, row) }
            .distinctBy { it.normalizedName }

        return ParsedGroupScreen(
            groups = groups,
            screenFingerprint = fingerprint(groups.map { it.rowFingerprint }),
            looksLikeGroupList = looksLikeGroupList || groups.size >= 2,
        )
    }

    private fun parseRow(snapshot: WhatsAppUiSnapshot, row: WhatsAppUiNode): ParsedGroupCandidate? {
        val inside = snapshot.nodes.asSequence()
            .filter { node -> row.bounds.contains(node.bounds) }
            .filter { node -> node.bounds.width > 0 && node.bounds.height > 0 }
            .toList()

        val textNodes = inside.mapNotNull { node ->
            val raw = cleanLabel(node.text) ?: cleanLabel(node.contentDescription) ?: return@mapNotNull null
            val normalized = normalize(raw)
            if (!isPossibleTitle(normalized)) return@mapNotNull null
            node to raw
        }

        if (textNodes.isEmpty()) return null

        // WhatsApp chat rows normally render the title on the upper line. Time/count labels
        // are filtered out above, so the upper-most remaining label is the safest candidate.
        val titlePair = textNodes
            .sortedWith(compareBy<Pair<WhatsAppUiNode, String>> { it.first.bounds.top }.thenByDescending { it.first.bounds.left })
            .firstOrNull() ?: return null

        val displayName = titlePair.second.trim()
        val normalizedName = normalize(displayName)
        if (normalizedName.length < 2) return null

        val unread = inside.any { node ->
            val descriptor = normalize(listOfNotNull(node.text, node.contentDescription, node.viewId).joinToString(" "))
            "unread" in descriptor || "غير مقرو" in descriptor || "messages unread" in descriptor
        }
        val locked = inside.any { node ->
            val descriptor = normalize(listOfNotNull(node.text, node.contentDescription, node.viewId).joinToString(" "))
            "locked chat" in descriptor || "chat lock" in descriptor || "مقفل" in descriptor || "مقفلة" in descriptor
        }

        val confidence = when {
            row.clickable && textNodes.size >= 2 -> "HIGH"
            row.clickable -> "MEDIUM"
            else -> "LOW"
        }
        val rowFingerprint = fingerprint(
            listOf(
                normalizedName,
                row.bounds.top.toString(),
                row.bounds.bottom.toString(),
                unread.toString(),
                locked.toString(),
            )
        )

        return ParsedGroupCandidate(
            displayName = displayName,
            normalizedName = normalizedName,
            isUnread = unread,
            isLocked = locked,
            confidence = confidence,
            rowFingerprint = rowFingerprint,
        )
    }

    private fun isPossibleTitle(value: String): Boolean {
        if (value.isBlank() || value in ignoredExact) return false
        if (ignoredContains.any { token -> token in value }) return false
        if (value.length > 120) return false
        if (timeRegex.matches(value) || dateRegex.matches(value) || countRegex.matches(value)) return false
        if (value.all { it.isDigit() || it.isWhitespace() || it in "+-()/" }) return false
        return true
    }

    private fun cleanLabel(value: String?): String? = value
        ?.replace('\n', ' ')
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    private fun normalize(value: String): String = value
        .trim()
        .lowercase(Locale.ROOT)
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ى', 'ي')
        .replace('ة', 'ه')
        .replace(Regex("\\s+"), " ")

    private fun fingerprint(parts: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(parts.joinToString("|").toByteArray(Charsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }
}
