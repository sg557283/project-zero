package com.zero.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationalVoiceEngineTest {
    @Test
    fun routesDevanagariAndCommonHinglishToHindi() {
        assertEquals(SpeechLanguage.Hindi, ConversationalVoiceEngine.languageFor("नमस्ते, बैटरी कितनी है?"))
        assertEquals(SpeechLanguage.Hindi, ConversationalVoiceEngine.languageFor("YouTube kholo"))
        assertEquals(SpeechLanguage.Hindi, ConversationalVoiceEngine.languageFor("Kal yaad dilana"))
    }

    @Test
    fun keepsPlainEnglishOnEnglishVoice() {
        assertEquals(SpeechLanguage.English, ConversationalVoiceEngine.languageFor("Open YouTube and play music"))
    }

    @Test
    fun selectsLocalizedThinkingCue() {
        assertEquals("Hmm, let's see.", ConversationalVoiceEngine.thinkingCue(SpeechLanguage.English))
        assertEquals("अच्छा, देखते हैं।", ConversationalVoiceEngine.thinkingCue(SpeechLanguage.Hindi))
    }
}
