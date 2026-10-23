package dev.glacia.app.ui

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.io.File

/**
 * Local-only player. TDLib downloads the original before playback; GLACIA
 * never hands Telegram session data or content URLs to a third-party service.
 * ExoPlayer is scoped to this dialog and released when dismissed.
 */
@Composable
fun GlaciaPlaybackDialog(
    path: String,
    isVideo: Boolean,
    fallbackSeconds: Int,
    dark: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val trustedFile = remember(path) { verifiedPlaybackFile(context, path) }
    if (trustedFile == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
            title = { Text("File unavailable") },
            text = { Text("This media is not available in GLACIA's private storage.") }
        )
        return
    }
    val player = remember(trustedFile) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(trustedFile)))
            prepare()
            playWhenReady = true // user deliberately opened this player
        }
    }
    DisposableEffect(player) {
        onDispose {
            player.stop()
            player.release()
        }
    }
    var playing by remember(player) { mutableStateOf(true) }
    var currentMs by remember(player) { mutableLongStateOf(0L) }
    var durationMs by remember(player) { mutableLongStateOf((fallbackSeconds * 1000L).coerceAtLeast(0L)) }
    var playbackError by remember(player) { mutableStateOf<String?>(null) }
    LaunchedEffect(player) {
        while (true) {
            playing = player.isPlaying
            currentMs = player.currentPosition.coerceAtLeast(0L)
            if (player.duration != C.TIME_UNSET && player.duration > 0) {
                durationMs = player.duration
            }
            playbackError = if (player.playerError != null) {
                "Unable to play this media on your device (format or decoder error)."
            } else null
            delay(350)
        }
    }

    val surface = if (dark) Color(0xFF1C2949) else Color(0xFFF6F9FF)
    val ink = if (dark) Color(0xFFF3F6FF) else Color(0xFF1B2540)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = surface,
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(15.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isVideo) "GLACIA video" else "GLACIA voice / audio",
                        color = ink,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, "Close player", tint = ink)
                    }
                }
                if (isVideo) {
                    AndroidView(
                        factory = { viewContext ->
                            PlayerView(viewContext).apply {
                                this.player = player
                                useController = true
                                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            }
                        },
                        update = { it.player = player },
                        modifier = Modifier.fillMaxWidth()
                            .height(285.dp)
                            .background(Color.Black)
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = {
                                if (player.isPlaying) player.pause() else player.play()
                            }
                        ) {
                            Icon(
                                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (playing) "Pause voice" else "Play voice",
                                tint = ink
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            val max = durationMs.coerceAtLeast(1L)
                            Slider(
                                value = currentMs.coerceIn(0L, max).toFloat(),
                                onValueChange = { player.seekTo(it.toLong()) },
                                valueRange = 0f..max.toFloat()
                            )
                            Text(
                                "${formatPlaybackTime(currentMs)} / ${formatPlaybackTime(durationMs)}",
                                color = ink,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                playbackError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        }
    }
}

/** Reject path traversal and anything outside the TDLib/app-private file roots. */
private fun verifiedPlaybackFile(context: Context, path: String): File? = runCatching {
    val file = File(path).canonicalFile
    val roots = listOf(
        context.filesDir.canonicalFile,
        context.getDir("tdlib", Context.MODE_PRIVATE).canonicalFile
    )
    file.takeIf { candidate ->
        candidate.isFile && candidate.length() > 0L &&
            roots.any { candidate.path.startsWith(it.path + File.separator) }
    }
}.getOrNull()

private fun formatPlaybackTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}
