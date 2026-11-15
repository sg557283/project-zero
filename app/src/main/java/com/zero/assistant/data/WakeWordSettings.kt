package com.zero.assistant.data

import android.content.Context
import com.zero.assistant.core.WakePhrase
import com.zero.assistant.core.WakePhraseConfiguration
import com.zero.assistant.core.WakeServiceHeartbeat
import com.zero.assistant.core.WakeWord
import org.json.JSONArray
import org.json.JSONObject

object WakeWordSettings {
    enum class Sensitivity(
        val vadThreshold: Float,
        val keywordThreshold: Float,
        val keywordBoost: Float,
    ) {
        Normal(vadThreshold = 0.35f, keywordThreshold = 0.35f, keywordBoost = 1.0f),
        LowVoice(vadThreshold = 0.20f, keywordThreshold = 0.20f, keywordBoost = 2.8f),
    }
    const val ACTION_START = "com.zero.assistant.action.START_WAKE_WORD"
    const val ACTION_ACTIVATE = "com.zero.assistant.action.ACTIVATE_ZERO"
    const val ACTION_ONE_SHOT_COMMAND = "com.zero.assistant.action.ONE_SHOT_COMMAND"
    const val ACTION_STOP = "com.zero.assistant.action.STOP_WAKE_WORD"
    const val ACTION_COMMAND = "com.zero.assistant.action.REVIEW_VOICE_COMMAND"
    const val EXTRA_COMMAND = "com.zero.assistant.extra.VOICE_COMMAND"
    const val EXTRA_RESUME = "com.zero.assistant.extra.RESUME_WAKE_WORD"
    const val ACTION_SETTINGS_CHANGED = "com.zero.assistant.action.WAKE_WORD_SETTINGS_CHANGED"
    const val ACTION_PHRASES_CHANGED = "com.zero.assistant.action.WAKE_WORD_PHRASES_CHANGED"
    const val ACTION_ASSISTANT_PANEL = "com.zero.assistant.action.ASSISTANT_PANEL"
    const val ACTION_ON_SCREEN_ACTION = "com.zero.assistant.action.ON_SCREEN_ACTION"
    const val ACTION_WAKE_SENSITIVITY_CHANGED = "com.zero.assistant.action.WAKE_SENSITIVITY_CHANGED"
    const val EXTRA_ON_SCREEN_ACTION = "com.zero.assistant.extra.ON_SCREEN_ACTION"
    const val EXTRA_PANEL_MESSAGE = "com.zero.assistant.extra.PANEL_MESSAGE"
    const val EXTRA_PANEL_DISMISS = "com.zero.assistant.extra.PANEL_DISMISS"
    const val EXTRA_PANEL_DISMISS_AFTER_MS = "com.zero.assistant.extra.PANEL_DISMISS_AFTER_MS"
    const val EXTRA_PANEL_PREVIEW = "com.zero.assistant.extra.PANEL_PREVIEW"
    const val EXTRA_PANEL_FACE_MODE = "com.zero.assistant.extra.PANEL_FACE_MODE"
    const val EXTRA_PANEL_PENDING_TEXT = "com.zero.assistant.extra.PANEL_PENDING_TEXT"
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
    private const val KEY_SENSITIVITY = "sensitivity"

    fun enabled(context: Context): Boolean = preferences(context).getBoolean(KEY_ENABLED, false)

    fun sensitivity(context: Context): Sensitivity =
        runCatching { Sensitivity.valueOf(preferences(context).getString(KEY_SENSITIVITY, null) ?: Sensitivity.Normal.name) }
            .getOrDefault(Sensitivity.Normal)

    fun setSensitivity(context: Context, sensitivity: Sensitivity) {
        preferences(context).edit().putString(KEY_SENSITIVITY, sensitivity.name).apply()
        context.sendBroadcast(android.content.Intent(ACTION_WAKE_SENSITIVITY_CHANGED).setPackage(context.packageName))
    }

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

    fun updateAssistantPanel(
        context: Context,
        message: String,
        dismissAfterMillis: Long = 0L,
        preview: Boolean = false,
        faceMode: String? = null,
        pendingText: String? = null,
    ) {
        com.zero.assistant.FloatingBotOverlayService.updateExpression(
            context,
            faceMode ?: com.zero.assistant.BotFaceMode.Listening.name,
        )
        context.sendBroadcast(
            android.content.Intent(ACTION_ASSISTANT_PANEL)
                .setPackage(context.packageName)
                .putExtra(EXTRA_PANEL_MESSAGE, message)
                .putExtra(EXTRA_PANEL_DISMISS_AFTER_MS, dismissAfterMillis)
                .putExtra(EXTRA_PANEL_PREVIEW, preview)
                .putExtra(EXTRA_PANEL_FACE_MODE, faceMode)
                .putExtra(EXTRA_PANEL_PENDING_TEXT, pendingText),
        )
    }

    fun requestOnScreenAction(context: Context, command: String) {
        context.sendBroadcast(
            android.content.Intent(ACTION_ON_SCREEN_ACTION)
                .setPackage(context.packageName)
                .putExtra(EXTRA_ON_SCREEN_ACTION, command),
        )
    }

    fun dismissAssistantPanel(context: Context) {
        context.sendBroadcast(
            android.content.Intent(ACTION_ASSISTANT_PANEL)
                .setPackage(context.packageName)
                .putExtra(EXTRA_PANEL_DISMISS, true),
        )
    }

    private fun preferences(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private fun broadcastChanged(context: Context) {
        context.sendBroadcast(android.content.Intent(ACTION_SETTINGS_CHANGED).setPackage(context.packageName))
    }

    private const val HEARTBEAT_MAX_AGE_MS = 30_000L
}