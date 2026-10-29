package com.zero.assistant.core

object UniversalCommandActions {
    fun normalize(input: String): String = input
        .trim()
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s]"), " ")
        .replace(Regex("\\s+"), " ")

    fun toggleFlashlight(current: Boolean?): Boolean? = when (current) {
        true -> false
        false -> true
        null -> null
    }

    fun shouldExecute(text: String, previousText: String?, nowMillis: Long, previousMillis: Long, windowMillis: Long = 1_500L): Boolean =
        previousText == null || !normalize(text).equals(normalize(previousText), ignoreCase = true) || nowMillis - previousMillis >= windowMillis
}
