package dev.glacia.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/** Bounded, server-backed search; no search history or private query logging. */
data class ChatSearchHit(
    val id: Long,
    val preview: String,
    val date: Int,
    val outgoing: Boolean
)

data class ChatSearchState(
    val query: String = "",
    val loading: Boolean = false,
    val hits: List<ChatSearchHit> = emptyList(),
    val total: Int = 0,
    val nextFromMessageId: Long = 0,
    val error: String? = null
)

internal object ChatSearchReducer {
    fun merge(
        old: ChatSearchState, hits: List<ChatSearchHit>, total: Int,
        cursor: Long, loadMore: Boolean
    ): ChatSearchState {
        val merged = if (loadMore) old.hits + hits else hits
        return old.copy(
            loading = false,
            hits = merged.distinctBy { it.id }.take(200),
            total = total.coerceAtLeast(0),
            nextFromMessageId = cursor,
            error = null
        )
    }
}

class TdlibMessageSearch(private val client: () -> Client?) {
    private val lock = Any()
    private val generations = mutableMapOf<Long, Int>()
    private val _results = MutableStateFlow<Map<Long, ChatSearchState>>(emptyMap())
    val results = _results.asStateFlow()

    fun search(chatId: Long, rawQuery: String, more: Boolean = false) {
        if (chatId == 0L) return
        val query = rawQuery.trim().take(256)
        if (query.isEmpty()) {
            clear(chatId)
            return
        }
        val active = client() ?: run {
            synchronized(lock) {
                _results.value = _results.value + (chatId to ChatSearchState(
                    query = query, error = "Telegram is offline."
                ))
            }
            return
        }
        val generation: Int
        val cursor: Long
        synchronized(lock) {
            val prior = _results.value[chatId]
            val isMore = more && prior?.query == query && prior.nextFromMessageId > 0L
            if (more && (!isMore || prior?.loading == true || prior.hits.size >= 200)) return
            generation = (generations[chatId] ?: 0) + 1
            generations[chatId] = generation
            cursor = if (isMore) prior!!.nextFromMessageId else 0L
            _results.value = _results.value + (
                chatId to if (isMore) prior!!.copy(loading = true, error = null)
                    else ChatSearchState(query = query, loading = true)
            )
        }
        active.send(TdApi.SearchChatMessages().apply {
            this.chatId = chatId
            this.query = query
            fromMessageId = cursor
            offset = 0
            limit = 30
            senderId = null
            filter = null
        }) { reply ->
            synchronized(lock) {
                val now = _results.value[chatId] ?: return@synchronized
                if (generations[chatId] != generation || now.query != query) return@synchronized
                val state = when (reply) {
                    is TdApi.FoundChatMessages -> ChatSearchReducer.merge(
                        now,
                        reply.messages?.mapNotNull { msg ->
                            msg?.let {
                                ChatSearchHit(it.id, preview(it.content).take(180),
                                    it.date, it.isOutgoing)
                            }
                        }.orEmpty(),
                        reply.totalCount,
                        reply.nextFromMessageId,
                        cursor > 0L
                    )
                    is TdApi.Error -> now.copy(
                        loading = false,
                        error = "Search failed (Telegram code ${reply.code})."
                    )
                    else -> now.copy(loading = false, error = "Search returned an unexpected response.")
                }
                _results.value = _results.value + (chatId to state)
            }
        }
    }

    private fun preview(content: TdApi.MessageContent?): String = when (content) {
        is TdApi.MessageText -> content.text?.text.orEmpty()
        is TdApi.MessagePhoto -> "📷 Photo · ${content.caption?.text.orEmpty()}"
        is TdApi.MessageVideo -> "🎥 Video · ${content.caption?.text.orEmpty()}"
        is TdApi.MessageDocument -> "📄 ${content.document?.fileName.orEmpty()}"
        is TdApi.MessageAudio -> "🎵 Audio"
        is TdApi.MessageVoiceNote -> "🎤 Voice message"
        is TdApi.MessageSticker -> "🎨 Sticker"
        is TdApi.MessagePoll -> "📊 Poll"
        is TdApi.MessageLocation -> "📍 Location"
        else -> "Telegram message"
    }

    fun clear(chatId: Long) {
        synchronized(lock) {
            generations[chatId] = (generations[chatId] ?: 0) + 1
            _results.value = _results.value - chatId
        }
    }
    fun clearAll() {
        synchronized(lock) {
            // Do not reset generations to zero. An old TDLib callback from
            // before logout must never match a new search after re-login.
            generations.keys.toList().forEach { id ->
                generations[id] = (generations[id] ?: 0) + 1
            }
            _results.value = emptyMap()
        }
    }
}
