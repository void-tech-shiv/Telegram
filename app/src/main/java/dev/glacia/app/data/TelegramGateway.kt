package dev.glacia.app.data

import kotlinx.coroutines.flow.StateFlow

/**
 * UI-facing API. TDLib is only accessed by TdlibTelegramGateway.
 * No demo messages ever travel through this interface.
 */
sealed interface LoginStage {
    data object Unconfigured : LoginStage
    data object Starting : LoginStage
    data object Phone : LoginStage
    data object Code : LoginStage
    data object Password : LoginStage
    data object EmailAddress : LoginStage
    data object EmailCode : LoginStage
    data object Register : LoginStage
    data class ConfirmOtherDevice(val link: String) : LoginStage
    data object Ready : LoginStage
    data class Unavailable(val reason: String) : LoginStage
}

interface TelegramGateway {
    val stage: StateFlow<LoginStage>
    val chats: StateFlow<List<Chat>>
    val histories: StateFlow<Map<Long, List<Message>>>
    val error: StateFlow<String?>

    fun start()
    suspend fun sendPhone(phone: String)
    suspend fun sendCode(code: String)
    suspend fun sendPassword(password: String)
    suspend fun sendEmail(email: String)
    suspend fun sendEmailCode(code: String)
    suspend fun register(first: String, last: String)
    suspend fun refreshChats()
    suspend fun openChat(id: Long)
    suspend fun sendMessage(chatId: Long, text: String)
    suspend fun logOut()
    fun clearError()
    fun close()
}
