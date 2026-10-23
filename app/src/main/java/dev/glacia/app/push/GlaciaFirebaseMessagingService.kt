package dev.glacia.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dev.glacia.app.BuildConfig
import org.json.JSONObject

/**
 * Optional *data-only* FCM entrypoint. Telegram's encrypted push payload goes to
 * TDLib; never log, export or display unverified push data.
 *
 * Android force-stop blocks FCM until the user launches the application again.
 */
class GlaciaFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (!BuildConfig.FCM_CONFIGURED || token.isBlank()) return
        getSharedPreferences("glacia_push", MODE_PRIVATE)
            .edit().putString("token", token).apply()
        GlaciaPushRuntime.registerTokenIfRunning(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        if (!BuildConfig.FCM_CONFIGURED || message.data.isEmpty()) return
        // FCM data-only messages are delivered to this service even when the UI
        // process has to be started. TDLib validates/decrypts the payload.
        val raw = JSONObject(message.data as Map<*, *>).toString()
        if (raw.length > 16_384) return
        GlaciaPushRuntime.receivePush(applicationContext, raw)
    }
}
