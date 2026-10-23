package dev.glacia.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import android.content.Intent
import android.provider.Settings
import kotlinx.coroutines.delay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
import dev.glacia.app.data.ChatFilter
import dev.glacia.app.data.ChatFilters
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import java.io.File
import dev.glacia.app.data.NotificationHealth
import dev.glacia.app.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import dev.glacia.app.push.GlaciaPushRuntime
import dev.glacia.app.data.TdlibSession
import dev.glacia.app.settings.ThemeChoice
import dev.glacia.app.settings.ThemePreferences
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun GlaciaRoot(
    notificationChatId: Long? = null,
    onNotificationChatHandled: (Long) -> Unit = {}
) {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { ThemePreferences(context) }
    val theme by preferences.theme.collectAsState(initial = ThemeChoice.SYSTEM)
    val scope = rememberCoroutineScope()
    val session = remember(context) { GlaciaPushRuntime.attachUi(context) }
    LaunchedEffect(session) {
        if (BuildConfig.FCM_CONFIGURED && FirebaseApp.getApps(context).isNotEmpty()) {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                session.rememberPushToken(token)
            }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(session, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) session.setAppForeground(true)
            if (event == Lifecycle.Event.ON_PAUSE ||
                event == Lifecycle.Event.ON_STOP) session.setAppForeground(false)
        }
        session.setAppForeground(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        )
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var notificationsAllowed by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationsAllowed = granted }
    DisposableEffect(lifecycleOwner) {
        val permissionObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsAllowed = session.notificationsAllowed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(permissionObserver)
        onDispose { lifecycleOwner.lifecycle.removeObserver(permissionObserver) }
    }
    DisposableEffect(session) {
        session.start()
        onDispose { GlaciaPushRuntime.detachUi(session) }
    }
    val auth by session.auth.collectAsState()
    val realChats by session.chats.collectAsState()
    val photoPaths by session.filePaths.collectAsState()
    GlaciaTheme(theme) { dark ->
        if (auth is TdlibSession.Auth.Ready) {
            GlaciaApp(
                dark = dark,
                selectedTheme = theme,
                notificationChatId = notificationChatId,
                onNotificationChatHandled = onNotificationChatHandled,
                liveChats = realChats,
                session = session,
                photoPaths = photoPaths,
                notificationPermission = notificationsAllowed,
                onTestNotification = { session.sendTestNotification() },
                onOpenNotificationSettings = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                },
                onNotificationPermission = {
                    if (Build.VERSION.SDK_INT >= 33 && !notificationsAllowed) {
                        permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onThemeChange = { scope.launch { preferences.setTheme(it) } }
            )
        } else {
            AuthScreen(auth, session)
        }
    }
}

private fun ink(dark: Boolean) = if (dark) Color(0xFFF3F6FF) else Color(0xFF17213A)
private fun mutedInk(dark: Boolean) = if (dark) Color(0xFFB0BDD7) else Color(0xFF56647F)
private fun accent(dark: Boolean) = if (dark) Color(0xFF9BE4FF) else Color(0xFF4A5DE0)
private fun tile(dark: Boolean) = if (dark) Color(0xE61A2540) else Color(0xDDF7FAFF)

@Composable
private fun GlassPanel(
    hazeState: HazeState,
    dark: Boolean,
    modifier: Modifier = Modifier,
    radius: Dp = 26.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
.clip(shape)
            .animateContentSize()
            .hazeGlass(input = HazeInput.Backdrop(hazeState), style = GlassStyle.regular)
            .background(if (dark) Color(0xB8172544) else Color(0xCCFFFFFF))
            .border(
                1.dp,
                if (dark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.72f),
                shape
            ),
        content = content
    )
}

