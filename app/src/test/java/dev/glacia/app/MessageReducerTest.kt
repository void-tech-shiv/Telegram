package dev.glacia.app

import dev.glacia.app.data.Message
import dev.glacia.app.data.MessageReducer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageReducerTest {
    private fun msg(id: Long, date: Int, text: String = "message") =
        Message(id, 90L, text, "12:00", false, date)

    @Test fun historyAndLiveUpdatesAreOrderedAndDeduplicated() {
        val initial = listOf(msg(3L, 30), msg(4L, 40))
        val page = listOf(msg(2L, 20), msg(3L, 30), msg(1L, 10))
        val merged = MessageReducer.merge(initial, page)
        assertEquals(listOf(1L, 2L, 3L, 4L), merged.map { it.id })
    }

    @Test fun acknowledgementReplacesTemporaryMessageId() {
        val initial = listOf(msg(-5L, 50, "sending"))
        val confirmed = MessageReducer.merge(initial, listOf(msg(8L, 50, "sent")), -5L)
        assertEquals(listOf(8L), confirmed.map { it.id })
        assertEquals("sent", confirmed.single().text)
    }

    @Test fun editsAndDeletesDoNotDuplicateOrEraseUnrelatedMessages() {
        val updated = MessageReducer.merge(
            listOf(msg(1, 1, "old"), msg(2, 2)),
            listOf(msg(1, 1, "edited"))
        )
        assertEquals("edited", updated.first().text)
        val remaining = MessageReducer.delete(updated, listOf(2L))
        assertEquals(1, remaining.size)
        assertFalse(remaining.any { it.id == 2L })
        assertTrue(remaining.any { it.id == 1L })
    }

    @Test fun listIsBounded() {
        val many = (1..400).map { msg(it.toLong(), it) }
        val bounded = MessageReducer.merge(emptyList(), many, maximum = 300)
        assertEquals(300, bounded.size)
        assertEquals(101L, bounded.first().id)
    }
}
