package dev.glacia.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.glacia.app.data.Chat
import dev.glacia.app.data.DemoChatRepository
import dev.glacia.app.data.Message
import dev.glacia.app.settings.ThemeChoice
import dev.glacia.app.settings.ThemePreferences
import kotlinx.coroutines.launch

@Composable
fun GlaciaRoot() {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { ThemePreferences(context) }
    val theme by preferences.theme.collectAsState(initial = ThemeChoice.SYSTEM)
    val scope = rememberCoroutineScope()
    GlaciaTheme(choice = theme) { dark ->
        GlaciaApp(
            dark = dark,
            selectedTheme = theme,
            onThemeChange = { scope.launch { preferences.setTheme(it) } }
        )
    }
}

@Composable
internal fun GlassPanel(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    radius: Dp = 28.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .clip(shape)
            .hazeGlass(input = HazeInput.Backdrop(hazeState), style = GlassStyle.regular)
            .border(1.dp, Color.White.copy(alpha = 0.24f), shape),
        content = content
    )
}

@Composable
private fun GlaciaApp(
    dark: Boolean,
    selectedTheme: ThemeChoice,
    onThemeChange: (ThemeChoice) -> Unit
) {
    val hazeState = rememberHazeState()
    val repository = remember { DemoChatRepository() }
    val chats = remember { repository.chats() }
    var tab by rememberSaveable { mutableStateOf("Chats") }
    var openChatId by rememberSaveable { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val messages = remember {
        mutableStateMapOf<Long, List<Message>>().apply {
            chats.forEach { put(it.id, repository.messages(it.id)) }
        }
    }
    val current = chats.firstOrNull { it.id == openChatId }
    BackHandler(enabled = current != null) { openChatId = null }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
            AuroraBackground(dark)
            if (current != null) {
                Conversation(
                    chat = current,
                    messages = messages[current.id].orEmpty(),
                    dark = dark
                )
            } else {
                when (tab) {
                    "Chats" -> ChatList(
                        chats = chats.filter {
                            it.title.contains(query, ignoreCase = true) ||
                                it.preview.contains(query, ignoreCase = true)
                        },
                        query = query,
                        onQuery = { query = it },
                        dark = dark,
                        onOpen = { openChatId = it.id }
                    )
                    "Discover" -> DiscoverScreen()
                    else -> SettingsScreen(
                        selected = selectedTheme,
                        onSelect = onThemeChange
                    )
                }
            }
        }

        GlassPanel(
            hazeState = hazeState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            radius = 26.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (current != null) {
                    IconButton(onClick = { openChatId = null }) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Avatar(current.title, current.id, Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(current.title, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("Offline preview", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    Column(Modifier.weight(1f)) {
                        Text("GLACIA", fontWeight = FontWeight.ExtraBold, fontSize = 23.sp,
                            letterSpacing = 2.sp)
                        Text("THE GLASS MESSENGER", fontSize = 9.sp,
                            letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }

        if (current != null) {
            ComposerBar(
                hazeState = hazeState,
                onSend = { text ->
                    val existing = messages[current.id].orEmpty()
                    messages[current.id] = existing + Message(
                        id = System.nanoTime(),
                        chatId = current.id,
                        text = text,
                        time = "now",
                        outgoing = true
                    )
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        } else {
            GlassPanel(
                hazeState = hazeState,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth().navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                radius = 32.dp
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 11.dp, horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    NavigationTab("Chats", tab == "Chats", Icons.Rounded.ChatBubbleOutline) { tab = "Chats" }
                    NavigationTab("Discover", tab == "Discover", Icons.Rounded.Explore) { tab = "Discover" }
                    NavigationTab("Settings", tab == "Settings", Icons.Rounded.Settings) { tab = "Settings" }
                }
            }
        }
    }
}

@Composable
internal fun AuroraBackground(dark: Boolean) {
    val c1 = if (dark) Color(0xFF07101D) else Color(0xFFE8F7FF)
    val c2 = if (dark) Color(0xFF23143C) else Color(0xFFE6E1FF)
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(c1, c2, c1)))) {
        Box(Modifier.align(Alignment.TopEnd).offset(x = 72.dp, y = 72.dp)
            .size(280.dp).blur(88.dp).background(
                if (dark) Color(0x994858C6) else Color(0x7758B7FF), CircleShape))
        Box(Modifier.align(Alignment.CenterStart).offset(x = (-92).dp, y = 38.dp)
            .size(290.dp).blur(100.dp).background(
                if (dark) Color(0x886147A8) else Color(0x667E6DFF), CircleShape))
        Box(Modifier.align(Alignment.BottomEnd).offset(x = 42.dp, y = (-70).dp)
            .size(230.dp).blur(90.dp).background(
                if (dark) Color(0x9960B9E0) else Color(0x8865CDE3), CircleShape))
    }
}

@Composable
private fun ChatList(
    chats: List<Chat>,
    query: String,
    onQuery: (String) -> Unit,
    dark: Boolean,
    onOpen: (Chat) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 111.dp, bottom = 115.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        item {
            Column(Modifier.padding(horizontal = 22.dp)) {
                Text("Your conversations", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text("A more beautiful way to connect.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f))
                Spacer(Modifier.height(22.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = if (dark) 0.35f else 0.52f))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(22.dp))
                        .padding(horizontal = 17.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text("Search demo chats", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f))
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQuery,
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface)
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(Icons.Rounded.Close, "Clear", Modifier.clickable { onQuery("") })
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("MESSAGES  •  DEMO ONLY", fontWeight = FontWeight.Bold,
                    fontSize = 11.sp, letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(9.dp))
            }
        }
        items(chats, key = { it.id }) { chat ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = if (dark) 0.27f else 0.4f))
                    .clickable { onOpen(chat) }
                    .padding(horizontal = 12.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Avatar(chat.title, chat.id, Modifier.size(56.dp))
                    if (chat.isOnline) {
                        Box(Modifier.align(Alignment.BottomEnd).size(14.dp)
                            .background(Color(0xFF36D8A0), CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.background, CircleShape))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(chat.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(5.dp))
                    Text(chat.preview, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(chat.time, fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.57f))
                    Spacer(Modifier.height(8.dp))
                    if (chat.unread > 0) {
                        Box(Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text(chat.unread.toString(), color = MaterialTheme.colorScheme.background,
                                fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Icon(Icons.Rounded.Check, null, Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        if (chats.isEmpty()) {
            item {
                Text("No matching demo chats.", Modifier.padding(28.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun Avatar(name: String, id: Long, modifier: Modifier = Modifier) {
    val tones = listOf(
        listOf(Color(0xFF6D77FD), Color(0xFFB487E9)),
        listOf(Color(0xFF2697C4), Color(0xFF45D3C2)),
        listOf(Color(0xFFB15FBB), Color(0xFFE38BA4)),
        listOf(Color(0xFF447AD5), Color(0xFF7F98F7))
    )
    Box(
        modifier.clip(CircleShape).background(Brush.linearGradient(tones[((id - 1) % tones.size).toInt()])),
        contentAlignment = Alignment.Center
    ) {
        Text(name.split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }
            .joinToString("").uppercase(), color = Color.White,
            fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Conversation(chat: Chat, messages: List<Message>, dark: Boolean) {
    val state = rememberLazyListState()
    LaunchedEffect(chat.id, messages.size) {
        if (messages.isNotEmpty()) state.animateScrollToItem(messages.lastIndex)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 108.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp),
                contentAlignment = Alignment.Center) {
                Text("LOCAL PREVIEW • NOT SENT TO TELEGRAM", fontSize = 10.sp,
                    letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
        items(messages, key = { it.id }) { message ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = if (message.outgoing) Arrangement.End else Arrangement.Start
            ) {
                Column(
                    Modifier.widthIn(max = 300.dp)
                        .clip(RoundedCornerShape(
                            topStart = 22.dp, topEnd = 22.dp,
                            bottomStart = if (message.outgoing) 22.dp else 5.dp,
                            bottomEnd = if (message.outgoing) 5.dp else 22.dp
                        ))
                        .background(
                            if (message.outgoing) MaterialTheme.colorScheme.primary.copy(alpha = if (dark) 0.28f else 0.23f)
                            else MaterialTheme.colorScheme.surface.copy(alpha = if (dark) 0.45f else 0.65f)
                        )
                        .padding(horizontal = 17.dp, vertical = 12.dp)
                ) {
                    Text(message.text, fontSize = 15.sp, lineHeight = 21.sp)
                    Spacer(Modifier.height(5.dp))
                    Text(message.time, fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.align(Alignment.End))
                }
            }
        }
    }
}

@Composable
private fun ComposerBar(hazeState: HazeState, onSend: (String) -> Unit, modifier: Modifier = Modifier) {
    var input by rememberSaveable { mutableStateOf("") }
    fun send() {
        val value = input.trim()
        if (value.isNotEmpty()) {
            onSend(value)
            input = ""
        }
    }
    GlassPanel(
        hazeState = hazeState,
        modifier = modifier.fillMaxWidth().navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (input.isEmpty()) Text("Message (local demo)", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    maxLines = 4,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() })
                )
            }
            Spacer(Modifier.width(10.dp))
            FilledIconButton(onClick = ::send, enabled = input.isNotBlank()) {
                Icon(Icons.Rounded.Send, contentDescription = "Add local demo message")
            }
        }
    }
}

@Composable
private fun NavigationTab(
    label: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Column(
        Modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label,
            tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun DiscoverScreen() {
    Column(Modifier.fillMaxSize().padding(top = 150.dp, start = 28.dp, end = 28.dp)) {
        Icon(Icons.Rounded.Explore, null, Modifier.size(46.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        Text("Discover", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("Explore channels, groups and people after TDLib integration in Phase 2.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
        Spacer(Modifier.height(22.dp))
        Text("No network requests are made in this preview.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SettingsScreen(selected: ThemeChoice, onSelect: (ThemeChoice) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(top = 126.dp, start = 24.dp, end = 24.dp, bottom = 110.dp)
    ) {
        Text("Appearance", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Choose your perfect glass.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f))
        Spacer(Modifier.height(30.dp))
        ThemeChoice.entries.forEach { option ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
                    .clickable { onSelect(option) }
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(when (option) {
                    ThemeChoice.SYSTEM -> "Follow system"
                    ThemeChoice.DARK -> "Midnight Glass"
                    ThemeChoice.LIGHT -> "Frosted Daylight"
                }, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                if (option == selected) {
                    Icon(Icons.Rounded.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Text("PREVIEW MODE", color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Telegram account login, live chats and media are not enabled yet.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f))
    }
}
