package dev.glacia.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.glacia.app.data.GlaciaNotifications
import dev.glacia.app.ui.GlaciaRoot
import kotlinx.coroutines.flow.MutableStateFlow

/** One Activity: notification taps can route to the right chat even on cold start. */
class MainActivity : ComponentActivity() {
    private val notificationChatId = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readChatIntent(intent)
        setContent {
            val target by notificationChatId.collectAsState()
            GlaciaRoot(
                notificationChatId = target,
                onNotificationChatHandled = { handledId ->
                    if (notificationChatId.value == handledId) notificationChatId.value = null
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readChatIntent(intent)
    }

    private fun readChatIntent(incoming: Intent?) {
        val chatId = incoming?.getLongExtra(GlaciaNotifications.EXTRA_OPEN_CHAT_ID, 0L) ?: 0L
        if (chatId != 0L) notificationChatId.value = chatId
    }
}
