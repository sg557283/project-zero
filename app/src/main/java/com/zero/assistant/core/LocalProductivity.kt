package com.zero.assistant.core

import java.util.UUID

data class LocalNote(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

interface NoteStorage {
    fun read(): List<LocalNote>
    fun write(notes: List<LocalNote>)
}

class NoteRepository(private val storage: NoteStorage) {
    fun all(): List<LocalNote> = storage.read().sortedByDescending { it.updatedAt }

    fun save(note: LocalNote) {
        require(note.text.isNotBlank()) { "Note cannot be empty" }
        require(note.text.length <= MAX_NOTE_LENGTH) { "Note is too long" }
        storage.write(storage.read().filterNot { it.id == note.id } + note.copy(text = note.text.trim()))
    }

    fun delete(id: String) = storage.write(storage.read().filterNot { it.id == id })

    companion object {
        const val MAX_NOTE_LENGTH = 2_000
    }
}

enum class ShortcutActionType {
    OpenApp,
    OpenSettings,
    ShowTodayTasks,
    ShowBatteryStatus,
    TurnFlashlightOn,
    TurnFlashlightOff,
    ShowReminders,
    ShowNotes,
    SpeakText,
}

data class ShortcutAction(val type: ShortcutActionType, val value: String = "")

data class UserShortcut(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val actions: List<ShortcutAction>,
    val enabled: Boolean = true,
)

interface ShortcutStorage {
    fun readShortcuts(): List<UserShortcut>
    fun writeShortcuts(shortcuts: List<UserShortcut>)
}

object ShortcutValidator {
    private val allowedApps = setOf("youtube", "whatsapp", "camera", "settings")
    private val allowedSettings = setOf("wifi", "bluetooth", "sound", "display", "accessibility", "text_to_speech")

    fun validate(shortcut: UserShortcut) {
        require(shortcut.name.isNotBlank() && shortcut.name.length <= 48) { "Shortcut name must be 1 to 48 characters" }
        require(shortcut.actions.isNotEmpty()) { "Shortcut must contain at least one action" }
        require(shortcut.actions.size <= 12) { "Shortcut can contain at most 12 actions" }
        shortcut.actions.forEach { action ->
            when (action.type) {
                ShortcutActionType.OpenApp -> require(action.value.lowercase() in allowedApps) { "Unsupported app action" }
                ShortcutActionType.OpenSettings -> require(action.value in allowedSettings) { "Unsupported Settings screen" }
                ShortcutActionType.SpeakText -> require(action.value.isNotBlank() && action.value.length <= 200) { "Speech text must be 1 to 200 characters" }
                else -> require(action.value.isEmpty()) { "This action does not accept a value" }
            }
        }
    }
}

class ShortcutRepository(private val storage: ShortcutStorage) {
    fun all(): List<UserShortcut> = storage.readShortcuts().sortedBy { it.name.lowercase() }

    fun save(shortcut: UserShortcut) {
        ShortcutValidator.validate(shortcut)
        storage.writeShortcuts(storage.readShortcuts().filterNot { it.id == shortcut.id } + shortcut.copy(name = shortcut.name.trim(), actions = shortcut.actions.toList()))
    }

    fun duplicate(id: String, newName: String): UserShortcut {
        val original = storage.readShortcuts().firstOrNull { it.id == id } ?: error("Shortcut not found")
        val copy = original.copy(id = UUID.randomUUID().toString(), name = newName, actions = original.actions.toList(), enabled = false)
        save(copy)
        return copy
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val current = storage.readShortcuts()
        storage.writeShortcuts(current.map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    fun delete(id: String) = storage.writeShortcuts(storage.readShortcuts().filterNot { it.id == id })

    fun actionNames(): Set<String> = all().filter { it.enabled }.mapTo(mutableSetOf()) { it.name }
}