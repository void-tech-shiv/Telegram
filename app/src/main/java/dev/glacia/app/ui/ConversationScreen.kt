package dev.glacia.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
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
import dev.glacia.app.data.ChatSearchState
import dev.glacia.app.data.ExpressionState
import dev.glacia.app.data.ExpressionOption
import dev.glacia.app.data.Message
import dev.glacia.app.data.MessageActionPermissions
import dev.glacia.app.data.DeliveryReceipts
import dev.glacia.app.data.DeliveryState
import kotlinx.coroutines.delay
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import java.io.File

/**
 * A consistent Liquid Glass preview for both Telegram photos and images sent as files.
 * The blurred background fills portrait-image gutters while ContentScale.Fit preserves
 * the entire photo instead of cropping it.
 */
@Composable
private fun GlassImagePreview(
    path: String,
    description: String,
    modifier: Modifier = Modifier,
    onOpen: (() -> Unit)? = null
) {
    val rounded = RoundedCornerShape(16.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(194.dp)
            .clip(rounded)
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
    ) {
        AsyncImage(
            model = File(path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(20.dp)
        )
        Box(Modifier.fillMaxSize().background(Color(0x550B1433)))
        AsyncImage(
            model = File(path),
            contentDescription = description,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Live chat UI. Initial history, older pages and incoming messages come from TDLib.
 * Plain text can be sent; attachments are shown as descriptive placeholders only.
 */
@OptIn(ExperimentalFoundationApi::class)
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
    lastReadOutboxId: Long,
    active: Boolean,
    onVisibleMessages: (List<Long>) -> Unit,
    onTyping: (Boolean) -> Unit,
    onSend: (String, Long?) -> Unit,
    onEditMessage: (Long, String) -> Unit,
    onDeleteMessage: (Long, Boolean) -> Unit,
    onReactToMessage: (Long, String, Boolean) -> Unit,
    onForwardMessage: (Long, Long) -> Unit,
    onLoadMessageProperties: (Long) -> Unit,
    messageActionPermissions: Map<Pair<Long, Long>, MessageActionPermissions>,
    forwardTargets: List<Chat>,
    onAttachment: (android.net.Uri) -> Unit,
    onVoice: (RecordedVoice) -> Unit,
    expressions: ExpressionState,
    onRefreshExpressions: () -> Unit,
    onSendExpression: (ExpressionOption, Long?) -> Unit,
    mediaBusy: Boolean,
    onLoadMore: () -> Unit,
    searchState: ChatSearchState?,
    onSearchMessages: (String, Boolean) -> Unit,
    onClearSearch: () -> Unit,
    onJumpToMessage: (Long) -> Unit,
    onDismissError: () -> Unit
) {
    val foreground = if (dark) Color(0xFFF4F7FF) else Color(0xFF17213A)
    val secondary = if (dark) Color(0xFFC6D0E4) else Color(0xFF5D6981)
    val highlight = if (dark) Color(0xFF95E1FE) else Color(0xFF435EE3)
    val timelineState = rememberLazyListState()
    val saveDocument = rememberSaveDocument()
    var imageViewer by remember { mutableStateOf<String?>(null) }
    var playingMedia by remember { mutableStateOf<Triple<String, Boolean, Int>?>(null) }
    var newestSeen by remember(chat.id) { mutableLongStateOf(Long.MIN_VALUE) }
    var draft by rememberSaveable(chat.id) { mutableStateOf("") }
    var selectedMessage by remember(chat.id) { mutableStateOf<Message?>(null) }
    var showingSearch by rememberSaveable(chat.id) { mutableStateOf(false) }
    var showingExpressions by remember(chat.id) { mutableStateOf(false) }
    var searchText by rememberSaveable(chat.id) { mutableStateOf("") }
    var pendingJumpId by remember(chat.id) { mutableStateOf<Long?>(null) }
    var replyingTo by remember(chat.id) { mutableStateOf<Message?>(null) }
    var editing by remember(chat.id) { mutableStateOf<Message?>(null) }
    val context = LocalContext.current
    val recorder = remember(chat.id) { GlaciaVoiceRecorder(context.applicationContext) }
    var recording by remember(chat.id) { mutableStateOf(false) }
    var recordStartedAt by remember(chat.id) { mutableLongStateOf(0L) }
    var recordSeconds by remember(chat.id) { mutableIntStateOf(0) }
    var recordError by remember(chat.id) { mutableStateOf<String?>(null) }
    val chooseFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null && !mediaBusy) onAttachment(uri) }
    fun startRecording() {
        if (!active || mediaBusy || recording) return
        recordError = null
        recording = recorder.start()
        if (recording) {
            recordStartedAt = SystemClock.elapsedRealtime()
        } else {
            recordError = "Microphone unavailable or recording format unsupported."
        }
    }
    val microphone = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording()
        else recordError = "Microphone permission is required to record a voice note."
    }
    LaunchedEffect(recording) {
        while (recording) {
            recordSeconds = ((SystemClock.elapsedRealtime() - recordStartedAt) / 1000L)
                .toInt().coerceAtLeast(0)
            delay(250)
        }
    }
    LaunchedEffect(active) {
        if (!active && recording) {
            recorder.discard()
            recording = false
        }
    }
    DisposableEffect(recorder) { onDispose { recorder.discard() } }

    // Report typing at most once every four seconds. Cancel on Send, leaving
    // the chat, or moving the app to background. No drafts are logged or stored.
    LaunchedEffect(chat.id, active, draft.isNotBlank()) {
        if (!active || draft.isBlank()) return@LaunchedEffect
        var started = false
        try {
            delay(450)
            while (true) {
                onTyping(true)
                started = true
                delay(4_000)
            }
        } finally {
            if (started) onTyping(false)
        }
    }

    // Only report genuinely visible, incoming messages; never mark the entire
    // loaded history as read when opening a conversation.
    val visibleIncoming by remember(chat.id, messages) {
        derivedStateOf {
            val layout = timelineState.layoutInfo
            layout.visibleItemsInfo.mapNotNull { item ->
                val id = item.key as? Long ?: return@mapNotNull null
                val visiblePixels = (
                    minOf(item.offset + item.size, layout.viewportEndOffset) -
                        maxOf(item.offset, layout.viewportStartOffset)
                ).coerceAtLeast(0)
                if (visiblePixels * 2 < item.size) return@mapNotNull null
                id.takeIf { key ->
                    key > 0L && messages.any { it.id == key && !it.outgoing }
                }
            }.distinct().take(25)
        }
    }
    LaunchedEffect(chat.id, active, visibleIncoming) {
        if (active && visibleIncoming.isNotEmpty()) {
            delay(320)
            onVisibleMessages(visibleIncoming)
        }
    }

    // Initially show the latest message. Don't yank the list to the bottom
    // when a new message arrives while the user is reading older history.
    LaunchedEffect(chat.id, messages.lastOrNull()?.id) {
        val newest = messages.lastOrNull()?.id ?: return@LaunchedEffect
        if (newestSeen != newest) {
            val initial = newestSeen == Long.MIN_VALUE
            val nearBottom = timelineState.layoutInfo.visibleItemsInfo
                .lastOrNull()?.index?.let { it >= messages.size - 3 } ?: true
            newestSeen = newest
            if (initial || nearBottom) timelineState.scrollToItem(messages.size)
        }
    }
    LaunchedEffect(showingSearch, searchText) {
        if (showingSearch) {
            delay(350)
            if (searchText.isNotBlank()) onSearchMessages(searchText, false)
            else onClearSearch()
        }
    }
    LaunchedEffect(messages, pendingJumpId) {
        val target = pendingJumpId ?: return@LaunchedEffect
        val index = messages.indexOfFirst { it.id == target }
        if (index >= 0) {
            // LazyColumn has one "Load earlier" entry before actual messages.
            timelineState.animateScrollToItem(index + 1)
            pendingJumpId = null
        }
    }
    fun send() {
        val text = draft.trim()
        if (text.isNotBlank() && text.length <= 4096) {
            val editId = editing?.id
            if (editId != null) {
                onEditMessage(editId, text)
            } else {
                onSend(text, replyingTo?.id)
            }
            draft = ""
            editing = null
            replyingTo = null
        }
    }
    if (showingSearch) {
        Dialog(onDismissRequest = {
            showingSearch = false
            searchText = ""
            onClearSearch()
        }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = if (dark) Color(0xFF15203A) else Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Search messages", color = foreground,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            showingSearch = false
                            searchText = ""
                            onClearSearch()
                        }) { Icon(Icons.Rounded.Close, "Close search", tint = foreground) }
                    }
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it.take(256) },
                        placeholder = { Text("Search this Telegram chat") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    val results = searchState?.takeIf { it.query == searchText.trim() }
                    if (results?.loading == true) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    results?.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text(
                        if (results == null && searchText.isNotBlank()) "Search Telegram history"
                        else "${results?.hits?.size ?: 0} of ${results?.total ?: 0} results",
                        fontSize = 12.sp, color = secondary
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(results?.hits.orEmpty(), key = { it.id }) { hit ->
                            TextButton(
                                onClick = {
                                    pendingJumpId = hit.id
                                    onJumpToMessage(hit.id)
                                    showingSearch = false
                                    searchText = ""
                                    onClearSearch()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(
                                        hit.preview.ifBlank { "Telegram message" },
                                        color = foreground,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        if (hit.outgoing) "You · Tap to jump"
                                        else "Tap to jump",
                                        fontSize = 11.sp,
                                        color = secondary
                                    )
                                }
                            }
                        }
                        if (results != null && !results.loading &&
                            results.nextFromMessageId > 0L &&
                            results.hits.size < 200 && results.hits.isNotEmpty()) {
                            item {
                                TextButton(
                                    onClick = { onSearchMessages(searchText, true) },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("More results") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showingExpressions) {
        GlaciaExpressionsPicker(
            state = expressions,
            filePaths = filePaths,
            dark = dark,
            onReload = onRefreshExpressions,
            onSend = { item ->
                if (active && !mediaBusy) {
                    onSendExpression(item, replyingTo?.id)
                    replyingTo = null
                    showingExpressions = false
                }
            },
            onDismiss = { showingExpressions = false }
        )
    }
    selectedMessage?.let { message ->
        GlaciaMessageActionDialog(
            message = message,
            permissions = messageActionPermissions[chat.id to message.id],
            chats = forwardTargets,
            foreground = foreground,
            onDismiss = { selectedMessage = null },
            onReply = { replyingTo = message; editing = null },
            onEdit = { editing = message; replyingTo = null; draft = message.text },
            onForward = { destination -> onForwardMessage(message.id, destination) },
            onDelete = { everyone -> onDeleteMessage(message.id, everyone) },
            onReact = { emoji, chosen -> onReactToMessage(message.id, emoji, chosen) }
        )
    }

    playingMedia?.let { (path, isVideo, seconds) ->
        GlaciaPlaybackDialog(path, isVideo, seconds, dark) { playingMedia = null }
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
                        contentDescription = "Image preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 540.dp)
                    )
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { showingSearch = true }) {
                Icon(Icons.Rounded.Search, "Search Telegram messages", tint = highlight)
                Spacer(Modifier.width(6.dp))
                Text("Search in chat", color = highlight)
            }
        }
        // Keep partially scrolled messages clipped beneath the header, not over it.
        Box(Modifier.weight(1f).clipToBounds()) {
            LazyColumn(
                state = timelineState,
                modifier = Modifier.fillMaxSize().clipToBounds(),
                contentPadding = PaddingValues(horizontal = 17.dp, vertical = 20.dp),
                // Keep short conversations anchored near the composer, like a messenger.
                // Long histories still scroll normally without reversed message ordering.
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom)
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
                        val isVisual = msg.mediaType in setOf(
                            "photo", "video", "sticker", "animation"
                        )
                        val imageDocument = msg.mediaType == "document" &&
                            DocumentKind.isImage(msg.attachmentMime, msg.attachmentName)
                        val bubbleShape = RoundedCornerShape(
                            topStart = 22.dp, topEnd = 22.dp,
                            bottomStart = if (msg.outgoing) 22.dp else 7.dp,
                            bottomEnd = if (msg.outgoing) 7.dp else 22.dp
                        )
                        val bubbleWidth = if (isVisual || imageDocument) {
                            Modifier.fillMaxWidth(0.80f).widthIn(max = 278.dp)
                        } else {
                            Modifier.widthIn(max = 304.dp)
                        }
                        Column(
                            bubbleWidth
                                .clip(bubbleShape)
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        selectedMessage = msg
                                        onLoadMessageProperties(msg.id)
                                    }
                                )
                                .animateContentSize()
                                .background(
                                    brush = Brush.verticalGradient(
                                        if (msg.outgoing) {
                                            if (dark) listOf(Color(0xEC426797), Color(0xD82A466F))
                                            else listOf(Color(0xEFE9EEFF), Color(0xD8CEDCFF))
                                        } else {
                                            if (dark) listOf(Color(0xE629395B), Color(0xEA19243D))
                                            else listOf(Color(0xFAFFFFFF), Color(0xE3EAF1FE))
                                        }
                                    )
                                )
                                .border(
                                    1.dp, Color.White.copy(alpha = if (dark) 0.13f else 0.55f),
                                    bubbleShape
                                )
                                .padding(horizontal = 12.dp, vertical = 11.dp)
                        ) {
                            msg.replyToMessageId?.let { replyId ->
                                val quoted = messages.firstOrNull { it.id == replyId }?.text
                                    ?.take(85) ?: "Earlier message"
                                Text(
                                    "↩ $quoted",
                                    color = highlight,
                                    fontSize = 12.sp,
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(highlight.copy(alpha = 0.10f))
                                        .padding(7.dp),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(7.dp))
                            }
                            val thumbnailPath = msg.mediaFileId?.let(filePaths::get)
                            if (thumbnailPath != null && isVisual) {
                                GlassImagePreview(
                                    path = thumbnailPath,
                                    description = "${msg.mediaType} preview",
                                    onOpen = if (msg.mediaType == "photo") {
                                        { imageViewer = thumbnailPath }
                                    } else null
                                )
                                if (msg.mediaType == "video" || msg.mediaType == "animation") {
                                    Row(
                                        Modifier.padding(top = 7.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0x6616223C))
                                            .padding(horizontal = 9.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Rounded.Videocam,
                                            contentDescription = null,
                                            tint = foreground,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (msg.mediaType == "animation") "GIF preview"
                                            else "Video thumbnail", color = foreground, fontSize = 10.sp)
                                    }
                                }
                                Spacer(Modifier.height(7.dp))
                            } else if (isVisual) {
                                Text(
                                    "Preview unavailable",
                                    color = secondary,
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.height(5.dp))
                            }
                            if (msg.mediaType == "video" ||
                                msg.mediaType == "animation" ||
                                msg.mediaType == "voice" ||
                                msg.mediaType == "audio"
                            ) {
                                val originalId = msg.playbackFileId
                                val originalPath = originalId?.let(filePaths::get)
                                val transfer = originalId?.let(documentTransfers::get)
                                val video = msg.mediaType == "video" || msg.mediaType == "animation"
                                if (!video) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Rounded.Mic,
                                            contentDescription = null,
                                            tint = highlight,
                                            modifier = Modifier.size(23.dp)
                                        )
                                        Spacer(Modifier.width(7.dp))
                                        Text(
                                            if (msg.mediaType == "voice") "Voice message"
                                            else "Audio",
                                            color = foreground,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                                if (originalId != null) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = {
                                                if (originalPath != null) {
                                                    playingMedia = Triple(
                                                        originalPath,
                                                        video,
                                                        msg.playbackDurationSeconds
                                                    )
                                                } else {
                                                    onDownloadDocument(originalId)
                                                }
                                            },
                                            enabled = originalPath != null ||
                                                transfer?.downloading != true
                                        ) {
                                            Icon(
                                                if (originalPath != null) Icons.Rounded.PlayArrow
                                                else Icons.Rounded.Download,
                                                contentDescription = null,
                                                tint = highlight,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(5.dp))
                                            Text(
                                                when {
                                                    originalPath != null ->
                                                        if (video) "Play video/GIF" else "Play audio"
                                                    transfer?.downloading == true -> {
                                                        val total = transfer.totalBytes
                                                        if (total > 0L)
                                                            "Downloading ${(transfer.downloadedBytes * 100L / total).coerceIn(0L, 100L)}%"
                                                        else "Downloading…"
                                                    }
                                                    transfer?.error != null -> "Retry download"
                                                    else -> if (video) "Download video/GIF" else "Download audio"
                                                },
                                                color = highlight
                                            )
                                        }
                                    }
                                    if (transfer?.error != null) {
                                        Text(transfer.error, color = secondary, fontSize = 11.sp)
                                    }
                                } else {
                                    Text("Media unavailable", color = secondary, fontSize = 12.sp)
                                }
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
                                    GlassImagePreview(
                                        path = imagePath,
                                        description = "Image attachment preview",
                                        onOpen = if (downloadedPath != null) {
                                            { imageViewer = downloadedPath }
                                        } else null
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(highlight.copy(alpha = if (imagePath != null) 0.06f else 0.13f))
                                        .padding(
                                            horizontal = 10.dp,
                                            vertical = if (imagePath != null) 7.dp else 10.dp
                                        )
                                ) {
                                    if (imagePath == null) {
                                        Icon(
                                            Icons.Rounded.InsertDriveFile,
                                            contentDescription = null,
                                            tint = highlight,
                                            modifier = Modifier.size(29.dp)
                                        )
                                        Spacer(Modifier.width(9.dp))
                                    }
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
                            } else if (msg.mediaType != "voice" && msg.mediaType != "audio") {
                                // Media already has a visual preview and a dedicated action.
                                // Show its actual caption, but avoid duplicating the "Photo"
                                // or "Video" placeholder beneath the media controls.
                                val displayText = when (msg.mediaType) {
                                    "photo" -> msg.text.removePrefix("📷 Photo")
                                        .removePrefix(" · ").trim()
                                    "video" -> msg.text.removePrefix("🎥 Video")
                                        .removePrefix(" · ").trim()
                                    "animation" -> msg.text.removePrefix("🎞 GIF")
                                        .removePrefix(" · ").trim()
                                    "sticker" -> if (thumbnailPath != null) "" else msg.text
                                    else -> msg.text
                                }
                                if (displayText.isNotEmpty()) {
                                    Text(
                                        displayText,
                                        color = foreground,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontSize = 15.sp,
                                        lineHeight = 21.sp
                                    )
                                }
                            }
                            if (msg.reactions.isNotEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(top = 7.dp)
                                ) {
                                    msg.reactions.take(4).forEach { reaction ->
                                        Text(
                                            "${reaction.emoji} ${reaction.count}",
                                            color = if (reaction.chosen) highlight else secondary,
                                            fontSize = 12.sp,
                                            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                                .background(highlight.copy(alpha = 0.13f))
                                                .clickable {
                                                    onReactToMessage(
                                                        msg.id, reaction.emoji, reaction.chosen
                                                    )
                                                }
                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(5.dp))
                            val delivery = DeliveryReceipts.status(msg, lastReadOutboxId)
                            val trailing = when (delivery) {
                                DeliveryState.FAILED -> "  • Failed"
                                DeliveryState.SENDING -> "  • Sending"
                                DeliveryState.SENT -> "  ✓"
                                DeliveryState.READ -> "  ✓✓"
                                DeliveryState.NONE -> ""
                            }
                            Text(
                                msg.time + (if (msg.edited) " • edited" else "") + trailing,
                                color = when (delivery) {
                                    DeliveryState.FAILED -> Color(0xFFFF9E9E)
                                    DeliveryState.READ -> highlight
                                    else -> secondary
                                },
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

        if (replyingTo != null || editing != null) {
            val target = editing ?: replyingTo
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(highlight.copy(alpha = 0.13f))
                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    (if (editing != null) "Editing: " else "Replying to: ") +
                        target?.text.orEmpty().take(90),
                    color = foreground, maxLines = 2,
                    modifier = Modifier.weight(1f), fontSize = 12.sp
                )
                IconButton(onClick = {
                    editing = null
                    replyingTo = null
                    draft = ""
                }) { Icon(Icons.Rounded.Close, "Cancel edit or reply", tint = highlight) }
            }
        }
        if (recordError != null) {
            Text(
                recordError!!,
                color = Color(0xFFFFB0B0),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 3.dp)
            )
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
            IconButton(
                onClick = { chooseFile.launch(arrayOf("*/*")) },
                enabled = active && !mediaBusy && !recording,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Rounded.AttachFile, "Send photo, video or document", tint = highlight)
            }
            Spacer(Modifier.width(3.dp))
            IconButton(
                enabled = active && !mediaBusy && !recording,
                onClick = {
                    onRefreshExpressions()
                    showingExpressions = true
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Rounded.EmojiEmotions,
                    contentDescription = "Recent stickers and saved GIFs",
                    tint = highlight
                )
            }
            Spacer(Modifier.width(4.dp))
            if (recording) {
                Text(
                    "● REC ${recordSeconds}s",
                    color = Color(0xFFFFA2AE),
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    recorder.discard()
                    recording = false
                }) { Icon(Icons.Rounded.DeleteOutline, "Discard recording", tint = secondary) }
                IconButton(onClick = {
                    val recorded = recorder.finish()
                    recording = false
                    if (recorded == null) recordError = "Recording too short or unavailable."
                    else onVoice(recorded)
                }) { Icon(Icons.Rounded.StopCircle, "Stop and send voice note", tint = highlight) }
            } else {
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
            Spacer(Modifier.width(6.dp))
            if (draft.isBlank()) {
                IconButton(
                    enabled = active && !mediaBusy,
                    onClick = {
                        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED) {
                            startRecording()
                        } else microphone.launch(Manifest.permission.RECORD_AUDIO)
                    }
                ) { Icon(Icons.Rounded.Mic, "Record voice note", tint = highlight) }
            }
            IconButton(
                onClick = { send() },
                enabled = draft.isNotBlank() && !mediaBusy,
                modifier = Modifier.clip(CircleShape)
                    .background(highlight.copy(alpha = 0.17f))
            ) {
                Icon(Icons.Rounded.Send, contentDescription = "Send Telegram message",
                    tint = if (draft.isNotBlank()) highlight else secondary)
            }
            }
        }
    }
}
