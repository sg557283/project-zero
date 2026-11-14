package com.zero.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackTapDetectorTest {
    @Test
    fun multiTapSequenceWaitsForWindowBeforeDispatchingDoubleOrTriple() {
        val detector = BackTapSequenceDetector()
        assertEquals(null, detector.recordTap(100L))
        assertEquals(null, detector.recordTap(220L))
        assertEquals(null, detector.flush(619L))
        assertEquals(2, detector.flush(620L))

        assertEquals(null, detector.recordTap(1_000L))
        assertEquals(null, detector.recordTap(1_100L))
        assertEquals(null, detector.recordTap(1_200L))
        assertEquals(3, detector.flush(1_600L))
    }

    @Test
    fun fourthTapDispatchesImmediatelyAndSingleTapIsIgnored() {
        val detector = BackTapSequenceDetector()
        repeat(3) { index -> assertEquals(null, detector.recordTap(index * 100L)) }
        assertEquals(4, detector.recordTap(300L))
        assertEquals(null, detector.flush(800L))

        assertEquals(null, detector.recordTap(1_000L))
        assertEquals(null, detector.flush(1_400L))
    }

    @Test
    fun pulseDetectorRequiresReleaseBeforeAnotherTap() {
        val detector = BackTapPulseDetector()
        assertTrue(detector.sample(30f, 0f, 0f, 100L))
        assertFalse(detector.sample(30f, 0f, 0f, 250L))
        assertFalse(detector.sample(0f, 0f, 9.8f, 300L))
        assertTrue(detector.sample(30f, 0f, 0f, 350L))
    }
}
