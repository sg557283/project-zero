package com.zero.assistant.core

sealed interface AssistantCommand {
    data class OpenApp(val app: AppTarget) : AssistantCommand
    data class SetFlashlight(val enabled: Boolean) : AssistantCommand
    data object TellTime : AssistantCommand
    data object Help : AssistantCommand
    data object Unsupported : AssistantCommand
}

enum class AppTarget {
    YouTube,
    WhatsApp,
    Camera,
    Settings,
}

object CommandParser {
    private val timePhrases = listOf("time", "kitne baje", "samay", "waqt", "कितने बजे", "समय क्या")
    private val helpPhrases = listOf("help", "what can you do", "commands", "madad", "मदद", "सहायता")

    fun parse(input: String): AssistantCommand {
        val text = input.trim().lowercase().replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
        if (text.isBlank()) return AssistantCommand.Unsupported

        if (timePhrases.any(text::contains)) return AssistantCommand.TellTime
        if (helpPhrases.any(text::contains)) return AssistantCommand.Help

        val wantsOff = listOf("off", "band", "bujhao", "bujha do", "turn off", "बंद", "बुझाओ").any(text::contains)
        val wantsOn = listOf("on", "chalu", "jalao", "jalaa", "turn on", "चालू", "जलाओ").any(text::contains)
        if (listOf("torch", "flashlight", "flash light", "टॉर्च", "फ्लैशलाइट").any(text::contains)) {
            if (wantsOff) return AssistantCommand.SetFlashlight(false)
            if (wantsOn) return AssistantCommand.SetFlashlight(true)
        }

        val app = when {
            listOf("youtube", "you tube", "यूट्यूब").any(text::contains) -> AppTarget.YouTube
            listOf("whatsapp", "whats app", "व्हाट्सएप", "व्हाट्सऐप").any(text::contains) -> AppTarget.WhatsApp
            listOf("camera", "camra", "कैमरा").any(text::contains) -> AppTarget.Camera
            listOf("settings", "सेटिंग्स", "सेटिंग").any(text::contains) -> AppTarget.Settings
            else -> null
        }
        if (app != null && listOf("open", "launch", "kholo", "khol", "chalao", "start", "खोलो", "खोल").any(text::contains)) {
            return AssistantCommand.OpenApp(app)
        }

        return AssistantCommand.Unsupported
    }
}