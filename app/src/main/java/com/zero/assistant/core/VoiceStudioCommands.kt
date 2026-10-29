package com.zero.assistant.core

object VoiceStudioCommands {
    fun applyPunctuation(text: String, command: String): String {
        val normalized = text.trimEnd()
        return when (command.trim().lowercase()) {
            "full stop" -> "$normalized."
            "comma" -> "$normalized,"
            "new line" -> "$normalized\n"
            else -> text
        }
    }

    fun deleteLastSentence(text: String): String {
        val trimmed = text.trimEnd()
        val boundaries = trimmed.mapIndexedNotNull { index, character ->
            if (character == '.' || character == '?' || character == '!' || character == '\n') index else null
        }
        if (boundaries.isEmpty()) return ""
        val finalBoundary = boundaries.last()
        val previousBoundary = if (finalBoundary == trimmed.lastIndex && boundaries.size > 1) boundaries[boundaries.lastIndex - 1] else boundaries.getOrNull(boundaries.lastIndex - 1)
        return if (previousBoundary != null) {
            trimmed.substring(0, previousBoundary + 1).trimEnd()
        } else ""
    }

    fun process(text: String, command: String): String {
        val normalizedCommand = command.trim().lowercase()
        return if (normalizedCommand == "delete last sentence") {
            deleteLastSentence(text)
        } else {
            applyPunctuation(text, normalizedCommand)
        }
    }
}
