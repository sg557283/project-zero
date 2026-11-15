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
    const val KOKORO_ID = "kokoro-multi-lang-v1_1"
    const val KOKORO_SHA256 = "259c9c8bc906d6078e4e0570421885dabb68b539c2f970fcbb890a58eeaf3e9c"

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
            name = "Kokoro Multi-Speaker · INT8 · v1.1",
            summary = "103 English / Chinese speakers; three named English voices exposed here, no Hindi support",
            language = "English · Chinese",
            category = VoiceModelCategory.EmotionBased,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-multi-lang-v1_1.tar.bz2",
            archiveSizeBytes = 147_052_523,
            sha256 = KOKORO_SHA256,
            archiveRoot = "kokoro-int8-multi-lang-v1_1",
            license = "Apache-2.0",
            licenseUrl = "https://huggingface.co/hexgrad/Kokoro-82M",
        ),
    )

    fun model(id: String): VoiceModelSpec? = models.firstOrNull { it.id == id }
}

enum class HumanoidVoiceGender(val label: String) {
    Female("Female"),
    Male("Male"),
}

data class KokoroSpeakerSpec(
    val name: String,
    val speakerId: Int,
    val displayName: String,
    val gender: HumanoidVoiceGender,
    val languageTag: String,
    val languageLabel: String,
)

object KokoroSpeakerCatalog {
    val english = listOf(
        KokoroSpeakerSpec("af_maple", 0, "Maple", HumanoidVoiceGender.Female, "en-US", "English (US)"),
        KokoroSpeakerSpec("af_sol", 1, "Sol", HumanoidVoiceGender.Female, "en-US", "English (US)"),
        KokoroSpeakerSpec("bf_vale", 2, "Vale", HumanoidVoiceGender.Female, "en-GB", "English (UK)"),
    )

    fun speaker(name: String): KokoroSpeakerSpec? = english.firstOrNull { it.name == name }

    fun speaker(speakerId: Int): KokoroSpeakerSpec? = english.firstOrNull { it.speakerId == speakerId }
}

data class HumanoidVoiceSpec(
    val modelId: String,
    val name: String,
    val accent: String,
    val gender: HumanoidVoiceGender,
    val style: String,
    val languageTag: String,
    val languageLabel: String,
    val archiveSizeBytes: Long,
    val sha256: String,
) {
    val voiceId: String get() = "piper:$modelId"
    val archiveId: String get() = "$modelId-int8"
    val archiveRoot: String get() = archiveId
    val downloadUrl: String
        get() = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$archiveId.tar.bz2"
}

object HumanoidVoiceCatalog {
    val female = listOf(
        HumanoidVoiceSpec(
            "vits-piper-hi_IN-priyamvada-medium", "Priyamvada", "Hindi / Hinglish",
            HumanoidVoiceGender.Female, "Natural Female", "hi-IN", "Hindi / Hinglish",
            21_097_319, "2997c7bbc211e524af83dfdffe7712942f10bd76c079a526af2e09d1f36a92f2",
        ),
        HumanoidVoiceSpec(
            "vits-piper-en_US-amy-medium", "Amy", "English US",
            HumanoidVoiceGender.Female, "Warm Female", "en-US", "English (US)",
            21_028_122, "bd23c0aa629eb3719448582f45ede49e8fa6a679061fed5eab16a6a6fd8e7e82",
        ),
        HumanoidVoiceSpec(
            "vits-piper-en_US-lessac-medium", "Lessac", "English US",
            HumanoidVoiceGender.Female, "Expressive Assistant", "en-US", "English (US)",
            20_969_179, "f1c6d0295cf16087b05f80fdca5b44daca5cd78e2c425d419a42ba34929805f9",
        ),
        HumanoidVoiceSpec(
            "vits-piper-en_US-kristin-medium", "Kristin", "English US",
            HumanoidVoiceGender.Female, "Soft & Friendly", "en-US", "English (US)",
            20_882_061, "16289d7ee8e6b2311a0a0af6531a55f498f82499644a1bb6fddb991fe6fa950c",
        ),
        HumanoidVoiceSpec(
            "vits-piper-en_GB-jenny_dioco-medium", "Jenny Dioco", "English UK",
            HumanoidVoiceGender.Female, "Crisp Female", "en-GB", "English (UK)",
            20_950_036, "db7ed4edd3c1e28b1afdecd9a77f5cb44db326f687632ffd364be513ec6a11e6",
        ),
        HumanoidVoiceSpec(
            "vits-piper-en_GB-cori-high", "Cori", "English UK",
            HumanoidVoiceGender.Female, "Studio HD Female", "en-GB", "English (UK)",
            35_038_210, "115adb9000159c3a90076fff8ba75ae4d26ccbbc749a444a24dc277b0dad872b",
        ),
    )

    val male = listOf(
        HumanoidVoiceSpec(
            "vits-piper-hi_IN-pratham-medium", "Pratham", "Hindi / Hinglish",
            HumanoidVoiceGender.Male, "Clear Male", "hi-IN", "Hindi / Hinglish",
            20_987_965, "20f568c56207c13b9a0d9478aec8b7d1449122e618aeebc7211f6abc942b58b7",
        ),
        HumanoidVoiceSpec(
            "vits-piper-hi_IN-rohan-medium", "Rohan", "Hindi / Hinglish",
            HumanoidVoiceGender.Male, "Deep Male", "hi-IN", "Hindi / Hinglish",
            21_064_499, "b306822fe16516d3c882e070a7205d781524bd9e8f2d121c3da3e504afe20e05",
        ),
        HumanoidVoiceSpec(
            "vits-piper-en_US-ryan-high", "Ryan", "English US",
            HumanoidVoiceGender.Male, "Studio HD Male", "en-US", "English (US)",
            34_473_341, "df13fc140db80fd586e98834cad703a8988c1004748daa068dd07a59e502013e",
        ),
        HumanoidVoiceSpec(
            "vits-piper-en_US-joe-medium", "Joe", "English US",
            HumanoidVoiceGender.Male, "Deep Jarvis-Style Male", "en-US", "English (US)",
            21_230_019, "644527f29eca0ada5595d7b6e4daf6388f5ac0c4870b4d42996a6687fcefeb37",
        ),
        HumanoidVoiceSpec(
            "vits-piper-en_US-bryce-medium", "Bryce", "English US",
            HumanoidVoiceGender.Male, "Crisp Male", "en-US", "English (US)",
            20_910_568, "89cd4f464c91579440565927bfca9d26555f577c4e37431167568c934fdb82f6",
        ),
        HumanoidVoiceSpec(
            "vits-piper-en_GB-alan-medium", "Alan", "English UK",
            HumanoidVoiceGender.Male, "Classic Male", "en-GB", "English (UK)",
            21_103_831, "f0b2526c9600657141ccbbe412f6159dceedab5bab4ba4e8857d9443acb50de4",
        ),
    )

    val all = female + male
    fun voice(id: String): HumanoidVoiceSpec? = all.firstOrNull { it.voiceId == id || it.modelId == id }
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
