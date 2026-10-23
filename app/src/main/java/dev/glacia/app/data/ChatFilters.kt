package dev.glacia.app.data

/** Lightweight client-side filters, inspired by the idea of configurable chat tabs. */
enum class ChatFilter(val label: String) {
    ALL("All"),
    UNREAD("Unread"),
    GROUPS("Groups"),
    PRIVATE("Private")
}

object ChatFilters {
    fun apply(chats: List<Chat>, filter: ChatFilter, search: String = ""): List<Chat> {
        val term = search.trim()
        return chats.filter { chat ->
            val matchesCategory = when (filter) {
                ChatFilter.ALL -> true
                ChatFilter.UNREAD -> chat.unread > 0
                ChatFilter.GROUPS -> chat.isGroup
                ChatFilter.PRIVATE -> !chat.isGroup
            }
            matchesCategory &&
                (term.isEmpty() ||
                    chat.title.contains(term, ignoreCase = true) ||
                    chat.preview.contains(term, ignoreCase = true))
        }
    }
}
