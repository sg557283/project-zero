package com.zero.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandHistoryTest {
    private class MemoryStorage : HistoryStorage {
        private var entries = emptyList<HistoryEntry>()
        override fun read() = entries
        override fun write(entries: List<HistoryEntry>) { this.entries = entries }
    }

    @Test fun storesNewestFirstAndClears() {
        val history = CommandHistory(MemoryStorage())
        history.add("open YouTube", "Opening YouTube.", 1L)
        history.add("what time is it", "It's 9:00.", 2L)

        assertEquals(listOf("what time is it", "open YouTube"), history.recent().map { it.command })
        history.clear()
        assertEquals(emptyList<HistoryEntry>(), history.recent())
    }

    @Test fun keepsAtMostFiftyEntries() {
        val history = CommandHistory(MemoryStorage())
        repeat(55) { history.add("command $it", "response", it.toLong()) }
        assertEquals(50, history.recent().size)
        assertEquals("command 54", history.recent().first().command)
    }
}