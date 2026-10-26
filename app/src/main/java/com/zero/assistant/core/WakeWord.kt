package com.zero.assistant.core

data class WakeWordMatch(val command: String)

object WakeWord {
    const val DEFAULT_PHRASE = "Hey ZERO"

    fun match(transcript: String, phrase: String = DEFAULT_PHRASE): WakeWordMatch? {
        val normalizedTranscript = normalize(transcript)
        val normalizedPhrase = normalize(phrase)
        if (normalizedTranscript.isBlank() || normalizedPhrase.isBlank()) return null
        if (normalizedTranscript != normalizedPhrase && !normalizedTranscript.startsWith("$normalizedPhrase ")) return null
        return WakeWordMatch(normalizedTranscript.removePrefix(normalizedPhrase).trim())
    }

    private fun normalize(text: String): String = text.lowercase()
        .replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}