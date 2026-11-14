package com.zero.assistant.core

import java.util.ArrayDeque

data class ConversationTurn(
    val text: String,
    val lastContactName: String? = null,
    val lastPhoneNumber: String? = null,
    val lastAppOpened: String? = null,
    val lastLocation: String? = null,
    val lastTopic: String? = null,
)

class ConversationContextMemory(private val capacity: Int = DEFAULT_CAPACITY) {
    private val turns = ArrayDeque<ConversationTurn>()
    private var lastKnownContactName: String? = null
    private var lastKnownPhoneNumber: String? = null
    private var lastKnownAppOpened: String? = null
    private var lastKnownLocation: String? = null
    private var lastKnownTopic: String? = null

    init {
        require(capacity > 0) { "Context capacity must be positive." }
    }

    fun add(turn: ConversationTurn) {
        if (turns.size == capacity) turns.removeFirst()
        turns.addLast(turn)
        turn.lastContactName?.let { lastKnownContactName = it }
        turn.lastPhoneNumber?.let { lastKnownPhoneNumber = it }
        turn.lastAppOpened?.let { lastKnownAppOpened = it }
        turn.lastLocation?.let { lastKnownLocation = it }
        turn.lastTopic?.let { lastKnownTopic = it }
    }

    fun recent(): List<ConversationTurn> = turns.toList()

    fun latest(): ConversationTurn? = turns.lastOrNull()

    fun resolvePronoun(): String? = lastKnownContactName

    fun resolveFollowUp(input: String): String {
        val contact = lastKnownContactName ?: return input
        val followUp = Regex("^\\s*text\\s+(?:her|him|them|unhe|use)\\s+(.{2,1000})\\s*$", RegexOption.IGNORE_CASE)
        val message = followUp.matchEntire(input)?.groupValues?.get(1)?.trim() ?: return input
        return "WhatsApp message $contact: $message"
    }

    fun remember(text: String, command: AssistantCommand) {
        val previous = turns.lastOrNull()
        val turn = when (command) {
            is AssistantCommand.DraftCallContact ->
                ConversationTurn(text, lastContactName = command.contact, lastTopic = "calling")
            is AssistantCommand.CallNumber ->
                ConversationTurn(text, lastPhoneNumber = command.number, lastTopic = "calling")
            is AssistantCommand.OpenApp ->
                ConversationTurn(text, lastAppOpened = command.app.name, lastTopic = "app")
            is AssistantCommand.Weather ->
                ConversationTurn(text, lastLocation = command.city, lastTopic = "weather")
            is AssistantCommand.SearchYouTube ->
                ConversationTurn(text, lastTopic = command.query)
            else -> ConversationTurn(text, lastTopic = previous?.lastTopic)
        }
        add(
            turn.copy(
                lastContactName = turn.lastContactName ?: lastKnownContactName,
                lastPhoneNumber = turn.lastPhoneNumber ?: lastKnownPhoneNumber,
                lastAppOpened = turn.lastAppOpened ?: lastKnownAppOpened,
                lastLocation = turn.lastLocation ?: lastKnownLocation,
            ),
        )
    }

    companion object {
        const val DEFAULT_CAPACITY = 5
    }
}
