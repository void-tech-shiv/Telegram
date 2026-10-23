package dev.glacia.app.data

import android.content.Context
import android.media.AudioManager
import io.github.pytgcalls.NTgCalls
import io.github.pytgcalls.media.AudioDescription
import io.github.pytgcalls.media.Frame
import io.github.pytgcalls.media.MediaDescription
import io.github.pytgcalls.media.MediaSource
import io.github.pytgcalls.media.StreamDevice
import io.github.pytgcalls.media.StreamMode
import io.github.pytgcalls.media.VideoDescription
import io.github.pytgcalls.p2p.RTCServer
import org.drinkless.tdlib.TdApi
import org.webrtc.ContextUtils
import java.util.concurrent.atomic.AtomicBoolean

/**
 * In-process Telegram P2P transport. All entry points are called on the call
 * controller's single worker, except the native signaling/frame callbacks.
 * TDLib owns the E2E key exchange. NTgCalls receives the ephemeral call key;
 * never persist, stringify or log keys, caller identifiers or signaling bytes.
 *
 * EXPERIMENTAL: device/ABI/interoperability certification has not happened.
 */
internal class NativeCallMedia(
    private val context: Context,
    private val onSignaling: (ByteArray) -> Unit,
    private val onConnection: (String) -> Unit,
    private val onRemoteFrame: (Frame) -> Unit
) {
    private var native: NTgCalls? = null
    private var mediaUserId: Long = 0
    private val webrtcReady = AtomicBoolean(false)
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var originalMode = AudioManager.MODE_NORMAL
    private var routed = false

    fun protocol(): TdApi.CallProtocol {
        ensureWebRtcContext()
        val p = NTgCalls.getProtocol()
        check(p.minLayer > 0 && p.maxLayer >= p.minLayer && p.libraryVersions.isNotEmpty()) {
            "Native Telegram calling protocol unavailable."
        }
        return TdApi.CallProtocol(p.udpP2p, p.udpReflector, p.minLayer, p.maxLayer, p.libraryVersions.toTypedArray())
    }

    /** Does not ring anyone, and tests whether the native library can initialize. */
    fun preflight(): Boolean = try {
        protocol()
        if (native == null) native = NTgCalls()
        true
    } catch (_: Throwable) { false }

    fun connect(call: TdApi.Call, ready: TdApi.CallStateReady) {
        check(call.userId > 0 && ready.encryptionKey?.size == 256) {
            "Telegram did not supply a valid call key."
        }
        val instance = native ?: NTgCalls().also { native = it }
        // TDLib uses call.id for signaling. NTgCalls identifies the session by user.id.
        // Always map callback data to the currently active TDLib call in the controller.
        mediaUserId = call.userId
        instance.setSignalingDataCallback { id, bytes ->
            if (id == mediaUserId && bytes != null && bytes.isNotEmpty()) onSignaling(bytes)
        }
        instance.setConnectionChangeCallback { id, info ->
            if (id == mediaUserId) onConnection(info.state.name)
        }
        instance.setFrameCallback { id, mode, device, frames ->
            if (id == mediaUserId && mode == StreamMode.PLAYBACK && device == StreamDevice.CAMERA) {
                frames.forEach(onRemoteFrame)
            }
        }

        // Allow TDLib to conduct Diffie-Hellman. Native media uses its resulting key.
        instance.createP2pCall(call.userId)
        val mic = AudioDescription(MediaSource.DEVICE, "{\"is_microphone\":true}", true, 48000, 2)
        val speaker = AudioDescription(MediaSource.DEVICE, "{\"is_microphone\":false}", true, 48000, 2)
        // DEVICE capture transmits camera frames; EXTERNAL playback exposes decoded
        // remote I420 frames through the frame callback for rendering in Compose.
        val camera = if (call.isVideo) {
            val device = NTgCalls.getMediaDevices().camera.firstOrNull()
                ?: error("No camera available for native video calling.")
            VideoDescription(MediaSource.DEVICE, device.metadata, true, 640, 480, 24)
        } else null
        val remoteVideo = if (call.isVideo) {
            VideoDescription(MediaSource.EXTERNAL, "", false, 0, 0, 0)
        } else null
        instance.setStreamSources(call.userId, StreamMode.CAPTURE, MediaDescription(mic, null, camera, null))
        instance.setStreamSources(call.userId, StreamMode.PLAYBACK, MediaDescription(null, speaker, remoteVideo, null))
        instance.skipExchange(call.userId, ready.encryptionKey, call.isOutgoing)
        val mapped = ready.servers.mapNotNull(::toRtcServer)
        check(mapped.isNotEmpty()) { "Telegram did not provide usable call servers." }
        instance.connectP2p(
            call.userId, mapped, ready.protocol.libraryVersions.toList(),
            ready.allowP2p, ready.customParameters
        )
        originalMode = audio.mode
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        routed = true
    }

    private fun toRtcServer(s: TdApi.CallServer): RTCServer? {
        if (s.port !in 1..65535) return null
        val kind = s.type
        val reflector = kind as? TdApi.CallServerTypeTelegramReflector
        val webrtc = kind as? TdApi.CallServerTypeWebrtc
        if (reflector == null && webrtc == null) return null
        return RTCServer(
            s.id, s.ipAddress, s.ipv6Address, s.port,
            webrtc?.username ?: "", webrtc?.password ?: "",
            webrtc?.supportsTurn == true, webrtc?.supportsStun == true,
            reflector?.isTcp == true, reflector?.peerTag
        )
    }

    fun receiveSignaling(data: ByteArray) {
        if (data.isNotEmpty() && mediaUserId != 0L) native?.sendSignalingData(mediaUserId, data)
    }

    fun setMuted(muted: Boolean) {
        if (mediaUserId == 0L) return
        if (muted) native?.mute(mediaUserId) else native?.unmute(mediaUserId)
    }

    fun setSpeaker(speaker: Boolean) {
        @Suppress("DEPRECATION")
        audio.isSpeakerphoneOn = speaker
    }

    fun stop() {
        val userId = mediaUserId
        mediaUserId = 0L
        if (userId != 0L) runCatching { native?.stop(userId) }
        native = null
        if (routed) {
            routed = false
            @Suppress("DEPRECATION")
            runCatching { audio.isSpeakerphoneOn = false }
            runCatching { audio.mode = originalMode }
        }
    }

    private fun ensureWebRtcContext() {
        if (webrtcReady.compareAndSet(false, true)) {
            try { ContextUtils.initialize(context.applicationContext) }
            catch (e: Throwable) {
                webrtcReady.set(false)
                throw e
            }
        }
    }
}
