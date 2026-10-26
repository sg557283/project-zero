package com.zero.assistant.data

import android.annotation.SuppressLint
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zero.assistant.core.LocalNote
import com.zero.assistant.core.NoteStorage
import com.zero.assistant.core.ShortcutStorage
import com.zero.assistant.core.UserShortcut

@SuppressLint("UseKtx")
class ProductivityPreferences(context: Context) : NoteStorage, ShortcutStorage {
    private val preferences = context.getSharedPreferences("zero_productivity", Context.MODE_PRIVATE)
    private val gson = Gson()

    override fun read(): List<LocalNote> = decode(KEY_NOTES, NOTE_LIST_TYPE)
    override fun write(notes: List<LocalNote>) = encode(KEY_NOTES, notes)
    override fun readShortcuts(): List<UserShortcut> = decode(KEY_SHORTCUTS, SHORTCUT_LIST_TYPE)
    override fun writeShortcuts(shortcuts: List<UserShortcut>) = encode(KEY_SHORTCUTS, shortcuts)

    private fun <T> decode(key: String, type: java.lang.reflect.Type): List<T> = runCatching {
        gson.fromJson<List<T>>(preferences.getString(key, null), type).orEmpty()
    }.getOrDefault(emptyList())

    private fun encode(key: String, value: Any) {
        check(preferences.edit().putString(key, gson.toJson(value)).commit()) { "Local ZERO data could not be saved" }
    }

    private companion object {
        const val KEY_NOTES = "notes"
        const val KEY_SHORTCUTS = "shortcuts"
        val NOTE_LIST_TYPE = object : TypeToken<List<LocalNote>>() {}.type
        val SHORTCUT_LIST_TYPE = object : TypeToken<List<UserShortcut>>() {}.type
    }
}
