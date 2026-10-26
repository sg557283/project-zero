package com.zero.assistant.core

sealed interface AssistantCommand {
    data class OpenApp(val app: AppTarget) : AssistantCommand
    data class SetFlashlight(val enabled: Boolean) : AssistantCommand
    data class CallNumber(val number: String) : AssistantCommand
    data class SaveNote(val text: String) : AssistantCommand
    data class DraftReminder(val title: String) : AssistantCommand
    data class RunShortcut(val name: String) : AssistantCommand
    data object TellTime : AssistantCommand
    data object BatteryStatus : AssistantCommand
    data object ShowPlanner : AssistantCommand
    data object ShowNotes : AssistantCommand
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
    private val callPattern = Regex("^\\s*(?:call|dial|phone|कॉल)\\s+(\\+?[\\d\\s().-]{7,24})\\s*$", RegexOption.IGNORE_CASE)
    private val reminderPattern = Regex("^\\s*(?:remind me to|remind me|याद दिलाना)\\s+(.{1,160})$", RegexOption.IGNORE_CASE)
    private val notePattern = Regex("^\\s*(?:note|save note|write note|नोट)\\s+(.{1,2000})$", RegexOption.IGNORE_CASE)
    private val timePhrases = listOf("time", "kitne baje", "samay", "waqt", "कितने बजे", "समय क्या")
    private val batteryPhrases = listOf("battery", "charge", "बैटरी", "चार्ज")
    private val helpPhrases = listOf("help", "what can you do", "commands", "madad", "मदद", "सहायता")

    fun parse(input: String): AssistantCommand {
        callPattern.matchEntire(input)?.groupValues?.get(1)?.let { rawNumber ->
            val number = rawNumber.filter { it.isDigit() || it == '+' }
            if (number.count(Char::isDigit) in 7..15) return AssistantCommand.CallNumber(number)
        }
        reminderPattern.matchEntire(input)?.groupValues?.get(1)?.trim()?.let { title ->
            if (title.isNotBlank()) return AssistantCommand.DraftReminder(title)
        }
        notePattern.matchEntire(input)?.groupValues?.get(1)?.trim()?.let { text ->
            if (text.isNotBlank()) return AssistantCommand.SaveNote(text)
        }
        val text = input.trim().lowercase().replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
        if (text.isBlank()) return AssistantCommand.Unsupported

        if (timePhrases.any(text::contains)) return AssistantCommand.TellTime
        if (batteryPhrases.any(text::contains)) return AssistantCommand.BatteryStatus
        if (listOf("show reminders", "reminders", "show tasks", "today's tasks", "today s tasks", "planner", "योजनाएं", "याद दिलाने").any(text::contains)) return AssistantCommand.ShowPlanner
        if (listOf("show notes", "my notes", "notes", "नोट दिखाओ").any(text::contains)) return AssistantCommand.ShowNotes
        val shortcutName = Regex("^(?:run|shortcut)\\s+(.+)$").matchEntire(text)?.groupValues?.get(1)?.trim()
        if (!shortcutName.isNullOrBlank()) return AssistantCommand.RunShortcut(shortcutName)
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