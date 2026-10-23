package dev.glacia.app.data

/**
 * Fail closed on locally-known service/call entries and unsent placeholders.
 * For normal messages the Telegram server is authoritative:
 * GetMessageAvailableReactions decides which emojis are offered.
 */
object MessageReactionPolicy {
    fun eligibleForLookup(message: Message?): Boolean =
        message != null &&
            message.id > 0L &&
            !message.sending &&
            !message.failed &&
            message.mediaType != "call"

    fun shownChoices(
        allowed: List<String>,
        chosen: List<MessageReactionSummary>
    ): List<String> = (
        allowed + chosen.filter { it.chosen }.map { it.emoji }
    ).filter { it.isNotBlank() }.distinct().take(16)

    fun maySubmit(
        message: Message?,
        emoji: String,
        chosen: Boolean,
        allowed: List<String>
    ): Boolean = eligibleForLookup(message) &&
        emoji.isNotBlank() &&
        (if (chosen) message!!.reactions.any { it.emoji == emoji && it.chosen }
         else emoji in allowed)
}
