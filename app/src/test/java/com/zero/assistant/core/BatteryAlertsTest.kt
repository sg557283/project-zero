package com.zero.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class BatteryAlertsTest {
    private class MemoryStorage(var value: BatteryAlertState = BatteryAlertState()) : BatteryAlertStorage {
        override fun read() = value
        override fun write(state: BatteryAlertState) { value = state }
    }

    @Test
    fun alertsOnlyOnAboveToAtOrBelowCrossingAndRearmsAfterRecovery() {
        val storage = MemoryStorage()
        val evaluator = BatteryAlertEvaluator(storage)

        assertTrue(evaluator.onSnapshot(BatterySnapshot(50, false)).isEmpty())
        assertEquals(listOf(BatteryAlertEvent.Low(20, 20)), evaluator.onSnapshot(BatterySnapshot(20, false)))
        assertTrue(evaluator.onSnapshot(BatterySnapshot(10, false)).isEmpty())
        evaluator.onSnapshot(BatterySnapshot(25, false))
        assertEquals(listOf(BatteryAlertEvent.Low(18, 20)), evaluator.onSnapshot(BatterySnapshot(18, false)))
    }

    @Test
    fun doesNotFireOnInitialBelowThresholdSample() {
        val evaluator = BatteryAlertEvaluator(MemoryStorage())
        assertTrue(evaluator.onSnapshot(BatterySnapshot(5, false)).isEmpty())
        assertTrue(evaluator.onSnapshot(BatterySnapshot(4, false)).isEmpty())
    }

    @Test
    fun chargingAndFullAlertsRespectIndependentSettings() {
        val storage = MemoryStorage()
        val evaluator = BatteryAlertEvaluator(storage)
        evaluator.updateSettings(BatteryAlertSettings(lowEnabled = false, chargingStartedEnabled = true, fullyChargedEnabled = true))
        evaluator.onSnapshot(BatterySnapshot(80, false))
        assertEquals(listOf(BatteryAlertEvent.ChargingStarted), evaluator.onSnapshot(BatterySnapshot(81, true)))
        assertEquals(listOf(BatteryAlertEvent.FullyCharged), evaluator.onSnapshot(BatterySnapshot(100, true)))
        assertTrue(evaluator.onSnapshot(BatterySnapshot(100, true)).isEmpty())
        evaluator.onSnapshot(BatterySnapshot(99, true))
        assertEquals(listOf(BatteryAlertEvent.FullyCharged), evaluator.onSnapshot(BatterySnapshot(100, true)))
    }

    @Test
    fun thresholdValidationCoversOneThroughOneHundred() {
        assertThrows(IllegalArgumentException::class.java) { BatteryAlertSettings(lowThreshold = 0) }
        assertThrows(IllegalArgumentException::class.java) { BatteryAlertSettings(lowThreshold = 101) }
        assertEquals(100, BatteryAlertSettings(lowThreshold = 100).lowThreshold)
    }
}