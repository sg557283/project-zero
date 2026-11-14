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

    val actionBrain = MobileActionBrainSpec(
        id = "functiongemma-270m-mobile-actions",
        title = "FunctionGemma 270M • Mobile Action Brain",
        subtitle = "Google AI Edge • Offline Device & App Control (~289 MB INT8)",
        downloadUrl = "https://huggingface.co/JackJ1/functiongemma-270m-it-mobile-actions-litertlm/resolve/5d08507ee504b40476f8ab68486050837a093003/mobile-actions_q8_ekv1024.litertlm",
        sizeBytes = 284_426_240L,
        sha256 = "510c8257d1f9d12b5be630b5d6a593732e10896e9ee0b3f3d20b2eebff8e1b13",
        source = "JackJ1/functiongemma-270m-it-mobile-actions-litertlm",
        license = "Gemma Terms of Use",
        licenseUrl = "https://ai.google.dev/gemma/terms",
    )

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

data class MobileActionBrainSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String,
    val source: String,
    val license: String,
    val licenseUrl: String,
)

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
