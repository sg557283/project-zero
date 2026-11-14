package com.zero.assistant.core

import java.util.Locale

internal sealed interface OnScreenAction {
    data class Click(val label: String) : OnScreenAction
    data class TypeText(val text: String) : OnScreenAction
    data object ScrollForward : OnScreenAction
    data object ScrollBackward : OnScreenAction
    data object Back : OnScreenAction
    data object Home : OnScreenAction
    data object PlayPause : OnScreenAction
    data object NextTrack : OnScreenAction
    data object PreviousTrack : OnScreenAction
    data object ReadScreen : OnScreenAction
    data object SummarizeScreen : OnScreenAction
}

internal object OnScreenActionParser {
    private const val MAX_ARGUMENT_LENGTH = 2_000

    fun parse(input: String): OnScreenAction? {
        val command = input.trim()
        if (command.isEmpty()) return null
        val lower = command.lowercase(Locale.ROOT)
        return when {
            lower in setOf("scroll down", "scroll forward", "page down", "neeche scroll karo", "neeche scroll") -> OnScreenAction.ScrollForward
            lower in setOf("scroll up", "scroll backward", "page up", "upar scroll karo", "upar scroll") -> OnScreenAction.ScrollBackward
            lower in setOf("go back", "back", "navigate back") -> OnScreenAction.Back
            lower in setOf("go home", "home screen", "home") -> OnScreenAction.Home
            lower in setOf("play pause", "play or pause", "pause music", "resume music", "toggle music") -> OnScreenAction.PlayPause
            lower in setOf("next track", "next song", "skip track") -> OnScreenAction.NextTrack
            lower in setOf("previous track", "previous song", "go to previous track") -> OnScreenAction.PreviousTrack
            lower in setOf("read screen", "screen padho", "screen par kya hai") -> OnScreenAction.ReadScreen
            lower in setOf("summarize this", "summarize screen", "isko short me samjhao", "iska saar batao") ->
                OnScreenAction.SummarizeScreen
            else -> parseArgument(command, CLICK_PREFIXES)?.let(OnScreenAction::Click)
                ?: hinglishTapPattern.matchEntire(command)?.groupValues?.get(1)?.trim()?.takeIf(String::isNotEmpty)?.let(OnScreenAction::Click)
                ?: parseArgument(command, TYPE_PREFIXES)?.let(OnScreenAction::TypeText)
        }
    }

    private fun parseArgument(command: String, prefixes: List<String>): String? {
        val prefix = prefixes.firstOrNull { command.startsWith(it, ignoreCase = true) } ?: return null
        val argument = command.substring(prefix.length).trim()
        return argument.takeIf {
            it.isNotEmpty() && it.length <= MAX_ARGUMENT_LENGTH && it.none { character -> Character.isISOControl(character) }
        }
    }

    private val CLICK_PREFIXES = listOf("click ", "tap ")
    private val TYPE_PREFIXES = listOf("type ")
    private val hinglishTapPattern = Regex("^(.{1,100}?)\\s+par\\s+tap\\s+karo$", RegexOption.IGNORE_CASE)
}
