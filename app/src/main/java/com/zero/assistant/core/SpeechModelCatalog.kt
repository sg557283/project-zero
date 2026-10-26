package com.zero.assistant.core

import com.google.gson.Gson
import java.net.URI

data class SpeechModelSpec(
    val id: String,
    val language: String,
    val languageName: String,
    val name: String,
    val version: String,
    val archiveSizeBytes: Long,
    val maxExtractedBytes: Long,
    val sha256: String,
    val archiveRoot: String,
    val sourceUrl: String,
    val license: String,
    val bundledAsset: String?,
)

data class SpeechModelCatalog(val version: Int, val models: List<SpeechModelSpec>) {
    fun model(id: String): SpeechModelSpec? = models.firstOrNull { it.id == id }
}

object SpeechModelCatalogParser {
    private const val MAX_MODELS = 20
    private val modelIdPattern = Regex("[a-z0-9][a-z0-9.-]{0,63}")
    private val archiveRootPattern = Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,95}")
    private val checksumPattern = Regex("[a-f0-9]{64}")

    fun parse(json: String): SpeechModelCatalog {
        val catalog = Gson().fromJson(json, SpeechModelCatalog::class.java)
            ?: throw IllegalArgumentException("Model catalog is empty")
        require(catalog.version == 1) { "Unsupported model catalog version" }
        require(catalog.models.isNotEmpty() && catalog.models.size <= MAX_MODELS) { "Invalid model count" }
        require(catalog.models.map { it.id }.toSet().size == catalog.models.size) { "Duplicate model ID" }

        catalog.models.forEach(::validateModel)
        require(catalog.models.any { it.bundledAsset != null }) { "Catalog must include a bundled model" }
        return catalog
    }

    private fun validateModel(model: SpeechModelSpec) {
        require(modelIdPattern.matches(model.id) && ".." !in model.id && !model.id.endsWith('.')) { "Invalid model ID" }
        require(model.language in setOf("hi-IN", "en-US")) { "Unsupported model language" }
        require(model.languageName.isNotBlank() && model.name.isNotBlank() && model.version.isNotBlank()) {
            "Model metadata is incomplete"
        }
        require(model.archiveSizeBytes in 1..100_000_000) { "Invalid archive size" }
        require(model.maxExtractedBytes in model.archiveSizeBytes..300_000_000) { "Invalid extracted size limit" }
        require(checksumPattern.matches(model.sha256)) { "Invalid SHA-256 checksum" }
        require(archiveRootPattern.matches(model.archiveRoot)) { "Invalid archive root" }
        val uri = runCatching { URI(model.sourceUrl) }.getOrNull()
        require(uri?.scheme == "https" && uri.host == "alphacephei.com" && uri.userInfo == null) {
            "Untrusted model source"
        }
        require(uri.path == "/vosk/models/${model.archiveRoot}.zip") { "Unexpected model archive path" }
        model.bundledAsset?.let { asset ->
            require(asset == "models/${model.archiveRoot}.zip") { "Invalid bundled model asset" }
        }
        require(model.license == "Apache-2.0") { "Unverified model license" }
    }
}

sealed interface ModelTransferState {
    data object Idle : ModelTransferState
    data class Downloading(val receivedBytes: Long, val totalBytes: Long) : ModelTransferState
    data object Installing : ModelTransferState
    data object Installed : ModelTransferState
    data class Failed(val message: String) : ModelTransferState
    data object Cancelled : ModelTransferState
}

class ModelTransferStateMachine {
    private val states = mutableMapOf<String, ModelTransferState>()
    private var activeDownloadId: String? = null

    @Synchronized
    fun state(modelId: String): ModelTransferState = states[modelId] ?: ModelTransferState.Idle

    @Synchronized
    fun begin(modelId: String): Boolean {
        if (activeDownloadId != null) return false
        activeDownloadId = modelId
        states[modelId] = ModelTransferState.Downloading(0, 0)
        return true
    }

    @Synchronized
    fun progress(modelId: String, receivedBytes: Long, totalBytes: Long) {
        if (activeDownloadId != modelId) return
        states[modelId] = ModelTransferState.Downloading(
            receivedBytes.coerceAtLeast(0),
            totalBytes.coerceAtLeast(0),
        )
    }

    @Synchronized
    fun installing(modelId: String) {
        if (activeDownloadId == modelId) states[modelId] = ModelTransferState.Installing
    }

    @Synchronized
    fun finish(modelId: String, result: ModelTransferState) {
        if (activeDownloadId != modelId) return
        states[modelId] = result
        activeDownloadId = null
    }
}