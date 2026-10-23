package dev.glacia.app.data

import android.content.Context
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.os.Build
import android.util.Log
import io.github.pytgcalls.NTgCalls
import io.github.pytgcalls.media.AudioDescription
import io.github.pytgcalls.models.Frame
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
/**
 * Sanitized stage-only diagnostic. Do not expose native exception messages:
 * these may contain call configuration, ICE secrets, or endpoints.
 */
internal class NativeCallSetupFailure(val stage: String, cause: Throwable) :
    IllegalStateException("Native call failed at $stage", cause)

internal class NativeCallMedia(
    private val context: Context,
    private val onSignaling: (Int, ByteArray) -> Unit,
    private val onConnection: (Int, String) -> Unit,
    private val onRemoteFrame: (Int, Frame) -> Unit,
    private val onLocalFrame: (Int, Frame) -> Unit
) {
    private var native: NTgCalls? = null
    @Volatile private var mediaUserId: Long = 0
    private val webrtcReady = AtomicBoolean(false)
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var originalMode = AudioManager.MODE_NORMAL
    private var routed = false
    @Volatile private var connected = false
    @Volatile private var signalingReady = false
    private var cameraDeviceMetadata: String? = null
    private var activeVideoCall = false
    private var bluetoothCommunicationDeviceSelected = false
    private inline fun <T> step(name: String, action: () -> T): T {
        Log.i("GLACIA-VoIP", "media_stage=$name")
        return try { action() }
        catch (failure: Throwable) {
            Log.w("GLACIA-VoIP", "media_stage_failed=$name type=${failure.javaClass.simpleName}")
            throw NativeCallSetupFailure(name, failure)
        }
    }

    fun isMediaConnected(): Boolean = connected
    fun canReceiveSignaling(): Boolean = signalingReady && mediaUserId != 0L

    fun protocol(): TdApi.CallProtocol {
        ensureWebRtcContext()
        val p = NTgCalls.getProtocol()
        check(p.min_layer > 0 && p.max_layer >= p.min_layer && p.library_versions.isNotEmpty()) {
            "Native Telegram calling protocol unavailable."
        }
        return TdApi.CallProtocol(p.udp_p2p, p.udp_reflector, p.min_layer, p.max_layer, p.library_versions.toTypedArray())
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
        signalingReady = false
        connected = false
        activeVideoCall = call.isVideo
        val instance = native ?: NTgCalls().also { native = it }
        // TDLib uses call.id for signaling. NTgCalls identifies the session by user.id.
        // Always map callback data to the currently active TDLib call in the controller.
        mediaUserId = call.userId
        instance.onSignalingData { id, bytes ->
            if (id == call.userId && id == mediaUserId && bytes != null && bytes.isNotEmpty()) {
                onSignaling(call.id, bytes)
            }
        }
        instance.onConnectionChange { id, info ->
            if (id == mediaUserId) {
                val currentState = info.state.name
                connected = currentState.equals("CONNECTED", ignoreCase = true)
                onConnection(call.id, currentState)
            }
        }
        instance.onFrames { id, mode, device, frames ->
            if (id == mediaUserId && device == StreamDevice.CAMERA) {
                when (mode) {
                    StreamMode.PLAYBACK ->
                        frames.forEach { frame -> onRemoteFrame(call.id, frame) }
                    StreamMode.CAPTURE ->
                        frames.forEach { frame -> onLocalFrame(call.id, frame) }
                    else -> Unit
                }
            }
        }

        // Allow TDLib to conduct Diffie-Hellman. Native media uses its resulting key.
        step("create_p2p") { instance.createP2pCall(call.userId) }
        val mic = AudioDescription(MediaSource.DEVICE, 48000, 2, "{\"is_microphone\":true}", true)
        val speaker = AudioDescription(MediaSource.DEVICE, 48000, 2, "{\"is_microphone\":false}", true)
        // DEVICE capture transmits camera frames; EXTERNAL playback exposes decoded
        // remote I420 frames through the frame callback for rendering in Compose.
        val camera = if (call.isVideo) step("camera_enumeration") {
            val device = NTgCalls.getMediaDevices().camera.firstOrNull()
                ?: error("No camera available for native video calling.")
            cameraDeviceMetadata = device.metadata
            VideoDescription(MediaSource.DEVICE, 640, 480, 24, device.metadata, true)
        } else null
        val remoteVideo = if (call.isVideo) {
            VideoDescription(MediaSource.EXTERNAL, 0, 0, 0, "", false)
        } else null
        step("audio_capture") {
            instance.setStreamSources(call.userId, StreamMode.CAPTURE, MediaDescription(mic, null, camera, null))
        }
        step("audio_playback") {
            // ntgcalls StreamManager.enable_audio_incoming() checks the playback
            // MICROPHONE slot, not Speaker. Speaker slot only would silently
            // disable incoming RTP audio after an otherwise successful handshake.
            // is_microphone=false tells its Android sink to render, not capture.
            instance.setStreamSources(call.userId, StreamMode.PLAYBACK, MediaDescription(speaker, null, remoteVideo, null))
        }
        step("key_exchange") { instance.skipExchange(call.userId, ready.encryptionKey, call.isOutgoing) }
        val mapped = step("server_mapping") {
            ready.servers.mapNotNull(::toRtcServer).also {
                check(it.isNotEmpty()) { "Telegram did not provide usable call servers." }
            }
        }
        // ready.protocol describes the peer; native match_version() must get
        // a non-empty intersection. Never silently advertise wrong versions.
        val commonVersions = step("protocol_match") {
            NativeCallNegotiation.compatibleVersions(
                NTgCalls.getProtocol().library_versions,
                ready.protocol.libraryVersions.toList()
            ).also { require(it.isNotEmpty()) { "No common Telegram media version" } }
        }
        // ntgcalls v3.0 calls JSON::parse on any non-null value.
        // An empty string is NOT JSON. Passing it caused ice_connect to throw.
        val customParameters = NativeCallNegotiation.customParametersOrNull(ready.customParameters)
        Log.i(
            "GLACIA-VoIP",
            "ice_inputs servers=${mapped.size} protocol_versions=${commonVersions.size} custom_params=${customParameters != null}"
        )
        // Set communication mode before starting media, not afterwards.
        step("audio_routing") {
            originalMode = audio.mode
            audio.mode = AudioManager.MODE_IN_COMMUNICATION
            routed = true
        }
        step("ice_connect") {
            instance.connectP2p(
                call.userId, mapped, commonVersions,
                ready.allowP2p, customParameters
            )
        }
        signalingReady = true
        Log.i("GLACIA-VoIP", "media_setup_complete")
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
        if (data.isNotEmpty() && canReceiveSignaling()) native?.sendSignalingData(mediaUserId, data)
    }

    /**
     * Switch the actual NTgCalls capture device; do not start a second camera or
     * fabricate a local preview. Caller guarantees exclusive worker access.
     */
    fun switchCamera(): Boolean {
        val id = mediaUserId
        if (!activeVideoCall || id == 0L || !connected) return false
        val cameras = NTgCalls.getMediaDevices().camera
        if (cameras.size < 2) return false
        val from = cameras.indexOfFirst { it.metadata == cameraDeviceMetadata }
        val next = cameras[(from + 1).coerceAtLeast(0) % cameras.size]
        val mic = AudioDescription(MediaSource.DEVICE, 48000, 2, "{\"is_microphone\":true}", true)
        val camera = VideoDescription(MediaSource.DEVICE, 640, 480, 24, next.metadata, true)
        native?.setStreamSources(
            id, StreamMode.CAPTURE, MediaDescription(mic, null, camera, null)
        ) ?: return false
        cameraDeviceMetadata = next.metadata
        return true
    }

    fun setMuted(muted: Boolean) {
        if (mediaUserId == 0L) return
        if (muted) native?.mute(mediaUserId) else native?.unmute(mediaUserId)
    }

    fun setSpeaker(speaker: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            bluetoothCommunicationDeviceSelected) {
            audio.clearCommunicationDevice()
            bluetoothCommunicationDeviceSelected = false
        }
        @Suppress("DEPRECATION")
        audio.isSpeakerphoneOn = speaker
    }

    /**
     * Android 12+ routes microphone + playback to an already-connected headset.
     * Permissions are requested by UI only after explicit user tap.
     * Returns false when no paired communication headset is available.
     */
    fun selectBluetooth(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || mediaUserId == 0L ||
            !connected || !routed) return false
        val device = audio.availableCommunicationDevices.firstOrNull { entry ->
            entry.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                entry.type == AudioDeviceInfo.TYPE_HEARING_AID ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && (
                    entry.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        entry.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                ))
        } ?: return false
        val success = audio.setCommunicationDevice(device)
        if (success) bluetoothCommunicationDeviceSelected = true
        return success
    }

    fun stop() {
        val userId = mediaUserId
        mediaUserId = 0L
        signalingReady = false
        if (userId != 0L) runCatching { native?.stop(userId) }
        native = null
        connected = false
        activeVideoCall = false
        cameraDeviceMetadata = null
        if (routed) {
            routed = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                bluetoothCommunicationDeviceSelected) {
                runCatching { audio.clearCommunicationDevice() }
                bluetoothCommunicationDeviceSelected = false
            }
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
