package dev.glacia.app.calls

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import dev.glacia.app.BuildConfig
import dev.glacia.app.MainActivity
import dev.glacia.app.R
import dev.glacia.app.data.NativeCallUi
import dev.glacia.app.data.TdlibSession
import dev.glacia.app.push.GlaciaPushRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Phase 3.11 foreground ownership ONLY for calls that the user starts/answers
 * while GLACIA is visible. A microphone/camera service cannot be started from
 * an arbitrary background TDLib update on Android 14+.
 *
 * START_NOT_STICKY: never resurrect capture automatically after process death.
 * This service and the Activity share ONE TDLib client in GlaciaPushRuntime.
 */
class GlaciaCallService : Service() {
    companion object {
        private const val CHANNEL = "glacia_ongoing_calls_v1"
        private const val NOTIFICATION_ID = 8311
        private const val ACTION_START = "dev.glacia.app.call.START"
        private const val ACTION_HANGUP = "dev.glacia.app.call.HANGUP"
        private const val EXTRA_VIDEO = "video"
        private const val TAG = "GLACIA-CallSvc"

        /** Called only from a visible Activity after runtime permission checks. */
        fun startFromVisibleUi(context: Context, video: Boolean): Boolean {
            if (!BuildConfig.EXPERIMENTAL_NATIVE_CALLS) return false
            val intent = Intent(context, GlaciaCallService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_VIDEO, video)
            }
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else context.startService(intent)
                true
            } catch (e: RuntimeException) {
                // Android 12+ may reject foreground starts. No key/phone data in logs.
                Log.w(TAG, "foreground_service_start_denied type=${e.javaClass.simpleName}")
                false
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main.immediate + job)
    private lateinit var session: TdlibSession
    private lateinit var manager: NotificationManager
    private var foregroundStarted = false
    private var observedCall = false
    private var lastVideo = false
    private var callId: Int? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        session = GlaciaPushRuntime.attachCallService(applicationContext)
        manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL, "Ongoing GLACIA calls", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent status while an active Telegram call uses your microphone/camera"
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                setSound(null, null)
            }
        )
        session.start()
        scope.launch {
            session.nativeCall.collect { call ->
                if (call != null) {
                    observedCall = true
                    callId = call.callId
                    lastVideo = call.video
                    if (foregroundStarted) showOngoing(call.video, call.phase)
                } else if (observedCall) {
                    // TDLib terminal update ends actual capture; release owner.
                    stopSelf()
                }
            }
        }
        // Outgoing CreateCall may be waiting on the network. No indefinite FGS
        // if the remote never receives a call or the user leaves immediately.
        mainHandler.postDelayed({
            if (!observedCall) {
                Log.i(TAG, "call_start_timeout_without_tdlib_call")
                stopSelf()
            }
        }, 45_000L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!BuildConfig.EXPERIMENTAL_NATIVE_CALLS) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_HANGUP) {
            session.hangupNativeCall()
            // If TDLib never acknowledges the hangup, do not keep running forever.
            mainHandler.postDelayed({ stopSelf() }, 15_000L)
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) {
            stopSelf()
            return START_NOT_STICKY
        }
        val video = intent.getBooleanExtra(EXTRA_VIDEO, false)
        if (!hasCapturePermissions(video)) {
            Log.w(TAG, "missing_call_runtime_permission")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!foregroundStarted) {
            try {
                val notification = buildNotification(video, "Preparing secure call")
                val serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    if (video) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, serviceType)
                } else startForeground(NOTIFICATION_ID, notification)
                foregroundStarted = true
                lastVideo = video
            } catch (error: RuntimeException) {
                // Covers SecurityException, MissingForegroundServiceTypeException,
                // and ForegroundServiceStartNotAllowedException on newer Android.
                Log.w(TAG, "foreground_service_activation_failed type=${error.javaClass.simpleName}")
                stopSelf()
            }
        } else if (video && !lastVideo) {
            // A voice FGS doesn't have CAMERA type. Only upgrade while the
            // Activity is visible with CAMERA permission granted.
            if (hasCapturePermissions(true) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    startForeground(
                        NOTIFICATION_ID, buildNotification(true, "Preparing video call"),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                    )
                    lastVideo = true
                } catch (_: RuntimeException) {
                    Log.w(TAG, "camera_service_type_upgrade_rejected")
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun hasCapturePermissions(video: Boolean): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            (!video || checkSelfPermission(Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED)

    private fun showOngoing(video: Boolean, phase: String) {
        val current = if (phase == "Connected") "Connected · Tap to return"
            else if (phase == "Incoming") "Incoming call · Open GLACIA to answer"
            else "Telegram call · $phase"
        try {
            manager.notify(NOTIFICATION_ID, buildNotification(video, current))
        } catch (_: RuntimeException) {
            // Android 13 POST_NOTIFICATIONS may be denied; Android should still
            // represent an active foreground service in Task Manager.
            Log.w(TAG, "ongoing_notification_update_rejected")
        }
    }

    private fun buildNotification(video: Boolean, body: String): Notification {
        val open = PendingIntent.getActivity(
            this, 8311,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val hangup = PendingIntent.getService(
            this, 8312,
            Intent(this, GlaciaCallService::class.java).setAction(ACTION_HANGUP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_glacia_notification)
            .setContentTitle(if (video) "GLACIA · Video call" else "GLACIA · Voice call")
            .setContentText(body)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel, "Hang up", hangup
            ).build())
            .setCategory(Notification.CATEGORY_CALL)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        if (foregroundStarted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
        manager.cancel(NOTIFICATION_ID)
        GlaciaPushRuntime.detachCallService(session)
        super.onDestroy()
    }
}
