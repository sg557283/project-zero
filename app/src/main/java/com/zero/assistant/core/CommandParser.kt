package com.zero.assistant.core

sealed interface AssistantCommand {
    data object Identity : AssistantCommand
    data class OpenApp(val app: AppTarget) : AssistantCommand
    data class SetFlashlight(val enabled: Boolean) : AssistantCommand
    data class AdjustVolume(val increase: Boolean) : AssistantCommand
    data class SetAudioMuted(val muted: Boolean) : AssistantCommand
    data object ToggleFlashlight : AssistantCommand
    data class OpenSystemSettings(val target: String) : AssistantCommand
    data class CallNumber(val number: String) : AssistantCommand
    data class DraftCallContact(val contact: String) : AssistantCommand
    data class SearchYouTube(val query: String) : AssistantCommand
    data class DraftWhatsAppMessage(val contact: String, val message: String) : AssistantCommand
    data class SaveNote(val text: String) : AssistantCommand
    data class CallControl(val action: CallControlAction) : AssistantCommand
    data class Weather(val city: String?) : AssistantCommand
    data class DraftReminder(val title: String) : AssistantCommand
    data class RunShortcut(val name: String) : AssistantCommand
    data object TellTime : AssistantCommand
    data object BatteryStatus : AssistantCommand
    data object ShowPlanner : AssistantCommand
    data object ShowNotes : AssistantCommand
    data object Help : AssistantCommand
    data object Unsupported : AssistantCommand
}

enum class CallControlAction {
    Answer,
    End,
    Mute,
    Unmute,
    SpeakerOn,
    SpeakerOff,
}

enum class AppTarget {
    YouTube,
    Facebook,
    Instagram,
    WhatsApp,
    Gallery,
    Music,
    Maps,
    Browser,
    Phone,
    Messages,
    Calculator,
    Camera,
    Settings,
    Scanner,
    Notes,
    Vault,
}

object AssistantIdentity {
    const val RESPONSE = "I am ZERO, your personal assistant to make your work and life balanced and easy. I am a test product of Shree."
}

object CommandParser {
    private val callPattern = Regex("^\\s*(?:call|dial|phone|कॉल)\\s+(\\+?[\\d\\s().-]{7,24})\\s*$", RegexOption.IGNORE_CASE)
    private val youtubeSearchPattern = Regex("^\\s*(?:open\\s+)?youtube\\s+and\\s+search\\s+for\\s+(.{1,160})\\s*$", RegexOption.IGNORE_CASE)
    private val youtubeVideoPattern = Regex("^\\s*play\\s+(?:the\\s+)?video\\s+named\\s+(.{1,160})\\s*$", RegexOption.IGNORE_CASE)
    private val whatsappMessagePattern = Regex(
        "^\\s*(?:open\\s+)?whatsapp\\s+(?:and\\s+)?(?:text|message)\\s+([^:]{1,80}):\\s*(.{1,1000})\\s*$",
        RegexOption.IGNORE_CASE,
    )
    private val hinglishWhatsAppPattern = Regex(
        "^\\s*whatsapp\\s+par\\s+(.{1,80}?)\\s+ko\\s+message\\s+bhejo\\s*:\\s*(.{1,1000})\\s*$",
        RegexOption.IGNORE_CASE,
    )
    private val reminderPattern = Regex("^\\s*(?:remind me to|remind me|याद दिलाना)\\s+(.{1,160})$", RegexOption.IGNORE_CASE)
    private val hinglishReminderPattern = Regex("^\\s*(.{1,160})\\s+yaad\\s+dilana\\s*$", RegexOption.IGNORE_CASE)
    private val notePattern = Regex(
        "^\\s*(?:note\\s+save\\s+karo\\s+(.{1,2000})|(?:note|save note|write note|नोट)\\s+(.{1,2000})|(.{1,2000})\\s+note\\s+save\\s+karo)\\s*$",
        RegexOption.IGNORE_CASE,
    )
    private val namedCallPattern = Regex(
        "^\\s*(?:call|phone)\\s+([\\p{L}\\p{M}][\\p{L}\\p{M}\\s.'-]{0,78})\\s*$",
        RegexOption.IGNORE_CASE,
    )
    private val hinglishNamedCallPattern = Regex(
        "^\\s*([\\p{L}\\p{M}][\\p{L}\\p{M}\\s.'-]{0,78}?)\\s+ko\\s+call\\s+(?:lagao|karo)\\s*$",
        RegexOption.IGNORE_CASE,
    )
    private val timePhrases = listOf("time", "kitne baje", "samay", "waqt", "कितने बजे", "समय क्या")
    private val batteryPhrases = listOf("battery", "charge", "बैटरी", "चार्ज")
    private val helpPhrases = listOf("help", "what can you do", "commands", "madad", "मदद", "सहायता")
    private val identityPhrases = listOf(
        "who are you", "who is zero", "who is zero assistant", "tum kaun ho", "tum kon ho",
        "aap kaun ho", "aap kon ho", "zero kaun hai", "zero kon hai", "tum kaun ho zero",
        "तुम कौन हो", "आप कौन हैं", "जीरो कौन है", "जीरो कौन हो", "तुम कौन हो जीरो",
    )
    private val weatherCityPattern = Regex("^\\s*(?:live\\s+)?weather\\s+(?:in|for)\\s+(.{2,80})\\s*$", RegexOption.IGNORE_CASE)

