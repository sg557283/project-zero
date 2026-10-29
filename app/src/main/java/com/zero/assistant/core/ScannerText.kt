package com.zero.assistant.core

object ScannerText {
    fun normalize(text: String): String =
        text.replace('\u00a0', ' ')
            .lines()
            .joinToString("\n") { line -> line.trim().replace(Regex("[ \\t]+"), " ") }
            .trim()

    fun searchable(text: String): Boolean = normalize(text).isNotBlank()

    fun isLikelyLowQuality(text: String): Boolean =
        normalize(text).isBlank() || normalize(text).replace(Regex("[\\p{L}\\p{N}]"), "").length > normalize(text).length / 3
}
