package dev.glacia.app.data

data class Chat(
    val id: Long,
    val title: String,
    val username: String,
    val preview: String,
    val time: String,
    val unread: Int = 0,
    val isOnline: Boolean = false,
    val isGroup: Boolean = false,
    val pinned: Boolean = false,
    val photoFileId: Int? = null
)

data class MessageReactionSummary(val emoji: String, val count: Int, val chosen: Boolean)

data class Message(
    val id: Long,
    val chatId: Long,
    val text: String,
    val time: String,
    val outgoing: Boolean,
    val date: Int = 0,
    val sending: Boolean = false,
    val failed: Boolean = false,
    val mediaType: String? = null,
    val mediaFileId: Int? = null,
    val attachmentFileId: Int? = null,
    val attachmentName: String? = null,
    val attachmentMime: String? = null,
    val playbackFileId: Int? = null,
    val playbackDurationSeconds: Int = 0,
    val replyToMessageId: Long? = null,
    val edited: Boolean = false,
    val reactions: List<MessageReactionSummary> = emptyList()
)

interface ChatRepository {
    fun chats(): List<Chat>
    fun messages(chatId: Long): List<Message>
}

/** Offline-only sample data. Nothing here is read from or sent to Telegram. */
class DemoChatRepository : ChatRepository {
    private val demoChats = listOf(
        Chat(1, "Design Collective", "@designcollective", "Maya: the glass UI is looking clean ✨", "now", 12, isGroup = true, pinned = true),
        Chat(2, "Aarav Mehta", "@aarav", "That animation is seriously smooth.", "12:48", 2, isOnline = true),
        Chat(3, "Nina Kapoor", "@ninak", "Let's ship the first prototype 🚀", "11:32", isOnline = true),
        Chat(4, "GLACIA Updates", "@glacia", "Welcome to your demo workspace", "Yesterday", isGroup = true),
        Chat(5, "Dev Circle", "@devcircle", "Sam: Just pushed the new theme", "Yesterday", 3, isGroup = true),
        Chat(6, "Riya Sharma", "@riya", "Love the frosted panels!", "Mon"),
        Chat(7, "Photography Club", "@photos", "New photos are ready", "Sun", isGroup = true),
        Chat(8, "Karan", "@karan", "See you tomorrow 👋", "Sun")
    )

    override fun chats(): List<Chat> = demoChats

    override fun messages(chatId: Long): List<Message> = listOf(
        Message(101, chatId, "Hey! Welcome to the GLACIA preview.", "10:24", false),
        Message(102, chatId, "The new glass UI looks incredible.", "10:26", false),
        Message(103, chatId, "Everything here is demo data for now.", "10:27", true),
        Message(104, chatId, "Try sending a message — it stays on this device.", "10:29", false)
    )
}
