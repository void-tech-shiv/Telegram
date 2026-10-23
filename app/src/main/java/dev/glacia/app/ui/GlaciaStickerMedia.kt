package dev.glacia.app.ui

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.airbnb.lottie.compose.*
import dev.glacia.app.data.TgsDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Protect in-app renderers from paths outside TDLib/app-private storage. */
private fun trustedStickerFile(context: Context, path: String?): File? =
    if (path == null) null else runCatching {
        val file = File(path).canonicalFile
        val roots = listOf(
            context.filesDir.canonicalFile,
            context.getDir("tdlib", Context.MODE_PRIVATE).canonicalFile
        )
        file.takeIf { candidate ->
            candidate.isFile && candidate.length() > 0 &&
                roots.any { candidate.path.startsWith(it.path + File.separator) }
        }
    }.getOrNull()

/**
 * Actual Telegram sticker renderer: WEBP -> Coil, TGS -> Lottie, WebM -> ExoPlayer.
 * Only compose-visible WebM players exist; released immediately on dispose.
 * Audio is muted, video loops. If decoding fails, keep the user's thumbnail
 * and a retry button instead of displaying an infinite blank bubble.
 */
@Composable
internal fun GlaciaStickerMedia(
    stickerFormat: String?,
    filePath: String?,
    thumbnailPath: String?,
    label: String,
    onDownload: () -> Unit,
    downloading: Boolean,
    error: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val media = remember(filePath) { trustedStickerFile(context, filePath) }
    val thumbnail = remember(thumbnailPath) { trustedStickerFile(context, thumbnailPath) }
    var decodingFailed by remember(filePath, stickerFormat) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .width(206.dp)
            .height(192.dp)
            .background(Color(0x160F1E43), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        when {
            media != null && stickerFormat == "tgs" && !decodingFailed -> {
                val json by produceState<String?>(initialValue = null, media.absolutePath) {
                    value = withContext(Dispatchers.IO) { TgsDecoder.readBounded(media) }
                }
                if (json != null) {
                    val composition by rememberLottieComposition(
                        LottieCompositionSpec.JsonString(json!!)
                    )
                    if (composition != null) {
                        LottieAnimation(
                            composition = composition,
                            iterations = LottieConstants.IterateForever,
                            modifier = Modifier.fillMaxSize().padding(5.dp)
                        )
                    } else if (thumbnail != null) {
                        AsyncImage(
                            model = thumbnail,
                            contentDescription = label,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                } else if (thumbnail != null) {
                    AsyncImage(
                        model = thumbnail,
                        contentDescription = label,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text("Loading animated sticker…", style = MaterialTheme.typography.labelSmall)
                }
            }
            media != null && stickerFormat == "webm" && !decodingFailed -> {
                val lifecycle = LocalLifecycleOwner.current
                val player = remember(media.absolutePath) {
                    ExoPlayer.Builder(context).build().apply {
                        volume = 0f
                        repeatMode = Player.REPEAT_MODE_ONE
                        setMediaItem(MediaItem.fromUri(Uri.fromFile(media)))
                        prepare()
                        playWhenReady = true
                    }
                }
                DisposableEffect(player, lifecycle) {
                    val listener = object : Player.Listener {
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            decodingFailed = true
                        }
                    }
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_STOP) player.pause()
                        if (event == Lifecycle.Event.ON_START) player.play()
                    }
                    player.addListener(listener)
                    lifecycle.lifecycle.addObserver(observer)
                    onDispose {
                        player.removeListener(listener)
                        lifecycle.lifecycle.removeObserver(observer)
                        player.stop()
                        player.release()
                    }
                }
                AndroidView(
                    factory = {
                        PlayerView(it).apply {
                            useController = false
                            this.player = player
                        }
                    },
                    update = { it.player = player },
                    modifier = Modifier.fillMaxSize()
                )
            }
            media != null && stickerFormat == "webp" -> AsyncImage(
                model = media,
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            thumbnail != null -> AsyncImage(
                model = thumbnail,
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            else -> Text(label, style = MaterialTheme.typography.titleLarge)
        }

        if (media == null || decodingFailed) {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .background(Color(0xB0152445), RoundedCornerShape(12.dp)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextButton(onClick = onDownload) {
                    Text(
                        if (downloading) "Retry download"
                        else if (decodingFailed) "Sticker preview unavailable · Retry"
                        else "Load sticker",
                        color = Color(0xFFBCEAFF),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (error != null) Text(
                    error, color = Color(0xFFFFCCCB),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
