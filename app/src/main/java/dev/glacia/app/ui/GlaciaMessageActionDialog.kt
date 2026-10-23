package dev.glacia.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.glacia.app.data.Chat
import dev.glacia.app.data.Message
import dev.glacia.app.data.MessageActionPermissions
import dev.glacia.app.data.MessageReactionPolicy

/** Message actions shown only after Telegram's GetMessageProperties responds. */
@Composable
fun GlaciaMessageActionDialog(
    message: Message,
    permissions: MessageActionPermissions?,
    chats: List<Chat>,
    foreground: Color,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onForward: (Long) -> Unit,
    onDelete: (Boolean) -> Unit,
    onReact: (String, Boolean) -> Unit
) {
    var mode by remember(message.id) { mutableStateOf("actions") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when (mode) {
            "forward" -> "Forward to…"
            "delete" -> "Delete message?"
            else -> "Message actions"
        }) },
        text = {
            Column {
                if (mode == "forward") {
                    Text("Choose a conversation. Telegram permissions apply.")
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(chats.filter { it.id != 0L }.take(75), key = { it.id }) { chat ->
                            Text(
                                chat.title,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { onForward(chat.id); onDismiss() }
                                    .padding(13.dp)
                            )
                        }
                    }
                } else if (mode == "delete") {
                    if (permissions?.deleteForMe == true) {
                        TextButton(onClick = { onDelete(false); onDismiss() }) {
                            Text("Delete only for me")
                        }
                    }
                    if (permissions?.deleteForEveryone == true) {
                        TextButton(onClick = { onDelete(true); onDismiss() }) {
                            Text("Delete for everyone")
                        }
                    }
                    Text("Deletion cannot always be undone.")
                } else {
                    if (permissions == null) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(6.dp))
                        Text("Checking Telegram permissions…")
                    } else {
                        if (permissions.reply) {
                            TextButton(onClick = { onReply(); onDismiss() }) { Text("↩ Reply") }
                        }
                        if (permissions.forward) {
                            TextButton(onClick = { mode = "forward" }) { Text("➦ Forward") }
                        }
                        if (permissions.edit && message.outgoing && message.mediaType == null) {
                            TextButton(onClick = { onEdit(); onDismiss() }) { Text("✎ Edit text") }
                        }
                        if (permissions.deleteForMe || permissions.deleteForEveryone) {
                            TextButton(onClick = { mode = "delete" }) { Text("🗑 Delete") }
                        }
                    }
                    // Telegram's GetMessageAvailableReactions is authoritative.
                    // Never show a guessed reaction grid on call service events.
                    if (permissions != null) {
                        Spacer(Modifier.height(8.dp))
                        if (permissions.reactionChecking) {
                            Text(
                                "Checking allowed reactions…",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            val emojis = if (MessageReactionPolicy.eligibleForLookup(message)) {
                                MessageReactionPolicy.shownChoices(
                                    permissions.reactionChoices,
                                    message.reactions
                                )
                            } else emptyList()
                            if (emojis.isEmpty()) {
                                Text(
                                    permissions.reactionNotice
                                        ?: "Reactions aren't available for this message.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                Text("React:", style = MaterialTheme.typography.labelLarge)
                                emojis.chunked(4).forEach { row ->
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        row.forEach { emoji ->
                                            val chosen = message.reactions.any {
                                                it.emoji == emoji && it.chosen
                                            }
                                            TextButton(onClick = {
                                                onReact(emoji, chosen)
                                                onDismiss()
                                            }) {
                                                Text(emoji)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
