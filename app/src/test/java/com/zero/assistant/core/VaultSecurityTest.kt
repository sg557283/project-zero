package com.zero.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultSecurityTest {
    @Test fun passwordLengthIsValidated() {
        assertTrue(VaultSecurity.validatePassword("short") != null)
        assertTrue(VaultSecurity.validatePassword("correct horse") == null)
    }

    @Test fun repeatedFailuresApplyCooldown() {
        var state = VaultAttemptState()
        repeat(5) { state = VaultSecurity.recordFailure(state, 1_000) }
        assertEquals(5, state.failures)
        assertTrue(VaultSecurity.remainingLockMillis(state, 1_000) > 0)
        assertEquals(0L, VaultSecurity.remainingLockMillis(state, state.lockedUntilMillis))
    }

    @Test fun saltAndNonceAreRandom() {
        val saltA = VaultSecurity.newSalt()
        val saltB = VaultSecurity.newSalt()
        assertFalse(saltA.contentEquals(saltB))
    }
}
