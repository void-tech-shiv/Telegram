package dev.glacia.app

import dev.glacia.app.data.DemoChatRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoChatRepositoryTest {
    @Test fun chatsContainUniqueIds() {
        val chats = DemoChatRepository().chats()
        assertEquals(chats.size, chats.map { it.id }.toSet().size)
        assertTrue(chats.isNotEmpty())
    }

    @Test fun messagesMatchSelectedChat() {
        val repo = DemoChatRepository()
        val chat = repo.chats().first()
        assertTrue(repo.messages(chat.id).all { it.chatId == chat.id })
    }
}
