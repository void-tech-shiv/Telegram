package dev.glacia.app.data

/**
 * UI-only incoming 1:1 Telegram call metadata. Never contains encryption keys,
 * phone numbers, session data, or media. No native call can be answered until
 * a separately audited media transport is installed.
 */
data class IncomingCall(
    val callId: Int,
    val userId: Long,
    val video: Boolean,
    val callerName: String = "Telegram contact"
)

object IncomingCallState {
    /** New pending calls supersede stale ones; repeated updates keep the known name. */
    fun ringing(
        previous: IncomingCall?,
        callId: Int,
        userId: Long,
        video: Boolean
    ): IncomingCall? {
        if (callId <= 0 || userId <= 0L) return previous
        val name = previous?.takeIf { it.callId == callId && it.userId == userId }
            ?.callerName ?: "Telegram contact"
        return IncomingCall(callId, userId, video, name)
    }

    /** A delayed end event for a previous call must not dismiss a newer call. */
    fun finished(previous: IncomingCall?, callId: Int): IncomingCall? =
        previous?.takeUnless { it.callId == callId }

    fun named(previous: IncomingCall?, callId: Int, title: String): IncomingCall? =
        previous?.takeIf { it.callId == callId }?.copy(
            callerName = title.trim().take(80).ifBlank { "Telegram contact" }
        ) ?: previous
}
