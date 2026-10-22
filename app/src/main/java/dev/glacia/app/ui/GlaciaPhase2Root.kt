package dev.glacia.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.glacia.app.data.Chat
import dev.glacia.app.data.LoginStage
import dev.glacia.app.data.Message
import dev.glacia.app.data.TdlibTelegramGateway
import dev.glacia.app.settings.ThemeChoice
import dev.glacia.app.settings.ThemePreferences
import kotlinx.coroutines.launch

/**
 * Real Telegram route. Demo route remains opt-in and uses only demo data.
 * Never display fake chats as if they were fetched from Telegram.
 */
@Composable
fun GlaciaPhase2Root() {
    val context = LocalContext.current.applicationContext
    val gateway = remember(context) { TdlibTelegramGateway(context) }
    val preferences = remember(context) { ThemePreferences(context) }
    val theme by preferences.theme.collectAsState(initial = ThemeChoice.SYSTEM)
    val stage by gateway.stage.collectAsState()
    val chats by gateway.chats.collectAsState()
    val histories by gateway.histories.collectAsState()
    val serviceError by gateway.error.collectAsState()
    val scope = rememberCoroutineScope()
    var showDemo by rememberSaveable { mutableStateOf(false) }
    var selectedChat by rememberSaveable { mutableStateOf<Long?>(null) }
    var uiError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(gateway) {
        gateway.start()
        onDispose { gateway.close() }
    }

    LaunchedEffect(stage) {
        if (stage !is LoginStage.Ready) selectedChat = null
        uiError = null
    }

    if (showDemo) {
        Box {
            GlaciaRoot()
            FilledTonalButton(
                onClick = { showDemo = false },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding()
                    .padding(end = 20.dp, top = 90.dp)
            ) { Text("Exit demo") }
        }
    } else {
        GlaciaTheme(choice = theme) { dark ->
            val haze = rememberHazeState()
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().hazeSource(haze)) {
                    AuroraBackground(dark)
                    when (val current = stage) {
                        is LoginStage.Ready -> LiveChats(
                            chats = chats,
                            history = selectedChat?.let { histories[it] }.orEmpty(),
                            selected = chats.firstOrNull { it.id == selectedChat },
                            onOpen = {
                                selectedChat = it.id
                                scope.launch {
                                    runCatching { gateway.openChat(it.id) }
                                        .onFailure { error -> uiError = error.message ?: "Unable to load messages" }
                                }
                            },
                            onSend = { text ->
                                val chatId = selectedChat ?: return@LiveChats
                                scope.launch {
                                    runCatching { gateway.sendMessage(chatId, text) }
                                        .onFailure { error -> uiError = error.message ?: "Unable to send message" }
                                }
                            },
                            onBack = { selectedChat = null },
                            onRefresh = {
                                scope.launch {
                                    runCatching { gateway.refreshChats() }
                                        .onFailure { error -> uiError = error.message ?: "Unable to refresh chats" }
                                }
                            }
                        )
                        else -> AuthorizationContent(
                            stage = current,
                            onSubmit = { first, second ->
                                scope.launch {
                                    runCatching {
                                        when (current) {
                                            LoginStage.Phone -> gateway.sendPhone(first)
                                            LoginStage.Code -> gateway.sendCode(first)
                                            LoginStage.Password -> gateway.sendPassword(first)
                                            LoginStage.EmailAddress -> gateway.sendEmail(first)
                                            LoginStage.EmailCode -> gateway.sendEmailCode(first)
                                            LoginStage.Register -> gateway.register(first, second)
                                            else -> Unit
                                        }
                                    }.onFailure { error ->
                                        uiError = error.message ?: "Login request failed"
                                    }
                                }
                            },
                            onDemo = { showDemo = true }
                        )
                    }
                }
                GlassPanel(
                    hazeState = haze,
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                        .statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    radius = 26.dp
                ) {
                    Row(
                        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedChat != null && stage is LoginStage.Ready) {
                            IconButton(onClick = { selectedChat = null }) {
                                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (selectedChat != null && stage is LoginStage.Ready)
                                    chats.firstOrNull { it.id == selectedChat }?.title ?: "Conversation"
                                else "GLACIA",
                                fontSize = 20.sp, fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (stage is LoginStage.Ready) "TELEGRAM CONNECTED" else "SECURE TELEGRAM LOGIN",
                                fontSize = 10.sp, letterSpacing = 1.2.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (stage is LoginStage.Ready && selectedChat == null) {
                            IconButton(onClick = {
                                scope.launch {
                                    runCatching { gateway.refreshChats() }
                                        .onFailure { uiError = it.message }
                                }
                            }) { Icon(Icons.Rounded.Refresh, contentDescription = "Refresh") }
                        }
                    }
                }

                if (stage is LoginStage.Ready && selectedChat != null) {
                    LiveComposer(
                        haze = haze,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        onSend = { value ->
                            val id = selectedChat
                            if (id != null) {
                                scope.launch {
                                    runCatching { gateway.sendMessage(id, value) }
                                        .onFailure { uiError = it.message ?: "Send failed" }
                                }
                            }
                        }
                    )
                } else {
                    GlassPanel(
                        hazeState = haze,
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth().navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Text("LIVE • TDLib", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary)
                            TextButton(onClick = {
                                scope.launch {
                                    val next = when (theme) {
                                        ThemeChoice.SYSTEM -> ThemeChoice.DARK
                                        ThemeChoice.DARK -> ThemeChoice.LIGHT
                                        ThemeChoice.LIGHT -> ThemeChoice.SYSTEM
                                    }
                                    preferences.setTheme(next)
                                }
                            }) {
                                Icon(Icons.Rounded.Settings, contentDescription = null,
                                    modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(theme.name.lowercase().replaceFirstChar { it.uppercase() })
                            }
                            if (stage is LoginStage.Ready) {
                                var confirm by remember { mutableStateOf(false) }
                                IconButton(onClick = { confirm = true }) {
                                    Icon(Icons.Rounded.Logout, contentDescription = "Sign out")
                                }
                                if (confirm) {
                                    AlertDialog(
                                        onDismissRequest = { confirm = false },
                                        title = { Text("Sign out of Telegram?") },
                                        text = { Text("GLACIA will close this Telegram session. Signing out removes the local session.") },
                                        confirmButton = {
                                            TextButton(onClick = {
                                                confirm = false
                                                scope.launch {
                                                    runCatching { gateway.logOut() }
                                                        .onFailure { uiError = it.message }
                                                }
                                            }) { Text("Sign out") }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { confirm = false }) { Text("Cancel") }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                val warning = uiError ?: serviceError
                if (!warning.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopCenter)
                            .statusBarsPadding().padding(top = 84.dp, start = 24.dp, end = 24.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            warning,
                            Modifier.clickable {
                                uiError = null
                                gateway.clearError()
                            }.padding(14.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthorizationContent(
    stage: LoginStage,
    onSubmit: (String, String) -> Unit,
    onDemo: () -> Unit
) {
    var entry by remember(stage) { mutableStateOf("") }
    var surname by remember(stage) { mutableStateOf("") }
    val title = when (stage) {
        LoginStage.Unconfigured -> "Developer setup required"
        LoginStage.Starting -> "Connecting to Telegram…"
        LoginStage.Phone -> "Your phone number"
        LoginStage.Code -> "Check your Telegram messages"
        LoginStage.Password -> "Two-step verification"
        LoginStage.EmailAddress -> "Verify your email"
        LoginStage.EmailCode -> "Enter email code"
        LoginStage.Register -> "Create your account"
        is LoginStage.ConfirmOtherDevice -> "Confirm on another device"
        is LoginStage.Unavailable -> "Connection unavailable"
        LoginStage.Ready -> ""
    }
    val description = when (stage) {
        LoginStage.Unconfigured -> "Add TELEGRAM_API_ID and TELEGRAM_API_HASH to local.properties, then build locally. CI APKs intentionally contain no credentials."
        LoginStage.Phone -> "Enter your existing Telegram account number in international format."
        LoginStage.Code -> "Enter the code supplied by Telegram. Never share it with anyone."
        LoginStage.Password -> "Enter your Telegram two-step verification password."
        LoginStage.EmailAddress -> "Telegram requires an email address to continue."
        LoginStage.EmailCode -> "Enter the code sent to your email."
        LoginStage.Register -> "This number has no Telegram account. Registration creates one."
        is LoginStage.ConfirmOtherDevice -> "Approve the login in your existing Telegram session:\n${stage.link}"
        is LoginStage.Unavailable -> stage.reason
        else -> "Starting the TDLib engine…"
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(
            start = 27.dp, end = 27.dp, top = 155.dp, bottom = 120.dp
        ),
        verticalArrangement = Arrangement.Top
    ) {
        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(26.dp))
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(description, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.77f),
            lineHeight = 21.sp)
        Spacer(Modifier.height(28.dp))
        val label = when (stage) {
            LoginStage.Phone -> "Phone (+country code)"
            LoginStage.Code -> "Telegram code"
            LoginStage.Password -> "2-step verification password"
            LoginStage.EmailAddress -> "Email address"
            LoginStage.EmailCode -> "Email code"
            LoginStage.Register -> "First name"
            else -> ""
        }
        if (label.isNotBlank()) {
            OutlinedTextField(
                value = entry,
                onValueChange = { entry = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = when (stage) {
                        LoginStage.Phone -> KeyboardType.Phone
                        LoginStage.Code, LoginStage.EmailCode -> KeyboardType.Number
                        LoginStage.EmailAddress -> KeyboardType.Email
                        else -> KeyboardType.Text
                    }
                ),
                visualTransformation = if (stage is LoginStage.Password)
                    PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None
            )
            if (stage is LoginStage.Register) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = surname, onValueChange = { surname = it },
                    label = { Text("Last name (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = entry.isNotBlank(),
                shape = RoundedCornerShape(22.dp),
                onClick = {
                    onSubmit(entry, surname)
                    // Clearing one-time codes/passwords happens on the next auth stage.
                }
            ) { Text("Continue") }
        }
        if (stage is LoginStage.Unconfigured || stage is LoginStage.Unavailable) {
            Spacer(Modifier.height(25.dp))
            OutlinedButton(onClick = onDemo, shape = RoundedCornerShape(20.dp)) {
                Text("Explore offline UI demo")
            }
        }
    }
}

@Composable
private fun LiveChats(
    chats: List<Chat>,
    history: List<Message>,
    selected: Chat?,
    onOpen: (Chat) -> Unit,
    onSend: (String) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    BackHandler(enabled = selected != null) { onBack() }
    if (selected == null) {
        var filter by rememberSaveable { mutableStateOf("") }
        val filtered = chats.filter { it.title.contains(filter, ignoreCase = true) }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 115.dp, bottom = 122.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Text("Conversations", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text("Your Telegram chats, in glass.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                    Spacer(Modifier.height(20.dp))
                    OutlinedTextField(
                        value = filter, onValueChange = { filter = it },
                        label = { Text("Search loaded chats") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(25.dp)
                    )
                    Spacer(Modifier.height(13.dp))
                }
            }
            items(filtered, key = { it.id }) { chat ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
                        .clickable { onOpen(chat) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(55.dp).background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(chat.title.take(1).uppercase(), fontWeight = FontWeight.Bold,
                            fontSize = 21.sp)
                    }
                    Spacer(Modifier.width(15.dp))
                    Column(Modifier.weight(1f)) {
                        Text(chat.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(5.dp))
                        Text(chat.preview, fontSize = 13.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(chat.time, fontSize = 11.sp)
                        if (chat.unread > 0) {
                            Text(chat.unread.toString(), fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No loaded chats yet.")
                        Spacer(Modifier.height(10.dp))
                        TextButton(onClick = onRefresh) { Text("Refresh from Telegram") }
                    }
                }
            }
        }
    } else {
        val sorted = history.sortedWith(compareBy<Message> { it.time }.thenBy { it.id })
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 116.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            reverseLayout = true
        ) {
            items(sorted.reversed(), key = { it.id }) { message ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = if (message.outgoing)
                        Arrangement.End else Arrangement.Start
                ) {
                    Column(
                        Modifier.widthIn(max = 305.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                if (message.outgoing) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(message.text, fontSize = 15.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(message.time, fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.align(Alignment.End))
                    }
                }
            }
            if (history.isEmpty()) {
                item {
                    Text("No recent text messages loaded.", Modifier.padding(30.dp))
                }
            }
        }
    }
}

@Composable
private fun LiveComposer(
    haze: HazeState,
    modifier: Modifier = Modifier,
    onSend: (String) -> Unit
) {
    var text by rememberSaveable { mutableStateOf("") }
    GlassPanel(
        hazeState = haze,
        modifier = modifier.fillMaxWidth().navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message") },
                maxLines = 4, shape = RoundedCornerShape(23.dp)
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                enabled = text.isNotBlank(),
                onClick = { onSend(text); text = "" }
            ) { Icon(Icons.Rounded.Send, "Send to Telegram") }
        }
    }
}
