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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.glacia.app.data.Chat
import dev.glacia.app.data.Message

/**
 * Live chat UI. Initial history, older pages and incoming messages come from TDLib.
 * Plain text can be sent; attachments are shown as descriptive placeholders only.
 */
@Composable
fun ConversationScreen(
    chat: Chat,
    messages: List<Message>,
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
    var newestSeen by remember(chat.id) { mutableLongStateOf(Long.MIN_VALUE) }
    var draft by rememberSaveable(chat.id) { mutableStateOf("") }

    // Adding an older history page should not jump the conversation back to bottom.
    LaunchedEffect(chat.id, messages.lastOrNull()?.id) {
        val newest = messages.lastOrNull()?.id ?: return@LaunchedEffect
        if (newestSeen != newest) {
            newestSeen = newest
            timelineState.scrollToItem(messages.lastIndex)
        }
    }
    fun send() {
        val text = draft.trim()
        if (text.isNotBlank() && text.length <= 4096) {
            onSend(text)
            draft = ""
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
                        Column(
                            Modifier.widthIn(max = 304.dp)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 21.dp, topEnd = 21.dp,
                                        bottomStart = if (msg.outgoing) 21.dp else 5.dp,
                                        bottomEnd = if (msg.outgoing) 5.dp else 21.dp
                                    )
                                )
                                .background(
                                    if (msg.outgoing) {
                                        if (dark) Color(0xD83D5E90) else Color(0xDED6E1FF)
                                    } else {
                                        if (dark) Color(0xEA1D2B4B) else Color(0xF0FFFFFF)
                                    }
                                )
                                .border(
                                    1.dp, Color.White.copy(alpha = if (dark) 0.13f else 0.55f),
                                    RoundedCornerShape(21.dp)
                                )
                                .padding(horizontal = 15.dp, vertical = 11.dp)
                        ) {
                            Text(
                                msg.text,
                                color = foreground,
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 15.sp,
                                lineHeight = 21.sp
                            )
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
