package com.zero.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultSecurityTest {
    @Test fun passwordLengthIsValidated() {
        assertTrue(VaultSecurity.validatePassword("short") != null)
        assertTrue(VaultSecurity.validatePassword("correct horse 2026") == null)
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

    @Test fun strongPasswordRequiresLetterAndNumber() {
        assertTrue(VaultSecurity.validatePassword("abcdefghijkl") != null)
        assertTrue(VaultSecurity.validatePassword("123456789012") != null)
        assertEquals(null, VaultSecurity.validatePassword("ZeroAssist2026"))
    }

    @Test fun sessionExpiresAfterConfiguredTimeout() {
        assertFalse(VaultSecurity.sessionExpired(1_000L, 1_001L, 60_000L))
        assertTrue(VaultSecurity.sessionExpired(1_000L, 61_000L, 60_000L))
    }
}
