package dev.glacia.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.glacia.app.data.CommunityState
import dev.glacia.app.data.CoreSecurityState

/** Telegram keeps the authoritative permissions; no simulated admin controls. */
@Composable
internal fun CoreChatInfoDialog(
    state: CommunityState,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onDescription: (String) -> Unit,
    contacts: List<dev.glacia.app.data.CorePerson>,
    onAddMember: (Long) -> Unit,
    onInvite: () -> Unit,
    onUnread: () -> Unit,
    onJumpPinned: (Long) -> Unit,
    onBlock: (() -> Unit)? = null
) {
    val chat = state.selected
    var name by remember(chat?.id) { mutableStateOf(chat?.title.orEmpty()) }
    var about by remember(chat?.id) { mutableStateOf("") }
    var confirmInvite by remember { mutableStateOf(false) }
    var addingMember by remember { mutableStateOf<dev.glacia.app.data.CorePerson?>(null) }
    addingMember?.let { person ->
        AlertDialog(
            onDismissRequest = { addingMember = null },
            title = { Text("Add group member?") },
            text = { Text("Invite ${person.displayName} to this Telegram community? " +
                "They may see group messages depending on Telegram privacy rules.") },
            confirmButton = {
                TextButton(onClick = {
                    onAddMember(person.id)
                    addingMember = null
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addingMember = null }) {
                Text("Cancel")
            } }
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Telegram chat information") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 470.dp)
                .verticalScroll(rememberScrollState())) {
                Text(chat?.title ?: "Loading chat…", fontWeight = FontWeight.Bold)
                Text(chat?.kind ?: "Telegram chat",
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                chat?.pinnedMessageId?.let { id ->
                    Text("Pinned: ${chat.pinnedMessageText ?: "Message"}",
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { onJumpPinned(id) }) { Text("Jump to pinned message") }
                }
                TextButton(onClick = onUnread) { Text("Mark conversation unread") }
                if (chat?.kind == "Private chat") onBlock?.let {
                    TextButton(onClick = it) {
                        Text("Block this Telegram user", color = MaterialTheme.colorScheme.error)
                    }
                }
                if (chat?.kind == "Channel" || chat?.kind == "Supergroup" ||
                    chat?.kind == "Basic group") {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text("Administrator actions",
                        style = MaterialTheme.typography.titleSmall)
                    Text("Telegram checks your admin rights before saving.",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Known administrators: ${chat.administratorCount}",
                        style = MaterialTheme.typography.bodySmall)
                    if (contacts.isNotEmpty()) {
                        Text("Add Telegram contact to this group",
                            style = MaterialTheme.typography.labelMedium)
                        contacts.take(20).forEach { person ->
                            TextButton(onClick = { addingMember = person }) {
                                Text("+ ${person.displayName}")
                            }
                        }
                    }
                    OutlinedTextField(name, { name = it.take(128) },
                        label = { Text("Group/channel title") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { onRename(name) }, enabled = !state.actionBusy &&
                        name.trim().isNotBlank()) { Text("Update title") }
                    OutlinedTextField(about, { about = it.take(255) },
                        label = { Text("New description") }, maxLines = 3,
                        modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { onDescription(about) },
                        enabled = !state.actionBusy) { Text("Update description") }
                    HorizontalDivider()
                    if (!confirmInvite) {
                        TextButton(onClick = { confirmInvite = true }) {
                            Text("Create Telegram invitation link")
                        }
                    } else {
                        Text("Non-expiring invite links can let others join. " +
                            "Share only with trusted people.",
                            style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(onClick = onInvite, enabled = !state.actionBusy) {
                                Text("Confirm create")
                            }
                            TextButton(onClick = { confirmInvite = false }) { Text("Cancel") }
                        }
                    }
                    state.invitation?.let { url ->
                        // Invitation is only rendered; no clipboard/upload/log.
                        Text("Invite link (select and copy manually):",
                            style = MaterialTheme.typography.labelSmall)
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(url)
                        }
                    }
                }
                state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (state.actionBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
internal fun CoreCreateCommunityDialog(
    state: CommunityState,
    onDismiss: () -> Unit,
    onCreate: (String, String, Boolean) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var about by remember { mutableStateOf("") }
    var channel by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Telegram group or channel") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row {
                    FilterChip(selected = !channel, onClick = { channel = false },
                        label = { Text("Group") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = channel, onClick = { channel = true },
                        label = { Text("Channel") })
                }
                Text("Group: members can chat. Channel: broadcast posts.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(title, { title = it.take(128) },
                    label = { Text("Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(about, { about = it.take(255) },
                    label = { Text("Description (optional)") }, maxLines = 3,
                    modifier = Modifier.fillMaxWidth())
                state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (state.actionBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(title, about, channel) },
                enabled = !state.actionBusy && title.trim().isNotBlank()) {
                Text("Create on Telegram")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Phase 4.2: user must explicitly confirm destructive session revocation. */
@Composable
internal fun CoreSecuritySection(
    state: CoreSecurityState,
    onRefresh: () -> Unit,
    onTerminate: (Long) -> Unit,
    onUnblock: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var revokeId by remember { mutableStateOf<Long?>(null) }
    var unblockId by remember { mutableStateOf<Long?>(null) }
    if (revokeId != null) AlertDialog(
        onDismissRequest = { revokeId = null },
        title = { Text("Sign out this other device?") },
        text = { Text("This ends the selected Telegram session. " +
            "The device may need a login code and password to sign in again.") },
        confirmButton = { TextButton(onClick = {
            revokeId?.let(onTerminate); revokeId = null
        }) { Text("Revoke device") } },
        dismissButton = { TextButton(onClick = { revokeId = null }) { Text("Cancel") } }
    )
    if (unblockId != null) AlertDialog(
        onDismissRequest = { unblockId = null },
        title = { Text("Unblock this user?") },
        text = { Text("Unblocking can allow Telegram messages and calls again, subject to privacy settings.") },
        confirmButton = { TextButton(onClick = {
            unblockId?.let(onUnblock); unblockId = null
        }) { Text("Unblock") } },
        dismissButton = { TextButton(onClick = { unblockId = null }) { Text("Cancel") } }
    )
    Text("PRIVACY & SECURITY", style = MaterialTheme.typography.labelLarge)
    TextButton(onClick = {
        expanded = !expanded
        if (expanded) onRefresh()
    }) { Text(if (expanded) "Hide active sessions and blocked users" else "Active sessions & blocked users") }
    if (!expanded) return
    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    TextButton(onClick = onRefresh) { Text("Refresh Telegram security status") }
    Text("Logged-in Telegram devices", style = MaterialTheme.typography.titleSmall)
    if (state.devices.isEmpty()) Text("No session information loaded.")
    state.devices.forEach { device ->
        Row(Modifier.fillMaxWidth()) {
            Text(device.label + if (device.current) " (this device)" else "",
                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            if (!device.current) TextButton(onClick = { revokeId = device.id }) {
                Text("Revoke")
            }
        }
    }
    HorizontalDivider()
    Text("Blocked users (first 100)", style = MaterialTheme.typography.titleSmall)
    if (state.blocked.isEmpty()) Text("No blocked users loaded.")
    state.blocked.forEach { user ->
        Row(Modifier.fillMaxWidth()) {
            Text(user.label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { unblockId = user.id }) { Text("Unblock") }
        }
    }
    Text("Fine-grained Telegram last-seen/phone privacy and 2-step password " +
        "controls need a separate audited implementation.",
        style = MaterialTheme.typography.bodySmall)
}


@Composable
internal fun CoreFolderDialog(
    folders: List<dev.glacia.app.data.TdlibSession.ChatFolderTab>,
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit,
    onDelete: (Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("People") }
    var deleting by remember { mutableStateOf<dev.glacia.app.data.TdlibSession.ChatFolderTab?>(null) }
    deleting?.let { folder ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete Telegram folder?") },
            text = { Text("Delete folder “${folder.title}” across your Telegram devices? " +
                "Messages and chats will NOT be deleted or left.") },
            confirmButton = { TextButton(onClick = {
                onDelete(folder.id); deleting = null
            }) { Text("Delete folder") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Telegram chat folders") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                Text("Folders sync with Telegram. Choose what to include.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(name, { name = it.take(12) },
                    singleLine = true, label = { Text("New folder name") },
                    modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("People", "Groups", "Channels").forEach { category ->
                        FilterChip(selected = type == category,
                            onClick = { type = category },
                            label = { Text(category) })
                    }
                }
                TextButton(onClick = {
                    onCreate(name, type)
                    name = ""
                }, enabled = name.trim().isNotBlank()) { Text("Create folder") }
                HorizontalDivider()
                folders.forEach { folder ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(folder.title, Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { deleting = folder }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