@Composable
private fun GlaciaApp(
    dark: Boolean,
    selectedTheme: ThemeChoice,
    notificationChatId: Long?,
    onNotificationChatHandled: (Long) -> Unit,
    liveChats: List<Chat>,
    session: TdlibSession,
    photoPaths: Map<Int, String>,
    notificationPermission: Boolean,
    onTestNotification: () -> Boolean,
    onOpenNotificationSettings: () -> Unit,
    onNotificationPermission: () -> Unit,
    onThemeChange: (ThemeChoice) -> Unit
) {
    val hazeState = rememberHazeState()
    var tab by rememberSaveable { mutableStateOf("Chats") }
    var openChatId by rememberSaveable { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var discoverQuery by rememberSaveable { mutableStateOf("") }
    var chatFilter by rememberSaveable { mutableStateOf(ChatFilter.ALL) }
    val discovered by session.discoveryResults.collectAsState()
    val discovering by session.discoveryLoading.collectAsState()
    val discoveryError by session.discoveryError.collectAsState()
    val notificationTarget by session.notificationTargetChat.collectAsState()
    val chatMuteStates by session.chatMuteStates.collectAsState()
    LaunchedEffect(notificationChatId, notificationTarget?.id, liveChats) {
        val id = notificationChatId ?: return@LaunchedEffect
        if (id == 0L) return@LaunchedEffect
        if (liveChats.none { it.id == id } && notificationTarget?.id != id) {
            session.ensureChat(id)
        } else {
            tab = "Chats"
            openChatId = id
            onNotificationChatHandled(id)
        }
    }
    val current = (liveChats + discovered + listOfNotNull(notificationTarget))
        .firstOrNull { it.id == openChatId }
    DisposableEffect(session, current?.id) {
        session.setVisibleChat(current?.id)
        onDispose { session.setVisibleChat(null) }
    }
    val messagesByChat by session.messages.collectAsState()
    val loadingChats by session.historyLoading.collectAsState()
    val documentTransfers by session.documentTransfers.collectAsState()
    val messageErrors by session.messageErrors.collectAsState()
    val mediaBusy by session.mediaSending.collectAsState()
    val actionPermissions by session.actionPermissions.collectAsState()
    val outboxRead by session.outboxRead.collectAsState()
    val typingByChat by session.typingActivities.collectAsState()
    val notificationSetup by session.notificationSetup.collectAsState()
    val notificationEvents by session.notificationEvents.collectAsState()
    val notificationHealth by session.notificationHealth.collectAsState()
    val pushStatus by session.pushStatus.collectAsState()
    LaunchedEffect(current?.id) {
        if (current == null) return@LaunchedEffect
        while (true) {
            session.pruneTyping()
            delay(1_000)
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val watcher = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumed = true
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) resumed = false
        }
        lifecycleOwner.lifecycle.addObserver(watcher)
        onDispose { lifecycleOwner.lifecycle.removeObserver(watcher) }
    }
    LaunchedEffect(current?.id) {
        val chatId = current?.id ?: return@LaunchedEffect
        session.openChat(chatId)
        session.loadHistory(chatId)
        try { awaitCancellation() }
        finally { session.closeChat(chatId) }
    }

    BackHandler(enabled = current != null) { openChatId = null }

    // Capture only the smooth aurora layer for Haze, never the scrollable content.
    // This prevents recursive backdrop capture and rectangle-shaped blur artifacts.
    Box(Modifier.fillMaxSize()) {
        AuroraBackground(dark, Modifier.fillMaxSize().hazeSource(hazeState))

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            GlassPanel(
                hazeState = hazeState,
                dark = dark,
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 66.dp)
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (current != null) {
                        IconButton(onClick = { openChatId = null }) {
                            Icon(Icons.Rounded.ArrowBack, "Back", tint = ink(dark))
                        }
                        Avatar(current.title, current.id, Modifier.size(42.dp),
                            photoPath = current.photoFileId?.let(photoPaths::get))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(current.title, color = ink(dark), fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val typingNow = typingByChat[current.id] != null
                            Text(
                                if (typingNow) {
                                    if (current.isGroup) "Someone is typing…" else "typing…"
                                } else "Telegram • Live messages",
                                color = if (typingNow) accent(dark) else mutedInk(dark),
                                fontSize = 11.sp
                            )
                        }
                        val muted = chatMuteStates[current.id] == true
                        IconButton(
                            onClick = { session.setChatMuted(current.id, !muted) }
                        ) {
                            Icon(
                                if (muted) Icons.Rounded.NotificationsOff
                                else Icons.Rounded.NotificationsActive,
                                contentDescription =
                                    if (muted) "Unmute this Telegram chat"
                                    else "Mute this Telegram chat",
                                tint = if (muted) mutedInk(dark) else accent(dark)
                            )
                        }
                    } else {
                        Column(Modifier.weight(1f)) {
                            Text("GLACIA", color = ink(dark), fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold, letterSpacing = 1.6.sp)
                            Text("LIQUID GLASS MESSENGER", color = accent(dark),
                                fontSize = 9.sp, letterSpacing = 1.5.sp)
                        }
                        Box(
                            Modifier.size(43.dp).clip(CircleShape)
                                .background(accent(dark).copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = accent(dark))
                        }
                    }
                }
            }

            Box(Modifier.weight(1f).clipToBounds()) {
                if (current != null) {
                    ConversationScreen(
                        chat = current,
                        messages = messagesByChat[current.id].orEmpty(),
                        filePaths = photoPaths,
                        documentTransfers = documentTransfers,
                        onDownloadDocument = { session.requestDocumentDownload(it) },
                        loading = current.id in loadingChats,
                        error = messageErrors[current.id],
                        dark = dark,
                        lastReadOutboxId = outboxRead[current.id] ?: 0L,
                        active = resumed,
                        onVisibleMessages = { ids ->
                            session.viewVisibleMessages(current.id, ids)
                        },
                        onTyping = { typing ->
                            session.sendTypingAction(current.id, typing)
                        },
                        onSend = { text, replyId -> session.sendText(current.id, text, replyId) },
                        onEditMessage = { id, text -> session.editMessage(current.id, id, text) },
                        onDeleteMessage = { id, everyone ->
                            session.deleteMessage(current.id, id, everyone)
                        },
                        onReactToMessage = { id, emoji, chosen ->
                            session.reactToMessage(current.id, id, emoji, chosen)
                        },
                        onForwardMessage = { id, toChat ->
                            session.forwardMessage(current.id, id, toChat)
                        },
                        onLoadMessageProperties = { id ->
                            session.loadMessageActions(current.id, id)
                        },
                        messageActionPermissions = actionPermissions,
                        forwardTargets = liveChats,
                        onAttachment = { uri -> session.sendAttachment(current.id, uri) },
                        onVoice = { voice ->
                            session.sendVoice(current.id, voice.path, voice.durationSeconds, voice.opus)
                        },
                        mediaBusy = mediaBusy,
                        onLoadMore = {
                            val earliestId = messagesByChat[current.id]?.firstOrNull()?.id ?: 0L
                            session.loadHistory(current.id, earliestId)
                        },
                        onDismissError = { session.dismissMessageError(current.id) }
                    )
                } else {
                    when (tab) {
                        "Chats" -> ChatList(
                            chats = liveChats,
                            photoPaths = photoPaths,
                            query = query,
                            onQuery = { query = it },
                            selectedFilter = chatFilter,
                            onFilter = { chatFilter = it },
                            dark = dark,
                            onOpen = { openChatId = it.id }
                        )
                        "Discover" -> TelegramDiscoverScreen(
                            dark = dark,
                            photoPaths = photoPaths,
                            matches = discovered,
                            loading = discovering,
                            error = discoveryError,
                            query = discoverQuery,
                            onQueryChange = { discoverQuery = it },
                            onSearch = { session.searchTelegramChats(it) },
                            onOpen = { openChatId = it.id }
                        )
                        else -> SettingsScreen(
                            selectedTheme, dark, onThemeChange,
                            notificationPermission, onNotificationPermission,
                            onTestNotification, onOpenNotificationSettings,
                            notificationSetup, notificationEvents, notificationHealth,
                            pushStatus
                        )
                    }
                }
            }

            if (current == null) {
                GlassPanel(
                    hazeState = hazeState,
                    dark = dark,
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
                    radius = 30.dp
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NavigationTab("Chats", tab == "Chats", dark, Icons.Rounded.ChatBubbleOutline) {
                            tab = "Chats"
                        }
                        NavigationTab("Discover", tab == "Discover", dark, Icons.Rounded.Explore) {
                            tab = "Discover"
                        }
                        NavigationTab("Settings", tab == "Settings", dark, Icons.Rounded.Settings) {
                            tab = "Settings"
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuroraBackground(dark: Boolean, modifier: Modifier = Modifier) {
    val top = if (dark) Color(0xFF080F23) else Color(0xFFE9F4FF)
    val bottom = if (dark) Color(0xFF0C1126) else Color(0xFFF3EDFF)
    val blue = if (dark) Color(0xFF324AA4) else Color(0xFFB4D6FF)
    val violet = if (dark) Color(0xFF49346C) else Color(0xFFDCD2FF)
    val teal = if (dark) Color(0xFF1C7283) else Color(0xFFBAEAF0)
    Canvas(modifier) {
        drawRect(brush = Brush.verticalGradient(listOf(top, bottom)))
        val radius = maxOf(size.width, size.height) * 0.64f
        val topRight = Offset(size.width * 1.04f, size.height * 0.14f)
        val left = Offset(size.width * -0.18f, size.height * 0.54f)
        val bottomRight = Offset(size.width * 0.93f, size.height * 0.92f)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(blue.copy(alpha = if (dark) 0.55f else 0.85f), Color.Transparent),
                center = topRight, radius = radius
            ), radius = radius, center = topRight
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(violet.copy(alpha = if (dark) 0.44f else 0.70f), Color.Transparent),
                center = left, radius = radius
            ), radius = radius, center = left
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(teal.copy(alpha = if (dark) 0.38f else 0.66f), Color.Transparent),
                center = bottomRight, radius = radius
            ), radius = radius, center = bottomRight
        )
    }
}

