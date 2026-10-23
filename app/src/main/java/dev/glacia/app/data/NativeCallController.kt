package dev.glacia.app.data

import android.content.Context
import android.util.Log
import dev.glacia.app.BuildConfig
import io.github.pytgcalls.models.Frame
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.concurrent.Executors

data class NativeCallUi(
    val callId: Int,
    val video: Boolean,
    val outgoing: Boolean,
    val phase: String,
    val peer: String = "Telegram contact",
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val error: String? = null
)

/**
 * Experimental one-to-one TDLib signaling controller.
 * Real calls are only created/answered in an explicitly opt-in APK with the
 * native engine installed and a successful native preflight. Not production QA'd.
 */
class NativeCallController(
    context: Context,
    private val client: () -> Client?
) {
    val enabled: Boolean = BuildConfig.EXPERIMENTAL_NATIVE_CALLS
    private val worker = Executors.newSingleThreadExecutor { job ->
        Thread(job, "GLACIA-VoIP").apply { isDaemon = true }
    }
    private val _call = MutableStateFlow<NativeCallUi?>(null)
    val call = _call.asStateFlow()
    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()
    fun dismissNotice() { _notice.value = null }
    private val _remoteFrames = MutableSharedFlow<Frame>(extraBufferCapacity = 2)
    val remoteFrames = _remoteFrames.asSharedFlow()
    private val lock = Any()
    private var tdCall: TdApi.Call? = null
    private var connectedCallId = 0
    private var generation = 0L
    private var media: NativeCallMedia? = null
    // Encrypted TDLib signaling may arrive before CallStateReady / NTgCalls.
    // Strictly bounded in-memory buffers; cleared on hangup/logout, never logged.
    private val pendingSignaling = ArrayDeque<ByteArray>()
    private val maxBufferedPackets = 32
    private val maxPacketBytes = 65536
    private val appContext = context.applicationContext

    fun onUpdate(update: TdApi.Object) {
        if (!enabled) return
        when (update) {
            is TdApi.UpdateCall -> {
                val call = update.call
                val state = call.state
                if (state is TdApi.CallStateDiscarded ||
                    state is TdApi.CallStateError ||
                    state is TdApi.CallStateHangingUp) {
                    val shouldStop = synchronized(lock) {
                        if (tdCall?.id != call.id) false
                        else {
                            val prior = _call.value
                            if (state is TdApi.CallStateError) {
                                _notice.value = "Telegram call error (code ${state.error.code})."
                                Log.w("GLACIA-VoIP", "tdlib_call_error code=${state.error.code}")
                            } else if (state is TdApi.CallStateDiscarded &&
                                prior != null && prior.phase != "Connected" &&
                                prior.phase != "Incoming" && prior.phase != "Ringing…"
                            ) {
                                if (_notice.value == null) {
                                    _notice.value = "Call ended during setup. " +
                                        "Check the GLACIA-VoIP diagnostic in Android Studio Logcat."
                                }
                                Log.i("GLACIA-VoIP", "tdlib_call_ended_during_setup")
                            }
                            generation++
                            tdCall = null
                            connectedCallId = 0
                            pendingSignaling.clear()
                            _call.value = null
                            true
                        }
                    }
                    if (shouldStop) worker.execute {
                        media?.stop()
                        media = null
                    }
                    return
                }

                val gen: Long
                synchronized(lock) {
                    // Never let an older call update replace an existing live call.
                    if (tdCall != null && tdCall?.id != call.id) return
                    tdCall = call
                    gen = generation
                    val old = _call.value?.takeIf { it.callId == call.id }
                    _call.value = NativeCallUi(
                        call.id, call.isVideo, call.isOutgoing,
                        when (state) {
                            is TdApi.CallStatePending -> if (call.isOutgoing) "Ringing…" else "Incoming"
                            is TdApi.CallStateExchangingKeys -> "Securing…"
                            is TdApi.CallStateReady -> old?.phase?.takeIf { it == "Connected" } ?: "Connecting…"
                            else -> "Connecting…"
                        },
                        peer = old?.peer ?: "Telegram contact",
                        muted = old?.muted ?: false, speaker = old?.speaker ?: false,
                        error = old?.error
                    )
                }
                if (state is TdApi.CallStateReady) {
                    synchronized(lock) {
                        if (connectedCallId == call.id) return
                        connectedCallId = call.id
                    }
                    worker.execute {
                        if (!stillActive(call.id, gen)) return@execute
                        try {
                            val activeMedia = media ?: engine().also { media = it }
                            activeMedia.connect(call, state)
                            flushPendingSignaling(call.id, gen)
                            if (stillActive(call.id, gen) && _call.value?.phase != "Connected") {
                                changePhase(call.id, "Connecting…")
                            }
                        } catch (failure: Throwable) {
                            val stage = (failure as? NativeCallSetupFailure)?.stage ?: "media_start"
                            Log.w(
                                "GLACIA-VoIP",
                                "call_setup_error stage=$stage type=${failure.javaClass.simpleName}"
                            )
                            _notice.value = "Native call setup failed at $stage. " +
                                "Check the GLACIA-VoIP Logcat filter."
                            changeError(call.id, "Media setup failed at $stage.")
                            discard(call.id, call.isVideo)
                        }
                    }
                }
                if (state is TdApi.CallStatePending) resolvePeer(call.id, call.userId)
            }
            is TdApi.UpdateNewCallSignalingData -> {
                // Updates can arrive *before* native createP2pCall and connectP2p.
                // Previously this called media?.receiveSignaling(...) and silently
                // dropped the first ICE handshake packet, severing the call.
                val gen: Long
                synchronized(lock) {
                    if (tdCall?.id != update.callId) return
                    gen = generation
                    if (update.data.isEmpty() || update.data.size > maxPacketBytes) return
                    if (pendingSignaling.size == maxBufferedPackets) pendingSignaling.removeFirst()
                    pendingSignaling.addLast(update.data.copyOf())
                }
                worker.execute { flushPendingSignaling(update.callId, gen) }
            }
        }
    }

    private fun flushPendingSignaling(callId: Int, gen: Long) {
        if (!stillActive(callId, gen)) return
        val transport = media?.takeIf { it.canReceiveSignaling() } ?: return
        while (stillActive(callId, gen)) {
            val packet = synchronized(lock) { pendingSignaling.removeFirstOrNull() } ?: break
            try {
                transport.receiveSignaling(packet)
            } catch (failure: Throwable) {
                // Do not print packet contents or native exception messages.
                Log.w("GLACIA-VoIP", "signal_delivery_error type=${failure.javaClass.simpleName}")
                changeError(callId, "Encrypted signaling delivery failed.")
                break
            } finally {
                packet.fill(0)
            }
        }
    }

    fun dial(chatId: Long, video: Boolean) {
        if (!enabled || chatId == 0L) return
        if (synchronized(lock) { tdCall != null }) {
            _notice.value = "A call is already active."
            return
        }
        worker.execute {
            val protocol = prepareProtocol() ?: return@execute
            val active = client() ?: run { _notice.value = "Telegram is offline."; return@execute }
            active.send(TdApi.GetChat(chatId)) { result ->
                val privateChat = (result as? TdApi.Chat)?.type as? TdApi.ChatTypePrivate
                if (privateChat == null) {
                    _notice.value = "Only 1:1 Telegram calls are supported."
                    return@send
                }
                active.send(TdApi.CreateCall(privateChat.userId, protocol, video)) { reply ->
                    if (reply is TdApi.Error) {
                        _notice.value = "Telegram rejected call (code ${reply.code})."
                    } else if (reply !is TdApi.CallId) {
                        _notice.value = "Telegram did not create a call."
                    }
                }
            }
        }
    }

    fun accept() {
        if (!enabled) return
        val target = synchronized(lock) {
            tdCall?.takeIf { !it.isOutgoing && it.state is TdApi.CallStatePending }
        } ?: return
        worker.execute {
            val protocol = prepareProtocol() ?: return@execute
            client()?.send(TdApi.AcceptCall(target.id, protocol)) { reply ->
                if (reply is TdApi.Error) changeError(
                    target.id, "Telegram could not answer (code ${reply.code})."
                )
            }
        }
    }

    fun hangUp() {
        val current = synchronized(lock) { tdCall } ?: return
        discard(current.id, current.isVideo)
    }

    private fun discard(callId: Int, video: Boolean) {
        client()?.send(TdApi.DiscardCall().apply {
            this.callId = callId
            isDisconnected = false
            inviteLink = ""
            duration = 0
            isVideo = video
            connectionId = 0L
        }) { reply ->
            if (reply is TdApi.Error) changeError(callId, "Hang up failed (code ${reply.code}).")
        }
    }

    fun setMuted(muted: Boolean) {
        val id = _call.value?.callId ?: return
        worker.execute {
            runCatching { media?.setMuted(muted) }.onSuccess {
                _call.value = _call.value?.takeIf { it.callId == id }?.copy(muted = muted)
            }.onFailure { changeError(id, "Microphone setting failed.") }
        }
    }

    fun setSpeaker(speaker: Boolean) {
        val id = _call.value?.callId ?: return
        worker.execute {
            runCatching { media?.setSpeaker(speaker) }.onSuccess {
                _call.value = _call.value?.takeIf { it.callId == id }?.copy(speaker = speaker)
            }.onFailure { changeError(id, "Audio routing failed.") }
        }
    }

    private fun engine(): NativeCallMedia = NativeCallMedia(
        appContext,
        onSignaling = { callId, data ->
            // Prevent callbacks from an ended call being sent on a newer call.
            if (synchronized(lock) { tdCall?.id == callId }) {
                client()?.send(TdApi.SendCallSignalingData(callId, data)) { reply ->
                    if (reply is TdApi.Error) {
                        Log.w("GLACIA-VoIP", "outbound_signaling_error code=${reply.code}")
                        changeError(callId, "Telegram signaling error (code ${reply.code}).")
                    }
                }
            }
        },
        onConnection = { callId, state ->
            if (synchronized(lock) { tdCall?.id == callId }) {
                Log.i("GLACIA-VoIP", "connection_state=${state.take(35)}")
                when (state.uppercase()) {
                    "CONNECTED" -> changePhase(callId, "Connected")
                    "DISCONNECTED" -> {
                        // ICE routes can disconnect transiently during negotiation.
                        // Do NOT unconditionally DiscardCall here.
                        changePhase(callId, "Reconnecting…")
                    }
                    "FAILED", "CLOSED" -> {
                        changeError(callId, "Native media failed ($state). Try another network.")
                        changePhase(callId, "Connection failed")
                        // Keep Hang up available, let TDLib own final call lifecycle.
                    }
                    else -> changePhase(callId, state.replace('_', ' ').take(35))
                }
            }
        },
        onRemoteFrame = { callId, frame ->
            if (synchronized(lock) { tdCall?.id == callId }) _remoteFrames.tryEmit(frame)
        }
    )

    private fun prepareProtocol(): TdApi.CallProtocol? = try {
        val active = media ?: engine().also { media = it }
        if (!active.preflight()) error("Native library initialization failed.")
        active.protocol()
    } catch (_: Throwable) {
        _notice.value = "Secure native calling engine could not initialize on this device."
        null
    }

    private fun stillActive(callId: Int, gen: Long): Boolean =
        synchronized(lock) { tdCall?.id == callId && gen == generation }

    private fun changePhase(callId: Int, value: String) {
        synchronized(lock) {
            _call.value = _call.value?.takeIf { it.callId == callId }?.copy(phase = value)
        }
    }
    private fun changeError(callId: Int, value: String) {
        synchronized(lock) {
            _call.value = _call.value?.takeIf { it.callId == callId }?.copy(error = value)
        }
    }

    private fun resolvePeer(callId: Int, userId: Long) {
        client()?.send(TdApi.GetUser(userId)) { result ->
            val user = result as? TdApi.User ?: return@send
            val name = "${user.firstName} ${user.lastName}".trim().take(80).ifBlank { "Telegram contact" }
            synchronized(lock) {
                _call.value = _call.value?.takeIf { it.callId == callId }?.copy(peer = name)
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            generation++
            connectedCallId = 0
            tdCall = null
            pendingSignaling.clear()
            _call.value = null
            _notice.value = null
        }
        worker.execute { media?.stop(); media = null }
    }
}
