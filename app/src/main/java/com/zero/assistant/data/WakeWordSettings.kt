package com.zero.assistant.data

import android.content.Context
import com.zero.assistant.core.WakeWord

object WakeWordSettings {
    const val ACTION_START = "com.zero.assistant.action.START_WAKE_WORD"
    const val ACTION_STOP = "com.zero.assistant.action.STOP_WAKE_WORD"
    const val ACTION_COMMAND = "com.zero.assistant.action.REVIEW_VOICE_COMMAND"
    const val EXTRA_COMMAND = "com.zero.assistant.extra.VOICE_COMMAND"
    const val EXTRA_RESUME = "com.zero.assistant.extra.RESUME_WAKE_WORD"
    const val ACTION_SETTINGS_CHANGED = "com.zero.assistant.action.WAKE_WORD_SETTINGS_CHANGED"
    const val CHANNEL_ID = "zero_background_voice"
    const val NOTIFICATION_ID = 7101

    private const val PREFERENCES = "zero_wake_word"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_ACTIVE = "active"
    private const val KEY_PHRASE = "phrase"
    private const val KEY_ERROR = "error"

    fun enabled(context: Context): Boolean = preferences(context).getBoolean(KEY_ENABLED, false)

    fun active(context: Context): Boolean = preferences(context).getBoolean(KEY_ACTIVE, false)

    fun phrase(context: Context): String = preferences(context).getString(KEY_PHRASE, WakeWord.DEFAULT_PHRASE) ?: WakeWord.DEFAULT_PHRASE

    fun error(context: Context): String? = preferences(context).getString(KEY_ERROR, null)

    fun setEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        broadcastChanged(context)
    }

    fun setPhrase(context: Context, phrase: String) {
        preferences(context).edit().putString(KEY_PHRASE, phrase).apply()
        broadcastChanged(context)
    }

    fun setActive(context: Context, active: Boolean, error: String? = null) {
        preferences(context).edit().putBoolean(KEY_ACTIVE, active).putString(KEY_ERROR, error).apply()
        broadcastChanged(context)
    }

    private fun preferences(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private fun broadcastChanged(context: Context) {
        context.sendBroadcast(android.content.Intent(ACTION_SETTINGS_CHANGED).setPackage(context.packageName))
    }
}