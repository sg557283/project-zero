package com.zero.assistant.core

enum class VoiceModelCategory(val label: String) {
    Simple("Simple"),
    EmotionBased("Emotion-Based"),
    SpeechRecognition("Offline STT & Wake"),
}

data class VoiceModelSpec(
    val id: String,
    val name: String,
    val summary: String,
    val language: String,
    val category: VoiceModelCategory,
    val downloadUrl: String,
    val archiveSizeBytes: Long,
    val sha256: String,
    val archiveRoot: String,
    val license: String,
    val licenseUrl: String,
)

object VoiceModelCatalog {
    const val KOKORO_ID = "kokoro-int8-multi-lang-v1-0"
    const val KOKORO_SHA256 = "72c347230107ef8a31098ec4d468a605244beef5f1ed00db97efa13518800653"

    val models = listOf(
        VoiceModelSpec(
            id = KOKORO_ID,
            name = "Kokoro Multi-Speaker · INT8",
            summary = "53 English / Chinese speakers; speaker selection only, no Hindi or emotion controls",
            language = "English · Chinese",
            category = VoiceModelCategory.EmotionBased,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-multi-lang-v1_0.tar.bz2",
            archiveSizeBytes = 132_353_410,
            sha256 = KOKORO_SHA256,
            archiveRoot = "kokoro-int8-multi-lang-v1_0",
            license = "Apache-2.0",
            licenseUrl = "https://huggingface.co/hexgrad/Kokoro-82M",
        ),
    )

    fun model(id: String): VoiceModelSpec? = models.firstOrNull { it.id == id }
}

sealed interface VoiceModelTransferState {
    data object Idle : VoiceModelTransferState
    data class Downloading(val receivedBytes: Long, val totalBytes: Long) : VoiceModelTransferState
    data object Paused : VoiceModelTransferState
    data object Installing : VoiceModelTransferState
    data object Removing : VoiceModelTransferState
    data object Installed : VoiceModelTransferState
    data class Failed(val message: String) : VoiceModelTransferState
    data class RemoveFailed(val message: String) : VoiceModelTransferState
    data object Cancelled : VoiceModelTransferState
}