@Composable
private fun ChatList(
    chats: List<Chat>,
    photoPaths: Map<Int, String>,
    query: String,
    onQuery: (String) -> Unit,
    selectedFilter: ChatFilter,
    onFilter: (ChatFilter) -> Unit,
    dark: Boolean,
    onOpen: (Chat) -> Unit
) {
    val filtered = remember(chats, query, selectedFilter) {
        ChatFilters.apply(chats, selectedFilter, query)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().clipToBounds(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
                Text("Your conversations", fontSize = 26.sp,
                    fontWeight = FontWeight.Bold, color = ink(dark), maxLines = 2)
                Spacer(Modifier.height(5.dp))
                Text("All your chats, with a little more magic.",
                    fontSize = 13.sp, color = mutedInk(dark))
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(tile(dark))
                        .border(1.dp, Color.White.copy(alpha = if (dark) 0.12f else 0.78f),
                            RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Search, null, tint = accent(dark))
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text("Search conversations", color = mutedInk(dark), fontSize = 14.sp)
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQuery,
                            singleLine = true,
                            textStyle = TextStyle(color = ink(dark), fontSize = 14.sp),
                            cursorBrush = SolidColor(accent(dark)),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(Icons.Rounded.Close, "Clear search",
                            Modifier.clickable { onQuery("") }, tint = mutedInk(dark))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChatFilter.entries.forEach { option ->
                        FilterChip(
                            selected = selectedFilter == option,
                            onClick = { onFilter(option) },
                            label = { Text(option.label) }
                        )
                    }
                }
                Spacer(Modifier.height(11.dp))
                Text("CHATS  •  LIVE", fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp,
                    color = accent(dark))
                Spacer(Modifier.height(7.dp))
            }
        }
        items(filtered, key = { it.id }) { chat ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(tile(dark))
                    .border(1.dp,
                        Color.White.copy(alpha = if (dark) 0.08f else 0.66f),
                        RoundedCornerShape(22.dp))
                    .clickable { onOpen(chat) }
                    .padding(horizontal = 13.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(chat.title, chat.id, Modifier.size(52.dp),
                    photoPath = chat.photoFileId?.let(photoPaths::get))
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(chat.title.ifBlank { "Unnamed chat" }, color = ink(dark),
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(5.dp))
                    Text(chat.preview, color = mutedInk(dark), fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (chat.unread > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.clip(CircleShape).background(accent(dark))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(chat.unread.coerceAtMost(99).toString(), fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (dark) Color(0xFF13223B) else Color.White)
                    }
                }
            }
        }
        if (filtered.isEmpty()) {
            item {
                Text(if (query.isBlank() && selectedFilter == ChatFilter.ALL)
                         "Your Telegram chat list is empty."
                     else "No conversations match this filter.",
                    color = mutedInk(dark),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp))
            }
        }
    }
}