    fun parse(input: String): AssistantCommand {
        val commandInput = BilingualAliasCommandEngine.normalize(input)
        val normalized = commandInput.trim().lowercase()
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
        if (identityPhrases.any { normalized == it || normalized.contains(it) }) return AssistantCommand.Identity
        when {
            listOf("pick the call", "answer call", "receive call", "call answer", "call uthao", "receive karo", "कॉल उठाओ").any(normalized::contains) ->
                return AssistantCommand.CallControl(CallControlAction.Answer)
            listOf("cut the call", "end call", "disconnect", "call kaat do", "कॉल काट दो").any(normalized::contains) ->
                return AssistantCommand.CallControl(CallControlAction.End)
            listOf("speaker on", "speaker on karo", "speaker turn on").any(normalized::contains) ->
                return AssistantCommand.CallControl(CallControlAction.SpeakerOn)
            listOf("speaker off", "speaker off karo", "speaker turn off").any(normalized::contains) ->
                return AssistantCommand.CallControl(CallControlAction.SpeakerOff)
            listOf("unmute call").any(normalized::contains) ->
                return AssistantCommand.CallControl(CallControlAction.Unmute)
            listOf("mute the call", "call mute", "mute call", "awaz band karo", "आवाज़ बंद करो").any(normalized::contains) ->
                return AssistantCommand.CallControl(CallControlAction.Mute)
        }
        youtubeVideoPattern.matchEntire(commandInput)?.groupValues?.get(1)?.trim()?.let { query ->
            if (query.isNotBlank()) return AssistantCommand.SearchYouTube(query)
        }
        youtubeSearchPattern.matchEntire(commandInput)?.groupValues?.get(1)?.trim()?.let { query ->
            if (query.isNotBlank()) return AssistantCommand.SearchYouTube(query)
        }
        whatsappMessagePattern.matchEntire(commandInput)?.groupValues?.let { groups ->
            val contact = groups[1].trim()
            val message = groups[2].trim()
            if (contact.isNotBlank() && message.isNotBlank()) return AssistantCommand.DraftWhatsAppMessage(contact, message)
        }
        hinglishWhatsAppPattern.matchEntire(input)?.groupValues?.let { groups ->
            val contact = groups[1].trim()
            val message = groups[2].trim()
            if (contact.isNotBlank() && message.isNotBlank()) return AssistantCommand.DraftWhatsAppMessage(contact, message)
        }
        callPattern.matchEntire(commandInput)?.groupValues?.get(1)?.let { rawNumber ->
            val number = rawNumber.filter { it.isDigit() || it == '+' }
            if (number.count(Char::isDigit) in 7..15) return AssistantCommand.CallNumber(number)
        }
        namedCallPattern.matchEntire(commandInput)?.groupValues?.get(1)?.trim()?.let { contact ->
            if (contact.isNotBlank()) return AssistantCommand.DraftCallContact(contact)
        }
        hinglishNamedCallPattern.matchEntire(commandInput)?.groupValues?.get(1)?.trim()?.let { contact ->
            if (contact.isNotBlank()) return AssistantCommand.DraftCallContact(contact)
        }
        reminderPattern.matchEntire(commandInput)?.groupValues?.get(1)?.trim()?.let { title ->
            if (title.isNotBlank()) return AssistantCommand.DraftReminder(title)
        }
        hinglishReminderPattern.matchEntire(commandInput)?.groupValues?.get(1)?.trim()?.let { title ->
            if (title.isNotBlank()) return AssistantCommand.DraftReminder(title)
        }
        notePattern.matchEntire(commandInput)?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }?.trim()?.let { text ->
            if (text.isNotBlank()) return AssistantCommand.SaveNote(text)
        }
        weatherCityPattern.matchEntire(commandInput)?.groupValues?.get(1)?.trim()?.let { city ->
            if (city.isNotBlank()) return AssistantCommand.Weather(city)
        }
        if (listOf("weather", "weather batao", "aaj ka mausam", "mausam batao", "मौसम बताओ", "आज का मौसम").any(normalized::contains)) {
            return AssistantCommand.Weather(null)
        }
        val text = commandInput.trim().lowercase().replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
        if (text.isBlank()) return AssistantCommand.Unsupported

