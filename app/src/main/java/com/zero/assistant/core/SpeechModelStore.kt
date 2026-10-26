package com.zero.assistant.core

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

interface SpeechModelMetadata {
    fun installedIds(): Set<String>
    fun saveInstalledIds(ids: Set<String>)
    fun activeModelId(): String?
    fun saveActiveModelId(id: String?)
}

fun interface SpeechModelValidator {
    fun isValid(modelDirectory: File): Boolean
}

class SpeechModelStore(
    private val catalog: SpeechModelCatalog,
    private val root: File,
    private val metadata: SpeechModelMetadata,
    private val validator: SpeechModelValidator,
) {
    fun installedIds(): Set<String> = metadata.installedIds().filterTo(mutableSetOf()) { id ->
        val model = catalog.model(id)
        model != null && modelDirectory(id).isDirectory && isModelLayoutValid(modelDirectory(id))
    }

    fun activeModelId(): String? = metadata.activeModelId()?.takeIf { id ->
        id in installedIds()
    }

    fun activate(modelId: String) {
        require(catalog.model(modelId) != null) { "Unknown speech model" }
        require(modelId in installedIds()) { "Speech model is not installed" }
        metadata.saveActiveModelId(modelId)
    }

    fun installBundled(modelId: String, asset: InputStream): File {
        val spec = catalog.model(modelId) ?: throw IllegalArgumentException("Unknown speech model")
        require(spec.bundledAsset != null) { "Speech model is not bundled" }
        if (!root.exists() && !root.mkdirs()) throw IllegalStateException("Unable to create model storage")
        val stagedArchive = File(root.canonicalFile, ".$modelId-${UUID.randomUUID()}.bundled.zip")
        try {
            asset.use { input -> FileOutputStream(stagedArchive).use { output -> input.copyTo(output) } }
            return installArchive(modelId, stagedArchive)
        } finally {
            stagedArchive.delete()
        }
    }

    fun installArchive(
        modelId: String,
        archive: File,
        isCancelled: () -> Boolean = { false },
    ): File {
        val spec = catalog.model(modelId) ?: throw IllegalArgumentException("Unknown speech model")
        require(archive.isFile) { "Model archive is missing" }
        require(archive.length() == spec.archiveSizeBytes) { "Model archive size does not match catalog" }
        checkCancelled(isCancelled)
        require(sha256(archive, isCancelled) == spec.sha256) { "Model archive checksum does not match catalog" }
        checkCancelled(isCancelled)

        val canonicalRoot = root.canonicalFile
        if (!canonicalRoot.exists() && !canonicalRoot.mkdirs()) throw IllegalStateException("Unable to create model storage")
        val workDirectory = File(canonicalRoot, ".$modelId-${UUID.randomUUID()}.partial")
        check(workDirectory.mkdir()) { "Unable to create temporary model directory" }
        try {
            val extractedModel = extractArchive(spec, archive, workDirectory, isCancelled)
            check(isModelLayoutValid(extractedModel)) { "Model archive is missing required files" }
            check(validator.isValid(extractedModel)) { "Vosk could not load this model" }
            checkCancelled(isCancelled)

            val destination = modelDirectory(modelId)
            val backup = File(canonicalRoot, ".$modelId-${UUID.randomUUID()}.backup")
            val previousIds = metadata.installedIds()
            val previousActive = metadata.activeModelId()
            try {
                if (destination.exists()) move(destination, backup)
                move(extractedModel, destination)
                metadata.saveInstalledIds(previousIds + modelId)
                if (previousActive == null || previousActive !in previousIds) metadata.saveActiveModelId(modelId)
                deleteTree(backup)
            } catch (failure: Throwable) {
                deleteTree(destination)
                if (backup.exists()) move(backup, destination)
                runCatching { metadata.saveInstalledIds(previousIds) }
                runCatching { metadata.saveActiveModelId(previousActive) }
                throw failure
            }
            return destination
        } finally {
            deleteTree(workDirectory)
        }
    }

    fun uninstall(modelId: String) {
        val spec = catalog.model(modelId) ?: throw IllegalArgumentException("Unknown speech model")
        require(spec.bundledAsset == null) { "Bundled speech models cannot be removed" }
        require(modelId in installedIds()) { "Speech model is not installed" }
        val installed = installedIds()
        if (metadata.activeModelId() == modelId) {
            val replacement = installed.firstOrNull { it != modelId }
                ?: throw IllegalStateException("Install another speech model before removing the active model")
            metadata.saveActiveModelId(replacement)
        }
        val destination = modelDirectory(spec.id)
        deleteTree(destination)
        metadata.saveInstalledIds(installed - modelId)
        if (metadata.activeModelId() == modelId) metadata.saveActiveModelId(null)
    }

    fun modelDirectory(modelId: String): File {
        require(catalog.model(modelId) != null) { "Unknown speech model" }
        return File(root.canonicalFile, modelId)
    }

    private fun extractArchive(
        spec: SpeechModelSpec,
        archive: File,
        workDirectory: File,
        isCancelled: () -> Boolean,
    ): File {
        val extractionRoot = File(workDirectory, spec.archiveRoot)
        var extractedBytes = 0L
        var entryCount = 0
        ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { zip ->
            while (true) {
                checkCancelled(isCancelled)
                val entry = zip.nextEntry ?: break
                entryCount += 1
                check(entryCount <= MAX_ARCHIVE_ENTRIES) { "Model archive has too many entries" }
                val segments = safeSegments(entry.name, spec.archiveRoot)
                if (segments.isNotEmpty()) {
                    val target = segments.fold(extractionRoot) { parent, segment -> File(parent, segment) }
                    val canonicalTarget = target.canonicalFile
                    val rootPath = extractionRoot.canonicalPath + File.separator
                    check(canonicalTarget.path == extractionRoot.canonicalPath || canonicalTarget.path.startsWith(rootPath)) {
                        "Model archive contains an unsafe path"
                    }
                    if (entry.isDirectory) {
                        check(canonicalTarget.mkdirs() || canonicalTarget.isDirectory) { "Unable to create model directory" }
                    } else {
                        check(canonicalTarget.parentFile?.let { it.mkdirs() || it.isDirectory } == true) {
                            "Unable to create model files"
                        }
                        BufferedOutputStream(FileOutputStream(canonicalTarget)).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                checkCancelled(isCancelled)
                                val count = zip.read(buffer)
                                if (count < 0) break
                                extractedBytes += count
                                check(extractedBytes <= spec.maxExtractedBytes) { "Model archive exceeds extraction limit" }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        return extractionRoot
    }

    private fun safeSegments(entryName: String, expectedRoot: String): List<String> {
        check(entryName.isNotBlank() && '\u0000' !in entryName && '\\' !in entryName) {
            "Model archive contains an unsafe path"
        }
        check(!entryName.startsWith('/') && !Regex("^[A-Za-z]:").containsMatchIn(entryName)) {
            "Model archive contains an absolute path"
        }
        val segments = entryName.split('/').filter { it.isNotEmpty() }
        check(segments.isNotEmpty() && segments.none { it == "." || it == ".." }) {
            "Model archive contains an unsafe path"
        }
        check(segments.first() == expectedRoot) { "Model archive has an unexpected root directory" }
        return segments.drop(1)
    }

    private fun isModelLayoutValid(directory: File): Boolean =
        File(directory, "am/final.mdl").isFile &&
            File(directory, "conf/model.conf").isFile &&
            File(directory, "graph/Gr.fst").isFile &&
            File(directory, "graph/HCLr.fst").isFile

    private fun sha256(file: File, isCancelled: () -> Boolean): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                checkCancelled(isCancelled)
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun checkCancelled(isCancelled: () -> Boolean) {
        if (isCancelled()) throw ModelInstallCancelledException()
    }

    private fun move(source: File, destination: File) {
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath())
        }
    }

    private fun deleteTree(file: File) {
        if (!file.exists()) return
        Files.walk(file.toPath()).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    companion object {
        private const val MAX_ARCHIVE_ENTRIES = 10_000
    }
}

class ModelInstallCancelledException : Exception("Model installation was cancelled")