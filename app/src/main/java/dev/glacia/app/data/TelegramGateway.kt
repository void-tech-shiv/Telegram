package dev.glacia.app.data

import kotlinx.coroutines.flow.StateFlow

sealed interface TelegramConnection {
    data object NotConfigured : TelegramConnection
    data object Connecting : TelegramConnection
    data object Authorized : TelegramConnection
    data class Failed(val reason: String) : TelegramConnection
}

/**
 * Phase-2 boundary for TDLib integration.
 * There is intentionally NO real implementation and NO credentials in Phase 1.
 * The UI uses DemoChatRepository so accidental mock messages cannot reach Telegram.
 */
interface TelegramGateway {
    val connection: StateFlow<TelegramConnection>
    suspend fun start()
    suspend fun sendMessage(chatId: Long, text: String): Result<Unit>
    suspend fun close()
}
