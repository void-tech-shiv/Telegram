package dev.glacia.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

data class CommunityChatInfo(
    val id: Long,
    val title: String,
    val kind: String,
    val administratorCount: Int = 0,
    val pinnedMessageId: Long? = null,
    val pinnedMessageText: String? = null
)
data class CommunityState(
    val selected: CommunityChatInfo? = null,
    val invitation: String? = null,
    val actionBusy: Boolean = false,
    val notice: String? = null
)

/** Phase 4.3/4.4/4.5 Telegram-native chat info and community actions. */
class TdlibCoreCommunities(private val client: () -> Client?) {
    private val lock = Any()
    private var epoch = 0L
    private val _state = MutableStateFlow(CommunityState())
    val state = _state.asStateFlow()

    fun inspect(chatId: Long) {
        val active = client() ?: return
        val token: Long
        synchronized(lock) {
            token = ++epoch
            _state.value = CommunityState()
        }
        active.send(TdApi.GetChat(chatId)) { response ->
            if (response is TdApi.Chat) synchronized(lock) {
                if (token != epoch) return@synchronized
                _state.value = _state.value.copy(
                    selected = CommunityChatInfo(
                        id = chatId, title = response.title.orEmpty(),
                        kind = when (val type = response.type) {
                            is TdApi.ChatTypePrivate -> "Private chat"
                            is TdApi.ChatTypeSecret -> "Secret chat"
                            is TdApi.ChatTypeBasicGroup -> "Basic group"
                            is TdApi.ChatTypeSupergroup -> if (type.isChannel) "Channel" else "Supergroup"
                            else -> "Telegram chat"
                        }
                    )
                )
            }
        }
        active.send(TdApi.GetChatAdministrators(chatId)) { response ->
            if (response is TdApi.ChatAdministrators) synchronized(lock) {
                if (token != epoch) return@synchronized
                _state.value.selected?.let { chat ->
                    _state.value = _state.value.copy(
                        selected = chat.copy(
                            administratorCount = response.administrators?.size ?: 0
                        )
                    )
                }
            }
        }
        active.send(TdApi.GetChatPinnedMessage(chatId)) { response ->
            if (response is TdApi.Message) synchronized(lock) {
                if (token != epoch) return@synchronized
                _state.value.selected?.let { existing ->
                    _state.value = _state.value.copy(
                        selected = existing.copy(
                            pinnedMessageId = response.id,
                            pinnedMessageText = when (val content = response.content) {
                                is TdApi.MessageText -> content.text?.text?.take(200)
                                else -> "Pinned Telegram message"
                            }
                        )
                    )
                }
            }
        }
    }

    private fun perform(
        request: TdApi.Function<*>,
        success: String,
        onChat: ((Long) -> Unit)? = null
    ) {
        val active = client() ?: return
        synchronized(lock) {
            if (_state.value.actionBusy) return
            _state.value = _state.value.copy(actionBusy = true, notice = null)
        }
        active.send(request) { reply ->
            synchronized(lock) {
                _state.value = _state.value.copy(
                    actionBusy = false,
                    notice = if (reply is TdApi.Error)
                        "Telegram rejected action (code ${reply.code})." else success
                )
            }
            if (reply is TdApi.Chat) onChat?.invoke(reply.id)
        }
    }

    /** Owner/admin rights are checked by Telegram; never bypass them. */
    /** Explicit member add, restricted by Telegram rights and privacy. */
    fun addMember(chatId: Long, userId: Long) {
        if (userId <= 0L) return
        val active = client() ?: return
        active.send(TdApi.AddChatMember(chatId, userId, 0)) { response ->
            synchronized(lock) {
                _state.value = _state.value.copy(
                    notice = when (response) {
                        is TdApi.Error ->
                            "Telegram could not add member (code ${response.code})."
                        is TdApi.FailedToAddMembers ->
                            if (response.failedToAddMembers.orEmpty().isEmpty())
                                "Telegram member added."
                            else "Telegram couldn't add that user (privacy or invite restriction)."
                        else -> "Member-add request sent."
                    }
                )
            }
        }
    }

    fun rename(chatId: Long, title: String) {
        val value = title.trim().take(128)
        if (value.isEmpty()) return
        perform(TdApi.SetChatTitle(chatId, value), "Chat title updated.")
    }

    fun description(chatId: Long, about: String) {
        perform(TdApi.SetChatDescription(chatId, about.take(255)), "Description updated.")
    }

    fun markedUnread(chatId: Long, unread: Boolean) {
        perform(TdApi.ToggleChatIsMarkedAsUnread(chatId, unread),
            if (unread) "Marked unread." else "Marked read.")
    }

    /** Telegram supergroup with optional broadcast channel mode. */
    fun createCommunity(title: String, about: String, channel: Boolean, onChat: (Long) -> Unit) {
        val name = title.trim().take(128)
        if (name.isEmpty()) {
            synchronized(lock) { _state.value = _state.value.copy(
                notice = "Enter a group/channel name."
            ) }
            return
        }
        perform(
            TdApi.CreateNewSupergroupChat(name, false, channel,
                about.trim().take(255), null, 0, false),
            if (channel) "Channel created." else "Group created.", onChat
        )
    }

    fun createInvite(chatId: Long) {
        val active = client() ?: return
        synchronized(lock) {
            if (_state.value.actionBusy) return
            _state.value = _state.value.copy(actionBusy = true, invitation = null, notice = null)
        }
        // A non-expiring invite is security-sensitive: user explicitly taps
        // Create; show link only to them, never log or auto-share it.
        active.send(TdApi.CreateChatInviteLink(chatId, "", 0, 0, false)) { result ->
            synchronized(lock) {
                _state.value = _state.value.copy(
                    actionBusy = false,
                    invitation = (result as? TdApi.ChatInviteLink)?.inviteLink,
                    notice = if (result is TdApi.Error)
                        "Invite unavailable (Telegram code ${result.code})."
                    else "Invite created. Share only with people you trust."
                )
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            epoch++
            _state.value = CommunityState()
        }
    }
}
