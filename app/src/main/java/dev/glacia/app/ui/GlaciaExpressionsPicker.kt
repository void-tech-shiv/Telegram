package dev.glacia.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import dev.glacia.app.data.ExpressionOption
import dev.glacia.app.data.ExpressionState
import java.io.File

/**
 * Telegram-owned recent stickers and saved GIFs, not a mock catalog.
 * No full animations are fetched merely to display this picker.
 */
@Composable
internal fun GlaciaExpressionsPicker(
    state: ExpressionState,
    filePaths: Map<Int, String>,
    dark: Boolean,
    onReload: () -> Unit,
    onLoadPack: (Long) -> Unit,
    onSend: (ExpressionOption) -> Unit,
    onDismiss: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val items = when (tab) {
        0 -> state.stickers
        1 -> state.packStickers
        else -> state.gifs
    }
    val foreground = if (dark) Color.White else Color(0xFF152443)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = if (dark) Color(0xFF1A2846) else Color(0xFFEFF6FF),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("GLACIA stickers & GIFs",
                        color = foreground,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    FilterChip(selected = tab == 0, onClick = { tab = 0 },
                        label = { Text("Recent") })
                    FilterChip(selected = tab == 1, onClick = { tab = 1 },
                        label = { Text("My packs") })
                    FilterChip(selected = tab == 2, onClick = { tab = 2 },
                        label = { Text("Saved GIFs") })
                }
                if (tab == 1) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        state.packs.forEach { pack ->
                            FilterChip(
                                selected = state.selectedPackId == pack.id,
                                onClick = { onLoadPack(pack.id) },
                                label = { Text(pack.title.take(24)) }
                            )
                        }
                    }
                    if (state.packLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (state.packs.isEmpty() && !state.loading) {
                        Text("No installed sticker packs on this account.",
                            color = foreground, style = MaterialTheme.typography.bodySmall)
                    }
                }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (!state.loading && items.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            when (tab) {
                                0 -> "No recent stickers yet."
                                1 -> if (state.packs.isEmpty()) "No installed sticker packs."
                                    else "Select a sticker pack above."
                                else -> "No saved Telegram GIFs yet."
                            },
                            color = foreground
                        )
                        Spacer(Modifier.height(5.dp))
                        Text("Use Telegram to add a pack or save a GIF.",
                            color = foreground.copy(alpha = 0.65f),
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onReload) { Text("Refresh") }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 390.dp),
                        contentPadding = PaddingValues(5.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(items, key = { "${it.animated}-${it.fileId}" }) { item ->
                            val path = item.previewFileId?.let(filePaths::get)
                            Box(
                                Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(foreground.copy(alpha = .07f))
                                    .clickable { onSend(item) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (path != null) {
                                    AsyncImage(
                                        model = File(path),
                                        contentDescription = if (item.animated) "Send saved GIF"
                                            else "Send sticker ${item.emoji}",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize().padding(7.dp)
                                    )
                                } else {
                                    Text(
                                        if (item.animated) "GIF" else item.emoji.ifBlank { "🎨" },
                                        color = foreground,
                                        style = MaterialTheme.typography.titleLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
