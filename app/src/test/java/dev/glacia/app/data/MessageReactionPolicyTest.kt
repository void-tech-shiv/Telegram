package dev.glacia.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageReactionPolicyTest {
    private fun message(type: String? = null, id: Long = 5, sending: Boolean = false) =
        Message(id, 1L, "📹 Video call", "12:04", false,
            mediaType = type, sending = sending)

    @Test fun callHistoryEntryIsNotReactionEligible() {
        assertFalse(MessageReactionPolicy.eligibleForLookup(message("call")))
        assertFalse(MessageReactionPolicy.maySubmit(message("call"), "👍", false, listOf("👍")))
    }

    @Test fun sendingAndInvalidMessagesCannotBeReactedTo() {
        assertFalse(MessageReactionPolicy.eligibleForLookup(message(sending = true)))
        assertFalse(MessageReactionPolicy.eligibleForLookup(message(id = 0L)))
        assertFalse(MessageReactionPolicy.eligibleForLookup(null))
    }

    @Test fun onlyServerAllowedReactionsCanBeAdded() {
        val text = message()
        assertTrue(MessageReactionPolicy.maySubmit(text, "❤️", false, listOf("❤️")))
        assertFalse(MessageReactionPolicy.maySubmit(text, "😂", false, listOf("❤️")))
    }

    @Test fun previouslyChosenReactionCanBeRemoved() {
        val text = message().copy(reactions = listOf(MessageReactionSummary("😮", 1, true)))
        assertTrue(MessageReactionPolicy.maySubmit(text, "😮", true, emptyList()))
        assertFalse(MessageReactionPolicy.maySubmit(text, "😮", false, emptyList()))
        assertEquals(listOf("👍", "😮"), MessageReactionPolicy.shownChoices(
            listOf("👍", "👍"),
            text.reactions
        ))
    }
}
