package com.zero.assistant.core

object MultitaskCommandExecutor {
    private val separators = Regex(
        "\\s*(?:[,;]|\\b(?:and then|after that|then|and|aur|phir|uske baad)\\b)\\s*",
        RegexOption.IGNORE_CASE,
    )
    private val messageBodyStart = Regex(
        "\\b(?:text|message|msg)\\s+(?:on\\s+)?(?:wa|whatsapp)\\b.{0,80}?\\b(?:that|saying)\\b|\\b(?:whatsapp|wa)\\s+par\\b.{0,100}?\\bmessage\\s+bhejo\\s*:|\\b(?:whatsapp|wa)\\s+(?:and\\s+)?(?:text|message|msg)\\s+[^:]{1,80}:",
        RegexOption.IGNORE_CASE,
    )

    fun split(command: String): List<String> {
        val protectedStart = messageBodyStart.find(command)?.range?.last
        val steps = mutableListOf<String>()
        var stepStart = 0
        separators.findAll(command).forEach { separator ->
            if (protectedStart != null && separator.range.first > protectedStart) return@forEach
            command.substring(stepStart, separator.range.first).trim()
                .takeIf(String::isNotEmpty)
                ?.let(steps::add)
            stepStart = separator.range.last + 1
        }
        command.substring(stepStart).trim().takeIf(String::isNotEmpty)?.let(steps::add)
        return steps
    }

    fun normalizedSteps(command: String): List<String> =
        split(command).map(BilingualAliasCommandEngine::normalize)
}
