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
    const val MAX_CUSTOM_PHRASES = 3
    private val BUILT_IN_PHRASES = listOf(DEFAULT_PHRASE, "OK ZERO", "Hey Shree")

    fun initial(legacyPhrase: String? = null): List<WakePhrase> {
        val phrases = builtInPhrases()
        val legacy = legacyPhrase?.trim().orEmpty()
        if (legacy.isNotEmpty() && BUILT_IN_PHRASES.none { normalize(it) == normalize(legacy) }) {
            phrases += WakePhrase("custom-1", legacy, true)
        }
        return phrases
    }

    fun add(phrases: List<WakePhrase>, value: String): List<WakePhrase> {
        val phrase = validate(phrases, value)
        require(phrases.count { !it.isDefault } < MAX_CUSTOM_PHRASES) {
            "At most three custom wake phrases are supported."
        }
        val id = (1..MAX_CUSTOM_PHRASES).map { "custom-$it" }.first { candidate -> phrases.none { it.id == candidate } }
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
        val defaults = builtInPhrases().map { builtIn ->
            val stored = phrases.firstOrNull { it.isDefault && normalize(it.phrase) == normalize(builtIn.phrase) }
            builtIn.copy(enabled = stored?.enabled ?: true)
        }
        val custom = phrases.filterNot { it.isDefault }.take(MAX_CUSTOM_PHRASES)
        return defaults + custom
    }

    fun enableBuiltInPhrases(phrases: List<WakePhrase>): List<WakePhrase> =
        canonicalize(phrases).map { if (it.isDefault) it.copy(enabled = true) else it }

    private fun builtInPhrases(): MutableList<WakePhrase> = BUILT_IN_PHRASES.mapIndexed { index, phrase ->
        WakePhrase("default-$index", phrase, enabled = true, isDefault = true)
    }.toMutableList()

    private fun validate(phrases: List<WakePhrase>, value: String): String {
        val phrase = value.trim().replace(Regex("\\s+"), " ")
        require(phrase.length in 2..40) { "Wake phrases must contain 2 to 40 characters." }
        val normalized = normalize(phrase)
        require(normalized.isNotBlank()) { "Wake phrases must include at least one letter or number." }
        require(phrases.none { normalize(it.phrase) == normalized }) { "That wake phrase is already configured." }
        return phrase
    }

    private fun normalize(text: String): String = WakeWord.normalizePhrase(text)
}

object WakeWord {
    const val DEFAULT_PHRASE = WakePhraseConfiguration.DEFAULT_PHRASE

    fun normalizePhrase(text: String): String = text.lowercase()
        .replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .replace(Regex("\\bzro\\b"), "zero")

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