package com.zero.assistant.core

import java.util.ArrayDeque

enum class SpeechLanguage(val tag: String, val label: String) {
    English("en-US", "English"),
    Hindi("hi-IN", "Hindi"),
}

sealed interface SpeechOutputStatus {
    data object Initializing : SpeechOutputStatus
    data class Ready(val language: SpeechLanguage) : SpeechOutputStatus
    data class LanguageUnavailable(val language: SpeechLanguage) : SpeechOutputStatus
    data object InitializationFailed : SpeechOutputStatus
    data object SpeakFailed : SpeechOutputStatus
    data object Closed : SpeechOutputStatus
}

interface SpeechOutputEngine {
    fun initialize(onInitialized: (Boolean) -> Unit)
    fun selectOfflineVoice(languageTag: String): Boolean
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

    var status: SpeechOutputStatus = SpeechOutputStatus.Initializing
        private set

    fun initialize() {
        if (!closed && !initialized) engine.initialize(::onInitialized)
    }

    fun setLanguage(language: SpeechLanguage) {
        this.language = language
        if (initialized && !closed) configureLanguage()
    }

    fun speak(text: String) {
        if (closed || text.isBlank()) return
        if (!initialized) {
            pending.addLast(text)
            return
        }
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
        if (!closed) engine.stop()
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
        updateStatus(SpeechOutputStatus.Ready(language))
        while (pending.isNotEmpty() && status is SpeechOutputStatus.Ready && !closed) {
            speakWhenReady(pending.removeFirst())
        }
    }

    private fun speakWhenReady(text: String) {
        if (status !is SpeechOutputStatus.Ready) return
        if (!engine.speak(text)) updateStatus(SpeechOutputStatus.SpeakFailed)
    }

    private fun updateStatus(next: SpeechOutputStatus) {
        status = next
        onStatusChanged(next)
    }
}