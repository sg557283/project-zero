package com.zero.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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

    @Test fun defaultPhraseIsPreservedWithAtMostTwoCustomPhrases() {
        val oneCustom = WakePhraseConfiguration.add(WakePhraseConfiguration.initial(), "Hello ZERO")
        val twoCustom = WakePhraseConfiguration.add(oneCustom, "Okay ZERO")

        assertEquals(listOf("Hey ZERO", "Hello ZERO", "Okay ZERO"), twoCustom.map { it.phrase })
        assertThrows(IllegalArgumentException::class.java) { WakePhraseConfiguration.add(twoCustom, "Wake up") }
        assertThrows(IllegalArgumentException::class.java) { WakePhraseConfiguration.remove(twoCustom, "default") }
    }

    @Test fun rejectsEmptyAndDuplicatePhrasesAndHonorsDisabledCustomPhrase() {
        val initial = WakePhraseConfiguration.initial()
        assertThrows(IllegalArgumentException::class.java) { WakePhraseConfiguration.add(initial, "  ") }
        assertThrows(IllegalArgumentException::class.java) { WakePhraseConfiguration.add(initial, "!!!") }
        assertThrows(IllegalArgumentException::class.java) { WakePhraseConfiguration.add(initial, "hey zero") }
        val configured = WakePhraseConfiguration.add(initial, "Hello ZERO")
        val disabled = WakePhraseConfiguration.setEnabled(configured, "custom-1", false)

        assertNull(WakeWord.match("Hello ZERO", disabled))
        assertEquals("open youtube", WakeWord.match("Hey ZERO, open YouTube", disabled)?.second?.command)
        assertEquals("hello zero", WakeWord.normalizePhrase("Hello, ZERO!"))
    }

    @Test fun migratesLegacyPhraseWithoutRemovingDefault() {
        assertEquals(
            listOf("Hey ZERO", "Computer"),
            WakePhraseConfiguration.initial("Computer").map { it.phrase },
        )
    }

    @Test fun serviceStatusRequiresARecentActiveHeartbeat() {
        assertEquals(true, WakeServiceHeartbeat.isFresh(true, 9_000L, 10_000L, 2_000L))
        assertEquals(false, WakeServiceHeartbeat.isFresh(true, 1_000L, 10_000L, 2_000L))
        assertEquals(false, WakeServiceHeartbeat.isFresh(false, 9_000L, 10_000L, 2_000L))
        assertEquals(false, WakeServiceHeartbeat.isFresh(true, 11_000L, 10_000L, 2_000L))
    }
}