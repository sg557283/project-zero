package com.zero.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnScreenActionParserTest {
    @Test
    fun parsesScreenActionsAndPreservesTypedText() {
        assertEquals(OnScreenAction.Click("Send"), OnScreenActionParser.parse("Tap Send"))
        assertEquals(OnScreenAction.TypeText("Hello, Zero!"), OnScreenActionParser.parse("Type Hello, Zero!"))
        assertEquals(OnScreenAction.ScrollForward, OnScreenActionParser.parse("scroll down"))
        assertEquals(OnScreenAction.ScrollForward, OnScreenActionParser.parse("neeche scroll karo"))
        assertEquals(OnScreenAction.Click("Settings"), OnScreenActionParser.parse("Settings par tap karo"))
        assertEquals(OnScreenAction.Back, OnScreenActionParser.parse("go back"))
        assertEquals(OnScreenAction.Home, OnScreenActionParser.parse("home"))
        assertEquals(OnScreenAction.PlayPause, OnScreenActionParser.parse("pause music"))
        assertEquals(OnScreenAction.NextTrack, OnScreenActionParser.parse("next track"))
        assertEquals(OnScreenAction.PreviousTrack, OnScreenActionParser.parse("previous song"))
    }

    @Test
    fun rejectsEmptyOrUnsupportedRequests() {
        assertNull(OnScreenActionParser.parse(""))
        assertNull(OnScreenActionParser.parse("click"))
        assertNull(OnScreenActionParser.parse("open settings"))
    }
}
