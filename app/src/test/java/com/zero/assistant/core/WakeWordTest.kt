package com.zero.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WakeWordTest {
    @Test fun matchesDefaultPhraseAndKeepsFollowingCommand() {
        assertEquals(WakeWordMatch("open youtube"), WakeWord.match("Hey ZERO, open YouTube"))
        assertEquals(WakeWordMatch("open youtube"), WakeWord.match("Hey zro, open YouTube"))
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

    @Test fun keepsThreeBuiltInsAndAllowsThreeCustomPhrases() {
        val initial = WakePhraseConfiguration.initial()
        val withCustomPhrases = listOf("Hello ZERO", "Okay ZERO", "Computer")
            .fold(initial) { phrases, phrase -> WakePhraseConfiguration.add(phrases, phrase) }

        assertEquals(
            listOf("Hey ZERO", "OK ZERO", "Hey Shree", "Hello ZERO", "Okay ZERO", "Computer"),
            withCustomPhrases.map { it.phrase },
        )
        assertThrows(IllegalArgumentException::class.java) { WakePhraseConfiguration.add(withCustomPhrases, "Wake up") }
        assertThrows(IllegalArgumentException::class.java) { WakePhraseConfiguration.remove(withCustomPhrases, "default-0") }
    }

    @Test fun builtInPhrasesCanBeDisabledAndRestoredIndependently() {
        val initial = WakePhraseConfiguration.initial()
        val disabled = WakePhraseConfiguration.setEnabled(initial, "default-1", false)

        assertEquals(false, disabled.first { it.id == "default-1" }.enabled)
        assertEquals(true, disabled.first { it.id == "default-0" }.enabled)
        assertEquals(
            true,
            WakePhraseConfiguration.enableBuiltInPhrases(disabled).first { it.id == "default-1" }.enabled,
        )
    }

    @Test fun rejectsEmptyAndDuplicatePhrasesAndHonorsDisabledCustomPhrase() {
        val initial = WakePhraseConfiguration.initial()
        assertThrows(IllegalArgumentException::class.java) { WakePhraseConfiguration.add(initial, "  ") }
        assertThrows(IllegalArgumentException::class.java) { WakePhraseConfiguration.add(initial, "!!!") }
        assertThrows(IllegalArgumentException::class.java) { WakePhraseConfiguration.add(initial, "hey zero") }
        assertThrows(IllegalArgumentException::class.java) { WakePhraseConfiguration.add(initial, "hey zro") }
        assertThrows(IllegalArgumentException::class.java) { WakePhraseConfiguration.add(initial, "OK ZERO") }
        val configured = WakePhraseConfiguration.add(initial, "Hello ZERO")
        val disabled = WakePhraseConfiguration.setEnabled(configured, "custom-1", false)

        assertNull(WakeWord.match("Hello ZERO", disabled))
        assertEquals("open youtube", WakeWord.match("Hey ZERO, open YouTube", disabled)?.second?.command)
        assertEquals("hello zero", WakeWord.normalizePhrase("Hello, ZERO!"))
    }

    @Test fun migratesLegacyPhraseWithoutRemovingDefault() {
        assertEquals(
            listOf("Hey ZERO", "OK ZERO", "Hey Shree", "Computer"),
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