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
    private val chatLabels = setOf(
        "chats", "chat", "الدردشات", "المحادثات", "دردشات", "محادثات"
    )

    private val archivedLabels = setOf(
        "archived", "archived chats",
        "مؤرشفه", "المؤرشفه", "المؤرشف", "الدردشات المؤرشفه"
    )

    private val searchTokens = setOf(
        "search chats", "search", "بحث", "ابحث", "البحث"
    )

    /*
     * Do NOT treat generic "status/الحالة" as Channels. Those words can appear
     * inside normal chats and caused false navigation in earlier builds.
     */
    private val channelTokens = setOf(
        "channels", "channel", "updates",
        "القنوات", "القناه", "التحديثات"
    )

    private val communityTokens = setOf(
        "communities", "community", "المجتمعات", "مجتمع"
    )

    private val callsTokens = setOf(
        "calls", "call", "المكالمات", "مكالمات"
    )

    private val openChatTokens = setOf(
        "video call", "voice call", "contact info", "group info",
        "مكالمه فيديو", "مكالمه صوتيه", "معلومات جهه الاتصال", "معلومات القروب"
    )

    private val backTokens = setOf(
        "back", "navigate up", "رجوع", "الرجوع"
    )

    fun classify(
        snapshot: WhatsAppUiSnapshot,
        parsed: ParsedGroupScreen,
    ): WhatsAppSurface {
        if (snapshot.width <= 0 || snapshot.height <= 0 || snapshot.nodes.isEmpty()) {
            return WhatsAppSurface.UNKNOWN
        }

        val labels = snapshot.nodes.mapNotNull { node ->
            labelOf(node.text, node.contentDescription)
        }

        val topLabels = snapshot.nodes.asSequence()
            .filter { it.bounds.top <= snapshot.height * 0.34f }
            .mapNotNull { node -> labelOf(node.text, node.contentDescription) }
            .toList()

        val headerLabels = snapshot.nodes.asSequence()
            .filter { it.bounds.top <= snapshot.height * 0.20f }
            .mapNotNull { node -> labelOf(node.text, node.contentDescription) }
            .toList()

        val focusedSearch = snapshot.nodes.any { node ->
            node.focused &&
                (
                    node.editable ||
                        node.className?.contains("EditText", ignoreCase = true) == true
                    )
        }
        if (focusedSearch) return WhatsAppSurface.SEARCH

        /*
         * Negative surfaces are checked before the Chats fallback.
         * This is what prevents the old v0.3 bug where Channels was scrolled.
         */
        if (matchesSurface(headerLabels, channelTokens)) {
            return WhatsAppSurface.CHANNELS_OR_UPDATES
        }
        if (matchesSurface(headerLabels, communityTokens)) {
            return WhatsAppSurface.COMMUNITIES
        }
        if (matchesSurface(headerLabels, callsTokens)) {
            return WhatsAppSurface.CALLS
        }

        val chatVisible = labels.any(::isChatLabel)

        val hasBackAtTop = snapshot.nodes.asSequence()
            .filter { it.bounds.top <= snapshot.height * 0.20f }
            .mapNotNull { node -> labelOf(node.text, node.contentDescription) }
            .any { label -> backTokens.any { token -> label == token || label.contains(token) } }

        val archivedHeader = headerLabels.any { label ->
            archivedLabels.any { archived ->
                label == archived ||
                    label.startsWith("$archived ") ||
                    label.startsWith("$archived,") ||
                    label.startsWith("$archived،")
            }
        }

        if (archivedHeader && hasBackAtTop) {
            return WhatsAppSurface.ARCHIVED_LIST
        }

        if (
            hasBackAtTop &&
            headerLabels.any { label -> openChatTokens.any { token -> token in label } }
        ) {
            return WhatsAppSurface.OPEN_CHAT
        }

        val primaryScrollable = snapshot.nodes.any { node ->
            node.scrollable &&
                node.bounds.width >= (snapshot.width * 0.45f).toInt() &&
                node.bounds.height >= (snapshot.height * 0.22f).toInt()
        }

        val meaningfulContentCount = snapshot.nodes.asSequence()
            .filter {
                it.bounds.top >= snapshot.height * 0.08f &&
                    it.bounds.bottom <= snapshot.height * 0.92f
            }
            .flatMap { node -> sequenceOf(node.text, node.contentDescription) }
            .mapNotNull { value -> value?.takeIf { it.isNotBlank() }?.let(::normalize) }
            .filterNot { value ->
                value in chatLabels ||
                    value in archivedLabels ||
                    searchTokens.any { token -> token in value }
            }
            .distinct()
            .take(40)
            .count()

        /*
         * On Samsung/WhatsApp builds the RecyclerView or row clickability may not
         * be exposed. If the Chats tab/title is visible, and we already rejected
         * Channels/Communities/Calls above, Chats is the safest interpretation.
         */
        if (chatVisible) {
            return WhatsAppSurface.CHAT_LIST
        }

        if (parsed.looksLikeChatList || parsed.chatRowCount >= 2) {
            return WhatsAppSurface.CHAT_LIST
        }

        if (primaryScrollable && meaningfulContentCount >= 3) {
            return WhatsAppSurface.CHAT_LIST
        }

        val passiveSearch = topLabels.any { label ->
            searchTokens.any { token -> token in label }
        }
        if (passiveSearch && !primaryScrollable && parsed.chatRowCount == 0) {
            return WhatsAppSurface.SEARCH
        }

        if (hasBackAtTop) {
            return WhatsAppSurface.OPEN_CHAT
        }

        return WhatsAppSurface.UNKNOWN
    }

    private fun isChatLabel(label: String): Boolean =
        chatLabels.any { chat ->
            label == chat ||
                label.startsWith("$chat,") ||
                label.startsWith("$chat،") ||
                label.endsWith(" $chat") ||
                label.endsWith(", $chat") ||
                label.endsWith("، $chat") ||
                (label.contains("tab") && label.contains(chat)) ||
                (label.contains("علامه تبويب") && label.contains(chat))
        }

    private fun matchesSurface(
        labels: List<String>,
        tokens: Set<String>,
    ): Boolean =
        labels.any { label ->
            tokens.any { token ->
                label == token ||
                    label.startsWith("$token ") ||
                    label.startsWith("$token,") ||
                    label.startsWith("$token،") ||
                    (label.contains("tab") && label.contains(token)) ||
                    (label.contains("علامه تبويب") && label.contains(token))
            }
        }

    private fun labelOf(
        text: String?,
        description: String?,
    ): String? =
        listOfNotNull(text, description)
            .joinToString(" ")
            .takeIf { it.isNotBlank() }
            ?.let(::normalize)

    private fun normalize(value: String): String =
        value
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
