package dev.glacia.app.data

/** Small, testable policy for GLACIA notifications (never bypass TDLib mutes). */
object NotificationPolicy {
    fun shouldShow(
        appForeground: Boolean,
        visibleChatId: Long?,
        incomingChatId: Long,
        explicitlyMuted: Boolean
    ): Boolean =
        !explicitlyMuted && !(appForeground && visibleChatId == incomingChatId)

    fun groupKey(chatId: Long): String = "glacia.chat.$chatId"
    fun notificationTag(chatId: Long, groupId: Int): String = "chat:$chatId:group:$groupId"
}
