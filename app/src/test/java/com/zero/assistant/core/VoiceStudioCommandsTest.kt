package com.zero.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceStudioCommandsTest {
    @Test
    fun appliesPunctuationCommands() {
        assertEquals("Hello.", VoiceStudioCommands.process("Hello", "full stop"))
        assertEquals("Hello,", VoiceStudioCommands.process("Hello", "comma"))
        assertEquals("Hello\n", VoiceStudioCommands.process("Hello", "new line"))
    }

    @Test
    fun deletesLastSentence() {
        assertEquals("First sentence.", VoiceStudioCommands.deleteLastSentence("First sentence. Second sentence!"))
    }

    @Test
    fun recognizesEmptyAndBlankText() {
        assertEquals("", VoiceStudioCommands.deleteLastSentence(""))
        assertEquals("", ScannerText.normalize(" \n "))
    }
}
