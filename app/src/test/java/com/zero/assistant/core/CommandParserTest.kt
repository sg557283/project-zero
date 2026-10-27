package com.zero.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandParserTest {
    @Test fun parsesEnglishAppCommand() {
        assertEquals(AssistantCommand.OpenApp(AppTarget.YouTube), CommandParser.parse("Open YouTube"))
    }

    @Test fun parsesHinglishAppCommand() {
        assertEquals(AssistantCommand.OpenApp(AppTarget.YouTube), CommandParser.parse("YouTube kholo"))
        assertEquals(AssistantCommand.OpenApp(AppTarget.WhatsApp), CommandParser.parse("WhatsApp kholo"))
        assertEquals(AssistantCommand.OpenApp(AppTarget.Settings), CommandParser.parse("Settings kholo"))
        assertEquals(AssistantCommand.OpenApp(AppTarget.Camera), CommandParser.parse("Camera kholo"))
    }

    @Test fun parsesHindiScriptCommands() {
        assertEquals(AssistantCommand.TellTime, CommandParser.parse("अभी कितने बजे हैं?"))
        assertEquals(AssistantCommand.OpenApp(AppTarget.YouTube), CommandParser.parse("यूट्यूब खोलो"))
        assertEquals(AssistantCommand.SetFlashlight(true), CommandParser.parse("टॉर्च चालू करो"))
        assertEquals(AssistantCommand.SetFlashlight(false), CommandParser.parse("टॉर्च बंद करो"))
    }

    @Test fun parsesTorchCommands() {
        assertEquals(AssistantCommand.SetFlashlight(true), CommandParser.parse("Torch chalu karo"))
        assertEquals(AssistantCommand.SetFlashlight(false), CommandParser.parse("Torch band karo"))
        assertEquals(AssistantCommand.SetFlashlight(true), CommandParser.parse("Turn flashlight on"))
        assertEquals(AssistantCommand.SetFlashlight(false), CommandParser.parse("Turn flashlight off"))
        assertEquals(AssistantCommand.Unsupported, CommandParser.parse("flashlight"))
    }

    @Test fun parsesTimeInEnglishAndHinglish() {
        assertEquals(AssistantCommand.TellTime, CommandParser.parse("What time is it?"))
        assertEquals(AssistantCommand.TellTime, CommandParser.parse("Abhi kitne baje hain?"))
    }

    @Test fun parsesBatteryStatusInEnglishAndHindi() {
        assertEquals(AssistantCommand.BatteryStatus, CommandParser.parse("battery status"))
        assertEquals(AssistantCommand.BatteryStatus, CommandParser.parse("बैटरी कितनी है"))
    }

    @Test fun parsesConfirmedCallTargetsButRejectsShortNumbers() {
        assertEquals(AssistantCommand.CallNumber("+15551234567"), CommandParser.parse("call +1 (555) 123-4567"))
        assertEquals(AssistantCommand.CallNumber("1234567890"), CommandParser.parse("dial 123 456 7890"))
        assertEquals(AssistantCommand.Unsupported, CommandParser.parse("call 911"))
    }

    @Test fun parsesLocalNotesPlannerAndShortcutCommands() {
        assertEquals(AssistantCommand.SaveNote("buy milk"), CommandParser.parse("note buy milk"))
        assertEquals(AssistantCommand.DraftReminder("call Sam"), CommandParser.parse("remind me to call Sam"))
        assertEquals(AssistantCommand.ShowPlanner, CommandParser.parse("show today's tasks"))
        assertEquals(AssistantCommand.ShowNotes, CommandParser.parse("show notes"))
        assertEquals(AssistantCommand.RunShortcut("morning routine"), CommandParser.parse("run Morning Routine"))
    }

    @Test fun parsesYouTubeSearchAndWhatsAppMessageDrafts() {
        assertEquals(AssistantCommand.SearchYouTube("Shreeyansh"), CommandParser.parse("Open YouTube and search for Shreeyansh"))
        assertEquals(
            AssistantCommand.DraftWhatsAppMessage("Mamma", "I’m coming home early."),
            CommandParser.parse("Open WhatsApp and text Mamma: I’m coming home early."),
        )
    }

    @Test fun parsesHelpAndUnsupportedInput() {
        assertEquals(AssistantCommand.Help, CommandParser.parse("help"))
        assertEquals(AssistantCommand.Unsupported, CommandParser.parse(""))
        assertEquals(AssistantCommand.Unsupported, CommandParser.parse("write me a poem"))
    }

    @Test fun recognizesEnglishHindiAndHinglishIdentityQuestionsOffline() {
        assertEquals(AssistantCommand.Identity, CommandParser.parse("Who are you?"))
        assertEquals(AssistantCommand.Identity, CommandParser.parse("Who is ZERO?"))
        assertEquals(AssistantCommand.Identity, CommandParser.parse("Tum kaun ho?"))
        assertEquals(AssistantCommand.Identity, CommandParser.parse("तुम कौन हो?"))
        assertEquals(
            "I am ZERO, your personal assistant to make your work and life balanced and easy. I am a test product of Shree.",
            AssistantIdentity.RESPONSE,
        )
    }
}