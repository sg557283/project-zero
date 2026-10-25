package com.zero.assistant.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.zero.assistant.core.HistoryEntry
import com.zero.assistant.core.HistoryStorage
import org.json.JSONArray
import org.json.JSONObject

class PreferencesHistoryStorage(context: Context) : HistoryStorage {
    private val preferences = context.getSharedPreferences("zero_local", Context.MODE_PRIVATE)

    override fun read(): List<HistoryEntry> {
        val raw = preferences.getString("history", "[]") ?: "[]"
        return try {
            val items = JSONArray(raw)
            (0 until items.length()).map { index ->
                val item = items.getJSONObject(index)
                HistoryEntry(item.getString("command"), item.getString("response"), item.getLong("timestamp"))
            }
        } catch (exception: org.json.JSONException) {
            Log.e("ZERO", "Could not read local command history.", exception)
            emptyList()
        }
    }

    override fun write(entries: List<HistoryEntry>) {
        val items = JSONArray()
        entries.forEach { entry ->
            items.put(JSONObject().apply {
                put("command", entry.command)
                put("response", entry.response)
                put("timestamp", entry.timestamp)
            })
        }
        preferences.edit { putString("history", items.toString()) }
    }
}