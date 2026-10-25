package com.zero.assistant.core

data class HistoryEntry(val command: String, val response: String, val timestamp: Long)

interface HistoryStorage {
    fun read(): List<HistoryEntry>
    fun write(entries: List<HistoryEntry>)
}

class CommandHistory(private val storage: HistoryStorage) {
    fun recent(limit: Int = 50): List<HistoryEntry> = storage.read().take(limit)

    fun add(command: String, response: String, timestamp: Long = System.currentTimeMillis()) {
        val current = storage.read().toMutableList()
        current.add(0, HistoryEntry(command, response, timestamp))
        storage.write(current.take(50))
    }

    fun clear() = storage.write(emptyList())
}