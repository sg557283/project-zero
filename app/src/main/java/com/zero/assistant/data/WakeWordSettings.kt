package com.zero.assistant.data

import android.content.Context
import com.zero.assistant.core.WakePhrase
import com.zero.assistant.core.WakePhraseConfiguration
import com.zero.assistant.core.WakeServiceHeartbeat
import com.zero.assistant.core.WakeWord
import org.json.JSONArray
import org.json.JSONObject

object WakeWordSettings {
    const val ACTION_START = "com.zero.assistant.action.START_WAKE_WORD"
    const val ACTION_ACTIVATE = "com.zero.assistant.action.ACTIVATE_ZERO"
    const val ACTION_STOP = "com.zero.assistant.action.STOP_WAKE_WORD"
    const val ACTION_COMMAND = "com.zero.assistant.action.REVIEW_VOICE_COMMAND"
    const val EXTRA_COMMAND = "com.zero.assistant.extra.VOICE_COMMAND"
    const val EXTRA_RESUME = "com.zero.assistant.extra.RESUME_WAKE_WORD"
    const val ACTION_SETTINGS_CHANGED = "com.zero.assistant.action.WAKE_WORD_SETTINGS_CHANGED"
    const val ACTION_PHRASES_CHANGED = "com.zero.assistant.action.WAKE_WORD_PHRASES_CHANGED"
    const val CHANNEL_ID = "zero_background_voice"
    const val NOTIFICATION_ID = 7101

    private const val PREFERENCES = "zero_wake_word"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_ACTIVE = "active"
    private const val KEY_PHRASE = "phrase"
    private const val KEY_PHRASES = "phrases_v2"
    private const val KEY_ERROR = "error"
    private const val KEY_LAST_DETECTION = "last_detection"
    private const val KEY_HEARTBEAT = "service_heartbeat"

    fun enabled(context: Context): Boolean = preferences(context).getBoolean(KEY_ENABLED, false)

    fun active(context: Context): Boolean {
        val preferences = preferences(context)
        return WakeServiceHeartbeat.isFresh(
            preferences.getBoolean(KEY_ACTIVE, false),
            preferences.getLong(KEY_HEARTBEAT, 0L),
            System.currentTimeMillis(),
            HEARTBEAT_MAX_AGE_MS,
        )
    }

    fun phrases(context: Context): List<WakePhrase> {
        val preferences = preferences(context)
        val stored = preferences.getString(KEY_PHRASES, null)
        if (stored != null) {
            return runCatching {
                val json = JSONArray(stored)
                val phrases = (0 until json.length()).mapNotNull { index ->
                    val item = json.optJSONObject(index) ?: return@mapNotNull null
                    WakePhrase(
                        id = item.optString("id"),
                        phrase = item.optString("phrase"),
                        enabled = item.optBoolean("enabled", true),
                        isDefault = item.optBoolean("default", false),
                    )
                }
                WakePhraseConfiguration.canonicalize(phrases)
            }.getOrElse { WakePhraseConfiguration.initial(preferences.getString(KEY_PHRASE, null)) }
        }
        return WakePhraseConfiguration.initial(preferences.getString(KEY_PHRASE, null))
    }

    fun phrase(context: Context): String = phrases(context).firstOrNull { it.enabled }?.phrase ?: WakeWord.DEFAULT_PHRASE

    fun setPhrases(context: Context, phrases: List<WakePhrase>) {
        val canonical = WakePhraseConfiguration.canonicalize(phrases)
        val json = JSONArray().apply {
            canonical.forEach { phrase ->
                put(JSONObject().put("id", phrase.id).put("phrase", phrase.phrase).put("enabled", phrase.enabled).put("default", phrase.isDefault))
            }
        }
        preferences(context).edit().putString(KEY_PHRASES, json.toString()).apply()
        context.sendBroadcast(android.content.Intent(ACTION_PHRASES_CHANGED).setPackage(context.packageName))
    }

    fun error(context: Context): String? = preferences(context).getString(KEY_ERROR, null)

    fun lastDetection(context: Context): Long = preferences(context).getLong(KEY_LAST_DETECTION, 0L)

    fun recordDetection(context: Context) {
        preferences(context).edit().putLong(KEY_LAST_DETECTION, System.currentTimeMillis()).apply()
        broadcastChanged(context)
    }

    fun recordHeartbeat(context: Context) {
        if (preferences(context).getBoolean(KEY_ACTIVE, false)) {
            preferences(context).edit().putLong(KEY_HEARTBEAT, System.currentTimeMillis()).apply()
        }
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        broadcastChanged(context)
    }

    fun setPhrase(context: Context, phrase: String) {
        val current = phrases(context)
        val updated = if (current.any { !it.isDefault }) {
            WakePhraseConfiguration.update(current, current.first { !it.isDefault }.id, phrase)
        } else WakePhraseConfiguration.add(current, phrase)
        setPhrases(context, updated)
    }

    fun setActive(context: Context, active: Boolean, error: String? = null) {
        preferences(context).edit()
            .putBoolean(KEY_ACTIVE, active)
            .putLong(KEY_HEARTBEAT, if (active) System.currentTimeMillis() else 0L)
            .putString(KEY_ERROR, error)
            .apply()
        broadcastChanged(context)
    }

    private fun preferences(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private fun broadcastChanged(context: Context) {
        context.sendBroadcast(android.content.Intent(ACTION_SETTINGS_CHANGED).setPackage(context.packageName))
    }

    private const val HEARTBEAT_MAX_AGE_MS = 30_000L
}