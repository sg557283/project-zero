package com.zero.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineRecognitionStateTest {
    @Test
    fun followsModelLoadToListeningToRecognizedResult() {
        val machine = OfflineRecognitionStateMachine()

        machine.begin("Android on-device")
        assertEquals(OfflineRecognitionState.LoadingModel("Android on-device"), machine.state)
        machine.listening("Android on-device")
        assertEquals(OfflineRecognitionState.Listening("Android on-device"), machine.state)
        machine.result("  नमस्ते  ")
        assertEquals(OfflineRecognitionState.Result("नमस्ते"), machine.state)
    }

    @Test
    fun exposesMissingModelLoadAudioAndCancellationStates() {
        val machine = OfflineRecognitionStateMachine()

        machine.begin(null)
        assertEquals(OfflineRecognitionState.Failed("No on-device speech recognizer is available"), machine.state)
        machine.begin("Android on-device")
        machine.fail("The on-device speech recognizer could not be loaded")
        assertEquals(OfflineRecognitionState.Failed("The on-device speech recognizer could not be loaded"), machine.state)
        machine.begin("Android on-device")
        machine.listening("Android on-device")
        machine.fail("Microphone audio could not be read")
        assertEquals(OfflineRecognitionState.Failed("Microphone audio could not be read"), machine.state)
        machine.cancel()
        assertEquals(OfflineRecognitionState.Cancelled, machine.state)
    }
}