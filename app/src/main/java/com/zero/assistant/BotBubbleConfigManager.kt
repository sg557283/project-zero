package com.zero.assistant

import android.content.Context
import com.zero.assistant.core.AssistantAction

enum class BotBubbleAction(val label: String, val assistantAction: AssistantAction?) {
    Voice("Mic", null),
    Flashlight("Flash", AssistantAction.TOGGLE_FLASHLIGHT),
    Scanner("Scanner", AssistantAction.OPEN_UPI_SCANNER),
    AutoClick("Auto-click", AssistantAction.AUTO_CLICK_TARGET),
    Emergency("Emergency", AssistantAction.EMERGENCY_SOS),
    VoiceNote("Voice note", AssistantAction.OFFLINE_VOICE_NOTE),
    Camera("Camera", AssistantAction.OPEN_CAMERA),
    Media("Media", AssistantAction.SYSTEM_MEDIA_CONTROL),
    Back("Back", AssistantAction.SCREEN_NAVIGATION),
    Dialer("Dialer", AssistantAction.OFFLINE_CALL_SMS),
}

object BotBubbleConfigManager {
    const val MAX_BUBBLES = 7
    private const val PREFERENCES = "zero_bot_bubbles"
    private const val KEY_ACTIONS = "actions"
    private val defaults = listOf(
        BotBubbleAction.Voice,
        BotBubbleAction.Flashlight,
        BotBubbleAction.Scanner,
        BotBubbleAction.AutoClick,
        BotBubbleAction.Emergency,
    )

    fun actions(context: Context): List<BotBubbleAction> {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (!preferences.contains(KEY_ACTIONS)) return defaults
        return preferences.getString(KEY_ACTIONS, "").orEmpty()
            .split(',')
            .mapNotNull { name -> BotBubbleAction.entries.firstOrNull { it.name == name } }
            .distinct()
            .take(MAX_BUBBLES)
    }

    fun save(context: Context, actions: List<BotBubbleAction>) {
        require(actions.size <= MAX_BUBBLES) { "A maximum of $MAX_BUBBLES floating bot bubbles is supported." }
        require(actions.distinct().size == actions.size) { "A floating bot action can only appear once." }
        check(
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ACTIONS, actions.joinToString(",") { it.name })
                .commit(),
        ) { "Floating bot actions could not be saved." }
        FloatingBotOverlayService.refreshBubbles(context)
    }
}

object FloatingBotSettings {
    private const val PREFERENCES = "zero_bot_overlay"
    private const val KEY_VOLUME_TOGGLE_ENABLED = "volume_toggle_enabled"

    fun volumeToggleEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(KEY_VOLUME_TOGGLE_ENABLED, true)

    fun setVolumeToggleEnabled(context: Context, enabled: Boolean) {
        check(
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_VOLUME_TOGGLE_ENABLED, enabled)
                .commit(),
        ) { "Floating bot trigger setting could not be saved." }
    }
}
