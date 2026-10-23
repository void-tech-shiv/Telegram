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
    // Recoverable request errors remain visible without replacing the login form.
    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()
    fun dismissNotice() { _notice.value = null }
    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats = _chats.asStateFlow()
    private val known = linkedMapOf<Long, TdApi.Chat>()
    private val missingTitleRefreshes = mutableSetOf<Long>()
    private var client: Client? = null
    private val fileStore = TdlibFileStore { client }
    val filePaths = fileStore.paths
    val documentTransfers = fileStore.transfers
    fun requestDocumentDownload(fileId: Int) = fileStore.requestDocument(fileId)
    private val messaging = TdlibMessages({ client }, fileStore)
    val messages = messaging.messages
    val historyLoading = messaging.loading
    val messageErrors = messaging.errors

    fun loadHistory(chatId: Long, beforeId: Long = 0L) = messaging.loadHistory(chatId, beforeId)
    fun sendText(chatId: Long, text: String) = messaging.sendText(chatId, text)
    fun dismissMessageError(chatId: Long) = messaging.dismissError(chatId)

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

    private fun safeFailure(stage: String, error: TdApi.Error): String {
        // Avoid leaking raw server error messages: allow only short symbolic tags.
        val token = error.message?.trim()?.uppercase(Locale.ROOT)
            ?.takeIf { it.matches(Regex("[A-Z][A-Z0-9_]{2,63}")) }
        val guidance = when (token) {
            "API_ID_INVALID", "API_HASH_INVALID" ->
                "Check the API ID/hash in local.properties, then rebuild the APK."
            "PHONE_NUMBER_INVALID" -> "Check the country code and phone number."
            "PHONE_NUMBER_BANNED" -> "This number is restricted by Telegram."
            "PHONE_CODE_INVALID", "PHONE_CODE_EMPTY" -> "Check your Telegram verification code."
            "PHONE_CODE_EXPIRED" -> "Your verification code has expired."
            "PASSWORD_HASH_INVALID" -> "The two-step verification password was not accepted."
            else -> if (token?.startsWith("FLOOD_WAIT_") == true) {
                "Telegram rate-limited the request. Retry later."
            } else {
                "Check your credentials and input, then retry."
            }
        }
        val suffix = token?.let { ", $it" }.orEmpty()
        return "$stage failed (code ${error.code}$suffix). $guidance"
    }

    private fun request(function: TdApi.Function<*>, stage: String) {
        val active = client ?: run {
            _auth.value = Auth.Error("TDLib is not running. Restart the app.")
            return
        }
        _notice.value = null
        active.send(function) { result ->
            if (result is TdApi.Error) {
                // TDLib uses 404 to indicate LoadChats has no more chats to return.
                if (stage == "Chat sync" && result.code == 404) return@send
                val diagnostic = safeFailure(stage, result)
                if (stage == "TDLib initialization") {
                    _auth.value = Auth.Error(diagnostic)
                } else {
                    // Phone/OTP/2FA fields stay visible so the user can retry.
                    _notice.value = diagnostic
                }
            }
        }
    }

    private fun onUpdate(obj: TdApi.Object) {
        // Chat headers and message timelines share the same authorized TDLib client.
        fileStore.onUpdate(obj)
        messaging.onUpdate(obj)
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
            is TdApi.UpdateChatPhoto -> synchronized(known) {
                known[obj.chatId]?.photo = obj.photo
                emitChats()
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
                ), "TDLib initialization")
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> _auth.value = Auth.Phone
            is TdApi.AuthorizationStateWaitCode -> _auth.value = Auth.Code
            is TdApi.AuthorizationStateWaitPassword -> _auth.value = Auth.Password
            is TdApi.AuthorizationStateWaitEmailAddress -> _auth.value = Auth.EmailAddress
            is TdApi.AuthorizationStateWaitEmailCode -> _auth.value = Auth.EmailCode
            is TdApi.AuthorizationStateWaitRegistration -> _auth.value = Auth.RegistrationRequired
            is TdApi.AuthorizationStateReady -> {
                _auth.value = Auth.Ready
                request(TdApi.LoadChats(TdApi.ChatListMain(), 100), "Chat sync")
            }
            is TdApi.AuthorizationStateClosed -> {
                messaging.clear()
                fileStore.clear()
                synchronized(known) {
                    known.clear()
                    missingTitleRefreshes.clear()
                    _chats.value = emptyList()
                }
                client = null
                _auth.value = Auth.Starting
            }
            else -> _auth.value = Auth.Starting
        }
    }

    private fun requestMissingTitle(chat: TdApi.Chat) {
        if (chat.title.isNotBlank() || !missingTitleRefreshes.add(chat.id)) return
        client?.send(TdApi.GetChat(chat.id)) { result ->
            if (result is TdApi.Chat && result.title.isNotBlank()) {
                synchronized(known) {
                    known[result.id] = result
                    emitChats()
                }
            }
        }
    }

    private fun fallbackChatTitle(chat: TdApi.Chat): String {
        if (chat.title.isNotBlank()) return chat.title
        return when (chat.type) {
            is TdApi.ChatTypePrivate -> "Unavailable contact"
            is TdApi.ChatTypeSecret -> "Unavailable secret chat"
            else -> "Unavailable group or channel"
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
                val preview = when (val content = chat.lastMessage?.content) {
                    is TdApi.MessageText -> content.text?.text.orEmpty()
                    is TdApi.MessagePhoto -> "📷 Photo"
                    is TdApi.MessageVideo -> "🎥 Video"
                    is TdApi.MessageVoiceNote -> "🎤 Voice note"
                    is TdApi.MessageVideoNote -> "🎬 Video note"
                    is TdApi.MessageAudio -> "🎵 Audio"
                    is TdApi.MessageDocument -> "📄 Document"
                    is TdApi.MessageSticker -> "🎨 Sticker"
                    is TdApi.MessageAnimation -> "🎞 GIF"
                    is TdApi.MessagePoll -> "📊 Poll"
                    null -> "No messages"
                    else -> "Other message"
                }
                fileStore.ensure(chat.photo?.small)
                requestMissingTitle(chat)
                Chat(
                    id = chat.id,
                    title = fallbackChatTitle(chat),
                    username = "",
                    preview = preview,
                    time = "",
                    unread = chat.unreadCount,
                    isGroup = chat.type !is TdApi.ChatTypePrivate,
                    photoFileId = chat.photo?.small?.id
                )
            }.toList()
    }

    fun submitPhone(phone: String) {
        require(phone.startsWith("+") && phone.length in 8..17) { "Enter a phone number with country code." }
        request(TdApi.SetAuthenticationPhoneNumber(phone, null), "Phone login")
    }
    fun submitCode(code: String) = request(TdApi.CheckAuthenticationCode(code), "Verification code")
    fun submitPassword(password: String) = request(TdApi.CheckAuthenticationPassword(password), "Two-step password")

    fun close() {
        client?.send(TdApi.Close()) { }
        client = null
    }
}
