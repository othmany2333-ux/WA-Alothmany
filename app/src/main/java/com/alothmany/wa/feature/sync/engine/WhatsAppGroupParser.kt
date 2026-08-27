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
 * Reads every visible WhatsApp chat-list row, classifies it, and returns only
 * credible groups. Detection is multi-signal; no single word is trusted alone.
 */
@Singleton
class WhatsAppGroupParser @Inject constructor() {
    private val archivedLabels = setOf(
        "archived", "archived chats",
        "مؤرشفه", "المؤرشفه", "المؤرشف", "الدردشات المؤرشفه"
    )

    private val ignoredExact = setOf(
        "chats", "chat", "المحادثات", "الدردشات", "محادثات", "دردشات",
        "groups", "group", "المجموعات", "القروبات", "مجموعات", "قروبات",
        "unread", "غير مقروءه", "غير المقروءه",
        "favourites", "favorites", "المفضله",
        "archived", "archived chats", "مؤرشفه", "المؤرشفه", "المؤرشف", "الدردشات المؤرشفه",
        "communities", "المجتمعات", "community", "مجتمع",
        "search", "بحث", "new chat", "محادثه جديده",
        "updates", "التحديثات", "channels", "القنوات", "calls", "المكالمات",
    )

    private val ignoredContains = setOf(
        "ask meta ai", "meta ai", "search chats", "search",
        "اسال meta ai", "ابحث", "البحث"
    )

    private val strongGroupTokens = setOf(
        "group chat", "group icon", "group photo", "group info", "group subject",
        "participants", "participant", "members", "member",
        "مشاركين", "مشاركون", "اعضاء", "عضو", "صوره القروب", "معلومات القروب"
    )

    private val softGroupTokens = setOf(
        "group", "groups", "مجموعة", "مجموعه", "قروب", "قروبات", "مجموعات"
    )

    private val othersTokens = setOf(
        "others", "other participants", "and others", "اخرون", "وآخرون", "واخرون", "البقيه"
    )

    private val selfTokens = setOf("you", "you:", "انت", "انت:", "أنت", "أنت:")
    private val negativeSurfaceTokens = setOf(
        "channel", "channels", "newsletter", "follow channel", "القناه", "القنوات", "متابعه القناه"
    )

    private val timeRegex = Regex("^\\d{1,2}[:.]\\d{2}(?:\\s*[ap]m)?$", RegexOption.IGNORE_CASE)
    private val countRegex = Regex("^[+]?\\d{1,4}$")
    private val dateRegex = Regex("^(today|yesterday|اليوم|امس|\\d{1,2}[/.-]\\d{1,2}(?:[/.-]\\d{2,4})?)$", RegexOption.IGNORE_CASE)
    private val phoneRegex = Regex("(?<!\\d)\\+?\\d[\\d\\s().-]{5,}\\d(?!\\d)")
    private val namedSenderPrefixRegex = Regex("^(?:~\\s*)?([^:]{1,48}):\\s+.+$")

    fun parse(snapshot: WhatsAppUiSnapshot): ParsedGroupScreen {
        if (snapshot.width <= 0 || snapshot.height <= 0) {
            return ParsedGroupScreen(emptyList(), fingerprint(emptyList()), false)
        }

        val allNormalizedLabels = snapshot.nodes.asSequence()
            .flatMap { sequenceOf(it.text, it.contentDescription) }
            .mapNotNull(::cleanLabel)
            .map(::normalize)
            .toList()

        val hasArchivedEntry = allNormalizedLabels.any { label ->
            archivedLabels.any { archived -> label == archived || label.startsWith("$archived ") }
        }

        val minRowHeight = (snapshot.height * 0.043f).toInt().coerceAtLeast(42)
        val maxRowHeight = (snapshot.height * 0.22f).toInt().coerceAtLeast(minRowHeight + 1)
        val minRowWidth = (snapshot.width * 0.62f).toInt()
        val contentTop = (snapshot.height * 0.055f).toInt()

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
            .distinctBy { it.identityFingerprint }

        return ParsedGroupScreen(
            groups = groups,
            screenFingerprint = fingerprint(parsedRows.map { it.observationFingerprint }),
            looksLikeChatList = parsedRows.size >= 2,
            hasArchivedEntry = hasArchivedEntry,
            chatRowCount = parsedRows.size,
        )
    }

    private data class ParsedRow(
        val observationFingerprint: String,
        val group: ParsedGroupCandidate?,
    )

