package com.zero.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmergencyValidationTest {
    @Test fun validInternationalAndLocalNumbersNormalize() {
        assertEquals("+919876543210", EmergencyValidation.normalizePhone(" +91 98765-43210 "))
        assertEquals("9876543210", EmergencyValidation.normalizePhone("98765 43210"))
        assertNull(EmergencyValidation.normalizePhone("123-ABC"))
    }

    @Test fun rejectsInvalidLengths() {
        assertNull(EmergencyValidation.normalizePhone("123"))
        assertNull(EmergencyValidation.normalizePhone("+1234567890123456"))
    }

    @Test fun messageIsLimitedAndNotBlank() {
        assertEquals("Enter an approved help message.", EmergencyValidation.validateMessage("  "))
        assertEquals("Keep the help message to 500 characters or fewer.", EmergencyValidation.validateMessage("x".repeat(501)))
        assertNull(EmergencyValidation.validateMessage("Please call me."))
    }
}
