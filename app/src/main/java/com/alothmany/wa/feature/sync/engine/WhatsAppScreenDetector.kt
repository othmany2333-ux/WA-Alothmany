package com.alothmany.wa.feature.sync.engine

import com.alothmany.wa.feature.sync.model.ParsedGroupScreen
import com.alothmany.wa.system.accessibility.WhatsAppUiSnapshot
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class WhatsAppSurface {
    CHAT_LIST,
    ARCHIVED_LIST,
    SEARCH,
    CHANNELS_OR_UPDATES,
    COMMUNITIES,
    CALLS,
    OPEN_CHAT,
    UNKNOWN,
}

@Singleton
class WhatsAppScreenDetector @Inject constructor() {
    private val chatLabels = setOf("chats", "chat", "المحادثات", "الدردشات", "محادثات", "دردشات")
    private val archivedLabels = setOf("archived", "archived chats", "مؤرشفه", "المؤرشفه", "المؤرشف", "الدردشات المؤرشفه")
    private val searchTokens = setOf("search chats", "search", "بحث", "ابحث")
    private val channelTokens = setOf(
        "channels", "channel", "updates", "status", "statuses",
        "القنوات", "القناه", "التحديثات", "الحاله"
    )
    private val communityTokens = setOf("communities", "community", "المجتمعات", "مجتمع")
    private val callsTokens = setOf("calls", "call", "المكالمات", "مكالمات")
    private val openChatTokens = setOf(
        "video call", "voice call", "contact info",
        "مكالمه فيديو", "مكالمه صوتيه", "معلومات جهه الاتصال"
    )
    private val chromeTokens = setOf(
        "new chat", "محادثه جديده", "camera", "كاميرا",
        "more options", "المزيد من الخيارات", "menu", "القائمه"
    )

    fun classify(snapshot: WhatsAppUiSnapshot, parsed: ParsedGroupScreen): WhatsAppSurface {
        if (snapshot.width <= 0 || snapshot.height <= 0 || snapshot.nodes.isEmpty()) {
            return WhatsAppSurface.UNKNOWN
        }

        val labels = snapshot.nodes.mapNotNull { node ->
            listOfNotNull(node.text, node.contentDescription)
                .joinToString(" ")
                .takeIf { it.isNotBlank() }
                ?.let(::normalize)
        }

        val topLabels = snapshot.nodes.asSequence()
            .filter { it.bounds.top <= snapshot.height * 0.30f }
            .mapNotNull { node ->
                listOfNotNull(node.text, node.contentDescription)
                    .joinToString(" ")
                    .takeIf { it.isNotBlank() }
                    ?.let(::normalize)
            }
            .toList()

        val contentLabels = snapshot.nodes.asSequence()
            .filter { node ->
                node.bounds.top >= snapshot.height * 0.08f &&
                    node.bounds.bottom <= snapshot.height * 0.92f
            }
            .flatMap { node -> sequenceOf(node.text, node.contentDescription) }
            .mapNotNull { value -> value?.takeIf { it.isNotBlank() }?.let(::normalize) }
            .filterNot { value ->
                value in chatLabels ||
                    value in archivedLabels ||
                    searchTokens.any { it in value } ||
                    chromeTokens.any { it in value }
            }
            .distinct()
            .take(80)
            .toList()

        // A passive search box is normal on current WhatsApp. It becomes SEARCH
        // only when Android reports the editable field as focused.
        val focusedSearch = snapshot.nodes.any { node ->
            node.focused &&
                (node.editable || node.className?.contains("EditText", ignoreCase = true) == true)
        }
        if (focusedSearch) return WhatsAppSurface.SEARCH

        if (matchesTopSurface(topLabels, channelTokens)) return WhatsAppSurface.CHANNELS_OR_UPDATES
        if (matchesTopSurface(topLabels, communityTokens)) return WhatsAppSurface.COMMUNITIES
        if (matchesTopSurface(topLabels, callsTokens)) return WhatsAppSurface.CALLS

        val topArchived = topLabels.any { label ->
            archivedLabels.any { archived -> label == archived || label.startsWith("$archived ") }
        }

        val hasPrimaryScrollable = snapshot.nodes.any { node ->
            node.scrollable &&
                node.bounds.width >= (snapshot.width * 0.45f).toInt() &&
                node.bounds.height >= (snapshot.height * 0.22f).toInt()
        }

        val chatTabVisible = labels.any(::isChatLabel)
        val chatHeaderVisible = topLabels.any(::isChatLabel)
        val listEvidence = parsed.looksLikeChatList ||
            parsed.chatRowCount >= 1 ||
            hasPrimaryScrollable ||
            contentLabels.size >= 4

        if (topArchived && listEvidence) return WhatsAppSurface.ARCHIVED_LIST

        // Explicit open-chat chrome must win over a relaxed list fallback.
        if (topLabels.any { label -> openChatTokens.any { it in label } } && !chatTabVisible) {
            return WhatsAppSurface.OPEN_CHAT
        }

        val negativeTopSurface = containsNegativeSurface(topLabels)

        // Strongest rule: visible Chats tab/title + any credible list evidence.
        if ((chatTabVisible || chatHeaderVisible) && listEvidence && !negativeTopSurface) {
            return WhatsAppSurface.CHAT_LIST
        }

        // OEM/WhatsApp variants can omit the selected tab label from the tree.
        // Multiple inferred rows are enough as long as the screen is not a known
        // negative surface and search is not focused.
        if (parsed.looksLikeChatList && !negativeTopSurface) {
            return WhatsAppSurface.CHAT_LIST
        }

        if (parsed.chatRowCount >= 2 && !negativeTopSurface) {
            return WhatsAppSurface.CHAT_LIST
        }

        // Last-resort candidate: a large vertical container and enough independent
        // text items. This is deliberately conservative and never overrides known
        // Channels/Communities/Calls/Search surfaces.
        if (hasPrimaryScrollable && contentLabels.size >= 4 && !negativeTopSurface) {
            return WhatsAppSurface.CHAT_LIST
        }

        val passiveSearchLabel = topLabels.any { label -> searchTokens.any { token -> token in label } }
        if (passiveSearchLabel && !listEvidence) return WhatsAppSurface.SEARCH

        if (topLabels.any { label -> openChatTokens.any { it in label } }) {
            return WhatsAppSurface.OPEN_CHAT
        }

        return WhatsAppSurface.UNKNOWN
    }

    private fun isChatLabel(label: String): Boolean = chatLabels.any { chat ->
        label == chat ||
            label.startsWith("$chat,") ||
            label.startsWith("$chat،") ||
            label.endsWith(" $chat") ||
            label.endsWith("، $chat") ||
            label.endsWith(", $chat") ||
            (label.contains("tab") && label.contains(chat)) ||
            (label.contains("علامه تبويب") && label.contains(chat))
    }

    private fun matchesTopSurface(labels: List<String>, tokens: Set<String>): Boolean = labels.any { label ->
        tokens.any { token ->
            label == token ||
                label.startsWith("$token ") ||
                label.startsWith("$token,") ||
                label.startsWith("$token،") ||
                (label.contains("tab") && label.contains(token)) ||
                (label.contains("علامه تبويب") && label.contains(token))
        }
    }

    private fun containsNegativeSurface(labels: List<String>): Boolean =
        matchesTopSurface(labels, channelTokens) ||
            matchesTopSurface(labels, communityTokens) ||
            matchesTopSurface(labels, callsTokens)

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
}
