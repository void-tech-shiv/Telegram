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

/**
 * Best-effort Android notifications from TDLib's notification-group API.
 * Does not display message text, account phone numbers or chat titles on lockscreen.
 * No background service/FCM is installed: events require the TDLib client to be alive.
 */
class GlaciaNotifications(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    @Volatile private var foreground = true

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

    fun setForeground(isForeground: Boolean) { foreground = isForeground }

    fun onUpdate(update: TdApi.Object) {
        if (update !is TdApi.UpdateNotificationGroup) return
        val id = update.notificationGroupId
        if (update.totalCount <= 0) {
            manager.cancel(id)
            return
        }
        // TDLib handles mutes, notification-group delays, deduplication and removal.
        // Never turn an old startup snapshot into a new Android notification.
        if (update.addedNotifications.isEmpty() || foreground || !hasPermission()) return
        val quiet = update.notificationSoundId == 0L
        val launch = Intent(app, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            app, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val count = update.totalCount
        val notification = Notification.Builder(app, if (quiet) SILENT else MESSAGES)
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
        manager.notify(id, notification)
    }

    private fun hasPermission(): Boolean =
        manager.areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 ||
                app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED)
}
