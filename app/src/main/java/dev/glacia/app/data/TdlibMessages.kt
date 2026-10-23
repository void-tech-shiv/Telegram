package dev.glacia.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Live TDLib messages. Updates are delivered from TDLib threads, so the shared
 * message map is guarded and emitted as immutable snapshots for Compose.
 */
class TdlibMessages(
    private val client: () -> Client?,
    private val files: TdlibFileStore
) {
    private val lock = Any()
    private val snapshots = mutableMapOf<Long, List<Message>>()
    private val loadingChats = mutableSetOf<Long>()
    private val _messages = MutableStateFlow<Map<Long, List<Message>>>(emptyMap())
    val messages = _messages.asStateFlow()
    private val _loading = MutableStateFlow<Set<Long>>(emptySet())
    val loading = _loading.asStateFlow()
    private val _errors = MutableStateFlow<Map<Long, String>>(emptyMap())
    val errors = _errors.asStateFlow()

    fun clear() {
        synchronized(lock) {
            snapshots.clear()
            loadingChats.clear()
            _messages.value = emptyMap()
            _loading.value = emptySet()
            _errors.value = emptyMap()
        }
    }

    private fun error(chatId: Long, text: String?) {
        synchronized(lock) {
            _errors.value = _errors.value.toMutableMap().also {
                if (text == null) it.remove(chatId) else it[chatId] = text
            }
        }
    }

    fun dismissError(chatId: Long) = error(chatId, null)
    /** Share TDLib send failures with the same in-chat error surface as text. */
    fun reportError(chatId: Long, message: String) = error(chatId, message)
    fun addOutgoing(message: TdApi.Message) =
        merge(message.chatId, listOf(fromTd(message)))


    private fun merge(chatId: Long, received: List<Message>, replacedId: Long? = null) {
        synchronized(lock) {
            snapshots[chatId] = MessageReducer.merge(
                snapshots[chatId].orEmpty(), received, replacedId
            )
            _messages.value = snapshots.toMap()
        }
    }

    private fun delete(chatId: Long, ids: Collection<Long>) {
        synchronized(lock) {
            snapshots[chatId] = MessageReducer.delete(snapshots[chatId].orEmpty(), ids)
            _messages.value = snapshots.toMap()
        }
    }

    fun loadHistory(chatId: Long, beforeId: Long = 0L) {
        val active = client() ?: run {
            error(chatId, "Not connected to Telegram.")
            return
        }
        synchronized(lock) {
            if (!loadingChats.add(chatId)) return
            _loading.value = loadingChats.toSet()
        }
        error(chatId, null)
        active.send(TdApi.GetChatHistory(chatId, beforeId, 0, 40, false)) { result ->
            synchronized(lock) {
                loadingChats.remove(chatId)
                _loading.value = loadingChats.toSet()
            }
            when (result) {
                is TdApi.Messages -> {
                    val fetched = result.messages?.map { fromTd(it) }.orEmpty()
                    merge(chatId, fetched)
                }
                is TdApi.Error ->
                    error(chatId, "Unable to load messages (code " + result.code + ").")
                else -> error(chatId, "Unexpected history response. Try reopening this chat.")
            }
        }
    }

    fun sendText(chatId: Long, body: String) {
        val text = body.trim()
        if (text.isBlank() || text.length > 4096) {
            error(chatId, "Message must be between 1 and 4096 characters.")
            return
        }
        val active = client() ?: run {
            error(chatId, "Not connected to Telegram.")
            return
        }
        error(chatId, null)
        // Only send when the user taps Send; no telemetry or mock message path.
        val messageText = TdApi.FormattedText().apply { this.text = text }
        val input = TdApi.InputMessageText().apply { this.text = messageText }
        val function = TdApi.SendMessage().apply {
            this.chatId = chatId
            this.inputMessageContent = input
        }
        active.send(function) { result ->
            when (result) {
                is TdApi.Message -> merge(chatId, listOf(fromTd(result)))
                is TdApi.Error ->
                    error(chatId, "Message not sent (code " + result.code + ").")
            }
        }
    }

    /** TDLib updates for both incoming and outgoing messages. */
    fun onUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateNewMessage -> merge(update.message.chatId, listOf(fromTd(update.message)))
            is TdApi.UpdateMessageSendSucceeded ->
                merge(update.message.chatId, listOf(fromTd(update.message)), update.oldMessageId)
            is TdApi.UpdateMessageSendFailed -> {
                merge(update.message.chatId, listOf(fromTd(update.message)), update.oldMessageId)
                error(update.message.chatId, "A message failed to send. Check connection or chat permissions.")
            }
            is TdApi.UpdateMessageContent -> {
                synchronized(lock) {
                    val list = snapshots[update.chatId] ?: return
                    snapshots[update.chatId] = list.map {
                        if (it.id == update.messageId) {
                            val thumbnail = thumbnailFor(update.newContent)
                            val attachment = (update.newContent as? TdApi.MessageDocument)?.document
                            files.ensure(thumbnail)
                            files.observeExisting(attachment?.document)
                            val originalPlayback = originalPlaybackFile(update.newContent)
                            files.observeExisting(originalPlayback)
                            it.copy(
                                text = displayContent(update.newContent),
                                mediaType = mediaType(update.newContent),
                                mediaFileId = thumbnail?.id,
                                attachmentFileId = attachment?.document?.id,
                                attachmentName = attachment?.fileName,
                                attachmentMime = attachment?.mimeType,
                                playbackFileId = originalPlayback?.id,
                                playbackDurationSeconds = playbackDuration(update.newContent)
                            )
                        } else it
                    }
                    _messages.value = snapshots.toMap()
                }
            }
            is TdApi.UpdateDeleteMessages -> delete(update.chatId, update.messageIds.toList())
        }
    }

    private fun fromTd(message: TdApi.Message): Message {
        val label = displayContent(message.content)
        val thumbnail = thumbnailFor(message.content)
        val attachment = (message.content as? TdApi.MessageDocument)?.document
        files.ensure(thumbnail)
        files.observeExisting(attachment?.document)
        val originalPlayback = originalPlaybackFile(message.content)
        files.observeExisting(originalPlayback)
        val clock = if (message.date > 0)
            DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
                .format(Date(message.date.toLong() * 1000L))
        else ""
        return Message(
            id = message.id,
            chatId = message.chatId,
            text = label,
            time = clock,
            outgoing = message.isOutgoing,
            date = message.date,
            sending = message.sendingState is TdApi.MessageSendingStatePending,
            failed = message.sendingState is TdApi.MessageSendingStateFailed,
            mediaType = mediaType(message.content),
            mediaFileId = thumbnail?.id,
            attachmentFileId = attachment?.document?.id,
            attachmentName = attachment?.fileName,
            attachmentMime = attachment?.mimeType,
            playbackFileId = originalPlayback?.id,
            playbackDurationSeconds = playbackDuration(message.content)
        )
    }

    private fun mediaType(content: TdApi.MessageContent?): String? = when (content) {
        is TdApi.MessagePhoto -> "photo"
        is TdApi.MessageVideo -> "video"
        is TdApi.MessageVoiceNote -> "voice"
        is TdApi.MessageAudio -> "audio"
        is TdApi.MessageDocument -> "document"
        else -> null
    }

    private fun originalPlaybackFile(content: TdApi.MessageContent?): TdApi.File? = when (content) {
        is TdApi.MessageVideo -> content.video?.video
        is TdApi.MessageVoiceNote -> content.voiceNote?.voice
        is TdApi.MessageAudio -> content.audio?.audio
        else -> null
    }

    private fun playbackDuration(content: TdApi.MessageContent?): Int = when (content) {
        is TdApi.MessageVideo -> content.video?.duration ?: 0
        is TdApi.MessageVoiceNote -> content.voiceNote?.duration ?: 0
        is TdApi.MessageAudio -> content.audio?.duration ?: 0
        else -> 0
    }

    /** Prefer an economical medium-sized thumbnail, never full-resolution automatic downloads. */
    private fun thumbnailFor(content: TdApi.MessageContent?): TdApi.File? = when (content) {
        is TdApi.MessagePhoto -> {
            val sorted = content.photo?.sizes?.sortedBy { it.width.toLong() * it.height }
            (sorted?.firstOrNull {
                maxOf(it.width, it.height) in 240..1024
            } ?: sorted?.lastOrNull {
                maxOf(it.width, it.height) <= 1024
            })?.photo
        }
        is TdApi.MessageVideo -> content.video?.thumbnail?.file
        is TdApi.MessageDocument -> content.document?.thumbnail?.file
        else -> null
    }

    private fun displayContent(content: TdApi.MessageContent?): String = when (content) {
        is TdApi.MessageText -> content.text?.text.orEmpty()
        is TdApi.MessagePhoto -> "📷 Photo" + (content.caption?.text?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
        is TdApi.MessageVideo -> "🎥 Video" + (content.caption?.text?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
        is TdApi.MessageAnimation -> "🎞 Animation"
        is TdApi.MessageVoiceNote -> "🎤 Voice message"
        is TdApi.MessageVideoNote -> "🎬 Video message"
        is TdApi.MessageAudio -> "🎵 Audio"
        is TdApi.MessageDocument -> {
            val name = content.document?.fileName.orEmpty().trim().take(72)
            if (name.isBlank()) "📄 Document" else "📄 $name"
        }
        is TdApi.MessageSticker -> "🎨 Sticker"
        is TdApi.MessagePoll -> "📊 Poll"
        is TdApi.MessageLocation -> "📍 Location"
        is TdApi.MessageContact -> "👤 Contact"
        else -> "Unsupported message type"
    }
}
