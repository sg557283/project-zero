package com.zero.assistant.core

import java.security.SecureRandom
import java.util.concurrent.TimeUnit

data class VaultAttemptState(
    val failures: Int = 0,
    val lockedUntilMillis: Long = 0L,
)

object VaultSecurity {
    const val MIN_PASSWORD_LENGTH = 6

    fun validatePassword(password: String): String? = when {
        password.length < MIN_PASSWORD_LENGTH -> "Use at least $MIN_PASSWORD_LENGTH characters."
        password.length > 128 -> "Use 128 characters or fewer."
        else -> null
    }

    fun recordFailure(state: VaultAttemptState, nowMillis: Long): VaultAttemptState {
        val failures = state.failures + 1
        val delaySeconds = when (failures) {
            in 1..4 -> 0L
            in 5..9 -> 30L
            else -> 300L
        }
        return VaultAttemptState(failures, if (delaySeconds == 0L) 0L else nowMillis + TimeUnit.SECONDS.toMillis(delaySeconds))
    }

    fun resetFailures(): VaultAttemptState = VaultAttemptState()

    fun remainingLockMillis(state: VaultAttemptState, nowMillis: Long): Long =
        (state.lockedUntilMillis - nowMillis).coerceAtLeast(0L)

    fun newSalt(size: Int = 16): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }

    fun newNonce(size: Int = 12): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }
}
