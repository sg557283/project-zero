package com.zero.assistant.data

import android.content.Context
import android.annotation.SuppressLint
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zero.assistant.core.PlannerReminder
import com.zero.assistant.core.ReminderStorage

@SuppressLint("UseKtx")
class PlannerPreferences(context: Context) : ReminderStorage {
    private val preferences = context.getSharedPreferences("zero_planner", Context.MODE_PRIVATE)
    private val gson = Gson()

    override fun read(): List<PlannerReminder> = runCatching {
        val json = preferences.getString(KEY_REMINDERS, null) ?: return emptyList()
        gson.fromJson<List<PlannerReminder>>(json, REMINDER_LIST_TYPE).orEmpty()
    }.getOrDefault(emptyList())

    override fun write(reminders: List<PlannerReminder>) {
        check(preferences.edit().putString(KEY_REMINDERS, gson.toJson(reminders)).commit()) {
            "Reminder data could not be saved"
        }
    }

    private companion object {
        const val KEY_REMINDERS = "reminders"
        val REMINDER_LIST_TYPE = object : TypeToken<List<PlannerReminder>>() {}.type
    }
}