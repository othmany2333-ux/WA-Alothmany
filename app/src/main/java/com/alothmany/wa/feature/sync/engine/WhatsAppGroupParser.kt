package com.alothmany.wa.feature.sync.engine

import com.alothmany.wa.feature.sync.model.ParsedGroupCandidate
import com.alothmany.wa.feature.sync.model.ParsedGroupScreen
import com.alothmany.wa.system.accessibility.WhatsAppUiNode
import com.alothmany.wa.system.accessibility.WhatsAppUiSnapshot
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses the normal WhatsApp chat list and returns only rows that have credible
 * group evidence. It intentionally does not depend on WhatsApp's Groups search
 * filter; the sync engine walks the complete Chats list and the Archived list.
 *
 * The screen fingerprint is built from ALL chat rows, not only detected groups.
 * That prevents the end detector from stopping on a screen that happens to have
 * only personal chats.
 */
@Singleton
class WhatsAppGroupParser @Inject constructor() {
    // UI labels are normalized before comparison. Arabic normalization changes
    // taa marbuta (ة) to haa (ه), so keep normalized archived variants here too.
    private val archivedLabels = setOf(
        "archived", "archived chats",
        "مؤرشفة", "مؤرشفه",
        "المؤرشفة", "المؤرشفه",
        "المؤرشف",
        "الدردشات المؤرشفة", "الدردشات المؤرشفه"
    )

    private val ignoredExact = setOf(
        "chats", "chat", "المحادثات", "الدردشات",
        "groups", "group", "المجموعات", "القروبات", "مجموعات", "قروبات",
        "unread", "غير مقروءة", "غير المقروءة",
        "favourites", "favorites", "المفضلة",
        "archived", "archived chats", "مؤرشفة", "المؤرشفة", "المؤرشف", "الدردشات المؤرشفة",
        "communities", "المجتمعات", "community", "مجتمع",
        "search", "بحث", "new chat", "محادثة جديدة",
        "updates", "التحديثات", "channels", "القنوات", "calls", "المكالمات",
    )

    private val ignoredContains = setOf(
        "ask meta ai", "meta ai", "search chats", "search",
        "اسال meta ai", "اسأل meta ai", "ابحث", "البحث"
    )

    private val groupEvidenceTokens = setOf(
        "group", "groups", "group chat", "group icon", "group photo",
        "مجموعة", "مجموعه", "قروب", "قروبات", "مجموعات",
        "participants", "participant", "members", "member",
        "مشاركين", "مشاركون", "أعضاء", "اعضاء",
    )

    private val mentionTokens = setOf(
        "mention", "mentioned", "mentions", "إشارة", "اشارة", "ذِكر", "ذكر"
    )

    private val timeRegex = Regex("^\\d{1,2}[:.]\\d{2}(?:\\s*[ap]m)?$", RegexOption.IGNORE_CASE)
    private val countRegex = Regex("^[+]?\\d{1,4}$")
    private val dateRegex = Regex("^(today|yesterday|اليوم|أمس|امس|\\d{1,2}[/.-]\\d{1,2}(?:[/.-]\\d{2,4})?)$", RegexOption.IGNORE_CASE)
    private val senderPrefixRegex = Regex("^(?:~\\s*)?[^:]{1,48}:\\s+.+$", setOf(RegexOption.IGNORE_CASE))

    fun parse(snapshot: WhatsAppUiSnapshot): ParsedGroupScreen {
        if (snapshot.width <= 0 || snapshot.height <= 0) {
            return ParsedGroupScreen(emptyList(), fingerprint(emptyList()), false)
        }

        val allRawLabels = snapshot.nodes
            .asSequence()
            .flatMap { sequenceOf(it.text, it.contentDescription) }
            .mapNotNull(::cleanLabel)
            .toList()
        val allLabels = allRawLabels.map(::normalize).toSet()
        val hasArchivedEntry = allLabels.any { label -> archivedLabels.any { archived -> label == archived || label.startsWith("$archived ") } }

        val minRowHeight = (snapshot.height * 0.045f).toInt().coerceAtLeast(44)
        val maxRowHeight = (snapshot.height * 0.20f).toInt().coerceAtLeast(minRowHeight + 1)
        val minRowWidth = (snapshot.width * 0.68f).toInt()
        val contentTop = (snapshot.height * 0.07f).toInt()

        val rowNodes = snapshot.nodes.asSequence()
            .filter { it.enabled && it.clickable }
            .filter { it.bounds.width >= minRowWidth }
            .filter { it.bounds.height in minRowHeight..maxRowHeight }
            .filter { it.bounds.top >= contentTop && it.bounds.bottom <= snapshot.height }
            .distinctBy { listOf(it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom) }
            .sortedBy { it.bounds.top }
            .toList()

        val parsedRows = rowNodes.mapNotNull { row -> parseRow(snapshot, row) }
        val groups = parsedRows.mapNotNull { it.group }
            .distinctBy { it.normalizedName }

        return ParsedGroupScreen(
            groups = groups,
            screenFingerprint = fingerprint(parsedRows.map { it.rowFingerprint }),
            looksLikeGroupList = parsedRows.size >= 2,
            hasArchivedEntry = hasArchivedEntry,
            chatRowCount = parsedRows.size,
        )
    }

