package dev.glacia.app.data

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/** Ephemeral activity: never store in database or keep stale "typing" forever. */
data class TypingActivity(val label: String, val expiresAt: Long)

object TypingStatePolicy {
    fun isActive(expiresAt: Long, now: Long): Boolean = expiresAt > now
}

class TdlibTyping(private val client: () -> Client?) {
    private val lock = Any()
    private val _activity = MutableStateFlow<Map<Long, TypingActivity>>(emptyMap())
    val activity = _activity.asStateFlow()

    fun onUpdate(update: TdApi.Object) {
        if (update !is TdApi.UpdateChatAction) return
        synchronized(lock) {
            when (update.action) {
                is TdApi.ChatActionTyping -> {
                    _activity.value = _activity.value + (
                        update.chatId to TypingActivity(
                            label = "typing…",
                            expiresAt = SystemClock.elapsedRealtime() + 6_000L
                        )
                    )
                }
                is TdApi.ChatActionCancel -> {
                    _activity.value = _activity.value - update.chatId
                }
                else -> {
                    // Do not leave an old typing state visible when the action changes.
                    _activity.value = _activity.value - update.chatId
                }
            }
        }
    }

    fun prune() {
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            _activity.value = _activity.value.filterValues { TypingStatePolicy.isActive(it.expiresAt, now) }
        }
    }

    fun sendTyping(chatId: Long, typing: Boolean) {
        val active = client() ?: return
        if (chatId == 0L) return
        active.send(TdApi.SendChatAction().apply {
            this.chatId = chatId
            action = if (typing) TdApi.ChatActionTyping() else TdApi.ChatActionCancel()
        }) { /* Other clients may reject typing; never disrupt message entry. */ }
    }

    fun clear() = synchronized(lock) { _activity.value = emptyMap() }
}
