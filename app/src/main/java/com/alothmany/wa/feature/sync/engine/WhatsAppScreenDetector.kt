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
    private val channelTokens = setOf("channels", "channel", "updates", "القنوات", "القناه", "التحديثات")
    private val communityTokens = setOf("communities", "community", "المجتمعات", "مجتمع")
    private val callsTokens = setOf("calls", "call", "المكالمات", "مكالمات")
    private val openChatTokens = setOf("video call", "voice call", "مكالمه فيديو", "مكالمه صوتيه", "معلومات جهه الاتصال")

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

        val hasEditText = snapshot.nodes.any { it.className?.contains("EditText", ignoreCase = true) == true }
        if (hasEditText || topLabels.any { label -> searchTokens.any { it in label } }) return WhatsAppSurface.SEARCH
        if (topLabels.any { label -> channelTokens.any { token -> label == token || label.startsWith("$token ") } }) {
            return WhatsAppSurface.CHANNELS_OR_UPDATES
        }
        if (topLabels.any { label -> communityTokens.any { token -> label == token || label.startsWith("$token ") } }) {
            return WhatsAppSurface.COMMUNITIES
        }
        if (topLabels.any { label -> callsTokens.any { token -> label == token || label.startsWith("$token ") } }) {
            return WhatsAppSurface.CALLS
        }

        val topArchived = topLabels.any { label -> archivedLabels.any { archived -> label == archived || label.startsWith("$archived ") } }
        if (topArchived && parsed.chatRowCount > 0) return WhatsAppSurface.ARCHIVED_LIST

        val chatTabVisible = labels.any { label ->
            chatLabels.any { chat ->
                label == chat || label.startsWith("$chat,") || label.startsWith("$chat،") ||
                    label.endsWith(" $chat") || label.endsWith("، $chat") || label.endsWith(", $chat") ||
                    (label.contains("tab") && label.contains(chat)) ||
                    (label.contains("علامه تبويب") && label.contains(chat))
            }
        }
        if (parsed.chatRowCount >= 2 && chatTabVisible) return WhatsAppSurface.CHAT_LIST
        if (parsed.chatRowCount >= 3 && !containsNegativeSurface(topLabels)) return WhatsAppSurface.CHAT_LIST

        if (topLabels.any { label -> openChatTokens.any { it in label } }) return WhatsAppSurface.OPEN_CHAT
        return WhatsAppSurface.UNKNOWN
    }

    private fun containsNegativeSurface(labels: List<String>): Boolean = labels.any { label ->
        channelTokens.any { it in label } || communityTokens.any { it in label } || callsTokens.any { it in label }
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
