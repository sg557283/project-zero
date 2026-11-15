package com.zero.assistant

import android.content.Context

enum class WidgetQuickAction(val label: String) {
    Flashlight("Flashlight"),
    Scanner("UPI Scanner"),
    VoiceNote("Voice Note"),
    Emergency("Emergency SOS"),
}

object WidgetActionSettings {
    private const val PREFERENCES = "zero_widget"
    private val defaults = listOf(
        WidgetQuickAction.Flashlight,
        WidgetQuickAction.Scanner,
        WidgetQuickAction.VoiceNote,
        WidgetQuickAction.Emergency,
    )

    fun actions(context: Context): List<WidgetQuickAction> =
        defaults.indices.map { slot ->
            val stored = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString("action_$slot", null)
            stored?.let { value -> WidgetQuickAction.entries.firstOrNull { it.name == value } } ?: defaults[slot]
        }

    fun setAction(context: Context, slot: Int, action: WidgetQuickAction) {
        require(slot in defaults.indices) { "Widget action slot is out of range." }
        check(
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString("action_$slot", action.name)
                .commit(),
        ) { "Widget action could not be saved." }
        ZeroAssistantWidget.refresh(context)
    }
}
