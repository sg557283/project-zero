package com.zero.assistant.core

data class ActivationState(
    val serviceRunning: Boolean = false,
    val captureActive: Boolean = false,
    val wakeTriggered: Boolean = false,
    val commandStarted: Boolean = false,
    val speaking: Boolean = false,
)

object AssistantActivation {
    fun acceptWake(state: ActivationState, nowMillis: Long, lastWakeMillis: Long, debounceMillis: Long = 2_500L): Boolean =
        state.serviceRunning && state.captureActive && !state.wakeTriggered && !state.speaking && nowMillis - lastWakeMillis >= debounceMillis

    fun acceptCommand(state: ActivationState, duplicateText: String?, previousText: String?, nowMillis: Long, previousMillis: Long, duplicateWindowMillis: Long = 1_500L): Boolean =
        state.serviceRunning && state.commandStarted && (duplicateText == null || !duplicateText.equals(previousText, ignoreCase = true) || nowMillis - previousMillis >= duplicateWindowMillis)

    fun shouldRestart(serviceRunning: Boolean, enabled: Boolean, restartPending: Boolean): Boolean =
        serviceRunning && enabled && !restartPending
}

object HardwareActivationDebouncer {
    fun accept(nowMillis: Long, previousMillis: Long, cooldownMillis: Long = 2_000L): Boolean = nowMillis - previousMillis >= cooldownMillis
}
