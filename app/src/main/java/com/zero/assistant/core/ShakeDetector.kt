package com.zero.assistant.core

import kotlin.math.abs
import kotlin.math.sqrt

enum class ShakeSensitivity(val threshold: Float, val minimumPeakGapMillis: Long, val maximumPeakGapMillis: Long) {
    LOW(12.5f, 180L, 900L),
    NORMAL(10.5f, 150L, 750L),
    HIGH(8.8f, 120L, 650L),
}

class DoubleShakeDetector(private val sensitivity: ShakeSensitivity = ShakeSensitivity.NORMAL) {
    private var firstPeakAt = 0L
    private var lastPeakAt = 0L
    private var lastAcceptedAt: Long? = null
    private var armed = true

    fun reset() { firstPeakAt = 0L; lastPeakAt = 0L; lastAcceptedAt = null; armed = true }

    fun sample(x: Float, y: Float, z: Float, nowMillis: Long): Boolean {
        val magnitude = sqrt(x * x + y * y + z * z)
        val currentPeak = abs(magnitude - GRAVITY)
        if (currentPeak < sensitivity.threshold) {
            if (lastPeakAt == 0L || nowMillis - lastPeakAt >= sensitivity.minimumPeakGapMillis) armed = true
            if (nowMillis - firstPeakAt > sensitivity.maximumPeakGapMillis && firstPeakAt != 0L) {
                armed = true
            }
            return false
        }
        if (!armed || (lastPeakAt != 0L && nowMillis - lastPeakAt < sensitivity.minimumPeakGapMillis)) return false
        armed = false
        lastPeakAt = nowMillis
        if (firstPeakAt == 0L) {
            firstPeakAt = nowMillis
            return false
        }
        val gap = nowMillis - firstPeakAt
        val cooldownExpired = lastAcceptedAt?.let { nowMillis - it >= COOLDOWN_MILLIS } ?: true
        if (gap in sensitivity.minimumPeakGapMillis..sensitivity.maximumPeakGapMillis && cooldownExpired) {
            lastAcceptedAt = nowMillis
            firstPeakAt = 0L
            return true
        }
        if (gap > sensitivity.maximumPeakGapMillis) firstPeakAt = nowMillis
        return false
    }

    companion object { const val GRAVITY = 9.81f; const val COOLDOWN_MILLIS = 2_000L }
}
