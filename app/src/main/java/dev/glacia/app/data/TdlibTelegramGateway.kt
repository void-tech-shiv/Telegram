package dev.glacia.app.data

import android.content.Context
import android.os.Build
import dev.glacia.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * TDLib's native JNI client runs in its own receiver thread. MutableStateFlow is
 * thread-safe; TDLib callbacks must not directly modify Compose state.
 *
 * Scope: one user session, real cloud chats + text messages.
 * Calling/secret-chat presentation/media transfers are NOT implemented here.
 */
class TdlibTelegramGateway(
    private val context: Context,
    private val apiId: Int = BuildConfig.TELEGRAM_API_ID,
    private val apiHash: String = BuildConfig.TELEGRAM_API_HASH
) : TelegramGateway {
    private val _stage = MutableStateFlow<LoginStage>(LoginStage.Starting)
    override val stage: StateFlow<LoginStage> = _stage
    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    override val chats: StateFlow<List<Chat>> = _chats
    private val _histories = MutableStateFlow<Map<Long, List<Message>>>(emptyMap())
    override val histories: StateFlow<Map<Long, List<Message>>> = _histories
    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error

    private val nativeChats = ConcurrentHashMap<Long, TdApi.Chat>()
    @Volatile private var client: Client? = null
    @Volatile private var disposed: Boolean = false

    override fun start() {
        if (disposed) return
        if (apiId <= 0 || apiHash.isBlank()) {
            _stage.value = LoginStage.Unconfigured
            return
        }
        if (client != null) return
        try {
            // Client loads the JNI tdjni library. Never log credentials, OTPs or passwords.
            client = Client.create(
                Client.ResultHandler { update -> onUpdate(update) },
                Client.ExceptionHandler { _error.value = "TDLib update handler failed" },
                Client.ExceptionHandler { _error.value = "TDLib result handler failed" }
            )
        } catch (error: Throwable) {
            _stage.value = LoginStage.Unavailable(
                "TDLib could not start: ${error.javaClass.simpleName}"
            )
        }
    }

    private fun onUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> onAuthorization(update.authorizationState)
            is TdApi.UpdateNewChat -> upsertChat(update.chat)
            is TdApi.UpdateChatTitle,
            is TdApi.UpdateChatLastMessage,
            is TdApi.UpdateChatPosition,
            is TdApi.UpdateChatReadInbox -> {
                val chatId = when (update) {
                    is TdApi.UpdateChatTitle -> update.chatId
                    is TdApi.UpdateChatLastMessage -> update.chatId
                    is TdApi.UpdateChatPosition -> update.chatId
                    is TdApi.UpdateChatReadInbox -> update.chatId
                    else -> 0L
                }
                refreshOneChat(chatId)
            }
            is TdApi.UpdateNewMessage -> addMessage(update.message)
            is TdApi.UpdateMessageSendSucceeded -> {
                _histories.value = _histories.value.mapValues { (chatId, old) ->
                    if (chatId != update.message.chatId) old
                    else (old.filterNot { it.id == update.oldMessageId } +
                        toMessage(update.message)).distinctBy { it.id }
                        .sortedBy { it.id }
                }
                refreshOneChat(update.message.chatId)
            }
            is TdApi.UpdateDeleteMessages -> {
                if (update.isPermanent) {
                    val ids = update.messageIds.toSet()
                    _histories.value = _histories.value.mapValues { (id, list) ->
                        if (id == update.chatId) list.filterNot { it.id in ids } else list
                    }
                }
            }
        }
    }

    private fun onAuthorization(auth: TdApi.AuthorizationState) {
        when (auth) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> initialize()
            is TdApi.AuthorizationStateWaitPhoneNumber -> _stage.value = LoginStage.Phone
            is TdApi.AuthorizationStateWaitCode -> _stage.value = LoginStage.Code
            is TdApi.AuthorizationStateWaitPassword -> _stage.value = LoginStage.Password
            is TdApi.AuthorizationStateWaitEmailAddress -> _stage.value = LoginStage.EmailAddress
            is TdApi.AuthorizationStateWaitEmailCode -> _stage.value = LoginStage.EmailCode
            is TdApi.AuthorizationStateWaitRegistration -> _stage.value = LoginStage.Register
            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation ->
                _stage.value = LoginStage.ConfirmOtherDevice(auth.link)
            is TdApi.AuthorizationStateReady -> {
                _stage.value = LoginStage.Ready
                loadChats()
            }
            is TdApi.AuthorizationStateLoggingOut,
            is TdApi.AuthorizationStateClosing -> _stage.value = LoginStage.Starting
            is TdApi.AuthorizationStateClosed -> {
                _stage.value = LoginStage.Starting
                client = null
                _chats.value = emptyList()
                _histories.value = emptyMap()
                nativeChats.clear()
                if (!disposed) start()
            }
            else -> _stage.value = LoginStage.Unavailable("Additional Telegram authorization step required")
        }
    }

    private fun initialize() {
        try {
            val dbFolder = File(context.noBackupFilesDir, "tdlib").apply { mkdirs() }
            val filesFolder = File(context.filesDir, "tdlib_media").apply { mkdirs() }
            val key = DatabaseKeyStore(context).readOrCreate()
            val params = TdApi.SetTdlibParameters().apply {
                useTestDc = false
                databaseDirectory = dbFolder.absolutePath
                filesDirectory = filesFolder.absolutePath
                databaseEncryptionKey = key
                useFileDatabase = true
                useChatInfoDatabase = true
                useMessageDatabase = true
                useSecretChats = false
                apiId = this@TdlibTelegramGateway.apiId
                apiHash = this@TdlibTelegramGateway.apiHash
                systemLanguageCode = Locale.getDefault().language.ifBlank { "en" }
                deviceModel = Build.MODEL ?: "Android"
                systemVersion = Build.VERSION.RELEASE ?: "Android"
                applicationVersion = BuildConfig.VERSION_NAME
            }
            key.fill(0)
            client?.send(params, Client.ResultHandler { result ->
                if (result is TdApi.Error) _error.value = "Database initialization: ${result.message}"
            }, null)
        } catch (_: Exception) {
            _stage.value = LoginStage.Unavailable(
                "Secure TDLib storage unavailable. Check Android Keystore or reset app data."
            )
        }
    }

    private suspend fun request(query: TdApi.Function<*>): TdApi.Object =
        suspendCancellableCoroutine { continuation ->
            val current = client
            if (current == null) {
                continuation.resumeWithException(IllegalStateException("TDLib is not connected"))
                return@suspendCancellableCoroutine
            }
            try {
                current.send(query, Client.ResultHandler { result ->
                    if (continuation.isActive) {
                        if (result is TdApi.Error) {
                            continuation.resumeWithException(
                                IllegalStateException("Telegram ${result.code}: ${result.message}")
                            )
                        } else continuation.resume(result)
                    }
                }, null)
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }

    override suspend fun sendPhone(phone: String) {
        require(Regex("^\\+[1-9]\\d{7,14}$").matches(phone.trim())) {
            "Use international phone format, e.g. +919876543210"
        }
        request(TdApi.SetAuthenticationPhoneNumber(phone.trim(), null))
    }
    override suspend fun sendCode(code: String) {
        require(code.isNotBlank()) { "Enter the Telegram code" }
        request(TdApi.CheckAuthenticationCode(code.trim()))
    }
    override suspend fun sendPassword(password: String) {
        require(password.isNotEmpty()) { "Enter your 2-step verification password" }
        request(TdApi.CheckAuthenticationPassword(password))
    }
    override suspend fun sendEmail(email: String) {
        require(email.contains("@")) { "Enter an email address" }
        request(TdApi.SetAuthenticationEmailAddress(email.trim()))
    }
    override suspend fun sendEmailCode(code: String) {
        require(code.isNotBlank()) { "Enter the email confirmation code" }
        request(TdApi.CheckAuthenticationEmailCode(TdApi.EmailAddressAuthenticationCode(code.trim())))
    }
    override suspend fun register(first: String, last: String) {
        require(first.isNotBlank()) { "First name is required" }
        request(TdApi.RegisterUser(first.trim(), last.trim(), true))
    }

    private fun loadChats() {
        client?.send(TdApi.LoadChats(TdApi.ChatListMain(), 100), Client.ResultHandler {
            // Error 404 means all chats were already loaded.
            client?.send(TdApi.GetChats(TdApi.ChatListMain(), 100), Client.ResultHandler { response ->
                if (response is TdApi.Chats) {
                    response.chatIds.forEach { refreshOneChat(it) }
                }
            }, null)
        }, null)
    }

    override suspend fun refreshChats() {
        check(_stage.value is LoginStage.Ready) { "Log in first" }
        try {
            request(TdApi.LoadChats(TdApi.ChatListMain(), 100))
        } catch (error: IllegalStateException) {
            // TDLib 404 means all chats are loaded; keep the in-memory list.
            if (!error.message.orEmpty().contains("Telegram 404:")) throw error
        }
        val response = request(TdApi.GetChats(TdApi.ChatListMain(), 100))
        if (response is TdApi.Chats) response.chatIds.forEach { refreshOneChat(it) }
    }

    private fun refreshOneChat(id: Long) {
        client?.send(TdApi.GetChat(id), Client.ResultHandler { result ->
            if (result is TdApi.Chat) upsertChat(result)
        }, null)
    }

    private fun mainOrder(chat: TdApi.Chat): Long =
        chat.positions.firstOrNull { it.list is TdApi.ChatListMain }?.order ?: 0L

    private fun upsertChat(chat: TdApi.Chat) {
        nativeChats[chat.id] = chat
        _chats.value = nativeChats.values
            .filter { mainOrder(it) > 0L }
            .sortedWith(compareByDescending<TdApi.Chat> { mainOrder(it) }.thenByDescending { it.id })
            .take(150)
            .map {
                Chat(
                    id = it.id,
                    title = it.title,
                    username = "",
                    preview = it.lastMessage?.let { last -> preview(last) } ?: "No messages yet",
                    time = it.lastMessage?.let { last -> formatTime(last.date) } ?: "",
                    unread = it.unreadCount,
                    isOnline = false,
                    isGroup = it.type is TdApi.ChatTypeSupergroup || it.type is TdApi.ChatTypeBasicGroup,
                    pinned = it.positions.any { p -> p.list is TdApi.ChatListMain && p.isPinned }
                )
            }
    }

    private fun preview(message: TdApi.Message): String = when (val content = message.content) {
        is TdApi.MessageText -> content.text.text
        is TdApi.MessagePhoto -> "📷 Photo"
        is TdApi.MessageVideo -> "🎬 Video"
        is TdApi.MessageVoiceNote -> "🎤 Voice message"
        is TdApi.MessageDocument -> "📎 Document"
        is TdApi.MessageSticker -> "Sticker"
        else -> "Media / service message"
    }

    override suspend fun openChat(id: Long) {
        check(_stage.value is LoginStage.Ready) { "Log in first" }
        val response = request(TdApi.GetChatHistory(id, 0, 0, 50, false))
        if (response is TdApi.Messages) {
            _histories.value = _histories.value + (id to response.messages.map(::toMessage)
                .sortedWith(compareBy<Message> { it.time }.thenBy { it.id }))
        }
    }

    private fun toMessage(message: TdApi.Message): Message =
        Message(
            id = message.id,
            chatId = message.chatId,
            text = preview(message),
            time = formatTime(message.date),
            outgoing = message.isOutgoing
        )

    private fun addMessage(message: TdApi.Message) {
        val item = toMessage(message)
        _histories.value = _histories.value.toMutableMap().apply {
            val old = get(message.chatId) ?: return
            put(message.chatId, (old + item).distinctBy { it.id }
                .sortedWith(compareBy<Message> { it.time }.thenBy { it.id }))
        }
        refreshOneChat(message.chatId)
    }

    override suspend fun sendMessage(chatId: Long, text: String) {
        require(text.isNotBlank()) { "Message cannot be empty" }
        check(_stage.value is LoginStage.Ready) { "Log in first" }
        val input = TdApi.InputMessageText(TdApi.FormattedText(text.trim(), null), null, true)
        val response = request(TdApi.SendMessage(chatId, null, null, null, null, input))
        if (response is TdApi.Message) addMessage(response)
    }

    override suspend fun logOut() {
        request(TdApi.LogOut())
        _chats.value = emptyList()
        _histories.value = emptyMap()
    }

    override fun clearError() { _error.value = null }

    override fun close() {
        disposed = true
        client?.send(TdApi.Close(), null, null)
        client = null
    }

    private fun formatTime(epochSeconds: Int): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochSeconds.toLong() * 1000))
}
