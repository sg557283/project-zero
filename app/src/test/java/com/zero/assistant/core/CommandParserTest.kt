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

    @Test fun parsesHelpAndUnsupportedInput() {
        assertEquals(AssistantCommand.Help, CommandParser.parse("help"))
        assertEquals(AssistantCommand.Unsupported, CommandParser.parse(""))
        assertEquals(AssistantCommand.Unsupported, CommandParser.parse("write me a poem"))
    }
}