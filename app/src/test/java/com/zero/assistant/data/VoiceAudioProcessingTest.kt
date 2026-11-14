package com.zero.assistant.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAudioProcessingTest {
    @Test
    fun preGainRaisesSoftInputAndKeepsSamplesBounded() {
        val input = 3_277

        val boosted = VoiceAudioProcessing.applyPreGain(input.toShort())

        assertTrue(boosted > input / 32_768f)
        assertTrue(boosted < 1f)
        assertEquals(-boosted, VoiceAudioProcessing.applyPreGain((-input).toShort()), 0.0001f)
        assertEquals(0f, VoiceAudioProcessing.applyPreGain(0), 0f)
    }

    @Test
    fun fullScaleInputSoftClipsInsteadOfExceedingPcmRange() {
        val boosted = VoiceAudioProcessing.applyPreGain(Short.MAX_VALUE)

        assertTrue(boosted in 0f..1f)
        assertTrue(boosted < 1f)
    }
}
