package dev.glacia.app.ui

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.glacia.app.data.CoreAccountState
import dev.glacia.app.data.CorePerson
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun contactPresence(person: CorePerson): String {
    if (person.online) return "online"
    if (person.lastSeenAt <= 0) return if (person.username.isBlank())
        "Telegram contact" else "@${person.username}"
    val time = Instant.ofEpochSecond(person.lastSeenAt.toLong())
        .atZone(ZoneId.systemDefault())
    val today = java.time.LocalDate.now()
    return when {
        time.toLocalDate() == today ->
            "last seen at ${time.format(DateTimeFormatter.ofPattern("h:mm a"))}"
        time.toLocalDate() == today.minusDays(1) ->
            "last seen yesterday at ${time.format(DateTimeFormatter.ofPattern("h:mm a"))}"
        else -> "last seen ${time.format(DateTimeFormatter.ofPattern("d MMM"))}"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CompactContactsHome(
    state: CoreAccountState,
    paths: Map<Int, String>,
    dark: Boolean,
    onRefresh: () -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (Long) -> Unit,
    onOpen: (Long) -> Unit,
    onSaved: () -> Unit,
    onDismissNotice: () -> Unit
) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var removeTarget by remember { mutableStateOf<CorePerson?>(null) }
    val visible = remember(state.contacts, search) {
        state.contacts.filter {
            search.isBlank() ||
                it.displayName.contains(search.trim(), ignoreCase = true) ||
                it.username.contains(search.trim(), ignoreCase = true)
        }
    }
    if (showAdd) AlertDialog(
        onDismissRequest = { showAdd = false },
        title = { Text("Add Telegram contact") },
        text = {
            Column {
                Text("Enter a public @username. Your phone number isn't shared by this action.",
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(username, { username = it.take(33) },
                    label = { Text("Telegram username") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(username); showAdd = false },
                enabled = username.isNotBlank() && !state.busy
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
    )
    removeTarget?.let { person ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Remove Telegram contact?") },
            text = { Text("Remove ${person.displayName} from your contacts? Chat messages won't be erased.") },
            confirmButton = { TextButton(onClick = {
                onRemove(person.id); removeTarget = null
            }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removeTarget = null }) {
                Text("Cancel")
            } }
        )
    }
    LazyColumn(
        Modifier.fillMaxSize().background(coreSurface(dark)),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item(key = "head") {
            Column(Modifier.padding(horizontal = 15.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Contacts", color = coreFg(dark), fontSize = 28.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onRefresh, enabled = !state.contactsLoading) {
                        Icon(Icons.Rounded.Refresh, "Refresh Telegram contacts",
                            tint = coreSub(dark))
                    }
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Rounded.Add, "Add Telegram contact", tint = coreGold(dark))
                    }
                }
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(
                    value = search, onValueChange = { search = it.take(100) },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text("Search Contacts") },
                    shape = RoundedCornerShape(24.dp), singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                CompactMenuGroup(dark) {
                    CompactMenuRow("📨", "Invite Friends",
                        "Share an invitation to Telegram", dark) {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT,
                                "Chat with me on Telegram: https://telegram.org/")
                        }
                        context.startActivity(Intent.createChooser(share, "Invite friends"))
                    }
                    HorizontalDivider(color = coreSurface(dark), thickness = 0.5.dp)
                    CompactMenuRow("🔖", "Saved Messages",
                        "Your private cloud conversation", dark, onSaved)
                }
                Spacer(Modifier.height(15.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Sorted by last seen time", color = coreSub(dark),
                        fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("${state.contacts.size} contacts", color = coreSub(dark),
                        fontSize = 12.sp)
                }
                Spacer(Modifier.height(9.dp))
                state.notice?.let { message ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(message, color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = onDismissNotice) { Text("Dismiss") }
                    }
                }
                if (state.contactsLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        items(visible, key = { it.id }) { person ->
            Row(
                Modifier.fillMaxWidth()
                    .combinedClickable(
                        onClick = { onOpen(person.id) },
                        onLongClick = { removeTarget = person },
                        onLongClickLabel = "Remove Telegram contact"
                    )
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactPersonAvatar(person, paths, 52)
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(person.displayName, color = coreFg(dark),
                        fontWeight = FontWeight.Medium, fontSize = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(contactPresence(person),
                        color = if (person.online) coreGold(dark) else coreSub(dark),
                        fontSize = 12.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(
                color = if (dark) Color(0xFF262628) else Color(0xFFE7E7E9),
                thickness = 0.5.dp,
                modifier = Modifier.padding(start = 83.dp)
            )
        }
        if (visible.isEmpty() && !state.contactsLoading) item {
            Text(
                if (search.isNotBlank()) "No matching contacts."
                else "No Telegram cloud contacts synced yet.",
                color = coreSub(dark),
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 28.dp)
            )
        }
    }
}
