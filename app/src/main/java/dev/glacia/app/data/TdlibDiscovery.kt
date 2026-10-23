package dev.glacia.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/**
 * A bounded, debounced-by-UI discovery query against Telegram.
 * SearchChats finds cached/local chats; SearchPublicChats searches public users,
 * channels and groups. No message contents or queries are sent to any third party.
 */
class TdlibDiscovery(
    private val client: () -> Client?,
    private val files: TdlibFileStore
) {
    private val lock = Any()
    private var generation = 0L
    private var pending = 0
    private val found = linkedMapOf<Long, Chat>()
    private val _results = MutableStateFlow<List<Chat>>(emptyList())
    val results = _results.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun search(raw: String) {
        val query = raw.trim().take(70)
        val token: Long
        synchronized(lock) {
            generation++
            token = generation
            pending = if (query.length >= 2) 2 else 0
            found.clear()
            _results.value = emptyList()
            _error.value = null
            _loading.value = pending > 0
        }
        if (query.length < 2) return

        val active = client()
        if (active == null) {
            synchronized(lock) {
                if (generation == token) {
                    pending = 0
                    _loading.value = false
                    _error.value = "Connect to Telegram to search."
                }
            }
            return
        }
        active.send(TdApi.SearchChats(query, 30)) { reply ->
            handleMatches(active, token, reply)
        }
        active.send(TdApi.SearchPublicChats(query)) { reply ->
            handleMatches(active, token, reply)
        }
    }

    private fun handleMatches(active: Client, token: Long, reply: TdApi.Object) {
        if (reply is TdApi.Chats) {
            val ids = reply.chatIds.take(30).distinct()
            synchronized(lock) {
                if (token != generation) return
                pending += ids.size
            }
            ids.forEach { id ->
                active.send(TdApi.GetChat(id)) { obj ->
                    if (obj is TdApi.Chat && obj.title.isNotBlank()) {
                        files.ensure(obj.photo?.small)
                        val chat = Chat(
                            id = obj.id,
                            title = obj.title,
                            username = "",
                            preview = when (obj.type) {
                                is TdApi.ChatTypePrivate -> "Telegram user"
                                is TdApi.ChatTypeSecret -> "Secret chat"
                                else -> "Telegram group or channel"
                            },
                            time = "",
                            unread = obj.unreadCount,
                            isGroup = obj.type !is TdApi.ChatTypePrivate,
                            photoFileId = obj.photo?.small?.id
                        )
                        synchronized(lock) {
                            if (token == generation) {
                                found[chat.id] = chat
                                _results.value = found.values.toList()
                            }
                        }
                    }
                    finish(token)
                }
            }
        } else if (reply is TdApi.Error) {
            synchronized(lock) {
                if (token == generation && found.isEmpty()) {
                    _error.value = "Telegram search is temporarily unavailable (code ${reply.code})."
                }
            }
        }
        finish(token)
    }

    private fun finish(token: Long) {
        synchronized(lock) {
            if (token != generation) return
            pending = (pending - 1).coerceAtLeast(0)
            _loading.value = pending > 0
        }
    }

    fun clear() {
        synchronized(lock) {
            generation++
            pending = 0
            found.clear()
            _results.value = emptyList()
            _loading.value = false
            _error.value = null
        }
    }
}
