package com.zero.assistant.data

import android.annotation.SuppressLint
import android.content.Context
import com.google.gson.Gson
import com.zero.assistant.core.BatteryAlertState
import com.zero.assistant.core.BatteryAlertStorage

@SuppressLint("UseKtx")
class BatteryAlertPreferences(context: Context) : BatteryAlertStorage {
    private val preferences = context.getSharedPreferences("zero_battery_alerts", Context.MODE_PRIVATE)
    private val gson = Gson()

    override fun read(): BatteryAlertState = runCatching {
        gson.fromJson(preferences.getString(KEY_STATE, null), BatteryAlertState::class.java) ?: BatteryAlertState()
    }.getOrDefault(BatteryAlertState())

    override fun write(state: BatteryAlertState) {
        check(preferences.edit().putString(KEY_STATE, gson.toJson(state)).commit()) { "Battery alert settings could not be saved" }
    }

    private companion object {
        const val KEY_STATE = "state"
    }
}