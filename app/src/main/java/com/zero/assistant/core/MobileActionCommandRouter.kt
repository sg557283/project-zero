package com.zero.assistant.core

data class MobileActionToolCall(
    val name: String,
    val arguments: Map<String, Any?>,
)

object MobileActionCommandRouter {
    private const val MAX_TOOL_CALLS = 8
    private const val MAX_ARGUMENT_LENGTH = 1_000
    private val safeTarget = Regex("^[\\p{L}\\p{M}\\p{N}][\\p{L}\\p{M}\\p{N} .'-]{0,159}$")
    private val commandSeparator = Regex("\\b(?:and|then|after that|aur|phir|uske baad)\\b", RegexOption.IGNORE_CASE)

    fun toCommands(calls: List<MobileActionToolCall>): List<String>? {
        if (calls.isEmpty() || calls.size > MAX_TOOL_CALLS) return null
        val commands = calls.map { call -> toCommand(call) ?: return null }
        return commands.takeIf { it.isNotEmpty() }
    }

    private fun toCommand(call: MobileActionToolCall): String? {
        fun argument(vararg keys: String): String? =
            keys.asSequence()
                .mapNotNull { key -> call.arguments[key] as? String }
                .map(String::trim)
                .firstOrNull { it.isNotEmpty() && it.length <= MAX_ARGUMENT_LENGTH }

        return when (call.name) {
            "turn_on_flashlight" -> "torch on"
            "turn_off_flashlight" -> "torch off"
            "make_call" -> argument("phone_number")?.takeIf(::isPhoneNumber)?.let { "call $it" }
                ?: argument("contact")?.takeIf(::isSafeTarget)?.let { "call $it" }
            "send_whatsapp_message" -> {
                val contact = argument("contact")?.takeIf(::isSafeTarget) ?: return null
                val message = argument("message") ?: return null
                "whatsapp text $contact: $message"
            }
            "open_app_and_search" -> {
                val app = argument("app")?.takeIf(::isKnownApp) ?: return null
                val query = argument("query")
                if (app.equals("youtube", ignoreCase = true) && query != null) {
                    if (!isSafeTarget(query)) return null
                    "play video named $query"
                } else if (query == null) {
                    "open $app"
                } else {
                    return null
                }
            }
            "control_active_call" -> when (argument("action")?.lowercase()) {
                "answer", "pick", "accept" -> "answer call"
                "end", "hang up", "cut" -> "end call"
                "mute" -> "mute call"
                "unmute" -> "unmute call"
                "speaker on" -> "speaker on"
                "speaker off" -> "speaker off"
                else -> null
            }
            "take_call_note" -> null
            "create_reminder" -> argument("title")?.takeIf(::isSafeTarget)?.let { "remind me to $it" }
            "get_weather" -> argument("city")?.takeIf(::isSafeTarget)?.let { "weather in $it" } ?: "weather"
            "read_active_screen" -> "read screen"
            "perform_accessibility_click" -> argument("target")?.takeIf(::isSafeTarget)?.let { "click $it" }
            else -> null
        }
    }

    private fun isPhoneNumber(value: String): Boolean =
        value.length <= 24 && value.matches(Regex("^\\+?[\\d\\s().-]+$")) &&
            value.count(Char::isDigit) in 7..15

    private fun isSafeTarget(value: String): Boolean =
        value.length <= 160 && safeTarget.matches(value) && !commandSeparator.containsMatchIn(value)

    private fun isKnownApp(value: String): Boolean =
        value.lowercase() in setOf(
            "youtube", "yt", "facebook", "fb", "instagram", "insta", "ig",
            "whatsapp", "wa", "spotify", "music", "camera", "gallery", "photos",
            "settings", "calculator", "browser", "maps", "messages",
        )
}

sealed interface MobileActionBrainState {
    data object Idle : MobileActionBrainState
    data class Downloading(val receivedBytes: Long, val totalBytes: Long) : MobileActionBrainState
    data object Paused : MobileActionBrainState
    data object Loading : MobileActionBrainState
    data object Installed : MobileActionBrainState
    data object Active : MobileActionBrainState
    data object Removing : MobileActionBrainState
    data class Failed(val message: String) : MobileActionBrainState
    data class RemoveFailed(val message: String) : MobileActionBrainState
    data object Cancelled : MobileActionBrainState
}
