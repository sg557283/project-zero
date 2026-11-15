package com.zero.assistant.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import com.zero.assistant.core.VoiceModelCatalog
import com.zero.assistant.core.VoiceModelSpec
import com.zero.assistant.core.VoiceModelTransferState
import com.zero.assistant.core.KokoroSpeakerCatalog
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ModelDownloadManager(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val modelRoot = File(appContext.filesDir, "tts_models/kokoro")
    private val cacheFile = File(appContext.cacheDir, "${VoiceModelCatalog.KOKORO_ID}.tar.bz2.part")
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val lock = Object()
    private val cancelled = AtomicBoolean(false)
    @Volatile private var paused = false
    @Volatile private var activeConnection: HttpURLConnection? = null
    @Volatile private var closed = false

    private val mutableState = MutableStateFlow<VoiceModelTransferState>(initialState())
    val state: StateFlow<VoiceModelTransferState> = mutableState.asStateFlow()
    private val mutableActiveModelId = MutableStateFlow(activeModelIdFromPreferences())
    val activeModelId: StateFlow<String?> = mutableActiveModelId.asStateFlow()

    fun isInstalled(): Boolean = validModelDirectory(modelRoot)

    fun availableVoices(): List<Pair<String, Int>> =
        if (activeModelId.value == VoiceModelCatalog.KOKORO_ID && isInstalled()) {
            kokoroSpeakers()
        } else {
            emptyList()
        }

    fun download(): Boolean {
        if (closed || isInstalled() || state.value is VoiceModelTransferState.Downloading ||
            state.value == VoiceModelTransferState.Paused || state.value == VoiceModelTransferState.Installing ||
            state.value == VoiceModelTransferState.Removing || state.value is VoiceModelTransferState.RemoveFailed
        ) return false
        if (executor.isShutdown) return false
        cancelled.set(false)
        paused = false
        cacheFile.delete()
        mutableState.value = VoiceModelTransferState.Downloading(0, VoiceModelCatalog.models.first().archiveSizeBytes)
        executor.execute { downloadAndInstall(VoiceModelCatalog.models.first()) }
        return true
    }

    fun pause() {
        if (state.value !is VoiceModelTransferState.Downloading) return
        paused = true
        activeConnection?.disconnect()
        mutableState.value = VoiceModelTransferState.Paused
    }

    fun resume(): Boolean {
        if (closed || state.value != VoiceModelTransferState.Paused) return false
        paused = false
        synchronized(lock) { lock.notifyAll() }
        return true
    }

    fun cancel() {
        cancelled.set(true)
        paused = false
        activeConnection?.disconnect()
        synchronized(lock) { lock.notifyAll() }
    }

    fun activate(): Boolean {
        if (!isInstalled()) return false
        val saved = preferences.edit().putString(ACTIVE_ID, VoiceModelCatalog.KOKORO_ID).commit()
        if (saved) mutableActiveModelId.value = VoiceModelCatalog.KOKORO_ID
        return saved
    }

    fun uninstall(onComplete: (Boolean) -> Unit): Boolean {
        if (closed || !modelRoot.exists() || state.value is VoiceModelTransferState.Installing ||
            state.value == VoiceModelTransferState.Removing || executor.isShutdown
        ) return false
        if (preferences.getString(ACTIVE_ID, null) == VoiceModelCatalog.KOKORO_ID) {
            if (!preferences.edit().remove(ACTIVE_ID).commit()) return false
            mutableActiveModelId.value = null
        }
        mutableState.value = VoiceModelTransferState.Removing
        return try {
            executor.execute {
                val removed = deleteTree(modelRoot)
                mutableState.value = if (removed) {
                    VoiceModelTransferState.Idle
                } else {
                    VoiceModelTransferState.RemoveFailed("Some Kokoro files could not be removed.")
                }
                mainHandler.post { onComplete(removed) }
            }
            true
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            mutableState.value = VoiceModelTransferState.Installed
            false
        }
    }

    fun modelDirectory(): File? = modelRoot.takeIf(::validModelDirectory)

    private fun downloadAndInstall(spec: VoiceModelSpec) {
        try {
            val requiredBytes = spec.archiveSizeBytes + MAX_EXTRACTED_BYTES + MIN_FREE_SPACE_BYTES
            val storageRoot = modelRoot.parentFile ?: appContext.filesDir
            check(storageRoot.exists() || storageRoot.mkdirs()) { "Unable to access voice model storage." }
            if (StatFs(storageRoot.absolutePath).availableBytes < requiredBytes) {
                error("Not enough free storage. Free at least ${requiredBytes / MIB} MiB and retry.")
            }
            downloadArchive(spec)
            checkCancelled()
            check(cacheFile.length() == spec.archiveSizeBytes) { "Downloaded archive size did not match the catalog." }
            check(sha256(cacheFile) == spec.sha256) { "Downloaded archive checksum did not match the catalog." }
            mutableState.value = VoiceModelTransferState.Installing
            installArchive(spec)
            mutableState.value = VoiceModelTransferState.Installed
        } catch (_: DownloadCancelledException) {
            cacheFile.delete()
            mutableState.value = VoiceModelTransferState.Cancelled
        } catch (failure: Exception) {
            if (cancelled.get() || closed) {
                cacheFile.delete()
                mutableState.value = VoiceModelTransferState.Cancelled
            } else {
                mutableState.value = VoiceModelTransferState.Failed(failure.message ?: "Voice model download or installation failed.")
            }
        } finally {
            activeConnection?.disconnect()
            activeConnection = null
        }
    }

    private fun downloadArchive(spec: VoiceModelSpec) {
        var received = cacheFile.length()
        check(received <= spec.archiveSizeBytes) { "Partial download exceeds the catalog size." }
        while (received < spec.archiveSizeBytes) {
            waitIfPaused()
            checkCancelled()
            var connection: HttpURLConnection? = null
            try {
                connection = openHttpsConnection(spec.downloadUrl, received)
                activeConnection = connection
                val expectedCode = if (received == 0L) HttpURLConnection.HTTP_OK else HttpURLConnection.HTTP_PARTIAL
                check(connection.responseCode == expectedCode) { "Download server did not support safe resume (HTTP ${connection.responseCode})." }
                if (received > 0) {
                    val range = connection.getHeaderField("Content-Range")
                    check(range?.startsWith("bytes $received-") == true) { "Download resume started at an unexpected byte." }
                }
                val announcedLength = connection.contentLengthLong
                if (announcedLength >= 0) {
                    check(received + announcedLength <= spec.archiveSizeBytes) { "Download exceeded the catalog size." }
                }
                FileOutputStream(cacheFile, received > 0).use { output ->
                    BufferedInputStream(connection.inputStream).use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            checkCancelled()
                            if (paused) break
                            val count = input.read(buffer)
                            if (count < 0) break
                            received += count
                            check(received <= spec.archiveSizeBytes) { "Download exceeded the catalog size." }
                            output.write(buffer, 0, count)
                            output.flush()
                            mutableState.value = VoiceModelTransferState.Downloading(received, spec.archiveSizeBytes)
                        }
                        output.fd.sync()
                    }
                }
            } catch (failure: Exception) {
                if (!paused) throw failure
            } finally {
                connection?.disconnect()
                activeConnection = null
            }
        }
    }

    private fun openHttpsConnection(source: String, offset: Long): HttpURLConnection {
        var nextUrl = source
        repeat(MAX_REDIRECTS + 1) { redirect ->
            val url = URL(nextUrl)
            check(url.protocol == "https") { "Voice model source must use HTTPS." }
            val connection = url.openConnection() as? HttpURLConnection
                ?: error("Voice model source did not return an HTTP connection.")
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
            val code = connection.responseCode
            if (code in REDIRECT_CODES) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                check(redirect < MAX_REDIRECTS && !location.isNullOrBlank()) { "Too many or invalid download redirects." }
                nextUrl = URL(url, location).toString()
            } else {
                return connection
            }
        }
        error("Too many download redirects.")
    }

    private fun installArchive(spec: VoiceModelSpec) {
        val base = modelRoot.parentFile?.canonicalFile ?: error("Voice model storage is unavailable.")
        check(base.exists() || base.mkdirs()) { "Unable to create voice model storage." }
        val staging = File(base, ".${spec.id}-${UUID.randomUUID()}.partial")
        val extractedRoot = File(staging, spec.archiveRoot)
        check(staging.mkdir()) { "Unable to create a temporary voice model directory." }
        try {
            extractTarBz2(cacheFile, staging, extractedRoot)
            check(validModelDirectory(extractedRoot)) { "The Kokoro archive is missing required model files." }
            check(!closed) { "Model manager is closed." }
            val backup = File(base, ".kokoro-${UUID.randomUUID()}.backup")
            if (modelRoot.exists()) check(modelRoot.renameTo(backup)) { "Unable to stage the previous voice model." }
            try {
                check(extractedRoot.renameTo(modelRoot)) { "Unable to activate the installed voice model files." }
                deleteTree(backup)
            } catch (failure: Exception) {
                if (backup.exists()) backup.renameTo(modelRoot)
                throw failure
            }
        } finally {
            deleteTree(staging)
            cacheFile.delete()
        }
    }

    private fun extractTarBz2(archive: File, staging: File, expectedRoot: File) {
        var extractedBytes = 0L
        var entries = 0
        BZip2CompressorInputStream(BufferedInputStream(FileInputStream(archive))).use { bzip ->
            TarArchiveInputStream(bzip).use { tar ->
                while (true) {
                    checkCancelled()
                    val entry = tar.nextEntry ?: break
                    entries++
                    check(entries <= MAX_ARCHIVE_ENTRIES) { "Voice model archive contains too many files." }
                    check(!entry.isSymbolicLink && !entry.isLink && !entry.isSparse) {
                        "Voice model archive contains an unsupported link."
                    }
                    val segments = safeSegments(entry.name, expectedRoot.name)
                    if (segments.isEmpty()) continue
                    val target = segments.fold(expectedRoot) { parent, segment -> File(parent, segment) }
                    val canonical = target.canonicalFile
                    val rootPath = expectedRoot.canonicalPath + File.separator
                    check(canonical.path == expectedRoot.canonicalPath || canonical.path.startsWith(rootPath)) {
                        "Voice model archive contains an unsafe path."
                    }
                    if (entry.isDirectory) {
                        check(canonical.mkdirs() || canonical.isDirectory) { "Unable to create a model directory." }
                    } else {
                        check(entry.isFile) { "Voice model archive contains an unsupported file type." }
                        check(canonical.parentFile?.let { it.mkdirs() || it.isDirectory } == true) {
                            "Unable to create a model file directory."
                        }
                        BufferedOutputStream(FileOutputStream(canonical)).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                checkCancelled()
                                val count = tar.read(buffer)
                                if (count < 0) break
                                extractedBytes += count
                                check(extractedBytes <= MAX_EXTRACTED_BYTES) { "Voice model archive exceeds the extraction limit." }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                }
            }
        }
        check(expectedRoot.isDirectory && expectedRoot.parentFile == staging) { "Archive root is invalid." }
    }

    private fun safeSegments(entryName: String, expectedRoot: String): List<String> {
        check(entryName.isNotBlank() && '\u0000' !in entryName && '\\' !in entryName) {
            "Voice model archive contains an unsafe path."
        }
        check(!entryName.startsWith('/') && !Regex("^[A-Za-z]:").containsMatchIn(entryName)) {
            "Voice model archive contains an absolute path."
        }
        val segments = entryName.split('/').filter { it.isNotEmpty() }
        check(segments.isNotEmpty() && segments.none { it == "." || it == ".." }) {
            "Voice model archive contains an unsafe path."
        }
        check(segments.first() == expectedRoot) { "Voice model archive has an unexpected root." }
        return segments.drop(1)
    }

    private fun validModelDirectory(directory: File): Boolean =
        directory.isDirectory &&
            (File(directory, "model.onnx").isFile || File(directory, "model.int8.onnx").isFile) &&
            File(directory, "voices.bin").isFile &&
            File(directory, "tokens.txt").isFile &&
            File(directory, "espeak-ng-data").isDirectory &&
            File(directory, "lexicon-us-en.txt").isFile &&
            File(directory, "lexicon-gb-en.txt").isFile &&
            File(directory, "lexicon-zh.txt").isFile

    private fun initialState(): VoiceModelTransferState = when {
        isInstalled() -> VoiceModelTransferState.Installed
        modelRoot.exists() -> VoiceModelTransferState.RemoveFailed("The installed model files are incomplete.")
        else -> VoiceModelTransferState.Idle
    }

    private fun activeModelIdFromPreferences(): String? =
        preferences.getString(ACTIVE_ID, null)?.takeIf { it == VoiceModelCatalog.KOKORO_ID && isInstalled() }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                checkCancelled()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun waitIfPaused() {
        if (!paused) return
        mutableState.value = VoiceModelTransferState.Paused
        synchronized(lock) {
            while (paused && !cancelled.get() && !closed) lock.wait()
        }
        checkCancelled()
        mutableState.value = VoiceModelTransferState.Downloading(cacheFile.length(), VoiceModelCatalog.models.first().archiveSizeBytes)
    }

    private fun checkCancelled() {
        if (cancelled.get() || closed) throw DownloadCancelledException()
    }

    private fun deleteTree(file: File): Boolean {
        if (!file.exists()) return true
        return try {
            file.walkBottomUp().all { it.delete() }
        } catch (_: SecurityException) {
            false
        }
    }

    override fun close() {
        closed = true
        cancelled.set(true)
        paused = false
        activeConnection?.disconnect()
        synchronized(lock) { lock.notifyAll() }
        executor.shutdownNow()
    }

    private class DownloadCancelledException : Exception()

    companion object {
        private const val PREFS = "zero_voice_models"
        private const val ACTIVE_ID = "active_tts_model_id"
        private const val MIB = 1024L * 1024L
        private const val MIN_FREE_SPACE_BYTES = 64L * MIB
        private const val MAX_EXTRACTED_BYTES = 700L * MIB
        private const val MAX_ARCHIVE_ENTRIES = 20_000
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_REDIRECTS = 5
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

        private fun kokoroSpeakers(): List<Pair<String, Int>> = buildList {
            KokoroSpeakerCatalog.english.forEach { add(it.name to it.speakerId) }
        }
    }
}

class KokoroTtsSession(directory: File, languageTag: String) : AutoCloseable {
    private val engine = OfflineTts(
        null,
        OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(
                    model = listOf("model.onnx", "model.int8.onnx")
                        .map { File(directory, it) }
                        .firstOrNull(File::isFile)
                        ?.absolutePath
                        ?: error("Kokoro ONNX model file is missing."),
                    voices = File(directory, "voices.bin").absolutePath,
                    tokens = File(directory, "tokens.txt").absolutePath,
                    dataDir = File(directory, "espeak-ng-data").absolutePath,
                    lexicon = listOf("lexicon-us-en.txt", "lexicon-gb-en.txt", "lexicon-zh.txt")
                        .joinToString(",") { File(directory, it).absolutePath },
                    lang = languageTag.lowercase(),
                    dictDir = File(directory, "dict").absolutePath,
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu",
            ),
        ),
    )

    fun generate(text: String, speakerId: Int) = engine.generate(text, speakerId, 1.0f)

    override fun close() {
        engine.release()
    }
}
