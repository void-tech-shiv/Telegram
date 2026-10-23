package dev.glacia.app.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/**
 * EXTERNAL CALL HANDOFF, not an in-app VoIP implementation.
 * TDLib call signaling alone can't capture/encode/decrypt/play real-time calls:
 * a tested native WebRTC/tgcalls media engine is required.
 *
 * Open a private contact's official Telegram profile, where the user may initiate
 * an audio/video call using the installed Telegram client.
 */
class TelegramCallHandoff(
    private val context: Context,
    private val client: () -> Client?,
    private val error: (Long, String) -> Unit
) {
    fun openContact(chatId: Long) {
        val active = client() ?: run {
            error(chatId, "Telegram is offline; cannot open contact profile.")
            return
        }
        active.send(TdApi.GetChat(chatId)) { chat ->
            val type = (chat as? TdApi.Chat)?.type as? TdApi.ChatTypePrivate
            if (type == null) {
                error(chatId, "Calls can only be opened for individual Telegram users.")
                return@send
            }
            active.send(TdApi.GetUser(type.userId)) { response ->
                val user = response as? TdApi.User
                val username = user?.usernames?.activeUsernames?.firstOrNull()
                if (username.isNullOrBlank() || !username.matches(Regex("[A-Za-z0-9_]{5,32}"))) {
                    error(
                        chatId,
                        "This contact has no public username. In-app calls need a native VoIP engine."
                    )
                    return@send
                }
                val uri = Uri.parse("https://t.me/$username?profile")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage("org.telegram.messenger")
                }
                try {
                    context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    } catch (_: Exception) {
                        error(chatId, "Install official Telegram to open this contact for calling.")
                    }
                } catch (_: SecurityException) {
                    error(chatId, "Android blocked opening Telegram.")
                }
            }
        }
    }
}
