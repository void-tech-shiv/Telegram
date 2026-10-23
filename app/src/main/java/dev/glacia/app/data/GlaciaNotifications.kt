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
    private val healthLock = Any()
    private val _health = MutableStateFlow(NotificationHealth())
    val health = _health.asStateFlow()

    private fun record(update: (NotificationHealth) -> NotificationHealth) {
        synchronized(healthLock) { _health.value = update(_health.value) }
    }


    companion object {
        private const val MESSAGES = "glacia_messages"
        private const val SILENT = "glacia_messages_silent"
    }

    init {
        manager.createNotificationChannel(
            NotificationChannel(MESSAGES, "Telegram messages", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Privacy-preserving Telegram alerts"; lockscreenVisibility = Notification.VISIBILITY_SECRET }
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
        val test = Notification.Builder(app, MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
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
                lastDecision = if (foreground)
                    "Incoming message seen while app foregrounded (OS alert suppressed)"
                else "Incoming message seen in background; waiting for TDLib notification"
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
            manager.cancel(id)
            record { it.copy(lastDecision = "Telegram removed/cleared a notification group") }
            return
        }
        // Respect Telegram notification policy and its cross-device delay.
        if (additions == 0) {
            record { it.copy(lastDecision = "TDLib group updated without new notifications") }
            return
        }
        if (foreground) {
            record { it.copy(lastDecision = "App foregrounded: Android alert intentionally skipped") }
            return
        }
        if (!hasPermission()) {
            record { it.copy(lastDecision = "Android permission or channel disabled") }
            return
        }
        val quiet = update.notificationSoundId == 0L
        val channel = if (quiet) SILENT else MESSAGES
        if (manager.getNotificationChannel(channel)?.importance ==
            NotificationManager.IMPORTANCE_NONE) {
            record { it.copy(lastDecision = "Android message channel disabled: $channel") }
            return
        }
        val launch = Intent(app, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            app, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val count = update.totalCount
        val notification = Notification.Builder(app, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("GLACIA · New Telegram message")
            .setContentText(if (count == 1) "You have 1 unread notification"
                else "You have $count unread notifications")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setOnlyAlertOnce(true)
            .build()
        try {
            manager.notify(id, notification)
            record { it.copy(
                posted = it.posted + 1,
                lastDecision = if (quiet)
                    "Posted a quiet notification (check Silent channel in shade)"
                else "Posted an Android message notification"
            ) }
        } catch (_: SecurityException) {
            record { it.copy(lastDecision = "Android rejected notification permission") }
        } catch (_: RuntimeException) {
            record { it.copy(lastDecision = "Android notification service rejected posting") }
        }
    }

    private fun hasPermission(): Boolean =
        manager.areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 ||
                app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED)
}
