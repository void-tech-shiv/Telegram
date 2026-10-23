package dev.glacia.app.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.glacia.app.data.NativeCallUi
import io.github.pytgcalls.models.Frame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow

private val Ice = Color(0xFF8DE8FF)
private val Midnight = Color(0xFF050B20)
private val MidnightBlue = Color(0xFF101D3B)

private fun elapsedLabel(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%02d:%02d".format(m, s)
}

/**
 * Real 1:1 call overlay — no simulated media or fabricated timer.
 * The existing TDLib/NTgCalls session remains alive when the chat screen changes.
 * Reject accidental back/outside dismissal so hangup is an explicit user action.
 */
@Composable
internal fun NativeCallScreen(
    call: NativeCallUi,
    remoteFrames: SharedFlow<Frame>,
    localFrames: SharedFlow<Frame>,
    onAnswer: () -> Unit,
    onHangUp: () -> Unit,
    onMute: () -> Unit,
    onSpeaker: () -> Unit,
    onCameraSwitch: () -> Unit
) {
    var clock by remember(call.callId) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(call.callId, call.connectedAtElapsedMs) {
        while (true) {
            clock = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    val elapsed = call.connectedAtElapsedMs?.let { elapsedLabel(((clock - it).coerceAtLeast(0)) / 1000) }
    val incoming = !call.outgoing && call.phase == "Incoming"
    val active = call.phase == "Connected"
    val hasVideo = call.video && active

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(
                    listOf(Midnight, MidnightBlue, Color(0xFF092737), Midnight)
                ))
        ) {
            if (hasVideo) {
                // Real decoded remote I420 camera frames; call ID scopes lifecycle.
                NativeCallVideo(
                    frames = remoteFrames,
                    callId = call.callId,
                    modifier = Modifier.fillMaxSize()
                )
                // Dark glass readability overlay, without hiding remote video.
                Box(Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(
                        Color(0xB3000918), Color.Transparent,
                        Color.Transparent, Color(0xC9000918)
                    ))
                ))
                Box(
                    Modifier.align(Alignment.CenterEnd)
                        .padding(end = 16.dp, bottom = 90.dp)
                        .size(width = 120.dp, height = 164.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(Color(0xBB14243D))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(23.dp))
                ) {
                    // Native camera capture frames; not a second camera session.
                    NativeCallVideo(
                        frames = localFrames,
                        callId = call.callId,
                        mirrored = true,
                        modifier = Modifier.fillMaxSize()
                    )
                    Text(
                        "YOU",
                        modifier = Modifier.align(Alignment.BottomStart)
                            .padding(8.dp),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // Voice calls have a deliberately calm glass/aurora identity.
                Box(
                    Modifier.align(Alignment.Center)
                        .size(240.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Ice.copy(alpha = .35f), Color(0xFF22478D), Color.Transparent)
                            )
                        )
                )
                Box(
                    Modifier.align(Alignment.Center)
                        .size(124.dp)
                        .clip(CircleShape)
                        .border(2.dp, Ice.copy(alpha = 0.6f), CircleShape)
                        .background(Color(0xFF172F56)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        call.peer.trim().split(Regex("\\s+")).take(2)
                            .mapNotNull { it.firstOrNull()?.uppercase() }.joinToString(""),
                        color = Ice, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Column(
                modifier = Modifier.align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 30.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "GLACIA  /  SECURE CALL",
                    color = Ice.copy(alpha = .9f),
                    fontSize = 11.sp,
                    letterSpacing = 2.0.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    call.peer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (elapsed != null) "Connected  •  $elapsed"
                    else call.phase,
                    color = Ice,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
                call.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        color = Color(0xFFFFC0C0),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Column(
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(36.dp))
                        .background(Color(0xC21B2D45))
                        .border(1.dp, Color.White.copy(alpha = .17f), RoundedCornerShape(36.dp))
                        .padding(vertical = 16.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassCallAction(
                        label = if (call.muted) "Unmute" else "Mute",
                        selected = call.muted, enabled = active,
                        onClick = onMute
                    ) { Icon(if (call.muted) Icons.Rounded.MicOff else Icons.Rounded.Mic, null) }
                    GlassCallAction(
                        label = if (call.speaker) "Speaker on" else "Speaker",
                        selected = call.speaker, enabled = active,
                        onClick = onSpeaker
                    ) { Icon(Icons.Rounded.VolumeUp, null) }
                    if (call.video) {
                        GlassCallAction(
                            label = "Flip",
                            enabled = active,
                            onClick = onCameraSwitch
                        ) { Icon(Icons.Rounded.Cameraswitch, null) }
                    } else {
                        GlassCallAction(label = "Voice", enabled = false, onClick = {}) {
                            Icon(Icons.Rounded.Call, null)
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (incoming) {
                        CallPill(
                            text = "Answer",
                            background = Color(0xFF138D68),
                            onClick = onAnswer
                        ) { Icon(Icons.Rounded.Call, null) }
                        Spacer(Modifier.width(18.dp))
                    }
                    CallPill(
                        text = if (incoming) "Decline" else "End call",
                        background = Color(0xFFCC405D),
                        onClick = onHangUp
                    ) { Icon(Icons.Rounded.CallEnd, null) }
                }
                Spacer(Modifier.height(9.dp))
                Text(
                    if (call.video) "Video • End-to-end encrypted" else "Voice • End-to-end encrypted",
                    color = Color.White.copy(alpha = .54f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun GlassCallAction(
    label: String,
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = if (selected) Ice else Color.White.copy(alpha = if (enabled) .13f else .05f),
            contentColor = if (selected) Midnight else Color.White,
            modifier = Modifier.size(57.dp),
        ) {
            IconButton(onClick = onClick, enabled = enabled) { icon() }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            label,
            color = if (enabled) Color.White else Color.White.copy(alpha = .4f),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun CallPill(
    text: String,
    background: Color,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        color = background,
        contentColor = Color.White,
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 22.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(Modifier.width(9.dp))
            Text(text, fontWeight = FontWeight.Bold)
        }
    }
}
