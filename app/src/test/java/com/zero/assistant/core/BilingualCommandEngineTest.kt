package com.zero.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Test

class BilingualCommandEngineTest {
    @Test fun splitsEnglishHindiAndHinglishStepsInOrder() {
        assertEquals(
            listOf("Torch on karo", "volume mute karo", "notes kholo"),
            MultitaskCommandExecutor.split("Torch on karo, volume mute karo, aur notes kholo"),
        )
        assertEquals(
            listOf("open YT", "play a song"),
            MultitaskCommandExecutor.split("open YT then play a song"),
        )
        assertEquals(
            listOf("Call Mamma", "text on WA that I am coming home"),
            MultitaskCommandExecutor.split("Call Mamma and text on WA that I am coming home"),
        )
        assertEquals(
            listOf("WhatsApp par Mamma ko message bhejo: kal aana"),
            MultitaskCommandExecutor.split("WhatsApp par Mamma ko message bhejo: kal aana"),
        )
        assertEquals(
            listOf("whatsapp text Mamma: I'm coming home; then open camera"),
            MultitaskCommandExecutor.split("whatsapp text Mamma: I'm coming home; then open camera"),
        )
    }

    @Test fun tracksOnlyTheMostRecentFiveTurnsAndResolvesTheLatestContact() {
        val memory = ConversationContextMemory()
        (1..6).forEach { index ->
            memory.add(ConversationTurn("turn $index", lastContactName = "Contact $index"))
        }

        assertEquals(5, memory.recent().size)
        assertEquals("turn 2", memory.recent().first().text)
        assertEquals("Contact 6", memory.resolvePronoun())
    }

    @Test fun resolvesContactPronounsForFollowUpMessages() {
        val memory = ConversationContextMemory()
        memory.remember("Call Mamma", AssistantCommand.DraftCallContact("Mamma"))

        assertEquals(
            "WhatsApp message Mamma: I'm coming home",
            memory.resolveFollowUp("Text her I'm coming home"),
        )
    }
}
