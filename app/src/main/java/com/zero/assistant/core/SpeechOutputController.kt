package com.zero.assistant.core

import java.util.ArrayDeque

enum class SpeechLanguage(val tag: String, val label: String) {
    English("en-US", "English"),
    Hindi("hi-IN", "Hindi"),
}

enum class VoiceLibraryCategory(val label: String) {
    Simple("Simple"),
    EmotionBased("Emotion-Based"),
    OfflineAI("Offline AI"),
}

data class OfflineTtsVoice(
    val id: String,
    val displayName: String,
    val languageTag: String,
    val languageLabel: String,
    val quality: Int,
)

sealed interface SpeechOutputStatus {
    data object Initializing : SpeechOutputStatus
    data class Ready(val language: SpeechLanguage) : SpeechOutputStatus
    data class ReadyWithVoice(val voice: OfflineTtsVoice) : SpeechOutputStatus
    data class LanguageUnavailable(val language: SpeechLanguage) : SpeechOutputStatus
    data object InitializationFailed : SpeechOutputStatus
    data object SpeakFailed : SpeechOutputStatus
    data object Speaking : SpeechOutputStatus
    data object Closed : SpeechOutputStatus
}

interface SpeechOutputEngine {
    fun initialize(onInitialized: (Boolean) -> Unit)
    fun selectOfflineVoice(languageTag: String): Boolean
    fun availableOfflineVoices(): List<OfflineTtsVoice> = emptyList()
    fun selectVoice(voiceId: String): Boolean = false
    fun setOnSpeechComplete(listener: () -> Unit) = Unit
    fun setOnSpeechError(listener: (String) -> Unit) = Unit
    fun prepareForVoiceModelRemoval(onReady: () -> Unit) = onReady()
    fun speak(text: String): Boolean
    fun stop()
    fun shutdown()
}

class SpeechOutputController(
    private val engine: SpeechOutputEngine,
    private val onStatusChanged: (SpeechOutputStatus) -> Unit,
) {
    private val pending = ArrayDeque<String>()
    private var initialized = false
    private var closed = false
    private var language = SpeechLanguage.English
    private var selectedVoice: OfflineTtsVoice? = null
    private var readyStatus: SpeechOutputStatus = SpeechOutputStatus.Ready(language)

    var status: SpeechOutputStatus = SpeechOutputStatus.Initializing
        private set

    fun initialize() {
        if (!closed && !initialized) {
            engine.setOnSpeechComplete {
                if (!closed && status == SpeechOutputStatus.Speaking) {
                    updateStatus(readyStatus)
                    speakNextPending()
                }
            }
            engine.setOnSpeechError {
                if (!closed) {
                    pending.clear()
                    updateStatus(SpeechOutputStatus.SpeakFailed)
                }
            }
            engine.initialize(::onInitialized)
        }
    }

    fun setLanguage(language: SpeechLanguage) {
        this.language = language
        selectedVoice = null
        if (initialized && !closed) configureLanguage()
    }

    fun availableOfflineVoices(): List<OfflineTtsVoice> = engine.availableOfflineVoices()

    fun selectVoice(voiceId: String): Boolean {
        if (!initialized || closed) return false
        val voice = engine.availableOfflineVoices().firstOrNull { it.id == voiceId } ?: return false
        if (!engine.selectVoice(voiceId)) return false
        selectedVoice = voice
        readyStatus = SpeechOutputStatus.ReadyWithVoice(voice)
        updateStatus(readyStatus)
        return true
    }

    fun speak(text: String) {
        if (closed || text.isBlank()) return
        if (!initialized || status == SpeechOutputStatus.Speaking) {
            pending.addLast(text)
            return
        }
        if (status == SpeechOutputStatus.SpeakFailed) updateStatus(readyStatus)
        speakWhenReady(text)
    }

    fun testSpeech() {
        speak(
            when (language) {
                SpeechLanguage.English -> "Hello. This is ZERO speaking English."
                SpeechLanguage.Hindi -> "नमस्ते। ZERO हिंदी में बोल रहा है।"
            },
        )
    }

    fun testSpeech(language: SpeechLanguage) {
        setLanguage(language)
        testSpeech()
    }

    fun stop() {
        pending.clear()
        if (!closed) {
            engine.stop()
            if (status == SpeechOutputStatus.Speaking) updateStatus(readyStatus)
        }
    }

    fun prepareForVoiceModelRemoval(onReady: () -> Unit) {
        pending.clear()
        if (closed) {
            onReady()
            return
        }
        engine.stop()
        selectedVoice = null
        if (initialized && engine.selectOfflineVoice(language.tag)) {
            readyStatus = SpeechOutputStatus.Ready(language)
        } else if (initialized) {
            readyStatus = SpeechOutputStatus.LanguageUnavailable(language)
        }
        engine.prepareForVoiceModelRemoval {
            if (!closed) updateStatus(readyStatus)
            onReady()
        }
    }

    fun close() {
        if (closed) return
        closed = true
        initialized = false
        pending.clear()
        engine.stop()
        engine.shutdown()
        updateStatus(SpeechOutputStatus.Closed)
    }

    private fun onInitialized(success: Boolean) {
        if (closed) return
        if (!success) {
            pending.clear()
            updateStatus(SpeechOutputStatus.InitializationFailed)
            return
        }
        initialized = true
        configureLanguage()
    }

    private fun configureLanguage() {
        if (!engine.selectOfflineVoice(language.tag)) {
            pending.clear()
            updateStatus(SpeechOutputStatus.LanguageUnavailable(language))
            return
        }
        readyStatus = SpeechOutputStatus.Ready(language)
        updateStatus(readyStatus)
        speakNextPending()
    }

    private fun speakNextPending() {
        if (pending.isNotEmpty() && !closed &&
            (status is SpeechOutputStatus.Ready || status is SpeechOutputStatus.ReadyWithVoice)
        ) {
            speakWhenReady(pending.removeFirst())
        }
    }

    private fun speakWhenReady(text: String) {
        if (status !is SpeechOutputStatus.Ready && status !is SpeechOutputStatus.ReadyWithVoice) return
        val targetLanguage = ConversationalVoiceEngine.languageFor(text)
        val selected = selectedVoice
        val restoredSelection = selected != null &&
            selected.languageTag.startsWith(targetLanguage.tag.substringBefore('-'), ignoreCase = true) &&
            engine.selectVoice(selected.id)
        if (restoredSelection) {
            readyStatus = SpeechOutputStatus.ReadyWithVoice(requireNotNull(selected))
        } else if (engine.selectOfflineVoice(targetLanguage.tag)) {
            readyStatus = SpeechOutputStatus.Ready(targetLanguage)
        }
        updateStatus(SpeechOutputStatus.Speaking)
        if (!engine.speak(text)) {
            pending.clear()
            updateStatus(SpeechOutputStatus.SpeakFailed)
        }
    }

    private fun updateStatus(next: SpeechOutputStatus) {
        status = next
        onStatusChanged(next)
    }
}