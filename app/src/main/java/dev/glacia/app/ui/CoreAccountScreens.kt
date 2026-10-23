package dev.glacia.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.glacia.app.data.CoreAccountState
import dev.glacia.app.data.CorePerson
import java.io.File

private fun coreInk(dark: Boolean) = if (dark) Color(0xFFF5F7FF) else Color(0xFF14223F)
private fun coreMuted(dark: Boolean) = if (dark) Color(0xFFACBBD6) else Color(0xFF54627E)
private fun coreAccent(dark: Boolean) = if (dark) Color(0xFF9BE4FF) else Color(0xFF475DDC)
private fun coreTile(dark: Boolean) = if (dark) Color(0xC71B2948) else Color(0xDDF9FBFF)

@Composable
private fun CoreAvatar(person: CorePerson, filePaths: Map<Int, String>, size: Int = 54) {
    val path = person.photoId?.let { filePaths[it] }
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Color(0xFF315C93)),
        contentAlignment = Alignment.Center
    ) {
        if (path != null) AsyncImage(
            model = File(path),
            contentDescription = "${person.displayName} profile photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        else Text(
            person.displayName.take(1).uppercase(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size / 2.4f).sp
        )
    }
}

/** Telegram cloud contacts (GetContacts), no Android READ_CONTACTS permission. */
@Composable
internal fun CoreContactsScreen(
    state: CoreAccountState,
    filePaths: Map<Int, String>,
    dark: Boolean,
    onRefresh: () -> Unit,
    onOpenContact: (Long) -> Unit,
    onOpenSavedMessages: () -> Unit,
    onAddContact: (String) -> Unit,
    onRemoveContact: (Long) -> Unit,
    onDismissNotice: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var removing by remember { mutableStateOf<CorePerson?>(null) }
    if (adding) AlertDialog(
        onDismissRequest = { adding = false },
        title = { Text("Add Telegram contact") },
        text = {
            Column {
                Text("Search by exact public @username. Your phone number will not be shared.")
                OutlinedTextField(username, { username = it.take(33) },
                    label = { Text("@username") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = {
            onAddContact(username); adding = false
        }, enabled = username.trim().isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } }
    )
    removing?.let { person ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove Telegram contact?") },
            text = { Text("Remove ${person.displayName} from your Telegram cloud contacts? Chat history stays.") },
            confirmButton = { TextButton(onClick = {
                onRemoveContact(person.id); removing = null
            }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } }
        )
    }
    val visible = remember(state.contacts, query) {
        state.contacts.filter {
            query.isBlank() || it.displayName.contains(query, true) ||
                it.username.contains(query, true)
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(15.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Contacts", fontSize = 30.sp, fontWeight = FontWeight.Bold,
                    color = coreInk(dark))
                Text("Your Telegram cloud contacts · ${state.contacts.size}",
                    fontSize = 12.sp, color = coreMuted(dark))
            }
            TextButton(onClick = { adding = true }) { Text("+ Add") }
            IconButton(onClick = onRefresh, enabled = !state.contactsLoading) {
                Icon(Icons.Rounded.Refresh, "Refresh Telegram contacts", tint = coreAccent(dark))
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(coreTile(dark))
                .clickable(enabled = state.self != null) { onOpenSavedMessages() }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Bookmark, "Saved Messages", tint = coreAccent(dark))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Saved Messages", color = coreInk(dark),
                    fontWeight = FontWeight.SemiBold)
                Text("Your private Telegram cloud chat",
                    color = coreMuted(dark), fontSize = 12.sp)
            }
            Icon(Icons.Rounded.ArrowForward, null, tint = coreAccent(dark))
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(100) },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            placeholder = { Text("Search contacts") },
            singleLine = true,
            shape = RoundedCornerShape(22.dp)
        )
        Spacer(Modifier.height(12.dp))
        state.notice?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = onDismissNotice) { Text("Dismiss") }
            }
        }
        if (state.contactsLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (visible.isEmpty() && !state.contactsLoading) {
            Text(
                if (query.isNotBlank()) "No Telegram contacts match your search."
                else "No synced Telegram contacts yet. Tap Refresh to check again.",
                color = coreMuted(dark),
                modifier = Modifier.padding(top = 36.dp)
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 1.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            items(visible, key = { it.id }) { person ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(coreTile(dark))
                        .clickable { onOpenContact(person.id) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CoreAvatar(person, filePaths)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(person.displayName, color = coreInk(dark),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold)
                        Text(
                            person.username.takeIf { it.isNotBlank() }?.let { "@$it" }
                                ?: "Telegram contact",
                            color = coreMuted(dark), fontSize = 12.sp
                        )
                    }
                    Column {
                        IconButton(onClick = { onOpenContact(person.id) }) {
                            Icon(Icons.Rounded.ArrowForward, "Open private chat",
                                tint = coreAccent(dark))
                        }
                        TextButton(onClick = { removing = person }) {
                            Text("Remove", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

/** Real profile state, not placeholder: GetMe/GetUserFullInfo and SetName/SetBio. */
@Composable
internal fun CoreProfileSection(
    state: CoreAccountState,
    filePaths: Map<Int, String>,
    dark: Boolean,
    onRefresh: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onSaveUsername: (String) -> Unit,
    onSavePhoto: (String) -> Unit,
    onDismissNotice: () -> Unit
) {
    val person = state.self
    var editing by remember { mutableStateOf(false) }
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var editingUsername by remember { mutableStateOf(false) }
    var publicUsername by remember { mutableStateOf("") }
    val context = LocalContext.current
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val type = context.contentResolver.getType(uri)
            require(type == "image/jpeg" || type == "image/png")
            val destination = java.io.File.createTempFile("glacia-avatar-", ".jpg", context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        require(total <= 5_000_000) { "Picture exceeds 5MB" }
                        output.write(buffer, 0, n)
                    }
                }
            } ?: error("Could not open photo")
            onSavePhoto(destination.absolutePath)
        }
    }
    if (editingUsername) AlertDialog(
        onDismissRequest = { editingUsername = false },
        title = { Text("Telegram public username") },
        text = {
            OutlinedTextField(publicUsername, { publicUsername = it.take(33) },
                label = { Text("Username (without @)") }, singleLine = true)
        },
        confirmButton = { TextButton(onClick = {
            onSaveUsername(publicUsername); editingUsername = false
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { editingUsername = false }) { Text("Cancel") } }
    )
    if (editing) {
        AlertDialog(
            onDismissRequest = { if (!state.busy) editing = false },
            title = { Text("Edit Telegram profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        first, { first = it.take(64) }, label = { Text("First name") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        last, { last = it.take(64) }, label = { Text("Last name") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        bio, { bio = it.take(70) }, label = { Text("Bio · 70 characters") },
                        modifier = Modifier.fillMaxWidth(), maxLines = 3
                    )
                    Text(
                        "Changes sync to your Telegram account.",
                        color = coreMuted(dark), fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = first.isNotBlank() && !state.busy,
                    onClick = {
                        onSave(first, last, bio)
                        editing = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }, enabled = !state.busy) {
                    Text("Cancel")
                }
            }
        )
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(coreTile(dark)).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (person != null) CoreAvatar(person, filePaths, 72)
            else Icon(Icons.Rounded.Person, "Profile unavailable",
                tint = coreAccent(dark), modifier = Modifier.size(56.dp))
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(person?.displayName ?: "Loading Telegram profile…",
                    color = coreInk(dark), fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    person?.username?.takeIf { it.isNotBlank() }?.let { "@$it" }
                        ?: "No public username",
                    color = coreMuted(dark), fontSize = 12.sp
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, "Refresh profile", tint = coreAccent(dark))
            }
        }
        if (person != null) {
            Spacer(Modifier.height(13.dp))
            if (state.bio.isNotBlank()) Text(state.bio, color = coreInk(dark),
                fontSize = 14.sp)
            else Text("No bio added", color = coreMuted(dark), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = {
                first = person.firstName; last = person.lastName; bio = state.bio
                editing = true
            }, enabled = !state.busy) {
                Icon(Icons.Rounded.Edit, null, Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text("Edit name and bio")
            }
            Row {
                TextButton(onClick = {
                    publicUsername = person.username
                    editingUsername = true
                }, enabled = !state.busy) { Text("Change username") }
                TextButton(onClick = { pickPhoto.launch("image/*") },
                    enabled = !state.busy) { Text("Update photo") }
            }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.notice?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(it, color = coreMuted(dark), fontSize = 12.sp,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = onDismissNotice) { Text("Dismiss") }
            }
        }
    }
}
