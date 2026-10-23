package dev.glacia.app

import dev.glacia.app.data.Chat
import dev.glacia.app.data.ChatFilter
import dev.glacia.app.data.ChatFilters
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatFiltersTest {
    private val chats = listOf(
        Chat(id = 1, title = "Personal", username = "", preview = "hello",
            time = "", unread = 0, isGroup = false),
        Chat(id = 2, title = "Group", username = "", preview = "update",
            time = "", unread = 9, isGroup = true),
        Chat(id = 3, title = "Friend", username = "", preview = "a photo",
            time = "", unread = 1, isGroup = false)
    )

    @Test fun unreadFilterKeepsUnreadOnly() {
        assertEquals(listOf(2L, 3L), ChatFilters.apply(chats, ChatFilter.UNREAD).map { it.id })
    }
    @Test fun groupAndPrivateTabsAreDisjoint() {
        assertEquals(listOf(2L), ChatFilters.apply(chats, ChatFilter.GROUPS).map { it.id })
        assertEquals(listOf(1L, 3L), ChatFilters.apply(chats, ChatFilter.PRIVATE).map { it.id })
    }
    @Test fun searchMatchesTitleOrPreviewCaseInsensitively() {
        assertEquals(listOf(3L), ChatFilters.apply(chats, ChatFilter.ALL, "PHOTO").map { it.id })
        assertEquals(listOf(2L), ChatFilters.apply(chats, ChatFilter.UNREAD, "gRoUp").map { it.id })
        assertEquals(3, ChatFilters.apply(chats, ChatFilter.ALL).size)
    }
}
