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
data class StickerPackOption(val id: Long, val title: String)

data class ExpressionState(
    val loading: Boolean = false,
    val stickers: List<ExpressionOption> = emptyList(),
    val gifs: List<ExpressionOption> = emptyList(),
    val packs: List<StickerPackOption> = emptyList(),
    val selectedPackId: Long? = null,
    val packStickers: List<ExpressionOption> = emptyList(),
    val packLoading: Boolean = false,
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
    private var packRequestGeneration = 0L
    private val _state = MutableStateFlow(ExpressionState())
    val state = _state.asStateFlow()

    private fun toStickerOption(sticker: TdApi.Sticker): ExpressionOption? {
        val file = sticker.sticker ?: return null
        val thumbnail = if (sticker.format is TdApi.StickerFormatWebp)
            file else sticker.thumbnail?.file
        files.ensure(thumbnail)
        // Picker fetches only small static preview files. Bulk-loading a
        // sticker pack must never download 120 full TGS/WebM originals.
        return ExpressionOption(
            file.id, thumbnail?.id, ExpressionPolicy.normalizedEmoji(sticker.emoji)
        )
    }

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
                            .mapNotNull(::toStickerOption)
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
        active.send(TdApi.GetInstalledStickerSets(TdApi.StickerTypeRegular())) { response ->
            synchronized(lock) {
                if (epoch != generation) return@synchronized
                when (response) {
                    is TdApi.StickerSets -> _state.value = _state.value.copy(
                        packs = response.sets.orEmpty().filterNotNull()
                            .take(80).map { StickerPackOption(it.id, it.title) }
                    )
                    is TdApi.Error -> _state.value = _state.value.copy(
                        error = "Sticker packs unavailable (Telegram code ${response.code})."
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

    /** The selected installed pack is fetched on tap; never bulk-load all packs. */
    fun loadPack(setId: Long) {
        if (setId <= 0L) return
        val active = client() ?: return
        val epoch: Long
        val request: Long
        synchronized(lock) {
            epoch = generation
            request = ++packRequestGeneration
            _state.value = _state.value.copy(
                selectedPackId = setId, packLoading = true,
                packStickers = emptyList(), error = null
            )
        }
        active.send(TdApi.GetStickerSet(setId)) { response ->
            synchronized(lock) {
                if (generation != epoch || packRequestGeneration != request) return@synchronized
                _state.value = when (response) {
                    is TdApi.StickerSet -> _state.value.copy(
                        packLoading = false,
                        packStickers = response.stickers.orEmpty().filterNotNull()
                            .take(120).mapNotNull(::toStickerOption)
                    )
                    is TdApi.Error -> _state.value.copy(
                        packLoading = false,
                        error = "Sticker pack unavailable (Telegram code ${response.code})."
                    )
                    else -> _state.value.copy(packLoading = false)
                }
            }
        }
    }

    fun send(chatId: Long, option: ExpressionOption, replyToMessageId: Long? = null) {
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
            if (replyToMessageId != null && replyToMessageId > 0) {
                this.replyTo = TdApi.InputMessageReplyToMessage().apply { messageId = replyToMessageId }
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
            packRequestGeneration++
            _state.value = ExpressionState()
        }
    }
}
