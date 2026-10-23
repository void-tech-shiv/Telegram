package dev.glacia.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.glacia.app.data.Chat
import kotlinx.coroutines.delay
import java.io.File

/** Real Telegram search, not mock Discover cards. */
@Composable
fun TelegramDiscoverScreen(
    dark: Boolean,
    photoPaths: Map<Int, String>,
    matches: List<Chat>,
    loading: Boolean,
    error: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onOpen: (Chat) -> Unit
) {
    val ink = if (dark) Color(0xFFF3F6FF) else Color(0xFF17213A)
    val secondary = if (dark) Color(0xFFB0BDD7) else Color(0xFF56647F)
    val accent = if (dark) Color(0xFF9BE4FF) else Color(0xFF4A5DE0)
    val card = if (dark) Color(0xD81B2948) else Color(0xDDF7FAFF)
    LaunchedEffect(query) {
        // Avoid Telegram API flood on every keystroke and discard old searches.
        delay(380)
        onSearch(query)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 25.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Icon(
                    Icons.Rounded.Explore, contentDescription = null, tint = accent,
                    modifier = Modifier.size(37.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Discover Telegram", color = ink,
                    fontSize = 26.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    "Search public users, channels, groups and your chats.",
                    color = secondary, fontSize = 13.sp
                )
                Spacer(Modifier.height(19.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(card)
                        .border(
                            1.dp, Color.White.copy(alpha = if (dark) 0.15f else 0.75f),
                            RoundedCornerShape(22.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Search, null, tint = accent)
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isBlank()) Text(
                            "Search Telegram", color = secondary, fontSize = 14.sp
                        )
                        BasicTextField(
                            value = query,
                            onValueChange = { onQueryChange(it.take(70)) },
                            singleLine = true,
                            textStyle = TextStyle(color = ink, fontSize = 15.sp),
                            cursorBrush = SolidColor(accent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Rounded.Close, "Clear search", tint = secondary)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "SEARCH RESULTS",
                    fontSize = 11.sp, letterSpacing = 1.3.sp,
                    fontWeight = FontWeight.SemiBold, color = accent
                )
            }
        }
        if (loading) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    color = accent
                )
            }
        }
        if (query.trim().length < 2) {
            item {
                Text(
                    "Type at least 2 characters to search Telegram.",
                    color = secondary, fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 17.dp)
                )
            }
        } else {
            if (error != null) {
                item { Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            }
            items(matches, key = { it.id }) { chat ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(card)
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(22.dp))
                        .clickable { onOpen(chat) }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(50.dp).clip(CircleShape)
                            .background(accent.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val path = chat.photoFileId?.let(photoPaths::get)
                        if (path != null) {
                            AsyncImage(
                                model = File(path),
                                contentScale = ContentScale.Crop,
                                contentDescription = "${chat.title} photo",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                chat.title.take(1).uppercase(), color = accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            chat.title, color = ink, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            chat.preview, color = secondary, fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (!loading && matches.isEmpty() && error == null) {
                item {
                    Text(
                        "No matching Telegram chats found. Try a different name or username.",
                        color = secondary, fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 15.dp)
                    )
                }
            }
        }
    }
}
