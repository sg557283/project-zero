package com.zero.assistant.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultVisibilityTest {
    @Test fun visibilityToggleIsImmediateAndDoesNotMutateEntry() {
        val entry = Any()
        assertFalse(VaultVisibility.action(false))
        assertTrue(VaultVisibility.action(true))
        assertSame(entry, VaultVisibility.visibleEntry(true, entry))
        assertNull(VaultVisibility.visibleEntry(false, entry))
    }

    @Test fun visibilityStateIsStableAcrossRestarts() {
        assertFalse(VaultVisibility.setVisible(false, false))
        assertTrue(VaultVisibility.setVisible(false, true))
        assertTrue(VaultVisibility.setVisible(true, true))
    }
}
