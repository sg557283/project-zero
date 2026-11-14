package com.zero.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class OrbEmotionCueTest {
    @Test
    fun emptyReplyUsesNeutralCue() {
        assertEquals(OrbEmotionCue.Neutral, inferOrbEmotionCue(null))
        assertEquals(OrbEmotionCue.Neutral, inferOrbEmotionCue("   "))
    }

    @Test
    fun classifiesWarningsBeforeOtherSignals() {
        assertEquals(
            OrbEmotionCue.Warning,
            inferOrbEmotionCue("The offline service is unavailable. Would you like help?"),
        )
        assertEquals(OrbEmotionCue.Warning, inferOrbEmotionCue("The service is not ready."))
        assertEquals(OrbEmotionCue.Warning, inferOrbEmotionCue("The online provider is offline."))
    }

    @Test
    fun classifiesQuestionsIdeasAndPositiveReplies() {
        assertEquals(OrbEmotionCue.Curious, inferOrbEmotionCue("Which planner should I open?"))
        assertEquals(OrbEmotionCue.Neutral, inferOrbEmotionCue("I will show you the planner."))
        assertEquals(OrbEmotionCue.SmartIdea, inferOrbEmotionCue("Tip: you can add a shortcut."))
        assertEquals(OrbEmotionCue.Happy, inferOrbEmotionCue("Your note was saved successfully."))
    }

    @Test
    fun recognizesPlayfulReplies() {
        assertEquals(OrbEmotionCue.Cool, inferOrbEmotionCue("Cool, that is ready 😎"))
    }
}
