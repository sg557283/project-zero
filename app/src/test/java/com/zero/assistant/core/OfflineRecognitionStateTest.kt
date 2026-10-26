package com.zero.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineRecognitionStateTest {
    @Test
    fun followsModelLoadToListeningToRecognizedResult() {
        val machine = OfflineRecognitionStateMachine()

        machine.begin("vosk-hi-small-0.22")
        assertEquals(OfflineRecognitionState.LoadingModel("vosk-hi-small-0.22"), machine.state)
        machine.listening("vosk-hi-small-0.22")
        assertEquals(OfflineRecognitionState.Listening("vosk-hi-small-0.22"), machine.state)
        machine.result("  नमस्ते  ")
        assertEquals(OfflineRecognitionState.Result("नमस्ते"), machine.state)
    }

    @Test
    fun exposesMissingModelLoadAudioAndCancellationStates() {
        val machine = OfflineRecognitionStateMachine()

        machine.begin(null)
        assertEquals(OfflineRecognitionState.Failed("No speech model is installed"), machine.state)
        machine.begin("english")
        machine.fail("The speech model could not be loaded")
        assertEquals(OfflineRecognitionState.Failed("The speech model could not be loaded"), machine.state)
        machine.begin("english")
        machine.listening("english")
        machine.fail("Microphone audio could not be read")
        assertEquals(OfflineRecognitionState.Failed("Microphone audio could not be read"), machine.state)
        machine.cancel()
        assertEquals(OfflineRecognitionState.Cancelled, machine.state)
    }
}