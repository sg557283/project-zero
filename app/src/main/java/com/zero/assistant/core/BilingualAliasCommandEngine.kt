package com.zero.assistant.core

object BilingualAliasCommandEngine {
    private val aliases = linkedMapOf(
        "wi fi" to "wifi",
        "wi-fi" to "wifi",
        "internet" to "wifi",
        "net" to "wifi",
        "data" to "wifi",
        "batti" to "torch",
        "roshni" to "torch",
        "light" to "torch",
        "flashlight" to "torch",
        "flash light" to "torch",
        "flash" to "torch",
        "torch" to "torch",
        "awaz" to "volume",
        "awaaz" to "volume",
        "sound" to "volume",
        "volume" to "volume",
        "y t" to "youtube",
        "why tee" to "youtube",
        "why t" to "youtube",
        "yt" to "youtube",
        "you tube" to "youtube",
        "f b" to "facebook",
        "fb" to "facebook",
        "instagram" to "instagram",
        "insta" to "instagram",
        "inshta" to "instagram",
        "ig" to "instagram",
        "whats app" to "whatsapp",
        "wa" to "whatsapp",
        "spotify" to "music",
        "gaana" to "music",
        "songs" to "music",
        "cam" to "camera",
        "photo" to "camera",
        "selfie" to "camera",
        "setting" to "settings",
        "calc" to "calculator",
        "hisab" to "calculator",
        "photos" to "gallery",
        "kholo" to "open",
        "khol" to "open",
        "chalao" to "open",
        "start" to "open",
        "launch" to "open",
        "on karo" to "turn on",
        "chalu karo" to "turn on",
        "चालू करो" to "turn on",
        "jalao" to "turn on",
        "bujhao" to "turn off",
        "band karo" to "turn off",
        "off karo" to "turn off",
        "hatao" to "turn off",
        "close" to "turn off",
        "stop" to "turn off",
        "phone lagao" to "call",
        "ring karo" to "call",
        "dial" to "call",
        "msg" to "message",
        "text" to "message",
        "bhejo" to "message",
        "likho" to "message",
    )

    private val orderedAliases = aliases.keys.sortedByDescending(String::length)

    fun normalize(input: String): String {
        var result = input
        orderedAliases.forEach { alias ->
            val canonical = aliases.getValue(alias)
            val pattern = Regex("(?<![\\p{L}\\p{N}])${Regex.escape(alias)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
            result = pattern.replace(result) { canonical }
        }
        return Regex("[\\p{L}\\p{M}]+").replace(result) { match -> fuzzyAlias(match.value) }
    }

    private fun fuzzyAlias(word: String): String {
        val candidate = word.lowercase()
        val fuzzyTargets = setOf("torch", "instagram", "youtube", "whatsapp", "facebook", "camera", "calculator", "settings")
        val matches = aliases.entries.asSequence()
            .filter { it.key in fuzzyTargets }
            .map { it.key to it.value }
            .filter { (alias, _) -> editDistanceAtMostOne(candidate, alias) }
            .toList()
        return matches.singleOrNull()?.second ?: word
    }

    private fun editDistanceAtMostOne(left: String, right: String): Boolean {
        if (kotlin.math.abs(left.length - right.length) > 1) return false
        var leftIndex = 0
        var rightIndex = 0
        var edits = 0
        while (leftIndex < left.length && rightIndex < right.length) {
            if (left[leftIndex].equals(right[rightIndex], ignoreCase = true)) {
                leftIndex++
                rightIndex++
            } else {
                if (++edits > 1) return false
                if (left.length > right.length) leftIndex++ else if (right.length > left.length) rightIndex++
                else {
                    leftIndex++
                    rightIndex++
                }
            }
        }
        if (leftIndex < left.length || rightIndex < right.length) edits++
        return edits <= 1
    }
}
