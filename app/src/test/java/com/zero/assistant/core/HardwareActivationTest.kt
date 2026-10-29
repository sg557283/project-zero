package com.zero.assistant.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareActivationTest {
    @Test fun hardwareActivationIsDebounced() {
        assertTrue(HardwareActivationDebouncer.accept(2_000L, 0L))
        assertFalse(HardwareActivationDebouncer.accept(2_999L, 1_000L))
        assertTrue(HardwareActivationDebouncer.accept(3_001L, 1_000L))
    }

    @Test fun activationStateRejectsDuplicateWake() {
        val state = AssistantActivation.acceptWake(ActivationState(serviceRunning = true, captureActive = true), 5_000L, 0L)
        assertTrue(state)
        assertFalse(AssistantActivation.acceptWake(ActivationState(serviceRunning = true, captureActive = true, wakeTriggered = true), 5_000L, 0L))
    }

    @Test fun doubleShakeAcceptsTwoDistinctPeaks() {
        val detector = DoubleShakeDetector(ShakeSensitivity.NORMAL)
        assertFalse(detector.sample(25f, 0f, 0f, 100L))
        assertFalse(detector.sample(0f, 0f, 9.81f, 300L))
        assertTrue(detector.sample(-25f, 0f, 0f, 700L))
    }

    @Test fun noisyAndRepeatedSamplesDoNotToggle() {
        val detector = DoubleShakeDetector(ShakeSensitivity.NORMAL)
        repeat(20) { detector.sample(0f, 0f, 9.81f + it * 0.01f, it * 100L) }
        assertFalse(detector.sample(20f, 0f, 0f, 2_100L))
        assertFalse(detector.sample(20f, 0f, 0f, 2_120L))
    }
}
