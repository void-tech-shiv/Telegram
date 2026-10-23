package dev.glacia.app.data

import android.content.Context
import android.os.Build
import android.os.SystemClock
import dev.glacia.app.calls.IncomingCallAlerts
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
    data class ChatFolderTab(val id: Int, val title: String)
    private val _folderTabs = MutableStateFlow<List<ChatFolderTab>>(emptyList())
    val folderTabs = _folderTabs.asStateFlow()
    private val _folderChats = MutableStateFlow<Map<Int, List<Chat>>>(emptyMap())
    val folderChats = _folderChats.asStateFlow()
    /** Create a real Telegram cloud folder, not a local fake category. */
    fun createTelegramFolder(name: String, kind: String) {
        val title = name.trim().take(12)
        if (title.isBlank() || kind !in setOf("People", "Groups", "Channels")) return
        val folder = TdApi.ChatFolder().apply {
            this.name = TdApi.ChatFolderName(
                TdApi.FormattedText(title, emptyArray()), false
            )
            colorId = -1
            pinnedChatIds = longArrayOf()
            includedChatIds = longArrayOf()
            excludedChatIds = longArrayOf()
            includeContacts = kind == "People"
            includeNonContacts = kind == "People"
            includeBots = kind == "People"
            includeGroups = kind == "Groups"
            includeChannels = kind == "Channels"
        }
        client?.send(TdApi.CreateChatFolder(folder)) { response ->
            if (response is TdApi.Error) {
                _chatActionError.value =
                    "Folder creation rejected (Telegram code ${response.code})."
            }
        }
    }

    /** Delete only the folder, never leave any of its chats. */
    fun deleteTelegramFolder(id: Int) {
        if (id !in _folderTabs.value.map { it.id }) return
        client?.send(TdApi.DeleteChatFolder(id, longArrayOf())) { response ->
            if (response is TdApi.Error) {
                _chatActionError.value = "Can't delete folder (Telegram code ${response.code})."
            }
        }
    }

    fun loadTelegramFolder(id: Int) {
        if (id !in _folderTabs.value.map { it.id }) return
        client?.send(TdApi.LoadChats(TdApi.ChatListFolder(id), 100)) { result ->
            if (result is TdApi.Error && result.code != 404) {
                _chatActionError.value = "Folder unavailable (Telegram code ${result.code})."
            }
        }
    }
    private val _archivedChats = MutableStateFlow<List<Chat>>(emptyList())
    val archivedChats = _archivedChats.asStateFlow()
    private val _chatActionBusy = MutableStateFlow<Set<Long>>(emptySet())
    val chatActionBusy = _chatActionBusy.asStateFlow()
    private val _chatActionError = MutableStateFlow<String?>(null)
    val chatActionError = _chatActionError.asStateFlow()
    fun dismissChatActionError() { _chatActionError.value = null }
    private val chatActionLock = Any()
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
    private val coreAccount = TdlibCoreAccount({ client }, fileStore)
    private val coreSecurity = TdlibCoreSecurity { client }
    val coreSecurityState = coreSecurity.state
    fun refreshCoreSecurity() = coreSecurity.refresh()
    fun updatePrivacyRule(key: String, audience: String) =
        coreSecurity.changePrivacy(key, audience)
    fun unblockTelegramUser(userId: Long) = coreSecurity.unblock(userId)
    fun blockTelegramUser(userId: Long) = coreSecurity.block(userId)
    fun blockTelegramPrivateChat(chatId: Long) {
        client?.send(TdApi.GetChat(chatId)) { response ->
            val userId = (response as? TdApi.Chat)?.type
                .let { it as? TdApi.ChatTypePrivate }?.userId ?: return@send
            coreSecurity.block(userId)
        }
    }
    fun terminateOtherTelegramSession(sessionId: Long) = coreSecurity.terminateOtherDevice(sessionId)
    private val communities = TdlibCoreCommunities { client }
    val communityState = communities.state
    fun inspectChatInfo(chatId: Long) = communities.inspect(chatId)
    fun renameCommunity(chatId: Long, title: String) = communities.rename(chatId, title)
    fun addCommunityMember(chatId: Long, userId: Long) = communities.addMember(chatId, userId)
    fun describeCommunity(chatId: Long, about: String) = communities.description(chatId, about)
    fun markTelegramChatUnread(chatId: Long, unread: Boolean) =
        communities.markedUnread(chatId, unread)
    fun makeCommunity(title: String, about: String, channel: Boolean, onChat: (Long) -> Unit) =
        communities.createCommunity(title, about, channel, onChat)
    fun generateChatInvite(chatId: Long) = communities.createInvite(chatId)
    val accountState = coreAccount.state
    fun refreshCoreAccount() = coreAccount.refresh()
    fun updateOwnProfile(first: String, last: String, bio: String) =
        coreAccount.saveProfile(first, last, bio)
    fun updateOwnUsername(username: String) = coreAccount.changeUsername(username)
    fun updateOwnPhoto(path: String) = coreAccount.uploadProfilePhoto(path)
    fun addTelegramContact(username: String) = coreAccount.addContactByUsername(username)
    fun removeTelegramContact(userId: Long) = coreAccount.removeContact(userId)
    fun openContactChat(userId: Long, onChat: (Long) -> Unit) =
        coreAccount.openContact(userId, onChat)
    fun openSavedMessages(onChat: (Long) -> Unit) = coreAccount.openSavedMessages(onChat)
    fun clearAccountNotice() = coreAccount.clearNotice()
    private val typing = TdlibTyping { client }
    private val notifications = GlaciaNotifications(context)
    private val incomingCallAlerts = IncomingCallAlerts(context)
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

    /**
     * Telegram-synced chat list actions. Do not fake a successful local move:
     * final list order and pin state are driven by TDLib UpdateChatPosition.
     * A pending request is de-duplicated by chat ID and cannot spam Telegram.
     */
    fun setChatArchived(chatId: Long, archive: Boolean) {
        val active = client ?: run {
            _chatActionError.value = "Telegram connection unavailable."
            return
        }
        val chat = synchronized(known) { known[chatId] } ?: return
        val currentlyArchived = chat.positions.any {
            it.list is TdApi.ChatListArchive && it.order > 0L
        }
        if (archive == currentlyArchived) return
        synchronized(chatActionLock) {
            if (chatId in _chatActionBusy.value) return
            _chatActionBusy.value = _chatActionBusy.value + chatId
        }
        val target: TdApi.ChatList = if (archive) TdApi.ChatListArchive() else TdApi.ChatListMain()
        active.send(TdApi.AddChatToList(chatId, target)) { result ->
            synchronized(chatActionLock) { _chatActionBusy.value = _chatActionBusy.value - chatId }
            if (result is TdApi.Error) {
                _chatActionError.value = "Archive change failed (Telegram code ${result.code})."
            }
        }
    }

    fun toggleChatPinned(chatId: Long) {
        val active = client ?: run {
            _chatActionError.value = "Telegram connection unavailable."
            return
        }
        val chat = synchronized(known) { known[chatId] } ?: return
        val position = chat.positions.firstOrNull {
            it.order > 0L && (it.list is TdApi.ChatListMain || it.list is TdApi.ChatListArchive)
        } ?: return
        synchronized(chatActionLock) {
            if (chatId in _chatActionBusy.value) return
            _chatActionBusy.value = _chatActionBusy.value + chatId
        }
        active.send(TdApi.ToggleChatIsPinned(position.list, chatId, !position.isPinned)) { result ->
            synchronized(chatActionLock) { _chatActionBusy.value = _chatActionBusy.value - chatId }
            if (result is TdApi.Error) {
                _chatActionError.value = "Pin change failed (Telegram code ${result.code})."
            }
        }
    }

    fun loadArchivedChats() {
        client?.send(TdApi.LoadChats(TdApi.ChatListArchive(), 100)) { result ->
            if (result is TdApi.Error && result.code != 404) {
                _chatActionError.value = "Could not sync archived chats (Telegram code ${result.code})."
            }
        }
    }

    fun sendTestNotification(): Boolean = notifications.sendTestNotification()
    fun notificationsAllowed(): Boolean = notifications.areNotificationsAllowed()
    val typingActivities = typing.activity
    fun sendTypingAction(chatId: Long, typingNow: Boolean) = typing.sendTyping(chatId, typingNow)
    fun pruneTyping() = typing.prune()
    fun setAppForeground(foreground: Boolean) {
        notifications.setForeground(foreground)
        incomingCallAlerts.setForeground(foreground)
    }
    fun setVisibleChat(chatId: Long?) = notifications.setVisibleChat(chatId)
    val discoveryResults = discovery.results
    val discoveryLoading = discovery.loading
    val discoveryError = discovery.error
    fun searchTelegramChats(query: String) = discovery.search(query)

    val filePaths = fileStore.paths
    val documentTransfers = fileStore.transfers
    fun requestDocumentDownload(fileId: Int) = fileStore.requestDocument(fileId)
    fun retryMediaDownload(fileId: Int) = fileStore.requestDocument(fileId, retry = true)
    private val messaging = TdlibMessages({ client }, fileStore)
    private val expressions = TdlibExpressions({ client }, fileStore, messaging)
    val expressionState = expressions.state
    fun loadExpressions() = expressions.load()
    fun loadStickerPack(setId: Long) = expressions.loadPack(setId)
    fun sendExpression(chatId: Long, item: ExpressionOption, replyTo: Long? = null) =
        expressions.send(chatId, item, replyTo)
    private val messageSearch = TdlibMessageSearch { client }
    val chatSearchResults = messageSearch.results
    fun searchChatMessages(chatId: Long, query: String, more: Boolean = false) =
        messageSearch.search(chatId, query, more)
    fun clearChatSearch(chatId: Long) = messageSearch.clear(chatId)
    private val messageActions = TdlibMessageActions({ client }, messaging)
    private val calls = TelegramCallHandoff(context, { client }) { id, explanation ->
        messaging.reportError(id, explanation)
    }
    fun openCallContact(chatId: Long) = calls.openContact(chatId)
    fun openOfficialTelegram(): Boolean = calls.openOfficialApp()
    private val nativeCalls = NativeCallController(context) { client }
    val nativeCallingEnabled = nativeCalls.enabled
    val nativeCall = nativeCalls.call
    val nativeCallNotice = nativeCalls.notice
    fun dismissNativeCallNotice() = nativeCalls.dismissNotice()
    val remoteCallFrames = nativeCalls.remoteFrames
    val localCallFrames = nativeCalls.localFrames
    fun startNativeCall(chatId: Long, video: Boolean) = nativeCalls.dial(chatId, video)
    fun answerNativeCall() = nativeCalls.accept()
    fun hangupNativeCall() = nativeCalls.hangUp()
    fun muteNativeCall(muted: Boolean) = nativeCalls.setMuted(muted)
    fun speakerNativeCall(on: Boolean) = nativeCalls.setSpeaker(on)
    fun bluetoothNativeCall() = nativeCalls.useBluetooth()
    fun switchNativeCamera() = nativeCalls.switchCamera()

    // TDLib call invitations are observable; no native media engine is present.
    private val callInbox = TdlibCallInbox { client }
    val incomingCall = callInbox.incoming
    val decliningIncomingCall = callInbox.declining
    val incomingCallError = callInbox.error
    fun declineIncomingCall() = callInbox.decline()
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
    fun loadMessageAround(chatId: Long, messageId: Long) = messaging.loadAround(chatId, messageId)
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
        coreAccount.onUpdate(obj)
        messaging.onUpdate(obj)
        callInbox.onUpdate(obj)
        nativeCalls.onUpdate(obj)
        if (nativeCallingEnabled) incomingCallAlerts.onUpdate(obj)
        typing.onUpdate(obj)
        notifications.onUpdate(obj)
        when (obj) {
            is TdApi.UpdateAuthorizationState -> onAuth(obj.authorizationState)
            is TdApi.UpdateChatFolders -> {
                _folderTabs.value = obj.chatFolders.orEmpty().map { folder ->
                    ChatFolderTab(folder.id, folder.name?.text?.text.orEmpty()
                        .ifBlank { "Folder" })
                }
                synchronized(known) { emitChats() }
            }
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
                    // Remove only the position from this list. In particular,
                    // archive updates must not accidentally discard Main/folders.
                    val position = obj.position
                    val existing = chat.positions.filterNot { old ->
                        (position.list is TdApi.ChatListMain && old.list is TdApi.ChatListMain) ||
                            (position.list is TdApi.ChatListArchive && old.list is TdApi.ChatListArchive)
                    }
                    chat.positions = (existing + position).toTypedArray()
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
                loadArchivedChats()
                refreshCoreAccount()
            }
            is TdApi.AuthorizationStateClosed -> {
                messaging.clear()
                expressions.clear()
                coreAccount.clear()
                coreSecurity.clear()
                communities.clear()
                messageSearch.clearAll()
                messageActions.clear()
                callInbox.clear()
                nativeCalls.clear()
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
                    _archivedChats.value = emptyList()
                    _folderTabs.value = emptyList()
                    _folderChats.value = emptyMap()
                }
                synchronized(chatActionLock) { _chatActionBusy.value = emptySet() }
                _chatActionError.value = null
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

    /** Accurate device-local Telegram chat timestamp (no fabricated labels). */
    private fun chatListTime(unixSeconds: Int): String {
        if (unixSeconds <= 0) return ""
        val whenSent = java.time.Instant.ofEpochSecond(unixSeconds.toLong())
            .atZone(java.time.ZoneId.systemDefault())
        val today = java.time.LocalDate.now()
        return when {
            whenSent.toLocalDate() == today ->
                whenSent.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
            whenSent.toLocalDate() == today.minusDays(1) -> "Yesterday"
            whenSent.toLocalDate().isAfter(today.minusDays(7)) ->
                whenSent.format(java.time.format.DateTimeFormatter.ofPattern("EEE"))
            else -> whenSent.format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))
        }
    }

    private fun emitChats() {
        fun mapChat(chat: TdApi.Chat, archived: Boolean, pinned: Boolean): Chat {
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
                is TdApi.MessageLocation -> "📍 Location"
                is TdApi.MessageCall -> if (content.isVideo) "📹 Video call" else "📞 Voice call"
                null -> "No messages"
                else -> "Other message"
            }
            fileStore.ensure(chat.photo?.small)
            requestMissingTitle(chat)
            return Chat(
                id = chat.id,
                title = fallbackChatTitle(chat),
                username = "",
                preview = preview,
                time = chatListTime(chat.lastMessage?.date ?: 0),
                unread = chat.unreadCount,
                isGroup = chat.type is TdApi.ChatTypeBasicGroup ||
                    chat.type is TdApi.ChatTypeSupergroup,
                pinned = pinned,
                photoFileId = chat.photo?.small?.id,
                archived = archived
            )
        }

        fun buildList(archive: Boolean): List<Chat> {
            return known.values.asSequence()
                .mapNotNull { chat ->
                    val candidate = chat.positions
                        .filter { pos ->
                            pos.order > 0L &&
                                (if (archive) pos.list is TdApi.ChatListArchive
                                else pos.list is TdApi.ChatListMain)
                        }.maxByOrNull { it.order }
                    candidate?.let { position -> Triple(chat, position.order, position.isPinned) }
                }
                .sortedWith(compareByDescending<Triple<TdApi.Chat, Long, Boolean>> { it.third }
                    .thenByDescending { it.second }.thenByDescending { it.first.id })
                .map { (chat, _, pinned) -> mapChat(chat, archive, pinned) }
                .toList()
        }
        _chats.value = buildList(false)
        _archivedChats.value = buildList(true)
        _folderChats.value = _folderTabs.value.associate { folder ->
            folder.id to known.values.asSequence()
                .mapNotNull { chat ->
                    val pos = chat.positions.firstOrNull {
                        it.order > 0L &&
                            (it.list as? TdApi.ChatListFolder)?.chatFolderId == folder.id
                    }
                    pos?.let { Triple(chat, it.order, it.isPinned) }
                }
                .sortedWith(compareByDescending<Triple<TdApi.Chat, Long, Boolean>> { it.third }
                    .thenByDescending { it.second })
                .map { (chat, _, pinned) -> mapChat(chat, false, pinned) }
                .toList()
        }
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
    /** Explicit user-confirmed account logout. Does not log out other devices. */
    fun logoutThisDevice() {
        if (_auth.value !is Auth.Ready) return
        client?.send(TdApi.LogOut()) { response ->
            if (response is TdApi.Error) {
                _notice.value = "Telegram logout failed (code ${response.code})."
            }
        }
    }

    fun close() {
        typing.clear()
        messageSearch.clearAll()
        expressions.clear()
        coreAccount.clear()
        coreSecurity.clear()
        communities.clear()
        callInbox.clear()
        nativeCalls.clear()
        incomingCallAlerts.clear()
        client?.send(TdApi.Close()) { }
        client = null
    }
}