    private data class ParsedRow(
        val rowFingerprint: String,
        val group: ParsedGroupCandidate?,
    )

    private fun parseRow(snapshot: WhatsAppUiSnapshot, row: WhatsAppUiNode): ParsedRow? {
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

        val sortedText = textNodes
            .sortedWith(
                compareByDescending<Pair<WhatsAppUiNode, String>> { !it.first.text.isNullOrBlank() }
                    .thenBy { it.first.bounds.top }
                    .thenByDescending { it.first.depth }
                    .thenByDescending { it.first.bounds.left }
            )

        // Prefer a real child TextView over a combined contentDescription placed on
        // the whole clickable row. The latter often contains title + preview + time.
        val titlePair = sortedText.firstOrNull() ?: return null
        val displayName = titlePair.second.trim()
        val normalizedName = normalize(displayName)
        if (normalizedName.length < 2) return null

        val remainingTexts = sortedText.drop(1).map { it.second.trim() }.filter { it.isNotBlank() }
        val descriptorRaw = inside.joinToString(" ") { node ->
            listOfNotNull(node.text, node.contentDescription, node.viewId).joinToString(" ")
        }
        val descriptor = normalize(descriptorRaw)

        val unread = "unread" in descriptor || "غير مقرو" in descriptor || "messages unread" in descriptor
        val locked = "locked chat" in descriptor || "chat lock" in descriptor || "مقفل" in descriptor || "مقفلة" in descriptor

        val strongTokenEvidence = groupEvidenceTokens.any { token -> token in descriptor }
        val mentionEvidence = mentionTokens.any { token -> token in descriptor } || inside.any { node -> cleanLabel(node.text) == "@" || cleanLabel(node.contentDescription) == "@" }
        val senderPrefixEvidence = remainingTexts.any(::looksLikeGroupPreview)
        val titleEvidence = groupEvidenceTokens.any { token -> token in normalizedName }
        val groupEvidence = strongTokenEvidence || mentionEvidence || senderPrefixEvidence || titleEvidence

        val genericRowFingerprint = fingerprint(
            buildList {
                add(normalizedName)
                remainingTexts.take(3).forEach { add(normalize(it)) }
                add(unread.toString())
                add(locked.toString())
            }
        )

        if (!groupEvidence) {
            return ParsedRow(rowFingerprint = genericRowFingerprint, group = null)
        }

        val confidence = when {
            strongTokenEvidence || mentionEvidence -> "HIGH"
            senderPrefixEvidence && remainingTexts.size >= 1 -> "HIGH"
            titleEvidence -> "MEDIUM"
            else -> "LOW"
        }

        return ParsedRow(
            rowFingerprint = genericRowFingerprint,
            group = ParsedGroupCandidate(
                displayName = displayName,
                normalizedName = normalizedName,
                isUnread = unread,
                isLocked = locked,
                confidence = confidence,
                rowFingerprint = genericRowFingerprint,
            )
        )
    }

    private fun looksLikeGroupPreview(raw: String): Boolean {
        val value = raw.trim()
        val normalized = normalize(value)
        if (senderPrefixRegex.matches(value)) return true
        if (normalized.startsWith("~ ") && normalized.length > 3) return true
        if (normalized.startsWith("you:") || normalized.startsWith("انت:") || normalized.startsWith("أنت:")) return true
        return false
    }

    private fun isPossibleTitle(value: String): Boolean {
        if (value.isBlank() || value in ignoredExact) return false
        if (ignoredContains.any { token -> token in value }) return false
        if (value.length > 160) return false
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
