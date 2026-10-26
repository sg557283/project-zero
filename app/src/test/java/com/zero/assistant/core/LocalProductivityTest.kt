package com.zero.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalProductivityTest {
    private class MemoryNotes : NoteStorage {
        var values = emptyList<LocalNote>()
        override fun read() = values
        override fun write(notes: List<LocalNote>) { values = notes.toList() }
    }

    private class MemoryShortcuts : ShortcutStorage {
        var values = emptyList<UserShortcut>()
        override fun readShortcuts() = values
        override fun writeShortcuts(shortcuts: List<UserShortcut>) { values = shortcuts.toList() }
    }

    @Test
    fun notesPersistAndValidateContent() {
        val storage = MemoryNotes()
        val repository = NoteRepository(storage)
        repository.save(LocalNote(id = "n1", text = "  Buy milk  ", updatedAt = 4))
        assertEquals("Buy milk", repository.all().single().text)
        repository.delete("n1")
        assertTrue(repository.all().isEmpty())
        assertThrows(IllegalArgumentException::class.java) { repository.save(LocalNote(text = " ")) }
    }

    @Test
    fun shortcutsPersistDuplicateToggleAndDelete() {
        val storage = MemoryShortcuts()
        val repository = ShortcutRepository(storage)
        val shortcut = UserShortcut(id = "s1", name = "Morning", actions = listOf(ShortcutAction(ShortcutActionType.ShowBatteryStatus)))
        repository.save(shortcut)
        assertEquals(setOf("Morning"), repository.actionNames())
        val duplicate = repository.duplicate("s1", "Morning Copy")
        assertFalse(duplicate.enabled)
        assertEquals(2, repository.all().size)
        repository.setEnabled("s1", false)
        assertTrue(repository.actionNames().isEmpty())
        repository.delete(duplicate.id)
        assertEquals(1, repository.all().size)
    }

    @Test
    fun shortcutActionsAreValidatedAndOrdered() {
        val storage = MemoryShortcuts()
        val repository = ShortcutRepository(storage)
        val actions = listOf(
            ShortcutAction(ShortcutActionType.OpenApp, "youtube"),
            ShortcutAction(ShortcutActionType.TurnFlashlightOff),
            ShortcutAction(ShortcutActionType.SpeakText, "Ready"),
        )
        repository.save(UserShortcut(id = "ordered", name = "Finish", actions = actions))
        assertEquals(actions, repository.all().single().actions)
        assertThrows(IllegalArgumentException::class.java) {
            repository.save(UserShortcut(name = "Wi-Fi magic", actions = listOf(ShortcutAction(ShortcutActionType.OpenSettings, "toggle_wifi"))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShortcutValidator.validate(UserShortcut(name = "Empty", actions = emptyList()))
        }
    }
}