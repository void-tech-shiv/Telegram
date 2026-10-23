package dev.glacia.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.glacia.app.data.Chat
import dev.glacia.app.data.ChatFilter
import dev.glacia.app.data.ChatFilters
import dev.glacia.app.data.TdlibSession
import java.io.File

/** Nicegram-inspired information density; GLACIA branding, real TDLib data. */
private fun compactBg(dark: Boolean) = if (dark) Color(0xFF171717) else Color(0xFFF8F9FB)
private fun compactText(dark: Boolean) = if (dark) Color(0xFFF5F5F5) else Color(0xFF1B2028)
private fun compactSub(dark: Boolean) = if (dark) Color(0xFF96979B) else Color(0xFF737984)
private fun compactSearch(dark: Boolean) = if (dark) Color(0xFF2B2B2D) else Color(0xFFEDEFF2)
private fun compactLine(dark: Boolean) = if (dark) Color(0xFF29292B) else Color(0xFFE7E9EB)
private fun compactAccent(dark: Boolean) = if (dark) Color(0xFFF0C585) else Color(0xFF9C6B25)

@Composable
private fun CompactTab(label: String, selected: Boolean, dark: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(24.dp))
            .background(if (selected) {
                if (dark) Color(0xFF403326) else Color(0xFFFFE8C5)
            } else Color.Transparent)
            .then(if (selected) Modifier else Modifier)
            .height(37.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, maxLines = 1,
            fontSize = 13.sp,
            color = if (selected) compactAccent(dark) else compactSub(dark),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
    }
}

@Composable
private fun CompactAvatar(chat: Chat, photoPath: String?) {
    val colors = listOf(
        listOf(Color(0xFF4986B5), Color(0xFF85BBD2)),
        listOf(Color(0xFF7452A0), Color(0xFFAA7DD2)),
        listOf(Color(0xFFB66D53), Color(0xFFD4A27B))
    )
    val shade = colors[Math.floorMod(chat.id, colors.size)]
    val initial = chat.title.trim().take(1).ifBlank { "?" }.uppercase()
    Box(Modifier.size(54.dp).clip(CircleShape)
        .background(Brush.linearGradient(shade)), contentAlignment = Alignment.Center) {
        if (photoPath != null) AsyncImage(
            model = File(photoPath),
            contentDescription = "${chat.title} avatar",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        ) else Text(initial, color = Color.White,
            fontWeight = FontWeight.SemiBold, fontSize = 23.sp)
    }
}

