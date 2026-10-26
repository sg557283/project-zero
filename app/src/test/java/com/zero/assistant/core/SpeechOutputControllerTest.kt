package com.zero.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechOutputControllerTest {
    private class FakeEngine(
        var initializationSucceeds: Boolean = true,
        var availableLanguages: Set<String> = setOf("en-US", "hi-IN"),
        var speakingSucceeds: Boolean = true,
    ) : SpeechOutputEngine {
        var initializationCallback: ((Boolean) -> Unit)? = null
        val selectedLanguages = mutableListOf<String>()
        val spoken = mutableListOf<String>()
        var stopped = false
        var shutDown = false

        override fun initialize(onInitialized: (Boolean) -> Unit) {
            initializationCallback = onInitialized
        }

        override fun selectOfflineVoice(languageTag: String): Boolean {
            selectedLanguages.add(languageTag)
            return languageTag in availableLanguages
        }

        override fun speak(text: String): Boolean {
            if (speakingSucceeds) spoken.add(text)
            return speakingSucceeds
        }

        override fun stop() {
            stopped = true
        }

        override fun shutdown() {
            shutDown = true
        }

        fun finishInitialization() = initializationCallback?.invoke(initializationSucceeds)
    }

    @Test
    fun waitsForInitializationBeforeSpeakingQueuedResults() {
        val engine = FakeEngine()
        val controller = SpeechOutputController(engine) {}
        controller.initialize()
        controller.speak("Opening YouTube.")

        assertTrue(engine.spoken.isEmpty())
        engine.finishInitialization()

        assertEquals(listOf("Opening YouTube."), engine.spoken)
        assertEquals(SpeechOutputStatus.Ready(SpeechLanguage.English), controller.status)
    }

    @Test
    fun reportsInitializationFailureAndDropsQueuedSpeech() {
        val engine = FakeEngine(initializationSucceeds = false)
        val controller = SpeechOutputController(engine) {}
        controller.initialize()
        controller.speak("This must not be spoken.")

        engine.finishInitialization()

        assertEquals(SpeechOutputStatus.InitializationFailed, controller.status)
        assertTrue(engine.spoken.isEmpty())
    }

    @Test
    fun reportsMissingOfflineLanguageAndDoesNotSpeak() {
        val engine = FakeEngine(availableLanguages = setOf("en-US"))
        val controller = SpeechOutputController(engine) {}
        controller.setLanguage(SpeechLanguage.Hindi)
        controller.initialize()
        engine.finishInitialization()
        controller.speak("नमस्ते")

        assertEquals(SpeechOutputStatus.LanguageUnavailable(SpeechLanguage.Hindi), controller.status)
        assertEquals(listOf("hi-IN"), engine.selectedLanguages)
        assertTrue(engine.spoken.isEmpty())
    }

    @Test
    fun testSpeechUsesSelectedLanguageAndCloseShutsDownEngine() {
        val engine = FakeEngine()
        val controller = SpeechOutputController(engine) {}
        controller.initialize()
        engine.finishInitialization()
        controller.setLanguage(SpeechLanguage.Hindi)
        controller.testSpeech()
        controller.close()

        assertEquals("नमस्ते। ZERO हिंदी में बोल रहा है।", engine.spoken.single())
        assertTrue(engine.stopped)
        assertTrue(engine.shutDown)
        assertEquals(SpeechOutputStatus.Closed, controller.status)
    }

    @Test
    fun reportsSpeechEngineFailure() {
        val engine = FakeEngine(speakingSucceeds = false)
        val controller = SpeechOutputController(engine) {}
        controller.initialize()
        engine.finishInitialization()

        controller.speak("This will fail.")

        assertEquals(SpeechOutputStatus.SpeakFailed, controller.status)
    }
}