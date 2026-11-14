package com.zero.assistant.core

object ConversationalVoiceEngine {
    private val hinglishWords = setOf(
        "aaj", "acha", "accha", "batao", "badhao", "band", "chahiye", "chalu", "hai",
        "kaisa", "kaise", "kar", "karo", "kitna", "kitni", "kholo", "kal", "lagao",
        "mujhe", "nahi", "namaste", "par", "please", "suno", "theek", "yaad",
    )
    private val wordPattern = Regex("[\\p{L}]+")

    fun languageFor(text: String): SpeechLanguage {
        if (text.any { it in '\u0900'..'\u097F' }) return SpeechLanguage.Hindi
        val words = wordPattern.findAll(text.lowercase()).map { it.value }.toSet()
        return if (words.any { it in hinglishWords }) SpeechLanguage.Hindi else SpeechLanguage.English
    }

    fun thinkingCue(language: SpeechLanguage): String = when (language) {
        SpeechLanguage.English -> "Hmm, let's see."
        SpeechLanguage.Hindi -> "अच्छा, देखते हैं।"
    }
}
