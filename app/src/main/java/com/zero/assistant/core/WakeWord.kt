package com.zero.assistant.core

data class WakeWordMatch(val command: String)

data class WakePhrase(
    val id: String,
    val phrase: String,
    val enabled: Boolean,
    val isDefault: Boolean = false,
)

object WakeServiceHeartbeat {
    fun isFresh(active: Boolean, timestamp: Long, now: Long, maxAgeMillis: Long): Boolean =
        active && timestamp > 0L && now >= timestamp && now - timestamp <= maxAgeMillis
}

object WakePhraseConfiguration {
    const val DEFAULT_PHRASE = "Hey ZERO"
    private const val MAX_PHRASES = 3

    fun initial(legacyPhrase: String? = null): List<WakePhrase> {
        val phrases = mutableListOf(WakePhrase("default", DEFAULT_PHRASE, true, isDefault = true))
        val legacy = legacyPhrase?.trim().orEmpty()
        if (legacy.isNotEmpty() && normalize(legacy) != normalize(DEFAULT_PHRASE)) {
            phrases += WakePhrase("custom-1", legacy, true)
        }
        return phrases
    }

    fun add(phrases: List<WakePhrase>, value: String): List<WakePhrase> {
        val phrase = validate(phrases, value)
        require(phrases.size < MAX_PHRASES) { "At most two custom wake phrases are supported." }
        val id = (1..2).map { "custom-$it" }.first { candidate -> phrases.none { it.id == candidate } }
        return canonicalize(phrases) + WakePhrase(id, phrase, enabled = true)
    }

    fun update(phrases: List<WakePhrase>, id: String, value: String): List<WakePhrase> {
        require(phrases.any { it.id == id && !it.isDefault }) { "The default wake phrase cannot be edited." }
        val phrase = validate(phrases.filterNot { it.id == id }, value)
        return canonicalize(phrases.map { if (it.id == id) it.copy(phrase = phrase) else it })
    }

    fun setEnabled(phrases: List<WakePhrase>, id: String, enabled: Boolean): List<WakePhrase> {
        require(phrases.any { it.id == id }) { "Wake phrase does not exist." }
        return canonicalize(phrases.map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    fun remove(phrases: List<WakePhrase>, id: String): List<WakePhrase> {
        require(phrases.none { it.id == id && it.isDefault }) { "The default wake phrase cannot be removed." }
        return canonicalize(phrases.filterNot { it.id == id })
    }

    fun canonicalize(phrases: List<WakePhrase>): List<WakePhrase> {
        val default = phrases.firstOrNull { it.isDefault } ?: WakePhrase("default", DEFAULT_PHRASE, true, isDefault = true)
        val custom = phrases.filterNot { it.isDefault }.take(2)
        return listOf(default.copy(id = "default", phrase = DEFAULT_PHRASE, isDefault = true)) + custom
    }

    private fun validate(phrases: List<WakePhrase>, value: String): String {
        val phrase = value.trim().replace(Regex("\\s+"), " ")
        require(phrase.length in 2..40) { "Wake phrases must contain 2 to 40 characters." }
        val normalized = normalize(phrase)
        require(normalized.isNotBlank()) { "Wake phrases must include at least one letter or number." }
        require(phrases.none { normalize(it.phrase) == normalized }) { "That wake phrase is already configured." }
        return phrase
    }

    private fun normalize(text: String): String = text.lowercase()
        .replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

object WakeWord {
    const val DEFAULT_PHRASE = WakePhraseConfiguration.DEFAULT_PHRASE

    fun normalizePhrase(text: String): String = text.lowercase()
        .replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun match(transcript: String, phrase: String = DEFAULT_PHRASE): WakeWordMatch? {
        val normalizedTranscript = normalizePhrase(transcript)
        val normalizedPhrase = normalizePhrase(phrase)
        if (normalizedTranscript.isBlank() || normalizedPhrase.isBlank()) return null
        if (normalizedTranscript != normalizedPhrase && !normalizedTranscript.startsWith("$normalizedPhrase ")) return null
        return WakeWordMatch(normalizedTranscript.removePrefix(normalizedPhrase).trim())
    }

    fun match(transcript: String, phrases: List<WakePhrase>): Pair<WakePhrase, WakeWordMatch>? =
        phrases.asSequence()
            .filter { it.enabled }
            .sortedByDescending { normalizePhrase(it.phrase).length }
            .mapNotNull { phrase -> match(transcript, phrase.phrase)?.let { phrase to it } }
            .firstOrNull()

    private fun normalize(text: String): String = text.lowercase()
}