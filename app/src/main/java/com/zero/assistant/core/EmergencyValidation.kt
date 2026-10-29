package com.zero.assistant.core

object EmergencyValidation {
    fun normalizePhone(value: String): String? {
        val normalized = value.trim().replace(Regex("[\\s()-]"), "")
        if (!normalized.matches(Regex("\\+?[0-9]{7,15}"))) return null
        return normalized
    }

    fun validateMessage(value: String): String? {
        val clean = value.trim()
        return when {
            clean.isEmpty() -> "Enter an approved help message."
            clean.length > 500 -> "Keep the help message to 500 characters or fewer."
            else -> null
        }
    }
}