        if (text in setOf("wifi", "wi fi", "वाईफाई", "वाई फाई") ||
            text in setOf("turn on wifi", "turn off wifi", "open wifi settings")
        ) return AssistantCommand.OpenSystemSettings("wifi")
        if (text in setOf("bluetooth", "ब्लूटूथ", "ब्लू टूथ")) return AssistantCommand.OpenSystemSettings("bluetooth")
        if (text in setOf("hotspot", "tethering", "हॉटस्पॉट")) return AssistantCommand.OpenSystemSettings("hotspot")
        if (text in setOf("dnd", "do not disturb", "do not disturb mode", "परेशान न करें")) return AssistantCommand.OpenSystemSettings("dnd")
        if (text in setOf("auto rotate", "autorotate", "ऑटो रोटेट")) return AssistantCommand.OpenSystemSettings("display")
        if (text in setOf("battery saver", "power saver", "बैटरी सेवर्स", "बैटरी सेविंग")) return AssistantCommand.OpenSystemSettings("battery_saver")
        if (timePhrases.any(text::contains)) return AssistantCommand.TellTime
        if (batteryPhrases.any(text::contains)) return AssistantCommand.BatteryStatus
        if (listOf("volume mute", "volume turn off", "mute volume").any(text::contains)) {
            return AssistantCommand.SetAudioMuted(true)
        }
        if (listOf("volume unmute", "unmute volume", "volume turn on").any(text::contains)) {
            return AssistantCommand.SetAudioMuted(false)
        }
        if (listOf("volume up", "volume increase", "turn volume up", "volume badhao", "awaz badhao", "आवाज़ बढ़ाओ", "वॉल्यूम बढ़ाओ").any(text::contains)) {
            return AssistantCommand.AdjustVolume(increase = true)
        }
        if (listOf("volume down", "volume decrease", "turn volume down", "volume kam karo", "awaz kam karo", "आवाज़ कम करो", "वॉल्यूम कम करो").any(text::contains)) {
            return AssistantCommand.AdjustVolume(increase = false)
        }
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
            if (listOf("torch", "flashlight", "flash light", "टॉर्च", "फ्लैशलाइट").any { text == it }) return AssistantCommand.ToggleFlashlight
        }

        val app = when {
            listOf("youtube", "you tube", "यूट्यूब").any(text::contains) -> AppTarget.YouTube
            listOf("facebook").any(text::contains) -> AppTarget.Facebook
            listOf("instagram").any(text::contains) -> AppTarget.Instagram
            listOf("whatsapp", "whats app", "व्हाट्सएप", "व्हाट्सऐप").any(text::contains) -> AppTarget.WhatsApp
            listOf("music", "संगीत", "गाना").any(text::contains) -> AppTarget.Music
            listOf("gallery").any(text::contains) -> AppTarget.Gallery
            listOf("maps", "map", "नक्शा", "मैप्स").any(text::contains) -> AppTarget.Maps
            listOf("browser", "chrome", "ब्राउज़र").any(text::contains) -> AppTarget.Browser
            listOf("phone", "dialer", "फोन", "डायलर").any(text::contains) -> AppTarget.Phone
            listOf("messages", "message", "sms", "संदेश").any(text::contains) -> AppTarget.Messages
            listOf("calculator", "calc", "कैल्कुलेटर").any(text::contains) -> AppTarget.Calculator
            listOf("camera", "camra", "कैमरा").any(text::contains) -> AppTarget.Camera
            listOf("scanner", "scan", "स्कैनर", "स्कैनर").any(text::contains) -> AppTarget.Scanner
            listOf("notes", "note", "नोट्स", "नोट").any(text::contains) -> AppTarget.Notes
            listOf("vault", "zero vault", "वाल्ट").any(text::contains) -> AppTarget.Vault
            listOf("settings", "सेटिंग्स", "सेटिंग").any(text::contains) -> AppTarget.Settings
            else -> null
        }
        if (app != null && (
            listOf("open", "launch", "kholo", "khol", "chalao", "bajao", "par", "start", "खोलो", "खोल").any(text::contains) ||
            text in setOf("youtube", "you tube", "यूट्यूब", "facebook", "instagram", "whatsapp", "whats app", "व्हाट्सएप", "व्हाट्सऐप", "music", "संगीत", "गाना", "gallery", "maps", "map", "browser", "chrome", "phone", "dialer", "messages", "sms", "calculator", "calc", "camera", "camra", "कैमरा", "scanner", "scan", "settings", "सेटिंग्स", "vault", "zero vault", "notes", "note", "नोट")
        )) {
            return AssistantCommand.OpenApp(app)
        }

        return AssistantCommand.Unsupported
    }
}