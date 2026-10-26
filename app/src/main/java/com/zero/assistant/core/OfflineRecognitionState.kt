package com.zero.assistant.core

sealed interface OfflineRecognitionState {
    data object Idle : OfflineRecognitionState
    data class LoadingModel(val modelId: String) : OfflineRecognitionState
    data class Listening(val modelId: String) : OfflineRecognitionState
    data class Result(val text: String) : OfflineRecognitionState
    data class Failed(val message: String) : OfflineRecognitionState
    data object Cancelled : OfflineRecognitionState
}

class OfflineRecognitionStateMachine(private val onStateChanged: (OfflineRecognitionState) -> Unit = {}) {
    var state: OfflineRecognitionState = OfflineRecognitionState.Idle
        private set

    fun begin(modelId: String?) {
        update(modelId?.let(OfflineRecognitionState::LoadingModel)
            ?: OfflineRecognitionState.Failed("No speech model is installed"))
    }

    fun listening(modelId: String) {
        if (state is OfflineRecognitionState.LoadingModel) update(OfflineRecognitionState.Listening(modelId))
    }

    fun result(text: String) {
        if (text.isNotBlank()) update(OfflineRecognitionState.Result(text.trim()))
        else fail("No speech was recognized")
    }

    fun fail(message: String) {
        update(OfflineRecognitionState.Failed(message.ifBlank { "Offline speech recognition failed" }))
    }

    fun cancel() {
        update(OfflineRecognitionState.Cancelled)
    }

    private fun update(next: OfflineRecognitionState) {
        state = next
        onStateChanged(next)
    }
}