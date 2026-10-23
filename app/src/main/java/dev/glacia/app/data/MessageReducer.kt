package dev.glacia.app.data

/**
 * Pure message-list reducer: merges pages and asynchronous TDLib updates, deduplicates
 * acknowledgements, and ensures stable chronological order without storing plaintext logs.
 */
object MessageReducer {
    fun merge(
        existing: List<Message>,
        incoming: List<Message>,
        replacedId: Long? = null,
        maximum: Int = 300
    ): List<Message> {
        val byId = existing.associateBy { it.id }.toMutableMap()
        if (replacedId != null) byId.remove(replacedId)
        for (message in incoming) byId[message.id] = message
        return byId.values.sortedWith(
            compareBy<Message> { it.date }.thenBy { it.id }
        ).takeLast(maximum)
    }

    fun delete(existing: List<Message>, ids: Collection<Long>): List<Message> {
        val toRemove = ids.toHashSet()
        return existing.filterNot { it.id in toRemove }
    }
}
