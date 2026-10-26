package com.zero.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakeWordTest {
    @Test fun matchesDefaultPhraseAndKeepsFollowingCommand() {
        assertEquals(WakeWordMatch("open youtube"), WakeWord.match("Hey ZERO, open YouTube"))
    }

    @Test fun matchesCustomPhraseIgnoringCaseAndPunctuation() {
        assertEquals(WakeWordMatch("battery status"), WakeWord.match("Hello Assistant! Battery status", "Hello Assistant"))
    }

    @Test fun rejectsPhraseInsideAnotherWord() {
        assertNull(WakeWord.match("hey zerox open YouTube"))
    }

    @Test fun returnsEmptyCommandForWakePhraseAlone() {
        assertEquals(WakeWordMatch(""), WakeWord.match("Hey ZERO"))
    }
}