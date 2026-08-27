package com.alothmany.wa.feature.sync.engine

import com.alothmany.wa.feature.sync.model.ParsedGroupCandidate
import com.alothmany.wa.feature.sync.model.ParsedGroupScreen
import com.alothmany.wa.system.accessibility.RectSnapshot
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

        val rowNodes = discoverRowNodes(
            snapshot = snapshot,
            minRowHeight = minRowHeight,
            maxRowHeight = maxRowHeight,
            minRowWidth = minRowWidth,
            contentTop = contentTop,
        )

        val parsedRows = rowNodes
            .mapNotNull { row -> parseRow(snapshot, row) }
            .distinctBy { it.observationFingerprint }

        val groups = parsedRows.mapNotNull { it.group }
            .distinctBy { it.identityFingerprint }

        val fallbackFingerprintParts = snapshot.nodes.asSequence()
            .filter { it.bounds.top >= contentTop && it.bounds.bottom <= (snapshot.height * 0.92f).toInt() }
            .flatMap { node -> sequenceOf(node.text, node.contentDescription) }
            .mapNotNull(::cleanLabel)
            .map(::normalize)
            .filter { it.isNotBlank() }
            .filterNot { it in ignoredExact }
            .take(120)
            .toList()

        val screenParts = parsedRows
            .map { it.observationFingerprint }
            .ifEmpty { fallbackFingerprintParts }

        val hasPrimaryScrollableList = snapshot.nodes.any { node ->
            node.scrollable &&
                node.bounds.width >= (snapshot.width * 0.52f).toInt() &&
                node.bounds.height >= (snapshot.height * 0.28f).toInt()
        }

        return ParsedGroupScreen(
            groups = groups,
            screenFingerprint = fingerprint(screenParts),
            looksLikeChatList = parsedRows.size >= 2 || hasPrimaryScrollableList,
            hasArchivedEntry = hasArchivedEntry,
            chatRowCount = parsedRows.size,
        )
    }

    /**
     * WhatsApp builds chat rows differently across releases/OEMs. Some versions
     * expose the whole row as clickable; others expose a non-clickable container.
     * We therefore discover rows from geometry + contained text and add a
     * text-band fallback when no stable row container is exposed.
     */
    private fun discoverRowNodes(
        snapshot: WhatsAppUiSnapshot,
        minRowHeight: Int,
        maxRowHeight: Int,
        minRowWidth: Int,
        contentTop: Int,
    ): List<WhatsAppUiNode> {
        val contentBottom = (snapshot.height * 0.92f).toInt()

        val structural = snapshot.nodes.asSequence()
            .filter { it.enabled && !it.scrollable }
            .filter { it.bounds.width >= minRowWidth }
            .filter { it.bounds.height in minRowHeight..maxRowHeight }
            .filter { it.bounds.top >= contentTop && it.bounds.bottom <= contentBottom }
            .filter { node ->
                node.clickable || meaningfulTextCount(snapshot, node) >= 2
            }
            .toList()

        val inferred = inferTextBandRows(snapshot, contentTop, contentBottom, maxRowHeight)
        val all = structural + inferred
        if (all.isEmpty()) return emptyList()

        val tolerance = (snapshot.height * 0.025f).toInt().coerceAtLeast(18)
        val selected = mutableListOf<WhatsAppUiNode>()

        all.sortedBy { it.bounds.top }.forEach { candidate ->
            val center = (candidate.bounds.top + candidate.bounds.bottom) / 2
            val existingIndex = selected.indexOfFirst { existing ->
                val existingCenter = (existing.bounds.top + existing.bounds.bottom) / 2
                kotlin.math.abs(existingCenter - center) <= tolerance
            }

            if (existingIndex < 0) {
                selected += candidate
            } else {
                val existing = selected[existingIndex]
                if (rowQuality(snapshot, candidate) > rowQuality(snapshot, existing)) {
                    selected[existingIndex] = candidate
                }
            }
        }

        return selected.sortedBy { it.bounds.top }
    }

    private fun inferTextBandRows(
        snapshot: WhatsAppUiSnapshot,
        contentTop: Int,
        contentBottom: Int,
        maxRowHeight: Int,
    ): List<WhatsAppUiNode> {
        val textNodes = snapshot.nodes.asSequence()
            .filter { it.bounds.width > 0 && it.bounds.height > 0 }
            .filter { it.bounds.top >= contentTop && it.bounds.bottom <= contentBottom }
            .filter { node ->
                val raw = cleanLabel(node.text) ?: cleanLabel(node.contentDescription)
                raw != null && normalize(raw).let { value ->
                    value.isNotBlank() && value !in ignoredExact && value.length <= 180
                }
            }
            .sortedBy { (it.bounds.top + it.bounds.bottom) / 2 }
            .toList()

        if (textNodes.size < 2) return emptyList()

        val bandGap = (snapshot.height * 0.043f).toInt().coerceAtLeast(36)
        val bands = mutableListOf<MutableList<WhatsAppUiNode>>()

        textNodes.forEach { node ->
            val center = (node.bounds.top + node.bounds.bottom) / 2
            val current = bands.lastOrNull()
            if (current == null) {
                bands += mutableListOf(node)
            } else {
                val currentCenter = current.map { (it.bounds.top + it.bounds.bottom) / 2 }.average().toInt()
                if (kotlin.math.abs(center - currentCenter) <= bandGap) {
                    current += node
                } else {
                    bands += mutableListOf(node)
                }
            }
        }

        return bands.mapNotNull { band ->
            val labels = band.mapNotNull { node ->
                cleanLabel(node.text) ?: cleanLabel(node.contentDescription)
            }.map(::normalize).distinct()

            val plausibleTitles = labels.filter(::isPossibleTitle)
            if (labels.size < 2 || plausibleTitles.isEmpty()) return@mapNotNull null

            val top = (band.minOf { it.bounds.top } - 8).coerceAtLeast(contentTop)
            val bottom = (band.maxOf { it.bounds.bottom } + 8).coerceAtMost(contentBottom)
            if (bottom <= top || bottom - top > maxRowHeight) return@mapNotNull null

            WhatsAppUiNode(
                text = null,
                contentDescription = null,
                className = "synthetic.ChatRow",
                viewId = "synthetic_chat_row",
                clickable = false,
                scrollable = false,
                enabled = true,
                depth = 0,
                bounds = RectSnapshot(0, top, snapshot.width, bottom),
            )
        }
    }

    private fun meaningfulTextCount(snapshot: WhatsAppUiSnapshot, row: WhatsAppUiNode): Int =
        snapshot.nodes.asSequence()
            .filter { node -> row.bounds.contains(node.bounds) }
            .flatMap { node -> sequenceOf(node.text, node.contentDescription) }
            .mapNotNull(::cleanLabel)
            .map(::normalize)
            .filter { it.isNotBlank() && it !in ignoredExact }
            .filterNot { timeRegex.matches(it) || dateRegex.matches(it) || countRegex.matches(it) }
            .distinct()
            .take(6)
            .count()

    private fun rowQuality(snapshot: WhatsAppUiSnapshot, row: WhatsAppUiNode): Int {
        val textScore = meaningfulTextCount(snapshot, row).coerceAtMost(6) * 18
        val clickScore = if (row.clickable) 80 else 0
        val realContainerScore = if (row.viewId == "synthetic_chat_row") 0 else 24
        val widthScore = (row.bounds.width * 12 / snapshot.width.coerceAtLeast(1)).coerceAtMost(12)
        return textScore + clickScore + realContainerScore + widthScore
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
            .filter { (_, raw) -> isPossibleTitle(normalize(raw)) }
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
