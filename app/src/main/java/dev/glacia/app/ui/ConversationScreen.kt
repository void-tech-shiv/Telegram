package dev.glacia.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.glacia.app.data.DocumentKind
import dev.glacia.app.data.FileTransfer
import dev.glacia.app.data.Chat
import dev.glacia.app.data.Message
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import java.io.File

/**
 * Live chat UI. Initial history, older pages and incoming messages come from TDLib.
 * Plain text can be sent; attachments are shown as descriptive placeholders only.
 */
@Composable
fun ConversationScreen(
    chat: Chat,
    messages: List<Message>,
    filePaths: Map<Int, String>,
    documentTransfers: Map<Int, FileTransfer>,
    onDownloadDocument: (Int) -> Unit,
    loading: Boolean,
    error: String?,
    dark: Boolean,
    onSend: (String) -> Unit,
    onLoadMore: () -> Unit,
    onDismissError: () -> Unit
) {
    val foreground = if (dark) Color(0xFFF4F7FF) else Color(0xFF17213A)
    val secondary = if (dark) Color(0xFFC6D0E4) else Color(0xFF5D6981)
    val highlight = if (dark) Color(0xFF95E1FE) else Color(0xFF435EE3)
    val timelineState = rememberLazyListState()
    val saveDocument = rememberSaveDocument()
    var imageViewer by remember { mutableStateOf<String?>(null) }
    var newestSeen by remember(chat.id) { mutableLongStateOf(Long.MIN_VALUE) }
    var draft by rememberSaveable(chat.id) { mutableStateOf("") }

    // Adding an older history page should not jump the conversation back to bottom.
    LaunchedEffect(chat.id, messages.lastOrNull()?.id) {
        val newest = messages.lastOrNull()?.id ?: return@LaunchedEffect
        if (newestSeen != newest) {
            newestSeen = newest
            timelineState.scrollToItem(messages.size)
        }
    }
    fun send() {
        val text = draft.trim()
        if (text.isNotBlank() && text.length <= 4096) {
            onSend(text)
            draft = ""
        }
    }

    imageViewer?.let { imagePath ->
        Dialog(onDismissRequest = { imageViewer = null }) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = if (dark) Color(0xFF15203A) else Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    IconButton(
                        onClick = { imageViewer = null },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Rounded.Close, "Close image", tint = foreground)
                    }
                    AsyncImage(
                        model = File(imagePath),
                        contentDescription = "Downloaded document image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 540.dp)
                    )
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = timelineState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 17.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TextButton(
                            onClick = onLoadMore,
                            enabled = !loading && messages.isNotEmpty()
                        ) {
                            Text(if (loading) "Loading…" else if (messages.isEmpty())
                                "Chat history" else "Load earlier messages",
                                color = highlight)
                        }
                    }
                }
                items(messages, key = { it.id }) { msg ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            if (msg.outgoing) Arrangement.End else Arrangement.Start
                    ) {
                        val isVisual = msg.mediaType == "photo" || msg.mediaType == "video"
                        val bubbleShape = RoundedCornerShape(22.dp)
                        val bubbleWidth = if (isVisual) {
                            Modifier.fillMaxWidth(0.80f).widthIn(max = 278.dp)
                        } else {
                            Modifier.widthIn(max = 304.dp)
                        }
                        Column(
                            bubbleWidth
                                .clip(bubbleShape)
                                .background(
                                    if (msg.outgoing) {
                                        if (dark) Color(0xD83D5E90) else Color(0xDED6E1FF)
                                    } else {
                                        if (dark) Color(0xEA1D2B4B) else Color(0xF0FFFFFF)
                                    }
                                )
                                .border(
                                    1.dp, Color.White.copy(alpha = if (dark) 0.13f else 0.55f),
                                    bubbleShape
                                )
                                .padding(horizontal = 12.dp, vertical = 11.dp)
                        ) {
                            val thumbnailPath = msg.mediaFileId?.let(filePaths::get)
                            if (thumbnailPath != null && isVisual) {
                                Box(
                                    Modifier.fillMaxWidth()
                                        .height(if (msg.mediaType == "video") 155.dp else 175.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                ) {
                                    AsyncImage(
                                        model = File(thumbnailPath),
                                        contentDescription = "${msg.mediaType} thumbnail preview",
                                        contentScale = if (msg.mediaType == "photo")
                                            ContentScale.Fit else ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (msg.mediaType == "video") {
                                        Row(
                                            Modifier.align(Alignment.BottomStart)
                                                .padding(8.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xD916223C))
                                                .padding(horizontal = 9.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Rounded.Videocam,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text("Preview only", color = Color.White, fontSize = 10.sp)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(9.dp))
                            } else if (isVisual) {
                                Text(
                                    "Preview unavailable",
                                    color = secondary,
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.height(5.dp))
                            }
                            if (msg.mediaType == "document") {
                                val attachmentId = msg.attachmentFileId
                                val downloadedPath = attachmentId?.let(filePaths::get)
                                val transfer = attachmentId?.let(documentTransfers::get)
                                val isImage = DocumentKind.isImage(
                                    msg.attachmentMime, msg.attachmentName
                                )
                                val imagePath = if (isImage) thumbnailPath ?: downloadedPath else null
                                if (imagePath != null) {
                                    AsyncImage(
                                        model = File(imagePath),
                                        contentDescription = "Document image preview",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxWidth().height(172.dp)
                                            .clip(RoundedCornerShape(15.dp))
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(highlight.copy(alpha = 0.13f))
                                        .padding(horizontal = 10.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.InsertDriveFile,
                                        contentDescription = null,
                                        tint = highlight,
                                        modifier = Modifier.size(29.dp)
                                    )
                                    Spacer(Modifier.width(9.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            msg.attachmentName?.takeIf { it.isNotBlank() }
                                                ?: "Document",
                                            color = foreground,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            when {
                                                downloadedPath != null -> "Ready on this device"
                                                transfer?.downloading == true -> {
                                                    val total = transfer.totalBytes
                                                    if (total > 0L) {
                                                        "${(transfer.downloadedBytes * 100L / total).coerceIn(0, 100)}% downloaded"
                                                    } else "Downloading…"
                                                }
                                                transfer?.error != null -> transfer.error
                                                else -> "Tap Download to save or view"
                                            },
                                            color = secondary,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (downloadedPath != null) {
                                        if (isImage) {
                                            TextButton(onClick = { imageViewer = downloadedPath }) {
                                                Text("View", color = highlight)
                                            }
                                        }
                                        TextButton(onClick = {
                                            saveDocument(
                                                downloadedPath,
                                                msg.attachmentName,
                                                msg.attachmentMime
                                            )
                                        }) {
                                            Text("Save", color = highlight)
                                        }
                                    } else if (attachmentId != null) {
                                        TextButton(
                                            onClick = { onDownloadDocument(attachmentId) },
                                            enabled = transfer?.downloading != true
                                        ) {
                                            Icon(Icons.Rounded.Download, null,
                                                Modifier.size(16.dp), tint = highlight)
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                if (transfer?.downloading == true) "Downloading"
                                                else if (transfer?.error != null) "Retry"
                                                else "Download",
                                                color = highlight
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    msg.text,
                                    color = foreground,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 15.sp,
                                    lineHeight = 21.sp
                                )
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(
                                msg.time + when {
                                    msg.failed -> "  • Failed"
                                    msg.sending -> "  • Sending"
                                    else -> ""
                                },
                                color = if (msg.failed) Color(0xFFFF9E9E) else secondary,
                                fontSize = 10.sp,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
                if (messages.isEmpty() && !loading) {
                    item {
                        Text(
                            "No messages to display. You can send a text message below.",
                            color = secondary,
                            modifier = Modifier.padding(15.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            if (loading && messages.isEmpty()) {
                CircularProgressIndicator(
                    Modifier.align(Alignment.Center),
                    color = highlight
                )
            }
        }

        if (error != null) {
            Row(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 5.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color(0xFF673342).copy(alpha = 0.91f))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(error, color = Color.White, fontSize = 12.sp,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = onDismissError) { Text("Dismiss") }
            }
        }

        Row(
            Modifier.fillMaxWidth()
                .padding(start = 13.dp, end = 13.dp, top = 6.dp, bottom = 10.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(if (dark) Color(0xE6283959) else Color(0xF0FFFFFF))
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(27.dp))
                .padding(start = 18.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (draft.isEmpty()) {
                    Text("Message", color = secondary)
                }
                BasicTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 4096) draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = foreground, fontSize = 15.sp),
                    cursorBrush = SolidColor(highlight),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() })
                )
            }
            Spacer(Modifier.width(10.dp))
            IconButton(
                onClick = { send() },
                enabled = draft.isNotBlank(),
                modifier = Modifier.clip(CircleShape)
                    .background(highlight.copy(alpha = 0.17f))
            ) {
                Icon(Icons.Rounded.Send, contentDescription = "Send Telegram message",
                    tint = if (draft.isNotBlank()) highlight else secondary)
            }
        }
    }
}