    private fun parseRow(snapshot: WhatsAppUiSnapshot, row: WhatsAppUiNode): ParsedRow? {
        val inside = snapshot.nodes.asSequence()
            .filter { node -> row.bounds.contains(node.bounds) }
            .filter { node -> node.bounds.width > 0 && node.bounds.height > 0 }
            .toList()
        if (inside.isEmpty()) return null

        val usableText = inside.mapNotNull { node ->
            val raw = cleanLabel(node.text) ?: cleanLabel(node.contentDescription) ?: return@mapNotNull null
            val normalized = normalize(raw)
            if (!isPossibleRowText(normalized)) return@mapNotNull null
            node to raw
        }.distinctBy { normalize(it.second) }

        if (usableText.isEmpty()) return null

        val titlePair = usableText
            .filter { (node, _) -> node.bounds.top <= row.bounds.top + (row.bounds.height * 0.62f) }
            .sortedWith(
                compareByDescending<Pair<WhatsAppUiNode, String>> { it.first.text != null }
                    .thenBy { it.first.bounds.top }
                    .thenByDescending { it.first.depth }
            )
            .firstOrNull()
            ?: usableText.minByOrNull { it.first.bounds.top }
            ?: return null

        val displayName = titlePair.second.trim()
        val normalizedName = normalize(displayName)
        if (!isPossibleTitle(normalizedName)) return null

        val subtitleCandidates = usableText
            .filter { it !== titlePair }
            .filter { (node, raw) ->
                node.bounds.top >= titlePair.first.bounds.top &&
                    normalize(raw) != normalizedName
            }
            .sortedBy { it.first.bounds.top }
            .map { it.second.trim() }
            .filter { it.isNotBlank() }

        val subtitle = subtitleCandidates.firstOrNull { value ->
            val normalized = normalize(value)
            !timeRegex.matches(normalized) && !dateRegex.matches(normalized) && !countRegex.matches(normalized)
        }

        val descriptorRaw = inside.joinToString(" ") { node ->
            listOfNotNull(node.text, node.contentDescription, node.viewId, node.className).joinToString(" ")
        }
        val descriptor = normalize(descriptorRaw)
        val normalizedSubtitle = subtitle?.let(::normalize).orEmpty()

        val unread = "unread" in descriptor || "غير مقرو" in descriptor
        val locked = "locked chat" in descriptor || "chat lock" in descriptor || "مقفل" in descriptor || "مقفله" in descriptor

        val evidence = linkedSetOf<String>()
        var score = 0

        val explicitGroupUi = strongGroupTokens.any { it in descriptor } ||
            inside.any { node -> normalize(node.viewId.orEmpty()).contains("group") }
        if (explicitGroupUi) {
            score += 6
            evidence += "GROUP_UI"
        }

        if (softGroupTokens.any { it in normalizedName }) {
            score += 2
            evidence += "GROUP_NAME_TOKEN"
        }

        val senderPrefix = senderPrefix(normalizedSubtitle)
        if (senderPrefix != null && senderPrefix !in selfTokens) {
            score += 4
            evidence += "SENDER_PREFIX"
        }

        val phones = phoneRegex.findAll(subtitle.orEmpty()).map { normalize(it.value) }.distinct().toList()
        if (phones.size >= 2) {
            score += 5
            evidence += "MULTIPLE_NUMBERS"
        } else if (phones.size == 1 && hasListSeparator(normalizedSubtitle)) {
            score += 2
            evidence += "NUMBER_LIST"
        }

        val participantLikeList = participantListScore(normalizedSubtitle)
        if (participantLikeList >= 2) {
            score += 3
            evidence += "PARTICIPANT_LIST"
        }

        if (othersTokens.any { it in normalizedSubtitle || it in descriptor }) {
            score += 4
            evidence += "OTHERS"
        }

        if ("@" in descriptor || "mention" in descriptor || "اشاره" in descriptor) {
            score += 2
            evidence += "MENTION"
        }

        // "You:" by itself is common in individual chats, so it is only a weak
        // supporting signal when another group clue already exists.
        if (selfTokens.any { token -> normalizedSubtitle.startsWith(token) }) {
            if (score > 0) score += 1
            evidence += "SELF_PREFIX"
        }

        if (negativeSurfaceTokens.any { it in descriptor }) {
            score -= 8
            evidence += "CHANNEL_NEGATIVE"
        }

        if (looksLikePhoneOnly(normalizedName) && score < 6) {
            score -= 3
            evidence += "PHONE_TITLE_NEGATIVE"
        }

        val structuralSignature = stableStructureSignature(inside, evidence)
        // Only include subtitle data in identity when it looks like a participant
        // roster (numbers/names/others), never when it is merely the volatile last message.
        val stableSubtitleHint = when {
            phones.size >= 2 -> phones.sorted().joinToString(",")
            participantLikeList >= 2 && senderPrefix == null -> normalizedSubtitle
            othersTokens.any { it in normalizedSubtitle } && senderPrefix == null -> normalizedSubtitle
            else -> ""
        }
        val identityFingerprint = fingerprint(
            listOf(
                normalizedName,
                stableSubtitleHint,
                structuralSignature,
                evidence.filterNot { it.endsWith("NEGATIVE") }.sorted().joinToString(","),
            )
        )

        val observationFingerprint = fingerprint(
            listOf(
                normalizedName,
                normalizedSubtitle,
                structuralSignature,
                unread.toString(),
                locked.toString(),
            )
        )

        val group = if (isCredibleGroup(score, evidence)) {
            ParsedGroupCandidate(
                displayName = displayName,
                normalizedName = normalizedName,
                subtitle = subtitle,
                isUnread = unread,
                isLocked = locked,
                confidence = when {
                    score >= 8 -> "HIGH"
                    score >= 5 -> "MEDIUM"
                    else -> "LOW"
                },
                evidenceScore = score,
                evidenceTags = evidence,
                identityFingerprint = identityFingerprint,
                observationFingerprint = observationFingerprint,
            )
        } else {
            null
        }

        return ParsedRow(observationFingerprint = observationFingerprint, group = group)
    }

