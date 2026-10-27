package com.zero.assistant.core

data class HomeSuggestion(val label: String, val command: String)

object HomeSuggestionCatalog {
    val primary = listOf(
        HomeSuggestion("Open YouTube", "open youtube"),
        HomeSuggestion("Planner", "planner"),
        HomeSuggestion("Battery status", "battery status"),
        HomeSuggestion("Today's plan", "today's tasks"),
    )

    val more = listOf(
        HomeSuggestion("Open camera", "open camera"),
        HomeSuggestion("Tell time", "time"),
        HomeSuggestion("Open WhatsApp", "open whatsapp"),
        HomeSuggestion("Notes", "notes"),
    )
}

enum class AssistantResponseRoute {
    Local,
    ConfirmOnline,
}

object AssistantResponseRouter {
    fun select(
        command: AssistantCommand,
        onlineEnabled: Boolean,
        keyConfigured: Boolean,
        endpointValid: Boolean,
        internetAvailable: Boolean,
    ): AssistantResponseRoute = if (
        command == AssistantCommand.Unsupported && onlineEnabled && keyConfigured && endpointValid && internetAvailable
    ) AssistantResponseRoute.ConfirmOnline else AssistantResponseRoute.Local
}