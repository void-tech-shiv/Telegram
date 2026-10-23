package dev.glacia.app.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import dev.glacia.app.R
import dev.glacia.app.MainActivity
import org.drinkless.tdlib.TdApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Best-effort Android notifications from TDLib's notification-group API.
 * Does not display message text, account phone numbers or chat titles on lockscreen.
 * No background service/FCM is installed: events require the TDLib client to be alive.
 */
/**
 * No user content is stored in diagnostics: only counters and suppression reasons.
 * This distinguishes a sleeping TDLib client from an Android channel problem.
 */
data class NotificationHealth(
    val incomingMessages: Int = 0,
    val groupEvents: Int = 0,
    val groupAdds: Int = 0,
    val posted: Int = 0,
    val inForeground: Boolean = true,
    val lastDecision: String = "No incoming message received yet"
)

class GlaciaNotifications(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    @Volatile private var foreground = true
    @Volatile private var visibleChatId: Long? = null
    @Volatile private var resolveChatTitle: (Long) -> String? = { null }
    @Volatile private var isChatMuted: (Long) -> Boolean = { false }
    private val groupLock = Any()
    private val activeGroups = mutableMapOf<Int, Long>()
    fun setTitleResolver(resolver: (Long) -> String?) { resolveChatTitle = resolver }
    fun setMuteResolver(resolver: (Long) -> Boolean) { isChatMuted = resolver }
    private val healthLock = Any()
    private val _health = MutableStateFlow(NotificationHealth())
    val health = _health.asStateFlow()

    private fun record(update: (NotificationHealth) -> NotificationHealth) {
        synchronized(healthLock) { _health.value = update(_health.value) }
    }


    companion object {
        const val EXTRA_OPEN_CHAT_ID = "dev.glacia.app.OPEN_CHAT_ID"
        private const val SUMMARY_ID = -3657
        private const val GROUP_ALL = "dev.glacia.app.conversations"
        // The old "glacia_messages" channel has DEFAULT importance; Android
        // will not let an app increase an existing channel's importance.
        // Use a new channel ID for opt-in heads-up notifications.
        private const val HEADS_UP = "glacia_messages_heads_up"
        private const val SILENT = "glacia_messages_silent"
    }

    init {
        manager.createNotificationChannel(
            NotificationChannel(HEADS_UP, "Telegram message banners", NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    description = "New Telegram messages with optional heads-up banners"
                    lockscreenVisibility = Notification.VISIBILITY_SECRET
                }
        )
        manager.createNotificationChannel(
            NotificationChannel(SILENT, "Silent Telegram messages", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Quiet alerts selected by Telegram"; lockscreenVisibility = Notification.VISIBILITY_SECRET }
        )
    }

    fun setForeground(isForeground: Boolean) {
        foreground = isForeground
        record { it.copy(inForeground = isForeground) }
    }

    /** A visible conversation already displays new messages, so avoid double alerts. */
    fun setVisibleChat(chatId: Long?) {
        visibleChatId = chatId
    }

    /** A deliberate local test to distinguish Android permission/channel issues
     *  from Telegram/TDLib notification delivery. Does not send a Telegram message.
     */
    fun sendTestNotification(): Boolean {
        if (!hasPermission()) return false
        val launch = Intent(app, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pending = PendingIntent.getActivity(
            app, 0, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val test = Notification.Builder(app, HEADS_UP)
            .setSmallIcon(R.drawable.ic_glacia_notification)
            .setContentTitle("GLACIA · Test notification")
            .setContentText("Android notifications are enabled.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .build()
        // Reserved negative ID: TDLib group IDs are positive.
        manager.notify(-3650, test)
        return true
    }

    fun areNotificationsAllowed(): Boolean = hasPermission()

    fun onUpdate(update: TdApi.Object) {
        if (update is TdApi.UpdateNewMessage && !update.message.isOutgoing) {
            record { it.copy(
                incomingMessages = it.incomingMessages + 1,
                lastDecision = "Incoming message received; waiting for TDLib notification group"
            ) }
            return
        }
        if (update !is TdApi.UpdateNotificationGroup) return
        val additions = update.addedNotifications.size
        record { it.copy(
            groupEvents = it.groupEvents + 1,
            groupAdds = it.groupAdds + additions
        ) }
        val id = update.notificationGroupId
        if (update.totalCount <= 0) {
            manager.cancel(NotificationPolicy.notificationTag(update.chatId, id), id)
            synchronized(groupLock) {
                activeGroups.remove(id)
                refreshSummary()
            }
            record { it.copy(lastDecision = "Telegram removed/cleared a notification group") }
            return
        }
        // Respect Telegram notification policy and its cross-device delay.
        if (additions == 0) {
            record { it.copy(lastDecision = "TDLib group updated without new notifications") }
            return
        }
        if (!NotificationPolicy.shouldShow(
                foreground, visibleChatId, update.chatId, isChatMuted(update.chatId)
            )) {
            record {
                it.copy(lastDecision = if (isChatMuted(update.chatId))
                    "Chat muted: alert suppressed"
                else "Open conversation: duplicate Android alert suppressed")
            }
            return
        }
        if (!hasPermission()) {
            record { it.copy(lastDecision = "Android permission or channel disabled") }
            return
        }
        val quiet = update.notificationSoundId == 0L
        val channel = if (quiet) SILENT else HEADS_UP
        if (manager.getNotificationChannel(channel)?.importance ==
            NotificationManager.IMPORTANCE_NONE) {
            record { it.copy(lastDecision = "Android message channel disabled: $channel") }
            return
        }
        val launch = Intent(app, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_OPEN_CHAT_ID, update.chatId)
        }
        // Different requestCode for every Telegram group keeps PendingIntent extras
        // separate when simultaneous chat notifications exist.
        val pendingIntent = PendingIntent.getActivity(
            app, id, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val count = update.totalCount
        val chatName = resolveChatTitle(update.chatId)
            ?.takeIf { it.isNotBlank() }?.take(60)
        val notification = Notification.Builder(app, channel)
            .setSmallIcon(R.drawable.ic_glacia_notification)
            .setContentTitle(chatName ?: "GLACIA · New Telegram message")
            .setContentText(if (count == 1) "You have 1 unread notification"
                else "You have $count unread notifications")
            .setContentIntent(pendingIntent)
            .setGroup(GROUP_ALL)
            .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setVisibility(Notification.VISIBILITY_SECRET)
            // A later message in the same Telegram group should be able to
            // generate another heads-up banner instead of being permanently quiet.
            .setOnlyAlertOnce(false)
            .build()
        try {
            // One active notification per TDLib notification group; multiple chats
            // remain independent child notifications beneath an Android summary.
            manager.notify(NotificationPolicy.notificationTag(update.chatId, id), id, notification)
            synchronized(groupLock) {
                activeGroups[id] = update.chatId
                refreshSummary()
            }
            record { it.copy(
                posted = it.posted + 1,
                lastDecision = if (quiet)
                    "Posted a quiet notification (no heads-up for silent messages)"
                else "Posted a Telegram message notification (heads-up channel)"
            ) }
        } catch (_: SecurityException) {
            record { it.copy(lastDecision = "Android rejected notification permission") }
        } catch (_: RuntimeException) {
            record { it.copy(lastDecision = "Android notification service rejected posting") }
        }
    }

    /** Called with groupLock held; avoid extra alerts from the summary row. */
    private fun refreshSummary() {
        if (activeGroups.size < 2) {
            manager.cancel(SUMMARY_ID)
            return
        }
        val chatCount = activeGroups.values.distinct().size
        val summary = Notification.Builder(app, SILENT)
            .setSmallIcon(R.drawable.ic_glacia_notification)
            .setContentTitle("GLACIA · Telegram")
            .setContentText("$chatCount conversations have new notifications")
            .setGroup(GROUP_ALL)
            .setGroupSummary(true)
            .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .build()
        manager.notify(SUMMARY_ID, summary)
    }

    private fun hasPermission(): Boolean =
        manager.areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 ||
                app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED)
}
