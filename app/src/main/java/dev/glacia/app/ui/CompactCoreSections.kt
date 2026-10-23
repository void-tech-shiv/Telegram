package dev.glacia.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.glacia.app.BuildConfig
import dev.glacia.app.data.CoreAccountState
import dev.glacia.app.data.CorePerson
import dev.glacia.app.settings.ThemeChoice
import java.io.File

internal fun coreSurface(dark: Boolean) =
    if (dark) Color(0xFF171717) else Color(0xFFF8F9FA)
internal fun corePanel(dark: Boolean) =
    if (dark) Color(0xFF232325) else Color(0xFFFFFFFF)
internal fun coreFg(dark: Boolean) =
    if (dark) Color(0xFFF5F5F6) else Color(0xFF242529)
internal fun coreSub(dark: Boolean) =
    if (dark) Color(0xFF99999F) else Color(0xFF77787D)
internal fun coreGold(dark: Boolean) =
    if (dark) Color(0xFFE6BB7A) else Color(0xFF976D32)

@Composable
internal fun CompactPersonAvatar(
    person: CorePerson?,
    paths: Map<Int, String>,
    size: Int = 58
) {
    val file = person?.photoId?.let(paths::get)
    val initial = person?.displayName?.take(1)?.uppercase().orEmpty().ifBlank { "G" }
    Box(
        Modifier.size(size.dp).clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFF3C627C), Color(0xFF896C9A)))),
        contentAlignment = Alignment.Center
    ) {
        if (file != null) AsyncImage(
            model = File(file), contentDescription = "Telegram profile photo",
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
        ) else Text(initial, color = Color.White, fontSize = (size / 2.6f).sp,
            fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun CompactSectionHeading(label: String, dark: Boolean) {
    Text(label, color = coreSub(dark), fontSize = 13.sp,
        modifier = Modifier.padding(start = 16.dp, top = 13.dp, bottom = 9.dp))
}

@Composable
internal fun CompactMenuRow(
    emoji: String,
    title: String,
    subtitle: String?,
    dark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 68.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(37.dp).clip(RoundedCornerShape(12.dp))
            .background(if (dark) Color(0xFF353539) else Color(0xFFF0F0F2)),
            contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 21.sp)
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = coreFg(dark), fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = coreSub(dark), fontSize = 12.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(Icons.Rounded.ArrowForward, contentDescription = null,
            tint = coreSub(dark), modifier = Modifier.size(17.dp))
    }
}

@Composable
internal fun CompactMenuGroup(
    dark: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(21.dp))
            .background(corePanel(dark)),
        content = content
    )
}

