package dev.glacia.app.data

import android.content.Context
import android.os.Build
import android.os.SystemClock
import dev.glacia.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

    // TDLib callbacks can arrive AFTER a newer authorization state update.
    // In-flight request bookkeeping prevents duplicate taps and stale errors.
    private val authorizationLock = Any()
    private var authorizationGeneration = 0L
    private var pendingAuthRequest: String? = null
    private val _authBusy = MutableStateFlow(false)
    val authBusy = _authBusy.asStateFlow()

    private val _canResendCode = MutableStateFlow(false)
    val canResendCode = _canResendCode.asStateFlow()
    // Monotonic timestamp; no clock/timezone dependency.
    private val _resendCodeAfterMs = MutableStateFlow(0L)
    val resendCodeAfterMs = _resendCodeAfterMs.asStateFlow()

    private fun publishAuth(state: Auth) {
        synchronized(authorizationLock) {
            if (_auth.value != state) {
                authorizationGeneration++
                pendingAuthRequest = null
                _authBusy.value = false
                _notice.value = null
            }
            _auth.value = state
        }
    }
    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats = _chats.asStateFlow()
    private val known = linkedMapOf<Long, TdApi.Chat>()
    private val missingTitleRefreshes = mutableSetOf<Long>()
    private val _outboxRead = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val outboxRead = _outboxRead.asStateFlow()
    // Avoid duplicate ViewMessages calls as Compose recomposes or scrolls.
    private val viewLock = Any()
    private val maxViewedByChat = mutableMapOf<Long, Long>()
    private var client: Client? = null
    private val fileStore = TdlibFileStore { client }
    private val discovery = TdlibDiscovery({ client }, fileStore)
    private val typing = TdlibTyping { client }
    private val notifications = GlaciaNotifications(context)
    private val _notificationSetup = MutableStateFlow("Waiting for Telegram authorization")
    val notificationSetup = _notificationSetup.asStateFlow()
    private val _notificationEvents = MutableStateFlow(0)
    val notificationEvents = _notificationEvents.asStateFlow()
    val notificationHealth = notifications.health
    private val _chatMuteStates = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    val chatMuteStates = _chatMuteStates.asStateFlow()
    private val _muteFeedback = MutableStateFlow<String?>(null)
    val muteFeedback = _muteFeedback.asStateFlow()

    init {
        notifications.setTitleResolver { id ->
            synchronized(known) { known[id]?.title }
        }
        notifications.setMuteResolver { id ->
            synchronized(known) {
                known[id]?.notificationSettings?.let { settings ->
                    !settings.useDefaultMuteFor && settings.muteFor > 0
                } ?: false
            }
        }
    }

    private val _notificationTargetChat = MutableStateFlow<Chat?>(null)
    val notificationTargetChat = _notificationTargetChat.asStateFlow()

    fun ensureChat(chatId: Long) {
        if (chatId == 0L) return
        val cached = synchronized(known) { known[chatId] }
        if (cached != null) {
            publishNotificationChat(cached)
            return
        }
        client?.send(TdApi.GetChat(chatId)) { response ->
            if (response is TdApi.Chat) synchronized(known) {
                known[chatId] = response
                trackMute(response)
                publishNotificationChat(response)
                emitChats()
            }
        }
    }

    private fun publishNotificationChat(chat: TdApi.Chat) {
        fileStore.ensure(chat.photo?.small)
        _notificationTargetChat.value = Chat(
            id = chat.id,
            title = chat.title.ifBlank { "Telegram chat" },
            username = "",
            preview = "",
            time = "",
            unread = chat.unreadCount,
            isGroup = chat.type is TdApi.ChatTypeBasicGroup ||
                chat.type is TdApi.ChatTypeSupergroup,
            photoFileId = chat.photo?.small?.id
        )
    }

    private fun trackMute(chat: TdApi.Chat) {
        val settings = chat.notificationSettings
        _chatMuteStates.value = _chatMuteStates.value +
            (chat.id to (!settings.useDefaultMuteFor && settings.muteFor > 0))
    }

    /** Telegram-synced per-chat mute (not just Android-side filtering). */
    fun setChatMuted(chatId: Long, muted: Boolean) {
        val chat = synchronized(known) { known[chatId] } ?: return
        val settings = chat.notificationSettings ?: return
        settings.useDefaultMuteFor = false
        settings.muteFor = if (muted) Int.MAX_VALUE else 0
        client?.send(TdApi.SetChatNotificationSettings(chatId, settings)) { reply ->
            if (reply is TdApi.Error) {
                _muteFeedback.value = "Telegram could not update notification settings (code ${reply.code})"
                client?.send(TdApi.GetChat(chatId)) { refreshed ->
                    if (refreshed is TdApi.Chat) synchronized(known) {
                        known[chatId] = refreshed
                        trackMute(refreshed)
                        emitChats()
                    }
                }
            } else {
                _muteFeedback.value = null
            }
        }
    }

    fun sendTestNotification(): Boolean = notifications.sendTestNotification()
    fun notificationsAllowed(): Boolean = notifications.areNotificationsAllowed()
    val typingActivities = typing.activity
    fun sendTypingAction(chatId: Long, typingNow: Boolean) = typing.sendTyping(chatId, typingNow)
    fun pruneTyping() = typing.prune()
    fun setAppForeground(foreground: Boolean) = notifications.setForeground(foreground)
    fun setVisibleChat(chatId: Long?) = notifications.setVisibleChat(chatId)
    val discoveryResults = discovery.results
    val discoveryLoading = discovery.loading
    val discoveryError = discovery.error
    fun searchTelegramChats(query: String) = discovery.search(query)

    val filePaths = fileStore.paths
    val documentTransfers = fileStore.transfers
    fun requestDocumentDownload(fileId: Int) = fileStore.requestDocument(fileId)
    private val messaging = TdlibMessages({ client }, fileStore)
    private val messageActions = TdlibMessageActions({ client }, messaging)
    private val calls = TelegramCallHandoff(context, { client }) { id, explanation ->
        messaging.reportError(id, explanation)
    }
    fun openCallContact(chatId: Long) = calls.openContact(chatId)
    val actionPermissions = messageActions.permissions
    fun loadMessageActions(chatId: Long, id: Long) = messageActions.properties(chatId, id)
    fun forwardMessage(fromChatId: Long, id: Long, toChatId: Long) =
        messageActions.forward(fromChatId, id, toChatId)
    fun editMessage(chatId: Long, id: Long, body: String) =
        messageActions.edit(chatId, id, body)
    fun deleteMessage(chatId: Long, id: Long, forEveryone: Boolean) =
        messageActions.delete(chatId, id, forEveryone)
    fun reactToMessage(chatId: Long, id: Long, emoji: String, chosen: Boolean) =
        messageActions.react(chatId, id, emoji, chosen)
    private val mediaOutbox = MediaOutbox(context, { client }, messaging)
    val mediaSending = mediaOutbox.busy
    fun sendAttachment(chatId: Long, uri: android.net.Uri) =
        mediaOutbox.sendPicked(chatId, uri)
    fun sendVoice(chatId: Long, path: String, seconds: Int, opus: Boolean) =
        mediaOutbox.sendRecorded(chatId, path, seconds, opus)
    val messages = messaging.messages
    val historyLoading = messaging.loading
    val messageErrors = messaging.errors

    fun loadHistory(chatId: Long, beforeId: Long = 0L) = messaging.loadHistory(chatId, beforeId)
    fun sendText(chatId: Long, text: String, replyTo: Long? = null) =
        messaging.sendText(chatId, text, replyTo)
    fun dismissMessageError(chatId: Long) = messaging.dismissError(chatId)

    fun openChat(chatId: Long) {
        client?.send(TdApi.OpenChat().apply { this.chatId = chatId }) { }
    }

    fun closeChat(chatId: Long) {
        client?.send(TdApi.CloseChat().apply { this.chatId = chatId }) { }
    }

    /**
     * Called only while the conversation is foregrounded with these messages on screen.
     * Never infer that a chat was read merely from selecting a tab.
     */
    fun viewVisibleMessages(chatId: Long, visibleMessageIds: List<Long>) {
        val ids = visibleMessageIds.filter { it > 0L }.distinct().take(25)
        if (ids.isEmpty()) return
        val active = client ?: return
        val maxId = ids.maxOrNull() ?: return
        val prior: Long
        synchronized(viewLock) {
            prior = maxViewedByChat[chatId] ?: 0L
            if (maxId <= prior) return
            maxViewedByChat[chatId] = maxId
        }
        active.send(TdApi.ViewMessages().apply {
            this.chatId = chatId
            messageIds = ids.toLongArray()
            source = null
            forceRead = false
        }) { result ->
            if (result is TdApi.Error) {
                synchronized(viewLock) {
                    if (maxViewedByChat[chatId] == maxId) maxViewedByChat[chatId] = prior
                }
            }
        }
    }

    @Synchronized fun start() {
        if (client != null) return
        if (BuildConfig.TELEGRAM_API_ID <= 0 || BuildConfig.TELEGRAM_API_HASH.isBlank()) {
            publishAuth(Auth.SetupRequired)
            return
        }
        try {
            client = Client.create({ update -> onUpdate(update) }, { _ ->
                publishAuth(Auth.Error("TDLib update processing error"))
            }, { _ ->
                publishAuth(Auth.Error("TDLib request error"))
            })
        } catch (_: Throwable) {
            publishAuth(Auth.Error("Could not load TDLib native library"))
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
        val expectedAuth: Auth? = when (stage) {
            "Phone login" -> Auth.Phone
            "Verification code", "Resend code" -> Auth.Code
            "Two-step password" -> Auth.Password
            else -> null
        }
        val active = client ?: run {
            publishAuth(Auth.Error("TDLib is not running. Restart the app."))
            return
        }
        val generation: Long
        synchronized(authorizationLock) {
            // Do not send a second verification request during an ongoing one.
            if (expectedAuth != null) {
                if (_auth.value != expectedAuth || pendingAuthRequest != null) return
                pendingAuthRequest = stage
                _authBusy.value = true
            }
            generation = authorizationGeneration
            _notice.value = null
        }
        active.send(function) { result ->
            val relevant: Boolean = synchronized(authorizationLock) {
                val sameGeneration = generation == authorizationGeneration
                if (expectedAuth != null && sameGeneration && pendingAuthRequest == stage) {
                    pendingAuthRequest = null
                    _authBusy.value = false
                }
                sameGeneration && (expectedAuth == null || _auth.value == expectedAuth)
            }
            // A phone-request failure arriving AFTER WaitCode must never appear
            // on the code screen. The state update is authoritative.
            if (!relevant) return@send
            if (result is TdApi.Error) {
                if (stage == "Chat sync" && result.code == 404) return@send
                val diagnostic = safeFailure(stage, result)
                if (stage == "TDLib initialization") {
                    publishAuth(Auth.Error(diagnostic))
                } else {
                    _notice.value = diagnostic
                }
            } else if (stage == "Resend code") {
                _notice.value = "Telegram accepted the resend request. Enter the newest code."
            }
        }
    }

    /**
     * TDLib Notification API is disabled by default (group limit = 0).
     * Without this SetOption request, UpdateNotificationGroup is never emitted.
     * Configure on every authorized launch to support existing installations.
     */
    private val _pushStatus = MutableStateFlow(
        if (BuildConfig.FCM_CONFIGURED) "Push token not registered yet"
        else "Background push inactive: add app/google-services.json"
    )
    val pushStatus = _pushStatus.asStateFlow()
    fun rememberPushToken(token: String) {
        if (!BuildConfig.FCM_CONFIGURED || token.isBlank()) return
        context.getSharedPreferences("glacia_push", Context.MODE_PRIVATE).edit()
            .putString("token", token).apply()
        if (_auth.value is Auth.Ready) registerPushToken(token)
    }

    private fun registerPushToken(token: String) {
        val active = client ?: return
        // Additional encrypted Telegram push is processed by TDLib, not exposed
        // in Android raw notifications.
        active.send(
            TdApi.RegisterDevice(
                TdApi.DeviceTokenFirebaseCloudMessaging(token, true),
                longArrayOf()
            )
        ) { reply ->
            _pushStatus.value = when (reply) {
                is TdApi.PushReceiverId -> "TDLib push device registration accepted"
                is TdApi.Error -> "TDLib push registration rejected (code ${reply.code})"
                else -> "TDLib push registration returned an unexpected response"
            }
        }
    }

    /** Bounded cold-start TDLib push handling on Firebase's worker thread. */
    fun processPushPayload(payload: String): Boolean {
        if (!BuildConfig.FCM_CONFIGURED || payload.isBlank()) return false
        val active = client ?: return false
        val finished = CountDownLatch(1)
        active.send(TdApi.ProcessPushNotification(payload)) { reply ->
            if (reply is TdApi.Error && reply.code == 406) {
                _pushStatus.value = "Push received; Telegram sync is required"
            }
            finished.countDown()
        }
        return finished.await(8L, TimeUnit.SECONDS)
    }

    private fun enableTdlibNotificationApi() {
        val active = client ?: return
        _notificationSetup.value = "Enabling Telegram notification events…"
        active.send(
            TdApi.SetOption("notification_group_count_max", TdApi.OptionValueInteger(15))
        ) { result ->
            _notificationSetup.value = when (result) {
                is TdApi.Ok -> "Telegram notification events enabled"
                is TdApi.Error -> "TDLib notification setup failed (code ${result.code})"
                else -> "TDLib notification setup returned an unexpected response"
            }
        }
    }

    private fun onUpdate(obj: TdApi.Object) {
        if (obj is TdApi.UpdateNotificationGroup) {
            _notificationEvents.value = _notificationEvents.value + 1
        }
        // Chat headers and message timelines share the same authorized TDLib client.
        fileStore.onUpdate(obj)
        messaging.onUpdate(obj)
        typing.onUpdate(obj)
        notifications.onUpdate(obj)
        when (obj) {
            is TdApi.UpdateAuthorizationState -> onAuth(obj.authorizationState)
            is TdApi.UpdateNewChat -> {
                synchronized(known) {
                    known[obj.chat.id] = obj.chat
                    trackMute(obj.chat)
                    _outboxRead.value = _outboxRead.value +
                        (obj.chat.id to obj.chat.lastReadOutboxMessageId)
                    emitChats()
                }
            }
            is TdApi.UpdateChatNotificationSettings -> synchronized(known) {
                known[obj.chatId]?.let { chat ->
                    chat.notificationSettings = obj.notificationSettings
                    trackMute(chat)
                    emitChats()
                }
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
                known[obj.chatId]?.unreadCount = obj.unreadCount
                // Telegram may mark previously viewed messages unread again from
                // another device. Permit a fresh ViewMessages acknowledgement.
                if (obj.unreadCount > 0) {
                    synchronized(viewLock) { maxViewedByChat.remove(obj.chatId) }
                }
                emitChats()
            }
            is TdApi.UpdateChatReadOutbox -> synchronized(known) {
                known[obj.chatId]?.lastReadOutboxMessageId = obj.lastReadOutboxMessageId
                _outboxRead.value = _outboxRead.value +
                    (obj.chatId to obj.lastReadOutboxMessageId)
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
            is TdApi.AuthorizationStateWaitPhoneNumber -> publishAuth(Auth.Phone)
            is TdApi.AuthorizationStateWaitCode -> {
                val info = state.codeInfo
                _canResendCode.value = info?.nextType != null
                _resendCodeAfterMs.value = SystemClock.elapsedRealtime() +
                    (info?.timeout ?: 0).coerceAtLeast(0) * 1000L
                publishAuth(Auth.Code)
            }
            is TdApi.AuthorizationStateWaitPassword -> publishAuth(Auth.Password)
            is TdApi.AuthorizationStateWaitEmailAddress -> publishAuth(Auth.EmailAddress)
            is TdApi.AuthorizationStateWaitEmailCode -> publishAuth(Auth.EmailCode)
            is TdApi.AuthorizationStateWaitRegistration -> publishAuth(Auth.RegistrationRequired)
            is TdApi.AuthorizationStateReady -> {
                _canResendCode.value = false
                publishAuth(Auth.Ready)
                enableTdlibNotificationApi()
                if (BuildConfig.FCM_CONFIGURED) {
                    context.getSharedPreferences("glacia_push", Context.MODE_PRIVATE)
                        .getString("token", null)?.takeIf { it.isNotBlank() }
                        ?.let(::registerPushToken)
                }
                request(TdApi.LoadChats(TdApi.ChatListMain(), 100), "Chat sync")
            }
            is TdApi.AuthorizationStateClosed -> {
                messaging.clear()
                messageActions.clear()
                discovery.clear()
                typing.clear()
                fileStore.clear()
                synchronized(viewLock) { maxViewedByChat.clear() }
                _outboxRead.value = emptyMap()
                _notificationSetup.value = "Waiting for Telegram authorization"
                _notificationEvents.value = 0
                synchronized(known) {
                    known.clear()
                    _notificationTargetChat.value = null
                    _chatMuteStates.value = emptyMap()
                    missingTitleRefreshes.clear()
                    _chats.value = emptyList()
                }
                client = null
                publishAuth(Auth.Starting)
            }
            else -> publishAuth(Auth.Starting)
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
                    is TdApi.MessageText -> ChatPreviewPrivacy.safePreview(content.text?.text.orEmpty())
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
                    isGroup = chat.type is TdApi.ChatTypeBasicGroup ||
                        chat.type is TdApi.ChatTypeSupergroup,
                    photoFileId = chat.photo?.small?.id
                )
            }.toList()
    }

    fun submitPhone(phone: String) {
        require(phone.startsWith("+") && phone.length in 8..17) { "Enter a phone number with country code." }
        request(TdApi.SetAuthenticationPhoneNumber(phone, null), "Phone login")
    }
    fun submitCode(code: String) = request(TdApi.CheckAuthenticationCode(code), "Verification code")

    fun resendCode() {
        if (_auth.value != Auth.Code || !_canResendCode.value) return
        if (SystemClock.elapsedRealtime() < _resendCodeAfterMs.value) return
        request(TdApi.ResendAuthenticationCode(), "Resend code")
    }

    fun submitPassword(password: String) = request(TdApi.CheckAuthenticationPassword(password), "Two-step password")

    fun close() {
        typing.clear()
        client?.send(TdApi.Close()) { }
        client = null
    }
}