@Composable
private fun UnreadIndicator(count: Int, muted: Boolean, dark: Boolean) {
    if (count <= 0) return
    // Unlike the old 99 cap, reflect Telegram's actual unread count.
    val label = if (count > 99999) "99999+" else count.toString()
    Box(
        Modifier.defaultMinSize(minWidth = 25.dp).heightIn(min = 22.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (muted) {
                if (dark) Color(0xFF414144) else Color(0xFFCED0D3)
            } else {
                if (dark) Color(0xFF464649) else Color(0xFFDFE2E6)
            })
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = compactText(dark), fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CompactChatList(
    chats: List<Chat>,
    photoPaths: Map<Int, String>,
    query: String,
    onQuery: (String) -> Unit,
    selectedFilter: ChatFilter,
    onFilter: (ChatFilter) -> Unit,
    archivedCount: Int,
    viewingArchive: Boolean,
    folders: List<TdlibSession.ChatFolderTab>,
    viewingFolderId: Int?,
    onFolder: (Int?) -> Unit,
    onArchiveViewChange: (Boolean) -> Unit,
    busyChats: Set<Long>,
    actionError: String?,
    onDismissActionError: () -> Unit,
    onTogglePin: (Long) -> Unit,
    onToggleArchive: (Long, Boolean) -> Unit,
    onCreateCommunity: () -> Unit,
    onManageFolders: () -> Unit,
    mutedChatIds: Set<Long>,
    dark: Boolean,
    onOpen: (Chat) -> Unit
) {
    val filtered = remember(chats, query, selectedFilter) {
        ChatFilters.apply(chats, selectedFilter, query)
    }
    var quickActions by remember { mutableStateOf(false) }
    val ink = compactText(dark)
    val muted = compactSub(dark)

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(compactBg(dark)),
        contentPadding = PaddingValues(bottom = 14.dp)
    ) {
        item(key = "header") {
            Column {
                Row(
                    Modifier.fillMaxWidth()
                        .padding(start = 20.dp, end = 14.dp, top = 16.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("GLACIA", color = ink, fontSize = 29.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { quickActions = true }) {
                            Icon(Icons.Rounded.MoreVert, "New chat or manage folders", tint = muted)
                        }
                        DropdownMenu(expanded = quickActions,
                            onDismissRequest = { quickActions = false }) {
                            DropdownMenuItem(
                                text = { Text("New group / channel") },
                                leadingIcon = { Icon(Icons.Rounded.Add, null) },
                                onClick = { quickActions = false; onCreateCommunity() }
                            )
                            DropdownMenuItem(
                                text = { Text("Manage chat folders") },
                                onClick = { quickActions = false; onManageFolders() }
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 15.dp)
                        .height(49.dp).clip(RoundedCornerShape(23.dp))
                        .background(compactSearch(dark))
                        .padding(horizontal = 17.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Search, null, tint = muted, modifier = Modifier.size(21.dp))
                    Spacer(Modifier.width(13.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isBlank()) {
                            Text("Search Chats", color = muted, fontSize = 14.sp)
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQuery,
                            singleLine = true,
                            textStyle = TextStyle(color = ink, fontSize = 14.sp),
                            cursorBrush = SolidColor(compactAccent(dark)),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.Close, "Clear search", tint = muted,
                                modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Single scrollable strip: do not stack Main, folders and chat
                // category filters into three massive separate rows.
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    CompactTab("All",
                        !viewingArchive && viewingFolderId == null &&
                            selectedFilter == ChatFilter.ALL, dark) {
                        onArchiveViewChange(false)
                        onFolder(null)
                        onFilter(ChatFilter.ALL)
                    }
                    CompactTab("Unread",
                        !viewingArchive && viewingFolderId == null &&
                            selectedFilter == ChatFilter.UNREAD, dark) {
                        onArchiveViewChange(false)
                        onFolder(null)
                        onFilter(ChatFilter.UNREAD)
                    }
                    CompactTab("Personal",
                        !viewingArchive && viewingFolderId == null &&
                            selectedFilter == ChatFilter.PRIVATE, dark) {
                        onArchiveViewChange(false)
                        onFolder(null)
                        onFilter(ChatFilter.PRIVATE)
                    }
                    CompactTab("Groups",
                        !viewingArchive && viewingFolderId == null &&
                            selectedFilter == ChatFilter.GROUPS, dark) {
                        onArchiveViewChange(false)
                        onFolder(null)
                        onFilter(ChatFilter.GROUPS)
                    }
                    folders.forEach { folder ->
                        CompactTab(folder.title.take(20),
                            viewingFolderId == folder.id && !viewingArchive, dark) {
                            onFolder(folder.id)
                        }
                    }
                    CompactTab("Archived ($archivedCount)", viewingArchive, dark) {
                        onArchiveViewChange(true)
                    }
                }
                actionError?.let { err ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(err, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = onDismissActionError) { Text("Dismiss") }
                    }
                }
                Spacer(Modifier.height(9.dp))
            }
        }
        items(filtered, key = { it.id }) { chat ->
            var showActions by remember(chat.id) { mutableStateOf(false) }
            val isMuted = chat.id in mutedChatIds
            Column {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 74.dp)
                        .combinedClickable(
                            onClick = { onOpen(chat) },
                            onLongClick = { showActions = true },
                            onLongClickLabel = "Chat actions"
                        )
                        .padding(start = 15.dp, end = 18.dp, top = 10.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactAvatar(chat, chat.photoFileId?.let(photoPaths::get))
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(chat.title.ifBlank { "Telegram chat" },
                                color = ink, fontSize = 15.5.sp,
                                fontWeight = if (chat.unread > 0) FontWeight.SemiBold
                                    else FontWeight.Medium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false))
                            if (isMuted) {
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Rounded.NotificationsOff,
                                    contentDescription = "Muted",
                                    tint = muted, modifier = Modifier.size(14.dp))
                            }
                            if (chat.pinned) {
                                Spacer(Modifier.width(3.dp))
                                Text("📌", fontSize = 11.sp)
                            }
                        }
                        Text(chat.preview.ifBlank { "No messages yet" },
                            color = muted, fontSize = 13.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(chat.time, color = muted, fontSize = 11.sp,
                            maxLines = 1)
                        UnreadIndicator(chat.unread, isMuted, dark)
                    }
                    Box {
                        DropdownMenu(expanded = showActions,
                            onDismissRequest = { showActions = false }) {
                            DropdownMenuItem(
                                text = { Text(if (chat.pinned) "Unpin chat" else "Pin chat") },
                                enabled = chat.id !in busyChats,
                                onClick = {
                                    showActions = false
                                    onTogglePin(chat.id)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (chat.archived) "Unarchive" else "Archive") },
                                enabled = chat.id !in busyChats,
                                onClick = {
                                    showActions = false
                                    onToggleArchive(chat.id, !chat.archived)
                                }
                            )
                        }
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = 81.dp, end = 15.dp),
                    thickness = 0.5.dp, color = compactLine(dark)
                )
            }
        }
        if (filtered.isEmpty()) {
            item(key = "empty") {
                Text(
                    if (query.isNotBlank()) "No chats match “$query”."
                    else if (viewingArchive) "No archived Telegram chats."
                    else "No chats in this tab.",
                    color = muted,
                    modifier = Modifier.padding(24.dp), fontSize = 14.sp
                )
            }
        }
    }
}