@Composable
internal fun CompactPageHeader(title: String, dark: Boolean,
                               onBack: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, "Back", tint = coreGold(dark))
            }
        }
        Text(title, color = coreFg(dark), fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun CompactSettingsHome(
    dark: Boolean,
    account: CoreAccountState,
    paths: Map<Int, String>,
    onProfile: () -> Unit,
    onAccount: () -> Unit,
    onAppearance: () -> Unit,
    onPrivacy: () -> Unit,
    onNotifications: () -> Unit,
    onFolders: () -> Unit,
    onDevices: () -> Unit,
    onAdvanced: () -> Unit,
    onHelp: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    fun visible(title: String) = title.contains(search, ignoreCase = true) || search.isBlank()
    Column(
        Modifier.fillMaxSize().background(coreSurface(dark))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 15.dp)
    ) {
        CompactPageHeader("Settings", dark)
        OutlinedTextField(
            value = search, onValueChange = { search = it.take(60) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search settings") }, singleLine = true,
            shape = RoundedCornerShape(23.dp)
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp))
                .background(corePanel(dark))
                .clickable(onClick = onProfile).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactPersonAvatar(account.self, paths, 69)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(account.self?.displayName ?: "Telegram account",
                    color = coreFg(dark), fontSize = 18.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold)
                Text(account.self?.username?.takeIf { it.isNotEmpty() }?.let { "@$it" }
                    ?: "Your profile", color = coreSub(dark), fontSize = 12.sp)
            }
            Icon(Icons.Rounded.ArrowForward, null, tint = coreGold(dark))
        }
        CompactSectionHeading("Account & preferences", dark)
        CompactMenuGroup(dark) {
            if (visible("Account")) CompactMenuRow("👤", "Account",
                "Number, username and bio", dark, onAccount)
            if (visible("Chat Settings")) CompactMenuRow("💬", "Chat Settings",
                "Theme and appearance", dark, onAppearance)
            if (visible("Privacy & Security")) CompactMenuRow("🔐", "Privacy & Security",
                "Last seen, blocked people, call privacy", dark, onPrivacy)
            if (visible("Notifications")) CompactMenuRow("🔔", "Notifications",
                "Messages, calls and Android permission", dark, onNotifications)
            if (visible("Chat Folders")) CompactMenuRow("📁", "Chat Folders",
                "Sort your Telegram chats into folders", dark, onFolders)
            if (visible("Devices")) CompactMenuRow("💻", "Devices",
                "Review and revoke Telegram sessions", dark, onDevices)
            if (visible("Data and Storage")) CompactMenuRow("💾", "Data and Storage",
                "Connection and media diagnostics", dark, onAdvanced)
        }
        CompactSectionHeading("GLACIA", dark)
        CompactMenuGroup(dark) {
            if (visible("Advanced settings")) CompactMenuRow("⚙️", "Advanced settings",
                "Theme, notification tests and diagnostic counters", dark, onAdvanced)
            if (visible("Help and FAQ")) CompactMenuRow("❓", "Help and FAQ",
                "Open official Telegram help", dark, onHelp)
        }
        Spacer(Modifier.height(20.dp))
        Text("GLACIA for Android · v${BuildConfig.VERSION_NAME}",
            color = coreSub(dark), fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
internal fun CompactProfileHome(
    dark: Boolean,
    account: CoreAccountState,
    paths: Map<Int, String>,
    onEdit: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit
) {
    val person = account.self
    Column(
        Modifier.fillMaxSize().background(coreSurface(dark))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, "Refresh Telegram profile",
                    tint = coreGold(dark))
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CompactPersonAvatar(person, paths, 125)
        }
        Spacer(Modifier.height(12.dp))
        Text(person?.displayName ?: "Loading profile…",
            modifier = Modifier.fillMaxWidth(),
            color = coreFg(dark), fontWeight = FontWeight.Bold, fontSize = 23.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(
            person?.username?.takeIf { it.isNotBlank() }?.let { "@$it" }
                ?: "Your Telegram account",
            modifier = Modifier.fillMaxWidth(), color = coreSub(dark),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 13.sp
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f).clip(RoundedCornerShape(17.dp))
                .background(corePanel(dark)).clickable(onClick = onEdit)
                .padding(vertical = 15.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Edit, null, tint = coreGold(dark))
                Spacer(Modifier.height(5.dp))
                Text("Edit Info", color = coreFg(dark), fontSize = 13.sp)
            }
            Column(Modifier.weight(1f).clip(RoundedCornerShape(17.dp))
                .background(corePanel(dark)).clickable(onClick = onSettings)
                .padding(vertical = 15.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.MoreVert, null, tint = coreGold(dark))
                Spacer(Modifier.height(5.dp))
                Text("Settings", color = coreFg(dark), fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
        CompactMenuGroup(dark) {
            ProfileProperty("Telegram ID", person?.id?.toString() ?: "Loading…", dark)
            ProfileProperty("Phone number", person?.phone?.ifBlank { "Not available" }
                ?: "Loading…", dark)
            ProfileProperty("Username", person?.username?.takeIf { it.isNotBlank() }
                ?.let { "@$it" } ?: "Not set", dark)
            ProfileProperty("Bio", account.bio.ifBlank { "No bio yet" }, dark)
        }
        account.notice?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = coreSub(dark), fontSize = 12.sp)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ProfileProperty(label: String, value: String, dark: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 13.dp)) {
        Text(value, color = coreFg(dark), fontSize = 15.sp, maxLines = 3,
            overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(label, color = coreSub(dark), fontSize = 12.sp)
    }
    HorizontalDivider(
        thickness = 0.5.dp,
        color = if (dark) Color(0xFF303033) else Color(0xFFE9E9EA)
    )
}

@Composable
internal fun CompactHubHome(
    dark: Boolean,
    onDiscover: () -> Unit,
    onSaved: () -> Unit,
    onContacts: () -> Unit,
    onFolders: () -> Unit,
    onGroup: () -> Unit,
    onSecurity: () -> Unit,
    onProfile: () -> Unit,
    onLogout: () -> Unit
) {
    var logoutDialog by remember { mutableStateOf(false) }
    if (logoutDialog) AlertDialog(
        onDismissRequest = { logoutDialog = false },
        title = { Text("Log out of GLACIA?") },
        text = { Text("This signs out only the current device. Telegram messages and other devices remain unchanged.") },
        confirmButton = { TextButton(onClick = {
            logoutDialog = false
            onLogout()
        }) { Text("Log out") } },
        dismissButton = { TextButton(onClick = { logoutDialog = false }) { Text("Cancel") } }
    )
    Column(
        Modifier.fillMaxSize().background(coreSurface(dark))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 17.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text("GLACIA", color = coreFg(dark),
            fontWeight = FontWeight.Bold, fontSize = 30.sp)
        Text("Your Telegram tools", color = coreSub(dark), fontSize = 13.sp)
        CompactSectionHeading("Shortcuts", dark)
        CompactMenuGroup(dark) {
            CompactMenuRow("🔎", "Discover Telegram", "Search real public channels and users",
                dark, onDiscover)
            CompactMenuRow("🔖", "Saved Messages", "Your private Telegram cloud chat",
                dark, onSaved)
            CompactMenuRow("👥", "Contacts", "Open your synced Telegram contacts",
                dark, onContacts)
            CompactMenuRow("📁", "Chat Folders", "Manage Telegram cloud folders",
                dark, onFolders)
            CompactMenuRow("➕", "New Group or Channel", "Create communities on Telegram",
                dark, onGroup)
            CompactMenuRow("🔐", "Privacy & Sessions", "Blocked users, rules and devices",
                dark, onSecurity)
            CompactMenuRow("👤", "My Profile", "Manage your Telegram account",
                dark, onProfile)
        }
        Spacer(Modifier.height(24.dp))
        CompactMenuGroup(dark) {
            CompactMenuRow("🚪", "Log out of this device",
                "Confirmation required", dark) { logoutDialog = true }
        }
        Spacer(Modifier.height(20.dp))
        Text("GLACIA v${BuildConfig.VERSION_NAME} · Telegram TDLib",
            modifier = Modifier.fillMaxWidth(), color = coreSub(dark),
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun CompactAppearancePage(
    selected: ThemeChoice, dark: Boolean,
    onBack: () -> Unit, onSelect: (ThemeChoice) -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(coreSurface(dark))
            .verticalScroll(rememberScrollState()).padding(horizontal = 15.dp)
    ) {
        CompactPageHeader("Chat Settings", dark, onBack)
        CompactSectionHeading("Appearance", dark)
        CompactMenuGroup(dark) {
            ThemeChoice.entries.forEach { choice ->
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(choice) }
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(when (choice) {
                        ThemeChoice.SYSTEM -> "Follow system"
                        ThemeChoice.DARK -> "Dark"
                        ThemeChoice.LIGHT -> "Light"
                    }, color = coreFg(dark), modifier = Modifier.weight(1f))
                    if (choice == selected) Text("✓", color = coreGold(dark))
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Chat wallpaper and custom font controls are not yet available.",
            color = coreSub(dark), fontSize = 12.sp)
    }
}

@Composable
internal fun CompactNotificationPage(
    dark: Boolean, permissionGranted: Boolean, onBack: () -> Unit,
    onAskPermission: () -> Unit, onAndroidSettings: () -> Unit,
    onTestNotification: () -> Boolean,
    pushStatus: String
) {
    var result by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().background(coreSurface(dark))
            .verticalScroll(rememberScrollState()).padding(horizontal = 15.dp)
    ) {
        CompactPageHeader("Notifications", dark, onBack)
        CompactSectionHeading("Device notifications", dark)
        CompactMenuGroup(dark) {
            CompactMenuRow("🔔", "Android notification settings",
                "Configure alerts, sound and vibration", dark, onAndroidSettings)
            if (!permissionGranted) {
                CompactMenuRow("✅", "Allow notifications",
                    "Android permission is required", dark, onAskPermission)
            }
            CompactMenuRow("🧪", "Send test notification",
                "Check whether Android displays a test alert", dark) {
                result = if (onTestNotification()) "Test posted. Check your notification shade."
                    else "Notification blocked by Android settings."
            }
        }
        result?.let { Text(it, color = coreSub(dark),
            modifier = Modifier.padding(10.dp), fontSize = 13.sp) }
        CompactSectionHeading("Push connection", dark)
        Text(pushStatus, color = coreSub(dark), fontSize = 13.sp,
            modifier = Modifier.padding(10.dp))
    }
}