    private fun isCredibleGroup(score: Int, evidence: Set<String>): Boolean {
        if ("CHANNEL_NEGATIVE" in evidence) return false
        if (score < 4) return false
        return evidence.any {
            it in setOf("GROUP_UI", "SENDER_PREFIX", "MULTIPLE_NUMBERS", "PARTICIPANT_LIST", "OTHERS")
        }
    }

    private fun senderPrefix(value: String): String? {
        val match = namedSenderPrefixRegex.find(value) ?: return null
        return normalize(match.groupValues[1]).trim().ifBlank { null }
    }

    private fun participantListScore(value: String): Int {
        if (value.isBlank()) return 0
        val parts = value.split(',', '،', '·', '•')
            .map { it.trim() }
            .filter { it.length in 2..40 }
            .filterNot { timeRegex.matches(it) || dateRegex.matches(it) }
        return parts.distinct().size
    }

    private fun hasListSeparator(value: String): Boolean =
        value.any { it == ',' || it == '،' || it == '·' || it == '•' }

    private fun stableStructureSignature(nodes: List<WhatsAppUiNode>, evidence: Set<String>): String {
        val classes = nodes.mapNotNull { it.className?.substringAfterLast('.') }
            .distinct().sorted().take(8).joinToString(",")
        val stableIds = nodes.mapNotNull { node ->
            node.viewId?.substringAfterLast('/')?.let(::normalize)
        }.filterNot { id ->
            id.contains("time") || id.contains("date") || id.contains("unread") || id.contains("badge")
        }.distinct().sorted().take(8).joinToString(",")
        val stableEvidence = evidence.filterNot {
            it in setOf("SELF_PREFIX", "MENTION", "NUMBER_LIST", "CHANNEL_NEGATIVE", "PHONE_TITLE_NEGATIVE")
        }.sorted().joinToString(",")
        return fingerprint(listOf(classes, stableIds, stableEvidence))
    }

    private fun looksLikePhoneOnly(value: String): Boolean =
        value.isNotBlank() && value.all { it.isDigit() || it.isWhitespace() || it in "+-()/" }

    private fun isPossibleRowText(value: String): Boolean {
        if (value.isBlank()) return false
        if (value.length > 180) return false
        return true
    }

    private fun isPossibleTitle(value: String): Boolean {
        if (value.isBlank() || value in ignoredExact) return false
        if (ignoredContains.any { token -> token in value }) return false
        if (value.length > 140) return false
        if (timeRegex.matches(value) || dateRegex.matches(value) || countRegex.matches(value)) return false
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
        .replace(Regex("[ًٌٍَُِّْـ]"), "")
        .replace(Regex("\\s+"), " ")

    private fun fingerprint(parts: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(parts.joinToString("|").toByteArray(Charsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }
}
