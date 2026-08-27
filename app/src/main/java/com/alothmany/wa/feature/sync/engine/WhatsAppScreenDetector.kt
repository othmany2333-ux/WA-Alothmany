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

    fun classify(snapshot: WhatsAppUiSnapshot, parsed: ParsedGroupScreen): WhatsAppSurface {
        val labels = snapshot.nodes.mapNotNull { node ->
            listOfNotNull(node.text, node.contentDescription)
                .joinToString(" ")
                .takeIf { it.isNotBlank() }
                ?.let(::normalize)
        }

        val topLabels = snapshot.nodes.asSequence()
            .filter { it.bounds.top <= snapshot.height * 0.33f }
            .mapNotNull { node ->
                listOfNotNull(node.text, node.contentDescription)
                    .joinToString(" ")
                    .takeIf { it.isNotBlank() }
                    ?.let(::normalize)
            }
            .toList()

        // Modern WhatsApp may keep a passive search EditText on the Chats screen.
        // It is a SEARCH surface only when that field is actually focused.
        val focusedSearch = snapshot.nodes.any { node ->
            node.focused &&
                (node.editable || node.className?.contains("EditText", ignoreCase = true) == true)
        }
        if (focusedSearch) return WhatsAppSurface.SEARCH

        if (topLabels.any { label -> channelTokens.any { token -> label == token || label.startsWith("$token ") } }) {
            return WhatsAppSurface.CHANNELS_OR_UPDATES
        }
        if (topLabels.any { label -> communityTokens.any { token -> label == token || label.startsWith("$token ") } }) {
            return WhatsAppSurface.COMMUNITIES
        }
        if (topLabels.any { label -> callsTokens.any { token -> label == token || label.startsWith("$token ") } }) {
            return WhatsAppSurface.CALLS
        }

        val hasPrimaryScrollable = snapshot.nodes.any { node ->
            node.scrollable &&
                node.bounds.width >= (snapshot.width * 0.52f).toInt() &&
                node.bounds.height >= (snapshot.height * 0.28f).toInt()
        }

        val topArchived = topLabels.any { label ->
            archivedLabels.any { archived -> label == archived || label.startsWith("$archived ") }
        }
        if (topArchived && (parsed.chatRowCount > 0 || parsed.looksLikeChatList || hasPrimaryScrollable)) {
            return WhatsAppSurface.ARCHIVED_LIST
        }

        val chatTabVisible = labels.any { label ->
            chatLabels.any { chat ->
                label == chat ||
                    label.startsWith("$chat,") ||
                    label.startsWith("$chat،") ||
                    label.endsWith(" $chat") ||
                    label.endsWith("، $chat") ||
                    label.endsWith(", $chat") ||
                    (label.contains("tab") && label.contains(chat)) ||
                    (label.contains("علامه تبويب") && label.contains(chat))
            }
        }

        val negativeTopSurface = containsNegativeSurface(topLabels)

        if (chatTabVisible && hasPrimaryScrollable && !negativeTopSurface) {
            return WhatsAppSurface.CHAT_LIST
        }

        if (chatTabVisible && parsed.chatRowCount >= 1 && !negativeTopSurface) {
            return WhatsAppSurface.CHAT_LIST
        }

        if (parsed.looksLikeChatList && hasPrimaryScrollable && !negativeTopSurface) {
            return WhatsAppSurface.CHAT_LIST
        }

        if (parsed.chatRowCount >= 2 && !negativeTopSurface) {
            return WhatsAppSurface.CHAT_LIST
        }

        // A passive search label on Chats is not enough to call the whole screen SEARCH.
        val passiveSearchLabel = topLabels.any { label -> searchTokens.any { token -> token in label } }
        if (passiveSearchLabel && !hasPrimaryScrollable && parsed.chatRowCount == 0) {
            return WhatsAppSurface.SEARCH
        }

        if (topLabels.any { label -> openChatTokens.any { it in label } }) {
            return WhatsAppSurface.OPEN_CHAT
        }

        return WhatsAppSurface.UNKNOWN
    }

    private fun containsNegativeSurface(labels: List<String>): Boolean = labels.any { label ->
        channelTokens.any { it in label } ||
            communityTokens.any { it in label } ||
            callsTokens.any { it in label }
    }

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
