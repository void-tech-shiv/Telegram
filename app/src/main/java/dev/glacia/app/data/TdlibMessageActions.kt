package dev.glacia.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

data class MessageActionPermissions(
    val reply: Boolean = false,
    val forward: Boolean = false,
    val edit: Boolean = false,
    val deleteForMe: Boolean = false,
    val deleteForEveryone: Boolean = false
)

/** All modifications are delegated to TDLib and authorized by Telegram. */
class TdlibMessageActions(
    private val client: () -> Client?,
    private val messages: TdlibMessages
) {
    private val lock = Any()
    private val _permissions = MutableStateFlow<Map<Pair<Long, Long>, MessageActionPermissions>>(emptyMap())
    val permissions = _permissions.asStateFlow()

    private fun error(chatId: Long, action: String, code: Int) {
        messages.reportError(chatId, "$action failed (Telegram code $code).")
    }

    fun properties(chatId: Long, messageId: Long) {
        if (messageId <= 0) return
        client()?.send(TdApi.GetMessageProperties(chatId, messageId)) { result ->
            when (result) {
                is TdApi.MessageProperties -> synchronized(lock) {
                    _permissions.value = _permissions.value + (
                        (chatId to messageId) to MessageActionPermissions(
                            reply = result.canBeReplied,
                            forward = result.canBeForwarded,
                            edit = result.canBeEdited,
                            deleteForMe = result.canBeDeletedOnlyForSelf,
                            deleteForEveryone = result.canBeDeletedForAllUsers
                        )
                    )
                }
                is TdApi.Error -> error(chatId, "Message actions", result.code)
            }
        }
    }

    fun forward(fromChat: Long, id: Long, toChat: Long) {
        if (fromChat == 0L || toChat == 0L || id <= 0L) return
        client()?.send(TdApi.ForwardMessages().apply {
            chatId = toChat
            fromChatId = fromChat
            messageIds = longArrayOf(id)
        }) { reply ->
            when (reply) {
                is TdApi.Messages -> {
                    reply.messages?.filterNotNull()?.forEach(messages::addOutgoing)
                }
                is TdApi.Error -> error(fromChat, "Forward", reply.code)
            }
        } ?: messages.reportError(fromChat, "Telegram connection unavailable.")
    }

    fun edit(chatId: Long, id: Long, text: String) {
        val body = text.trim()
        if (id <= 0 || body.isBlank() || body.length > 4096) {
            messages.reportError(chatId, "Edited text must be 1–4096 characters.")
            return
        }
        client()?.send(TdApi.EditMessageText().apply {
            this.chatId = chatId
            messageId = id
            inputMessageContent = TdApi.InputMessageText().apply {
                this.text = TdApi.FormattedText().apply { this.text = body }
            }
        }) { reply ->
            when (reply) {
                is TdApi.Message -> messages.addOutgoing(reply)
                is TdApi.Error -> error(chatId, "Edit", reply.code)
            }
        } ?: messages.reportError(chatId, "Telegram connection unavailable.")
    }

    fun delete(chatId: Long, id: Long, everyone: Boolean) {
        if (id <= 0L) return
        client()?.send(TdApi.DeleteMessages(chatId, longArrayOf(id), everyone)) { reply ->
            when (reply) {
                is TdApi.Ok -> messages.removeLocal(chatId, setOf(id))
                is TdApi.Error -> error(chatId, "Delete", reply.code)
            }
        } ?: messages.reportError(chatId, "Telegram connection unavailable.")
    }

    fun react(chatId: Long, id: Long, emoji: String, alreadyChosen: Boolean) {
        if (id <= 0L || emoji !in setOf("👍", "❤️", "😂", "🔥", "😮", "😢", "👏")) return
        val request: TdApi.Function<*> = if (alreadyChosen) {
            TdApi.RemoveMessageReaction().apply {
                this.chatId = chatId
                messageId = id
                reactionType = TdApi.ReactionTypeEmoji(emoji)
            }
        } else {
            TdApi.AddMessageReaction().apply {
                this.chatId = chatId
                messageId = id
                reactionType = TdApi.ReactionTypeEmoji(emoji)
                isBig = false
                updateRecentReactions = true
            }
        }
        client()?.send(request) { result ->
            if (result is TdApi.Error) error(chatId, "Reaction", result.code)
        } ?: messages.reportError(chatId, "Telegram connection unavailable.")
    }

    fun clear() { synchronized(lock) { _permissions.value = emptyMap() } }
}
