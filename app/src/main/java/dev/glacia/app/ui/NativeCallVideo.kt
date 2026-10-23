package dev.glacia.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.pytgcalls.models.Frame
import kotlinx.coroutines.flow.SharedFlow
import org.webrtc.EglBase
import org.webrtc.JavaI420Buffer
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoFrame
import java.nio.ByteBuffer

/**
 * Only renders decoded frames from real native P2P camera playback. It never
 * simulates or synthesizes video. EGL and Surface lifetimes are tied to Compose.
 */
@Composable
internal fun NativeCallVideo(frames: SharedFlow<Frame>, callId: Int) {
    val context = LocalContext.current
    val egl = remember(callId) { EglBase.create() }
    val renderer = remember(callId) { SurfaceViewRenderer(context) }
    DisposableEffect(egl, renderer) {
        renderer.init(egl.eglBaseContext, null)
        renderer.setEnableHardwareScaler(true)
        onDispose {
            renderer.release()
            egl.release()
        }
    }
    LaunchedEffect(frames, callId, renderer) {
        frames.collect { frame -> renderRemoteFrame(renderer, frame) }
    }
    AndroidView(
        factory = { renderer },
        modifier = Modifier.fillMaxWidth().height(240.dp)
    )
}

private fun renderRemoteFrame(renderer: SurfaceViewRenderer, native: Frame) {
    val info = native.frame_data ?: return
    val w = info.width
    val h = info.height
    if (w !in 2..1920 || h !in 2..1080 || w % 2 != 0 || h % 2 != 0) return
    val ySize = w * h
    val cSize = (w / 2) * (h / 2)
    val size = ySize + 2 * cSize
    val src = native.data
    if (src.size < size) return

    val bytes = ByteBuffer.allocateDirect(size)
    bytes.put(src, 0, size)
    bytes.flip()
    val y = bytes.duplicate().apply { position(0); limit(ySize) }.slice()
    val u = bytes.duplicate().apply { position(ySize); limit(ySize + cSize) }.slice()
    val v = bytes.duplicate().apply { position(ySize + cSize); limit(size) }.slice()
    val image = JavaI420Buffer.wrap(w, h, y, w, u, w / 2, v, w / 2) { }
    val rotated = when {
        info.rotation.name.endsWith("270") -> 270
        info.rotation.name.endsWith("180") -> 180
        info.rotation.name.endsWith("90") -> 90
        else -> 0
    }
    val frame = VideoFrame(image, rotated, info.absolute_capture_timestamp_ms * 1_000_000L)
    renderer.onFrame(frame)
    frame.release()
}
