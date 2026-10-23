package dev.glacia.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/**
 * Phase 3.9 / first increment: observe real incoming TDLib 1:1 call invitations
 * and let the user DECLINE. It NEVER calls AcceptCall or CreateCall because
 * this app currently has no tgcalls/WebRTC audio/video implementation.
 *
 * Callback threads may be non-UI threads; flows propagate immutable state.
 * No signaling payload, phone number, or cryptographic key is logged or stored.
 */
class TdlibCallInbox(private val client: () -> Client?) {
    private val lock = Any()
    private val _incoming = MutableStateFlow<IncomingCall?>(null)
    val incoming = _incoming.asStateFlow()
    private val _declining = MutableStateFlow(false)
    val declining = _declining.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private var pendingDeclineId: Int? = null

    fun onUpdate(update: TdApi.Object) {
        val call = (update as? TdApi.UpdateCall)?.call ?: return
        if (!call.isOutgoing && call.state is TdApi.CallStatePending) {
            val previousId: Int?
            synchronized(lock) {
                previousId = _incoming.value?.callId
                _incoming.value = IncomingCallState.ringing(
                    _incoming.value, call.id, call.userId, call.isVideo
                )
                if (previousId != call.id) {
                    pendingDeclineId = null
                    _declining.value = false
                    _error.value = null
                }
            }
            if (previousId != call.id) resolveName(call.id, call.userId)
        } else {
            // No audio/video path: all non-ringing states remove this invitation.
            // Critically, a late update for an older call can't dismiss a newer one.
            synchronized(lock) {
                _incoming.value = IncomingCallState.finished(_incoming.value, call.id)
                if (pendingDeclineId == call.id) {
                    pendingDeclineId = null
                    _declining.value = false
                }
            }
        }
    }

    private fun resolveName(callId: Int, userId: Long) {
        client()?.send(TdApi.GetUser(userId)) { response ->
            val user = response as? TdApi.User ?: return@send
            val safeName = listOf(user.firstName, user.lastName)
                .filterNotNull().joinToString(" ")
                .replace(Regex("[\\r\\n\\t]"), " ")
                .trim().take(80)
            synchronized(lock) {
                if (_incoming.value?.userId == userId) {
                    _incoming.value = IncomingCallState.named(_incoming.value, callId, safeName)
                }
            }
        }
    }

    fun decline() {
        val active = client() ?: run {
            _error.value = "Telegram is not connected."
            return
        }
        val call: IncomingCall
        synchronized(lock) {
            call = _incoming.value ?: return
            if (pendingDeclineId == call.callId) return
            pendingDeclineId = call.callId
            _declining.value = true
            _error.value = null
        }
        // Empty invite link/duration/connection ID: this is an unanswered call.
        active.send(TdApi.DiscardCall().apply {
            callId = call.callId
            isDisconnected = false
            inviteLink = ""
            duration = 0
            isVideo = call.video
            connectionId = 0L
        }) { response ->
            synchronized(lock) {
                if (pendingDeclineId != call.callId) return@synchronized
                pendingDeclineId = null
                _declining.value = false
                if (response is TdApi.Error) {
                    _error.value = "Could not decline call (Telegram code ${response.code})."
                } else {
                    _incoming.value = IncomingCallState.finished(_incoming.value, call.callId)
                }
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            _incoming.value = null
            _error.value = null
            _declining.value = false
            pendingDeclineId = null
        }
    }
}
