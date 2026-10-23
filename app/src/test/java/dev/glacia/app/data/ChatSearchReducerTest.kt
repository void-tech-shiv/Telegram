package dev.glacia.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSearchReducerTest {
    private fun hit(id: Long) = ChatSearchHit(id, "Preview", 100, false)

    @Test fun replacingQueryResultsDiscardsPreviousHits() {
        val old = ChatSearchState(query = "one", loading = true, hits = listOf(hit(10)))
        val actual = ChatSearchReducer.merge(old, listOf(hit(11)), 1, 0, false)
        assertEquals(listOf(11L), actual.hits.map { it.id })
        assertFalse(actual.loading)
    }

    @Test fun pagedResultsDeduplicateAndRetainOrder() {
        val old = ChatSearchState(query = "test", loading = true, hits = listOf(hit(9), hit(8)))
        val actual = ChatSearchReducer.merge(
            old, listOf(hit(8), hit(7)), 9, 7, true
        )
        assertEquals(listOf(9L, 8L, 7L), actual.hits.map { it.id })
        assertEquals(7L, actual.nextFromMessageId)
        assertEquals(9, actual.total)
        assertNull(actual.error)
    }

    @Test fun boundedResultListDoesNotGrowWithoutLimit() {
        val old = ChatSearchState(query = "large", hits = (1L..190L).map(::hit))
        val incoming = (191L..290L).map(::hit)
        val actual = ChatSearchReducer.merge(old, incoming, 10_000, 291L, true)
        assertEquals(200, actual.hits.size)
        assertTrue(actual.hits.last().id <= 200L)
    }
}
