package com.zero.assistant.core

class BackTapPulseDetector(
    private val threshold: Float = 14f,
    private val rearmThreshold: Float = 5f,
    private val minimumTapIntervalMs: Long = 90L,
) {
    private var armed = true
    private var lastTapAt = -minimumTapIntervalMs

    fun sample(x: Float, y: Float, z: Float, nowMs: Long): Boolean {
        val magnitude = kotlin.math.sqrt(x * x + y * y + z * z)
        val deviation = kotlin.math.abs(magnitude - SensorGravity.GRAVITY)
        if (deviation <= rearmThreshold) armed = true
        if (!armed || deviation < threshold || nowMs - lastTapAt < minimumTapIntervalMs) return false
        armed = false
        lastTapAt = nowMs
        return true
    }

    private object SensorGravity {
        const val GRAVITY = 9.80665f
    }
}

class BackTapSequenceDetector(private val sequenceWindowMs: Long = 400L) {
    private var tapCount = 0
    private var lastTapAt = 0L

    val pendingTapCount: Int get() = tapCount

    fun recordTap(nowMs: Long): Int? {
        val completed = flush(nowMs)
        tapCount++
        lastTapAt = nowMs
        if (tapCount == 4) {
            tapCount = 0
            return 4
        }
        return completed
    }

    fun flush(nowMs: Long): Int? {
        if (tapCount == 0 || nowMs - lastTapAt < sequenceWindowMs) return null
        val completed = tapCount
        tapCount = 0
        return completed.takeIf { it in 2..4 }
    }

    fun reset() {
        tapCount = 0
        lastTapAt = 0L
    }
}
