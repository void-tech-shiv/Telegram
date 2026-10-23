package dev.glacia.app.calls

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
import dev.glacia.app.R
import org.drinkless.tdlib.TdApi

/**
 * Best-effort heads-up alert while a live TDLib instance sees UpdateCall.
 *
 * This is NOT a wakeup/push implementation. Android may kill the TDLib process;
 * without encrypted push + platform-approved behavior, background rings are not
 * guaranteed. No Full-Screen Intent permission and no automatic background
 * microphone/camera start are attempted here.
 */
class IncomingCallAlerts(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val lock = Any()
    private var foreground = true
    private var activeCallId: Int? = null

    companion object {
        private const val CHANNEL = "glacia_incoming_calls_v1"
        private const val ID = 8312
    }

    init {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL, "Incoming Telegram calls", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Calls observed by GLACIA while Telegram is connected"
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
        )
    }

    fun setForeground(isForeground: Boolean) {
        synchronized(lock) {
            foreground = isForeground
            if (isForeground) {
                activeCallId = null
                manager.cancel(ID)
            }
        }
    }

    fun onUpdate(update: TdApi.Object) {
        val call = (update as? TdApi.UpdateCall)?.call ?: return
        if (call.id <= 0) return
        synchronized(lock) {
            val incoming = !call.isOutgoing && call.state is TdApi.CallStatePending
            if (!incoming) {
                if (activeCallId == call.id) {
                    activeCallId = null
                    manager.cancel(ID)
                }
                return
            }
            if (foreground) return
            if (activeCallId == call.id) return
            if (!manager.areNotificationsEnabled() ||
                (Build.VERSION.SDK_INT >= 33 &&
                    app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED)) return

            val launch = Intent(app, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val pending = PendingIntent.getActivity(
                app, ID, launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val channel = manager.getNotificationChannel(CHANNEL)
            if (channel?.importance == NotificationManager.IMPORTANCE_NONE) return
            val notification = Notification.Builder(app, CHANNEL)
                .setSmallIcon(R.drawable.ic_glacia_notification)
                .setContentTitle(
                    if (call.isVideo) "GLACIA · Incoming video call"
                    else "GLACIA · Incoming voice call"
                )
                .setContentText("Tap to open GLACIA and answer")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .setCategory(Notification.CATEGORY_CALL)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .setTimeoutAfter(45_000L)
                .build()
            try {
                manager.notify(ID, notification)
                activeCallId = call.id
            } catch (_: SecurityException) {
                // Disabled notification permission: never fall back to
                // background-starting a camera/microphone Activity/service.
            } catch (_: RuntimeException) {
                // OEM notification restrictions, no process crash.
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            activeCallId = null
            manager.cancel(ID)
        }
    }
}
