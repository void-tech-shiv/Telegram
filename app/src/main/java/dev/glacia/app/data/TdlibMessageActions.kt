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
    val deleteForEveryone: Boolean = false,
    val reactionChecking: Boolean = false,
    val reactionChoices: List<String> = emptyList(),
    val reactionNotice: String? = null
)

/** All modifications are delegated to TDLib and authorized by Telegram. */
class TdlibMessageActions(
    private val client: () -> Client?,
    private val messages: TdlibMessages
) {
    private val lock = Any()
    // Monotonic request IDs prevent old Telegram callbacks updating a new
    // selection after the dialog is closed or the account is reauthorized.
    private var nextRequestId = 0L
    private val latestRequest = mutableMapOf<Pair<Long, Long>, Long>()
    private val _permissions = MutableStateFlow<Map<Pair<Long, Long>, MessageActionPermissions>>(emptyMap())
    val permissions = _permissions.asStateFlow()

    private fun error(chatId: Long, action: String, code: Int) {
        messages.reportError(chatId, "$action failed (Telegram code $code).")
    }

    fun properties(chatId: Long, messageId: Long) {
        if (chatId == 0L || messageId <= 0L) return
        val key = chatId to messageId
        val token: Long
        synchronized(lock) {
            token = ++nextRequestId
            latestRequest[key] = token
            _permissions.value = _permissions.value - key
        }
        val active = client()
        if (active == null) {
            synchronized(lock) {
                if (latestRequest[key] == token) {
                    _permissions.value = _permissions.value + (key to MessageActionPermissions(
                        reactionNotice = "Connect to Telegram to check available reactions."
                    ))
                }
            }
            return
        }

        // A call-history bubble is a Telegram service event. Whether a
        // service message supports reactions is server-specific, so don't
        // offer hardcoded emojis. Avoid sending known-invalid requests.
        val model = messages.messages.value[chatId]?.firstOrNull { it.id == messageId }
        val candidate = MessageReactionPolicy.eligibleForLookup(model)
        active.send(TdApi.GetMessageProperties(chatId, messageId)) { result ->
            when (result) {
                is TdApi.MessageProperties -> {
                    synchronized(lock) {
                        if (latestRequest[key] != token) return@synchronized
                        _permissions.value = _permissions.value + (key to MessageActionPermissions(
                            reply = result.canBeReplied,
                            forward = result.canBeForwarded,
                            edit = result.canBeEdited,
                            deleteForMe = result.canBeDeletedOnlyForSelf,
                            deleteForEveryone = result.canBeDeletedForAllUsers,
                            reactionChecking = candidate,
                            reactionNotice = if (candidate) null
                                else "Reactions aren't available for this call or pending message."
                        ))
                    }
                    if (!candidate) return@send
                    // Telegram supplies the actual eligible emojis for this
                    // message/chat. A hardcoded grid can yield HTTP/MTProto 400
                    // for service events, restricted chats and unsupported emoji.
                    active.send(TdApi.GetMessageAvailableReactions(chatId, messageId, 8)) { found ->
                        synchronized(lock) {
                            if (latestRequest[key] != token) return@synchronized
                            val current = _permissions.value[key] ?: return@synchronized
                            val next = when (found) {
                                is TdApi.AvailableReactions -> {
                                    val choices = listOf(
                                        found.topReactions,
                                        found.recentReactions,
                                        found.popularReactions
                                    ).flatMap { it?.toList().orEmpty() }
                                        .mapNotNull { available ->
                                            if (available.needsPremium) null
                                            else (available.type as? TdApi.ReactionTypeEmoji)?.emoji
                                        }
                                        .distinct()
                                        .take(12)
                                    current.copy(
                                        reactionChecking = false,
                                        reactionChoices = choices,
                                        reactionNotice = if (choices.isEmpty())
                                            "Telegram doesn't allow standard reactions here."
                                            else null
                                    )
                                }
                                is TdApi.Error -> current.copy(
                                    reactionChecking = false,
                                    reactionNotice = if (found.code == 400)
                                        "Telegram doesn't allow reactions on this message."
                                    else "Can't load available reactions (Telegram code ${found.code})."
                                )
                                else -> current.copy(reactionChecking = false,
                                    reactionNotice = "Couldn't check available reactions.")
                            }
                            _permissions.value = _permissions.value + (key to next)
                        }
                    }
                }
                is TdApi.Error -> {
                    synchronized(lock) {
                        if (latestRequest[key] != token) return@synchronized
                        _permissions.value = _permissions.value + (key to MessageActionPermissions(
                            reactionNotice = "Couldn't check message actions."
                        ))
                    }
                    error(chatId, "Message actions", result.code)
                }
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
        val model = messages.messages.value[chatId]?.firstOrNull { it.id == id }
        val props = synchronized(lock) { _permissions.value[chatId to id] }
        if (props == null || props.reactionChecking ||
            !MessageReactionPolicy.maySubmit(
                model, emoji, alreadyChosen, props.reactionChoices
            )
        ) {
            messages.reportError(chatId, "This reaction isn't available for that message.")
            return
        }
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
            if (result is TdApi.Error) {
                // Telegram owns the policy; it may change after the picker opened.
                if (result.code == 400) {
                    messages.reportError(
                        chatId,
                        "Telegram rejected this reaction. It may be unavailable for this message or chat."
                    )
                } else error(chatId, "Reaction", result.code)
            }
        } ?: messages.reportError(chatId, "Telegram connection unavailable.")
    }

    fun clear() {
        synchronized(lock) {
            latestRequest.clear()
            // nextRequestId remains monotonic to prevent old callbacks from
            // re-populating a newly authorized account with stale results.
            _permissions.value = emptyMap()
        }
    }
}
