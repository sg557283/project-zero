package com.zero.assistant.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import com.zero.assistant.core.HumanoidVoiceCatalog
import com.zero.assistant.core.HumanoidVoiceGender
import com.zero.assistant.core.HumanoidVoiceSpec
import com.zero.assistant.core.VoiceModelTransferState
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

class HumanoidTtsManager(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val modelRoot = File(appContext.filesDir, "tts_models")
    private val cacheRoot = File(appContext.cacheDir, "tts_models")
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val operationLock = Any()
    private val transferStates = ConcurrentHashMap<String, MutableStateFlow<VoiceModelTransferState>>()
    private val cancelled = AtomicBoolean(false)
    @Volatile private var activeConnection: HttpURLConnection? = null
    @Volatile private var activeOperationId: String? = null
    @Volatile private var closed = false

    private val mutableActiveVoiceId = MutableStateFlow(
        preferences.getString(ACTIVE_VOICE_ID, null)
            ?.takeIf { id -> HumanoidVoiceCatalog.voice(id)?.let(::isInstalled) == true },
    )
    val activeVoiceId: StateFlow<String?> = mutableActiveVoiceId.asStateFlow()
    private val mutablePreferredGender = MutableStateFlow(
        preferences.getString(PREFERRED_GENDER, null)
            ?.let { value -> HumanoidVoiceGender.entries.firstOrNull { it.name == value } }
            ?: preferences.getString(ACTIVE_VOICE_ID, null)
                ?.let(HumanoidVoiceCatalog::voice)
                ?.gender,
    )
    val preferredGender: StateFlow<HumanoidVoiceGender?> = mutablePreferredGender.asStateFlow()

    fun state(voiceId: String): StateFlow<VoiceModelTransferState> =
        stateFlow(requireVoice(voiceId)).asStateFlow()

    fun isInstalled(voiceId: String): Boolean =
        HumanoidVoiceCatalog.voice(voiceId)?.let(::isInstalled) == true

    fun installedVoices(): List<HumanoidVoiceSpec> =
        HumanoidVoiceCatalog.all.filter(::isInstalled)

    fun modelDirectory(voiceId: String): File? =
        HumanoidVoiceCatalog.voice(voiceId)
            ?.let(::modelDirectory)
            ?.takeIf(::isValidModelDirectory)

    fun activate(voiceId: String): Boolean {
        val spec = HumanoidVoiceCatalog.voice(voiceId) ?: return false
        if (closed || !isInstalled(spec)) return false
        val saved = preferences.edit().putString(ACTIVE_VOICE_ID, spec.voiceId).commit()
        if (saved) mutableActiveVoiceId.value = spec.voiceId
        return saved
    }

    fun deactivate() {
        preferences.edit().remove(ACTIVE_VOICE_ID).apply()
        mutableActiveVoiceId.value = null
    }

    fun setPreferredGender(gender: HumanoidVoiceGender): Boolean {
        if (closed) return false
        val saved = preferences.edit().putString(PREFERRED_GENDER, gender.name).commit()
        if (saved) mutablePreferredGender.value = gender
        return saved
    }

    fun download(voiceId: String): Boolean {
        val spec = HumanoidVoiceCatalog.voice(voiceId) ?: return false
        synchronized(operationLock) {
            if (closed || activeOperationId != null || isInstalled(spec) || executor.isShutdown) return false
            cancelled.set(false)
            activeOperationId = spec.voiceId
            stateFlow(spec).value = VoiceModelTransferState.Downloading(0L, spec.archiveSizeBytes)
            return try {
                executor.execute { downloadAndInstall(spec) }
                true
            } catch (_: RejectedExecutionException) {
                activeOperationId = null
                stateFlow(spec).value = VoiceModelTransferState.Failed("Voice download could not be queued.")
                false
            }
        }
    }

    fun cancel(voiceId: String): Boolean {
        val spec = HumanoidVoiceCatalog.voice(voiceId) ?: return false
        if (activeOperationId != spec.voiceId) return false
        cancelled.set(true)
        activeConnection?.disconnect()
        return true
    }

    fun remove(voiceId: String, onComplete: (Boolean) -> Unit): Boolean {
        val spec = HumanoidVoiceCatalog.voice(voiceId) ?: return false
        synchronized(operationLock) {
            if (closed || activeOperationId != null || !modelDirectory(spec).exists() || executor.isShutdown) return false
            activeOperationId = spec.voiceId
            stateFlow(spec).value = VoiceModelTransferState.Removing
            if (mutableActiveVoiceId.value == spec.voiceId) {
                if (!preferences.edit().remove(ACTIVE_VOICE_ID).commit()) {
                    activeOperationId = null
                    stateFlow(spec).value = VoiceModelTransferState.Installed
                    return false
                }
                mutableActiveVoiceId.value = null
            }
            return try {
                executor.execute {
                    val removed = deleteTree(modelDirectory(spec))
                    stateFlow(spec).value = if (removed) {
                        VoiceModelTransferState.Idle
                    } else {
                        VoiceModelTransferState.RemoveFailed("Some voice files could not be removed.")
                    }
                    synchronized(operationLock) { activeOperationId = null }
                    mainHandler.post { onComplete(removed) }
                }
                true
            } catch (_: RejectedExecutionException) {
                activeOperationId = null
                stateFlow(spec).value = VoiceModelTransferState.Installed
                false
            }
        }
    }

    override fun close() {
        closed = true
        cancelled.set(true)
        activeConnection?.disconnect()
        executor.shutdownNow()
    }

    private fun downloadAndInstall(spec: HumanoidVoiceSpec) {
        val cacheFile = File(cacheRoot, "${spec.modelId}.tar.bz2.part")
        try {
            val requiredBytes = spec.archiveSizeBytes + MIN_FREE_SPACE_BYTES
            check(modelRoot.exists() || modelRoot.mkdirs()) { "Unable to access offline voice storage." }
            check(cacheRoot.exists() || cacheRoot.mkdirs()) { "Unable to access voice download cache." }
            if (StatFs(modelRoot.absolutePath).availableBytes < requiredBytes) {
                error("Not enough free storage. Free at least ${requiredBytes / MIB} MiB and retry.")
            }
            cacheFile.delete()
            downloadArchive(spec, cacheFile)
            checkCancelled()
            check(cacheFile.length() == spec.archiveSizeBytes) { "Downloaded archive size did not match the catalog." }
            check(sha256(cacheFile) == spec.sha256) { "Downloaded archive checksum did not match the catalog." }
            stateFlow(spec).value = VoiceModelTransferState.Installing
            installArchive(spec, cacheFile)
            stateFlow(spec).value = VoiceModelTransferState.Installed
        } catch (_: VoiceDownloadCancelledException) {
            cacheFile.delete()
            stateFlow(spec).value = VoiceModelTransferState.Cancelled
        } catch (failure: Exception) {
            cacheFile.delete()
            stateFlow(spec).value = if (cancelled.get() || closed) {
                VoiceModelTransferState.Cancelled
            } else {
                VoiceModelTransferState.Failed(failure.message ?: "Voice download or installation failed.")
            }
        } finally {
            activeConnection?.disconnect()
            activeConnection = null
            synchronized(operationLock) { activeOperationId = null }
        }
    }

    private fun downloadArchive(spec: HumanoidVoiceSpec, cacheFile: File) {
        val connection = openHttpsConnection(spec.downloadUrl)
        activeConnection = connection
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Voice model server returned HTTP ${connection.responseCode}."
            }
            val announcedLength = connection.contentLengthLong
            if (announcedLength >= 0L) {
                check(announcedLength == spec.archiveSizeBytes) { "Voice model size did not match the catalog." }
            }
            var received = 0L
            FileOutputStream(cacheFile).use { output ->
                BufferedInputStream(connection.inputStream).use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        checkCancelled()
                        val count = input.read(buffer)
                        if (count < 0) break
                        received += count
                        check(received <= spec.archiveSizeBytes) { "Voice model download exceeded the catalog size." }
                        output.write(buffer, 0, count)
                        stateFlow(spec).value = VoiceModelTransferState.Downloading(received, spec.archiveSizeBytes)
                    }
                    output.fd.sync()
                }
            }
        } finally {
            connection.disconnect()
            activeConnection = null
        }
    }

    private fun openHttpsConnection(source: String): HttpURLConnection {
        var nextUrl = source
        repeat(MAX_REDIRECTS + 1) { redirect ->
            val url = URL(nextUrl)
            check(url.protocol == "https") { "Voice model source must use HTTPS." }
            val connection = url.openConnection() as? HttpURLConnection
                ?: error("Voice model source did not return an HTTP connection.")
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            val code = connection.responseCode
            if (code in REDIRECT_CODES) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                check(redirect < MAX_REDIRECTS && !location.isNullOrBlank()) {
                    "Too many or invalid voice model redirects."
                }
                nextUrl = URL(url, location).toString()
            } else {
                return connection
            }
        }
        error("Too many voice model redirects.")
    }

    private fun installArchive(spec: HumanoidVoiceSpec, archive: File) {
        val staging = File(modelRoot, ".${spec.modelId}-${UUID.randomUUID()}.partial")
        val extractedRoot = File(staging, spec.archiveRoot)
        check(staging.mkdir()) { "Unable to create a temporary voice model directory." }
        try {
            extractTarBz2(archive, staging, extractedRoot)
            check(isValidModelDirectory(extractedRoot)) { "The voice archive is missing required model files." }
            check(!closed) { "Voice model manager is closed." }
            val destination = modelDirectory(spec)
            val backup = File(modelRoot, ".${spec.modelId}-${UUID.randomUUID()}.backup")
            if (destination.exists()) check(destination.renameTo(backup)) { "Unable to stage the previous voice files." }
            try {
                check(extractedRoot.renameTo(destination)) { "Unable to activate the installed voice model files." }
                deleteTree(backup)
            } catch (failure: Exception) {
                if (backup.exists()) backup.renameTo(destination)
                throw failure
            }
        } finally {
            deleteTree(staging)
            archive.delete()
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
                    check(entries <= MAX_ARCHIVE_ENTRIES) { "Voice archive contains too many files." }
                    check(!entry.isSymbolicLink && !entry.isLink && !entry.isSparse) {
                        "Voice archive contains an unsupported link."
                    }
                    val segments = safeSegments(entry.name, expectedRoot.name)
                    if (segments.isEmpty()) continue
                    val target = segments.fold(expectedRoot) { parent, segment -> File(parent, segment) }
                    val canonical = target.canonicalFile
                    val rootPath = expectedRoot.canonicalPath + File.separator
                    check(canonical.path == expectedRoot.canonicalPath || canonical.path.startsWith(rootPath)) {
                        "Voice archive contains an unsafe path."
                    }
                    if (entry.isDirectory) {
                        check(canonical.mkdirs() || canonical.isDirectory) { "Unable to create a voice model directory." }
                    } else {
                        check(entry.isFile) { "Voice archive contains an unsupported file type." }
                        check(canonical.parentFile?.let { it.mkdirs() || it.isDirectory } == true) {
                            "Unable to create a voice model file directory."
                        }
                        BufferedOutputStream(FileOutputStream(canonical)).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                checkCancelled()
                                val count = tar.read(buffer)
                                if (count < 0) break
                                extractedBytes += count
                                check(extractedBytes <= MAX_EXTRACTED_BYTES) {
                                    "Voice archive exceeds the extraction limit."
                                }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                }
            }
        }
        check(expectedRoot.isDirectory && expectedRoot.parentFile == staging) { "Voice archive root is invalid." }
    }

    private fun safeSegments(entryName: String, expectedRoot: String): List<String> {
        check(entryName.isNotBlank() && '\u0000' !in entryName && '\\' !in entryName) {
            "Voice archive contains an unsafe path."
        }
        check(!entryName.startsWith('/') && !Regex("^[A-Za-z]:").containsMatchIn(entryName)) {
            "Voice archive contains an absolute path."
        }
        val segments = entryName.split('/').filter { it.isNotEmpty() }
        check(segments.isNotEmpty() && segments.none { it == "." || it == ".." }) {
            "Voice archive contains an unsafe path."
        }
        check(segments.first() == expectedRoot) { "Voice archive has an unexpected root." }
        return segments.drop(1)
    }

    private fun isInstalled(spec: HumanoidVoiceSpec): Boolean = isValidModelDirectory(modelDirectory(spec))

    private fun isValidModelDirectory(directory: File): Boolean =
        directory.isDirectory &&
            directory.listFiles().orEmpty().any { it.isFile && it.extension.equals("onnx", ignoreCase = true) } &&
            File(directory, "tokens.txt").isFile &&
            File(directory, "espeak-ng-data").isDirectory

    private fun modelDirectory(spec: HumanoidVoiceSpec): File = File(modelRoot, spec.modelId)

    private fun stateFlow(spec: HumanoidVoiceSpec): MutableStateFlow<VoiceModelTransferState> =
        transferStates.getOrPut(spec.voiceId) {
            MutableStateFlow(
                when {
                    isInstalled(spec) -> VoiceModelTransferState.Installed
                    modelDirectory(spec).exists() ->
                        VoiceModelTransferState.RemoveFailed("The installed voice files are incomplete.")
                    else -> VoiceModelTransferState.Idle
                },
            )
        }

    private fun requireVoice(voiceId: String): HumanoidVoiceSpec =
        requireNotNull(HumanoidVoiceCatalog.voice(voiceId)) { "Unknown humanoid voice: $voiceId" }

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

    private fun checkCancelled() {
        if (cancelled.get() || closed) throw VoiceDownloadCancelledException()
    }

    private fun deleteTree(file: File): Boolean {
        if (!file.exists()) return true
        return try {
            file.walkBottomUp().all { it.delete() }
        } catch (_: SecurityException) {
            false
        }
    }

    private class VoiceDownloadCancelledException : Exception()

    companion object {
        private const val PREFERENCES = "zero_humanoid_tts"
        private const val ACTIVE_VOICE_ID = "active_voice_id"
        private const val PREFERRED_GENDER = "preferred_gender"
        private const val MIB = 1024L * 1024L
        private const val MIN_FREE_SPACE_BYTES = 64L * MIB
        private const val MAX_EXTRACTED_BYTES = 256L * MIB
        private const val MAX_ARCHIVE_ENTRIES = 20_000
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_REDIRECTS = 5
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