@Composable
private fun Avatar(
    name: String,
    id: Long,
    modifier: Modifier = Modifier,
    photoPath: String? = null
) {
    val tones = listOf(
        listOf(Color(0xFF546FE5), Color(0xFFA18AF5)),
        listOf(Color(0xFF2C93BE), Color(0xFF39B7B5)),
        listOf(Color(0xFFAC5BAC), Color(0xFFDF88AB)),
        listOf(Color(0xFF4674D0), Color(0xFF7E9BE8))
    )
    val initials = name.trim().split(Regex("\\s+")).take(2)
        .mapNotNull { it.firstOrNull()?.toString() }
        .joinToString("").uppercase().ifBlank { "?" }
    Box(
        modifier.clip(CircleShape)
            .background(Brush.linearGradient(tones[Math.floorMod(id, tones.size)])),
        contentAlignment = Alignment.Center
    ) {
        if (photoPath != null) {
            AsyncImage(
                model = File(photoPath),
                contentDescription = "$name profile photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(initials, color = Color.White, fontWeight = FontWeight.Bold,
                fontSize = 16.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ConversationPreview(dark: Boolean) {
    Box(
        Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("✨", fontSize = 40.sp)
            Spacer(Modifier.height(16.dp))
            Text("Conversation preview", fontSize = 22.sp,
                fontWeight = FontWeight.Bold, color = ink(dark))
            Spacer(Modifier.height(12.dp))
            Text("Live message history and sending are coming in Phase 3.",
                color = mutedInk(dark), fontSize = 14.sp,
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun NavigationTab(
    label: String,
    selected: Boolean,
    dark: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val highlightBackground by animateColorAsState(
        if (selected) accent(dark).copy(alpha = 0.20f) else Color.Transparent,
        label = "GLACIA tab highlight"
    )
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(highlightBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label,
            tint = if (selected) accent(dark) else mutedInk(dark))
        Spacer(Modifier.height(5.dp))
        Text(label, fontSize = 11.sp, color = if (selected) ink(dark) else mutedInk(dark),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun DiscoverScreen(dark: Boolean) {
    Column(Modifier.fillMaxSize().padding(26.dp)) {
        Spacer(Modifier.height(20.dp))
        Icon(Icons.Rounded.Explore, null, Modifier.size(45.dp), tint = accent(dark))
        Spacer(Modifier.height(18.dp))
        Text("Discover", color = ink(dark), fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("Search and discovery tools arrive in a later phase.",
            color = mutedInk(dark))
    }
}

@Composable
private fun SettingsScreen(
    selected: ThemeChoice,
    dark: Boolean,
    onSelect: (ThemeChoice) -> Unit,
    notificationPermission: Boolean,
    onNotificationPermission: () -> Unit,
    onTestNotification: () -> Boolean,
    onOpenNotificationSettings: () -> Unit,
    notificationSetup: String,
    notificationEvents: Int,
    notificationHealth: NotificationHealth,
    pushStatus: String
) {
    var notificationTestResult by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 23.dp, vertical = 18.dp)) {
        Text("Appearance", color = ink(dark), fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Text("Choose your perfect glass.", color = mutedInk(dark))
        Spacer(Modifier.height(22.dp))
        ThemeChoice.entries.forEach { option ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(tile(dark))
                    .clickable { onSelect(option) }
                    .padding(horizontal = 19.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when (option) {
                        ThemeChoice.SYSTEM -> "Follow system"
                        ThemeChoice.DARK -> "Midnight Glass"
                        ThemeChoice.LIGHT -> "Frosted Daylight"
                    },
                    Modifier.weight(1f), color = ink(dark), fontWeight = FontWeight.SemiBold
                )
                if (selected == option) {
                    Text("✓", color = accent(dark), fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("NOTIFICATIONS", color = accent(dark),
            fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            if (notificationPermission) "Android notification permission granted"
            else "Permission required for new-message alerts",
            color = mutedInk(dark), fontSize = 13.sp
        )
        if (!notificationPermission) {
            TextButton(onClick = onNotificationPermission) {
                Text("Allow notifications", color = accent(dark))
            }
        }
        TextButton(onClick = onOpenNotificationSettings) {
            Text("Android notification settings", color = accent(dark))
        }
        if (notificationPermission) {
            TextButton(onClick = {
                notificationTestResult = if (onTestNotification()) {
                    "Test posted. Open Android's notification shade to see it."
                } else {
                    "Android is blocking notifications. Check app notification settings."
                }
            }) {
                Text("Send test notification", color = accent(dark))
            }
        }
        notificationTestResult?.let { result ->
            Text(result, color = mutedInk(dark), fontSize = 12.sp)
            Spacer(Modifier.height(5.dp))
        }
        Text(notificationSetup, color = mutedInk(dark), fontSize = 12.sp)
        Text(
            "TDLib group updates: $notificationEvents",
            color = mutedInk(dark), fontSize = 12.sp
        )
        Text("Incoming message events: ${notificationHealth.incomingMessages}",
            color = mutedInk(dark), fontSize = 12.sp)
        Text("New TDLib notification items: ${notificationHealth.groupAdds}",
            color = mutedInk(dark), fontSize = 12.sp)
        Text("Android alerts posted: ${notificationHealth.posted}",
            color = mutedInk(dark), fontSize = 12.sp)
        Text(
            "App state: " + (if (notificationHealth.inForeground) "Foreground" else "Background"),
            color = mutedInk(dark), fontSize = 12.sp
        )
        Text("Last event: ${notificationHealth.lastDecision}",
            color = mutedInk(dark), fontSize = 12.sp, lineHeight = 17.sp)
        Spacer(Modifier.height(7.dp))
        Text("PUSH STATUS", color = accent(dark),
            fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text(pushStatus, color = mutedInk(dark), fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Diagnostic counters reset if Android restarts the app process. " +
                "They contain no chat contents or account identifiers.",
            color = mutedInk(dark), fontSize = 11.sp
        )
        Spacer(Modifier.height(7.dp))
        Text(
            "Message alerts appear while GLACIA is in the background and its process is running. " +
                "Foreground messages won't show an Android alert. Lock-screen previews hide content.",
            color = mutedInk(dark), fontSize = 12.sp
        )
        Spacer(Modifier.height(24.dp))
        Text("GLACIA  •  v0.3.8", color = accent(dark),
            fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Text("Experimental Telegram client", color = ink(dark),
            fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Available: Telegram messaging, photo/video/file upload, voice-note recording " +
                "(Android 10+), media playback, chat filters, smart notifications and typing.",
            color = mutedInk(dark), fontSize = 13.sp, lineHeight = 20.sp
        )
        Spacer(Modifier.height(14.dp))
        Text("STILL IN DEVELOPMENT", color = accent(dark),
            fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, fontSize = 11.sp)
        Spacer(Modifier.height(7.dp))
        Text(
            "Background push delivery after process termination is not guaranteed. " +
                "Message reactions, advanced search, upload cleanup and voice compatibility " +
                "need more device testing.",
            color = mutedInk(dark), fontSize = 13.sp, lineHeight = 20.sp
        )
    }
}
