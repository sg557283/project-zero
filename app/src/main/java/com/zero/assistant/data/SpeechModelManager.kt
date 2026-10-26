package com.zero.assistant.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import com.zero.assistant.core.ModelInstallCancelledException
import com.zero.assistant.core.ModelTransferState
import com.zero.assistant.core.ModelTransferStateMachine
import com.zero.assistant.core.SpeechModelCatalog
import com.zero.assistant.core.SpeechModelCatalogParser
import com.zero.assistant.core.SpeechModelSpec
import com.zero.assistant.core.SpeechModelStore
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection
import org.vosk.Model
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SpeechModelManager(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val transferMachine = ModelTransferStateMachine()
    private val metadata = SpeechModelPreferences(appContext)
    private val modelRoot = File(appContext.filesDir, "speech-models")
    private val store: SpeechModelStore
    val catalog: SpeechModelCatalog

    private val mutableStates = MutableStateFlow<Map<String, ModelTransferState>>(emptyMap())
    val states: StateFlow<Map<String, ModelTransferState>> = mutableStates.asStateFlow()
    private val mutableActiveModel = MutableStateFlow<String?>(null)
    val activeModelId: StateFlow<String?> = mutableActiveModel.asStateFlow()
    private val mutableModelLoadState = MutableStateFlow<ModelLoadState>(ModelLoadState.Idle)
    val modelLoadState: StateFlow<ModelLoadState> = mutableModelLoadState.asStateFlow()

    private var activeDownloadId: String? = null
    private var downloadCancelled: AtomicBoolean? = null
    private var activeConnection: HttpURLConnection? = null
    private var loadedModelId: String? = null
    private var loadedModel: Model? = null
    @Volatile private var closed = false

    init {
        appContext.cacheDir.listFiles { file -> file.name.endsWith(".zip.part") }
            ?.forEach { it.delete() }
        val json = appContext.assets.open(CATALOG_ASSET).bufferedReader().use { it.readText() }
        catalog = SpeechModelCatalogParser.parse(json)
        store = SpeechModelStore(catalog, modelRoot, metadata) { directory ->
            try {
                Model(directory.absolutePath).use { }
                true
            } catch (_: Exception) {
                false
            } catch (_: LinkageError) {
                false
            }
        }
        val installed = store.installedIds()
        mutableActiveModel.value = store.activeModelId()
        mutableStates.value = catalog.models.associate { spec ->
            spec.id to if (spec.id in installed) ModelTransferState.Installed else ModelTransferState.Idle
        }
    }

    fun ensureBundledModel() {
        val spec = catalog.models.firstOrNull { it.bundledAsset != null } ?: return
        if (spec.id in store.installedIds() || closed || !transferMachine.begin(spec.id)) return
        publish(spec.id, ModelTransferState.Installing)
        executor.execute {
            try {
                val requiredBytes = spec.archiveSizeBytes + spec.maxExtractedBytes + MIN_FREE_SPACE_BYTES
                if (StatFs(appContext.filesDir.absolutePath).availableBytes < requiredBytes) {
                    throw IllegalStateException("Not enough free space to install the included model.")
                }
                store.installBundled(spec.id, appContext.assets.open(requireNotNull(spec.bundledAsset)))
                complete(spec, ModelTransferState.Installed)
            } catch (failure: Exception) {
                complete(spec, ModelTransferState.Failed(failure.message ?: "Bundled model could not be installed"))
            }
        }
    }

    fun download(modelId: String): Boolean {
        val spec = catalog.model(modelId) ?: return false
        if (spec.bundledAsset != null || modelId in store.installedIds() || closed) return false
        if (!transferMachine.begin(modelId)) return false
        val cancelled = AtomicBoolean(false)
        synchronized(this) {
            if (closed || activeDownloadId != null) {
                transferMachine.finish(modelId, ModelTransferState.Cancelled)
                return false
            }
            activeDownloadId = modelId
            downloadCancelled = cancelled
        }
        publish(modelId, ModelTransferState.Downloading(0, spec.archiveSizeBytes))
        executor.execute { downloadAndInstall(spec, cancelled) }
        return true
    }

    fun cancelDownload(modelId: String) {
        synchronized(this) {
            if (activeDownloadId != modelId) return
            downloadCancelled?.set(true)
            activeConnection?.disconnect()
        }
    }

    fun activate(modelId: String): Boolean = runCatching {
        store.activate(modelId)
        mutableActiveModel.value = modelId
    }.isSuccess

    fun uninstall(modelId: String): Boolean = runCatching {
        store.uninstall(modelId)
        mutableActiveModel.value = store.activeModelId()
        publish(modelId, ModelTransferState.Idle)
    }.isSuccess

    fun installedStorageBytes(): Long = store.installedIds().sumOf { id ->
        store.modelDirectory(id).walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun installedStorageLabel(): String = "%.1f MiB".format(installedStorageBytes() / (1024.0 * 1024.0))

    fun loadActiveModel(onComplete: (Result<Model>) -> Unit) {
        val id = store.activeModelId()
        if (id == null) {
            mainHandler.post { onComplete(Result.failure(IllegalStateException("Install a speech model before using the microphone"))) }
            return
        }
        if (loadedModelId == id && loadedModel != null) {
            val current = loadedModel
            mainHandler.post { onComplete(Result.success(requireNotNull(current))) }
            return
        }
        mutableModelLoadState.value = ModelLoadState.Loading(id)
        executor.execute {
            val result = runCatching {
                val nextModel = Model(store.modelDirectory(id).absolutePath)
                synchronized(this) {
                    if (closed) {
                        nextModel.close()
                        error("Speech model manager is closed")
                    }
                    loadedModel?.close()
                    loadedModel = nextModel
                    loadedModelId = id
                }
                nextModel
            }
            mainHandler.post {
                mutableModelLoadState.value = result.fold(
                    onSuccess = { ModelLoadState.Ready(id) },
                    onFailure = { ModelLoadState.Failed(id, it.message ?: "Speech model could not be loaded") },
                )
                onComplete(result)
            }
        }
    }

    private fun downloadAndInstall(spec: SpeechModelSpec, cancelled: AtomicBoolean) {
        val partial = File(appContext.cacheDir, "${spec.id}.zip.part")
        var connection: HttpsURLConnection? = null
        try {
            val requiredBytes = spec.archiveSizeBytes + spec.maxExtractedBytes + MIN_FREE_SPACE_BYTES
            if (StatFs(appContext.filesDir.absolutePath).availableBytes < requiredBytes) {
                throw IllegalStateException("Not enough free space. Free at least ${formatMib(requiredBytes)} and retry.")
            }
            check(!cancelled.get()) { "Download cancelled" }
            connection = URL(spec.sourceUrl).openConnection() as? HttpsURLConnection
                ?: throw IllegalStateException("Model source did not use HTTPS")
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            synchronized(this) { activeConnection = connection }
            val status = connection.responseCode
            check(status == HttpURLConnection.HTTP_OK) { "Model download failed (HTTP $status)" }
            val announcedLength = connection.contentLengthLong
            check(announcedLength < 0 || announcedLength == spec.archiveSizeBytes) {
                "Model download size differs from the curated catalog"
            }
            var received = 0L
            BufferedInputStream(connection.inputStream).use { input ->
                BufferedOutputStream(FileOutputStream(partial)).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        check(!cancelled.get()) { "Download cancelled" }
                        val count = input.read(buffer)
                        if (count < 0) break
                        received += count
                        check(received <= spec.archiveSizeBytes) { "Model download exceeded the curated size" }
                        output.write(buffer, 0, count)
                        transferMachine.progress(spec.id, received, spec.archiveSizeBytes)
                        publish(spec.id, ModelTransferState.Downloading(received, spec.archiveSizeBytes))
                    }
                }
            }
            check(received == spec.archiveSizeBytes) { "Model download was incomplete" }
            check(!cancelled.get()) { "Download cancelled" }
            transferMachine.installing(spec.id)
            publish(spec.id, ModelTransferState.Installing)
            store.installArchive(spec.id, partial) { cancelled.get() }
            complete(spec, ModelTransferState.Installed)
        } catch (failure: Exception) {
            val state = if (cancelled.get() || failure is ModelInstallCancelledException) {
                ModelTransferState.Cancelled
            } else {
                ModelTransferState.Failed(failure.message ?: "Model download or installation failed")
            }
            complete(spec, state)
        } finally {
            connection?.disconnect()
            synchronized(this) {
                if (activeDownloadId == spec.id) {
                    activeConnection = null
                    downloadCancelled = null
                    activeDownloadId = null
                }
            }
            partial.delete()
        }
    }

    private fun complete(spec: SpeechModelSpec, result: ModelTransferState) {
        transferMachine.finish(spec.id, result)
        publish(spec.id, result)
        mutableActiveModel.value = store.activeModelId()
        synchronized(this) {
            if (activeDownloadId == spec.id) {
                activeConnection = null
                downloadCancelled = null
                activeDownloadId = null
            }
        }
    }

    private fun publish(modelId: String, state: ModelTransferState) {
        mutableStates.value = mutableStates.value + (modelId to state)
    }

    override fun close() {
        synchronized(this) {
            if (closed) return
            closed = true
            downloadCancelled?.set(true)
            activeConnection?.disconnect()
            loadedModel?.close()
            loadedModel = null
            loadedModelId = null
        }
        executor.shutdownNow()
    }

    private fun formatMib(bytes: Long): String = "%.0f MiB".format(bytes / MIB.toDouble())

    companion object {
        const val HINDI_MODEL_ID = "vosk-hi-small-0.22"
        const val ENGLISH_MODEL_ID = "vosk-en-us-small-0.15"
        private const val CATALOG_ASSET = "speech-models.json"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MIN_FREE_SPACE_BYTES = 32L * 1024 * 1024
        private const val MIB = 1024L * 1024L
    }
}

sealed interface ModelLoadState {
    data object Idle : ModelLoadState
    data class Loading(val modelId: String) : ModelLoadState
    data class Ready(val modelId: String) : ModelLoadState
    data class Failed(val modelId: String, val message: String) : ModelLoadState
}