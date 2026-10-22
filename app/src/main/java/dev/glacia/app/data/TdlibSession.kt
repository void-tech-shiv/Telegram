package dev.glacia.app.data

import android.content.Context
import android.os.Build
import dev.glacia.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.Locale

/**
 * Single-client TDLib session. Authorization updates, not button clicks, govern transitions.
 * Never log phone numbers, authorization codes, passwords, or response objects.
 */
class TdlibSession(private val context: Context) {
    sealed interface Auth {
        data object SetupRequired : Auth
        data object Starting : Auth
        data object Phone : Auth
        data object Code : Auth
        data object Password : Auth
        data object EmailAddress : Auth
        data object EmailCode : Auth
        data object RegistrationRequired : Auth
        data object Ready : Auth
        data class Error(val reason: String) : Auth
    }

    private val _auth = MutableStateFlow<Auth>(Auth.Starting)
    val auth = _auth.asStateFlow()
    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats = _chats.asStateFlow()
    private val known = linkedMapOf<Long, TdApi.Chat>()
    private var client: Client? = null

    @Synchronized fun start() {
        if (client != null) return
        if (BuildConfig.TELEGRAM_API_ID <= 0 || BuildConfig.TELEGRAM_API_HASH.isBlank()) {
            _auth.value = Auth.SetupRequired
            return
        }
        try {
            client = Client.create({ update -> onUpdate(update) }, { _ ->
                _auth.value = Auth.Error("TDLib update processing error")
            }, { _ ->
                _auth.value = Auth.Error("TDLib request error")
            })
        } catch (_: Throwable) {
            _auth.value = Auth.Error("Could not load TDLib native library")
        }
    }

    private fun request(function: TdApi.Function<*>, onSuccess: (() -> Unit)? = null) {
        val active = client ?: run {
            _auth.value = Auth.Error("TDLib is not running")
            return
        }
        active.send(function) { result ->
            if (result is TdApi.Error) {
                // Avoid rendering raw Telegram server error text that may include private input.
                _auth.value = Auth.Error("Request rejected (code ${result.code}). Try again.")
            } else onSuccess?.invoke()
        }
    }

    private fun onUpdate(obj: TdApi.Object) {
        when (obj) {
            is TdApi.UpdateAuthorizationState -> onAuth(obj.authorizationState)
            is TdApi.UpdateNewChat -> {
                synchronized(known) { known[obj.chat.id] = obj.chat; emitChats() }
            }
            is TdApi.UpdateChatTitle -> synchronized(known) {
                known[obj.chatId]?.title = obj.title; emitChats()
            }
            is TdApi.UpdateChatLastMessage -> synchronized(known) {
                known[obj.chatId]?.lastMessage = obj.lastMessage
                known[obj.chatId]?.positions = obj.positions
                emitChats()
            }
            is TdApi.UpdateChatPosition -> synchronized(known) {
                val chat = known[obj.chatId]
                if (chat != null) {
                    chat.positions = chat.positions.filterNot { it.list is TdApi.ChatListMain }.toTypedArray() +
                        obj.position
                    emitChats()
                }
            }
            is TdApi.UpdateChatReadInbox -> synchronized(known) {
                known[obj.chatId]?.unreadCount = obj.unreadCount; emitChats()
            }
        }
    }

    private fun onAuth(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                val directory = context.getDir("tdlib", Context.MODE_PRIVATE)
                request(TdApi.SetTdlibParameters(
                    false,
                    directory.absolutePath,
                    context.filesDir.absolutePath,
                    byteArrayOf(),
                    true, true, true, true,
                    BuildConfig.TELEGRAM_API_ID,
                    BuildConfig.TELEGRAM_API_HASH,
                    Locale.getDefault().toLanguageTag(),
                    Build.MODEL ?: "Android",
                    Build.VERSION.RELEASE ?: "Android",
                    BuildConfig.VERSION_NAME
                ))
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> _auth.value = Auth.Phone
            is TdApi.AuthorizationStateWaitCode -> _auth.value = Auth.Code
            is TdApi.AuthorizationStateWaitPassword -> _auth.value = Auth.Password
            is TdApi.AuthorizationStateWaitEmailAddress -> _auth.value = Auth.EmailAddress
            is TdApi.AuthorizationStateWaitEmailCode -> _auth.value = Auth.EmailCode
            is TdApi.AuthorizationStateWaitRegistration -> _auth.value = Auth.RegistrationRequired
            is TdApi.AuthorizationStateReady -> {
                _auth.value = Auth.Ready
                request(TdApi.LoadChats(TdApi.ChatListMain(), 100))
            }
            is TdApi.AuthorizationStateClosed -> {
                client = null
                _auth.value = Auth.Starting
            }
            else -> _auth.value = Auth.Starting
        }
    }

    private fun emitChats() {
        _chats.value = known.values.asSequence()
            .map { chat ->
                val mainOrder = chat.positions
                    .filter { it.list is TdApi.ChatListMain }
                    .maxOfOrNull { it.order } ?: 0L
                chat to mainOrder
            }
            .filter { it.second > 0L }
            .sortedWith(compareByDescending<Pair<TdApi.Chat, Long>> { it.second }
                .thenByDescending { it.first.id })
            .map { (chat, _) ->
                val preview = (chat.lastMessage?.content as? TdApi.MessageText)?.text?.text
                    ?: if (chat.lastMessage == null) "No messages" else "Media or another message"
                Chat(
                    id = chat.id,
                    title = chat.title,
                    username = "",
                    preview = preview,
                    time = "",
                    unread = chat.unreadCount,
                    isGroup = chat.type !is TdApi.ChatTypePrivate
                )
            }.toList()
    }

    fun submitPhone(phone: String) {
        require(phone.startsWith("+") && phone.length in 8..17) { "Enter a phone number with country code." }
        request(TdApi.SetAuthenticationPhoneNumber(phone, null))
    }
    fun submitCode(code: String) = request(TdApi.CheckAuthenticationCode(code))
    fun submitPassword(password: String) = request(TdApi.CheckAuthenticationPassword(password))

    fun close() {
        client?.send(TdApi.Close()) { }
        client = null
    }
}
