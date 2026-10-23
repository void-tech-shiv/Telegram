package dev.glacia.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/** One account's bounded sticker/GIF picker. No external Giphy key or server. */
data class ExpressionOption(
    val fileId: Int,
    val previewFileId: Int?,
    val emoji: String,
    val durationSeconds: Int = 0,
    val animated: Boolean = false
)
data class ExpressionState(
    val loading: Boolean = false,
    val stickers: List<ExpressionOption> = emptyList(),
    val gifs: List<ExpressionOption> = emptyList(),
    val error: String? = null
)

internal object ExpressionPolicy {
    fun canSend(option: ExpressionOption?): Boolean = option != null && option.fileId > 0
    fun normalizedEmoji(emoji: String): String = emoji.take(16)
}

class TdlibExpressions(
    private val client: () -> Client?,
    private val files: TdlibFileStore,
    private val messages: TdlibMessages
) {
    private val lock = Any()
    private var generation = 0L
    private val _state = MutableStateFlow(ExpressionState())
    val state = _state.asStateFlow()

    /** TDLib recents/saved items, without automatic downloading of full GIFs. */
    fun load() {
        val active = client() ?: run {
            synchronized(lock) { _state.value = ExpressionState(error = "Telegram is offline.") }
            return
        }
        val epoch: Long
        synchronized(lock) {
            epoch = ++generation
            _state.value = ExpressionState(loading = true)
        }
        active.send(TdApi.GetRecentStickers(false)) { response ->
            synchronized(lock) {
                if (epoch != generation) return@synchronized
                when (response) {
                    is TdApi.Stickers -> {
                        val models = response.stickers.orEmpty()
                            .filterNotNull()
                            .filter { it.fullType !is TdApi.StickerFullTypeCustomEmoji }
                            .take(60)
                            .mapNotNull { sticker ->
                                val file = sticker.sticker ?: return@mapNotNull null
                                // Only decode still WEBP thumbnails via Coil; vector/video
                                // formats are not rendered as static bitmap guesses.
                                val image = if (sticker.format is TdApi.StickerFormatWebp) file
                                    else sticker.thumbnail?.file
                                files.ensure(image)
                                ExpressionOption(file.id, image?.id, ExpressionPolicy.normalizedEmoji(sticker.emoji))
                            }
                        _state.value = _state.value.copy(
                            stickers = models,
                            error = null
                        )
                    }
                    is TdApi.Error -> _state.value = _state.value.copy(
                        error = "Sticker list unavailable (Telegram code ${response.code})."
                    )
                }
            }
        }
        active.send(TdApi.GetSavedAnimations()) { response ->
            synchronized(lock) {
                if (epoch != generation) return@synchronized
                when (response) {
                    is TdApi.Animations -> {
                        val models = response.animations.orEmpty().filterNotNull().take(40)
                            .mapNotNull { gif ->
                                val file = gif.animation ?: return@mapNotNull null
                                val thumbnail = gif.thumbnail?.file
                                files.ensure(thumbnail)
                                ExpressionOption(
                                    fileId = file.id,
                                    previewFileId = thumbnail?.id,
                                    emoji = "GIF",
                                    durationSeconds = gif.duration,
                                    animated = true
                                )
                            }
                        _state.value = _state.value.copy(gifs = models, error = null)
                    }
                    is TdApi.Error -> _state.value = _state.value.copy(
                        error = "Saved GIFs unavailable (Telegram code ${response.code})."
                    )
                }
                _state.value = _state.value.copy(loading = false)
            }
        }
    }

    fun send(chatId: Long, option: ExpressionOption, replyTo: Long? = null) {
        if (chatId == 0L || !ExpressionPolicy.canSend(option)) return
        val active = client() ?: run {
            messages.reportError(chatId, "Telegram connection unavailable.")
            return
        }
        val content: TdApi.InputMessageContent = if (option.animated) {
            TdApi.InputMessageAnimation().apply {
                animation = TdApi.InputAnimation().apply {
                    animation = TdApi.InputFileId(option.fileId)
                    duration = option.durationSeconds
                }
                caption = TdApi.FormattedText("", emptyArray())
            }
        } else {
            TdApi.InputMessageSticker().apply {
                sticker = TdApi.InputSticker().apply {
                    sticker = TdApi.InputFileId(option.fileId)
                }
                emoji = option.emoji
            }
        }
        active.send(TdApi.SendMessage().apply {
            this.chatId = chatId
            if (replyTo != null && replyTo > 0) {
                replyTo = TdApi.InputMessageReplyToMessage().apply { messageId = replyTo }
            }
            inputMessageContent = content
        }) { result ->
            when (result) {
                is TdApi.Message -> messages.addOutgoing(result)
                is TdApi.Error -> messages.reportError(chatId,
                    "Could not send ${if (option.animated) "GIF" else "sticker"} " +
                        "(Telegram code ${result.code}).")
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            generation++
            _state.value = ExpressionState()
        }
    }
}
