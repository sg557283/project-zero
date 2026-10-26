package com.zero.assistant.core

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule

class SpeechModelStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private class MemoryMetadata : SpeechModelMetadata {
        private var installed = emptySet<String>()
        private var active: String? = null
        override fun installedIds() = installed
        override fun saveInstalledIds(ids: Set<String>) { installed = ids.toSet() }
        override fun activeModelId() = active
        override fun saveActiveModelId(id: String?) { active = id }
    }

    @Test
    fun verifiesArchiveAndPersistsInstalledActiveModel() {
        val archive = archive("model-hi", modelFiles())
        val spec = spec("hindi", "model-hi", archive, bundled = true)
        val metadata = MemoryMetadata()
        val root = temporaryFolder.newFolder("persisted-store")
        val store = store(listOf(spec), metadata, root)

        val installedDirectory = store.installArchive("hindi", archive)

        assertTrue(File(installedDirectory, "am/final.mdl").isFile)
        assertEquals(setOf("hindi"), store.installedIds())
        assertEquals("hindi", store.activeModelId())
        assertEquals("hindi", store(metadata = metadata, models = listOf(spec), root = root).activeModelId())
    }

    @Test
    fun installsBundledAssetThroughSeparateVerifiedPath() {
        val archive = archive("model-hi", modelFiles())
        val spec = spec("hindi", "model-hi", archive, bundled = true)
        val root = temporaryFolder.newFolder("bundled-store")
        val store = SpeechModelStore(SpeechModelCatalog(1, listOf(spec)), root, MemoryMetadata()) { true }

        val result = store.installBundled("hindi", ByteArrayInputStream(archive.readBytes()))

        assertEquals("hindi", store.activeModelId())
        assertTrue(File(result, "am/final.mdl").isFile)
        assertTrue(root.listFiles().orEmpty().none { it.name.endsWith(".bundled.zip") })
    }

    @Test
    fun installsTheActualBundledHindiArchiveFromTheAppAssets() {
        val assets = File("src/main/assets")
        val catalog = SpeechModelCatalogParser.parse(File(assets, "speech-models.json").readText())
        val spec = requireNotNull(catalog.model("vosk-hi-small-0.22"))
        val archive = File(assets, requireNotNull(spec.bundledAsset))
        val root = temporaryFolder.newFolder("real-hindi-model")
        val store = SpeechModelStore(catalog, root, MemoryMetadata()) { directory ->
            File(directory, "am/final.mdl").isFile &&
                File(directory, "conf/model.conf").isFile &&
                File(directory, "graph/Gr.fst").isFile &&
                File(directory, "graph/HCLr.fst").isFile
        }

        val installed = FileInputStream(archive).use { store.installBundled(spec.id, it) }

        assertEquals("${spec.archiveSizeBytes}", archive.length().toString())
        assertEquals(spec.id, store.activeModelId())
        assertTrue(File(installed, "am/final.mdl").isFile)
        assertTrue(File(installed, "graph/HCLr.fst").isFile)
        assertTrue(File(installed, "conf/model.conf").isFile)
        assertThrows(IllegalArgumentException::class.java) { store.uninstall(spec.id) }
    }

    @Test
    fun rejectsChecksumMismatchWithoutChangingActiveModel() {
        val archive = archive("model-hi", modelFiles())
        val spec = spec("hindi", "model-hi", archive, checksum = "0".repeat(64), bundled = true)
        val metadata = MemoryMetadata()
        val store = store(listOf(spec), metadata)

        assertThrows(IllegalArgumentException::class.java) { store.installArchive("hindi", archive) }
        assertTrue(store.installedIds().isEmpty())
        assertEquals(null, metadata.activeModelId())
    }

    @Test
    fun rejectsPathTraversalAndDoesNotWriteOutsidePrivateRoot() {
        val entries = modelFiles() + ("model-hi/../escaped.txt" to "escape")
        val archive = archive("model-hi", entries)
        val spec = spec("hindi", "model-hi", archive, bundled = true)
        val root = temporaryFolder.newFolder("store")
        val store = SpeechModelStore(SpeechModelCatalog(1, listOf(spec)), root, MemoryMetadata()) { true }

        assertThrows(IllegalStateException::class.java) { store.installArchive("hindi", archive) }
        assertFalse(File(root.parentFile, "escaped.txt").exists())
    }

    @Test
    fun doesNotMarkModelInstalledUntilVoskValidationSucceeds() {
        val archive = archive("model-hi", modelFiles())
        val spec = spec("hindi", "model-hi", archive, bundled = true)
        val metadata = MemoryMetadata()
        val store = SpeechModelStore(SpeechModelCatalog(1, listOf(spec)), temporaryFolder.newFolder(), metadata) { false }

        assertThrows(IllegalStateException::class.java) { store.installArchive("hindi", archive) }
        assertTrue(metadata.installedIds().isEmpty())
        assertEquals(null, metadata.activeModelId())
    }

    @Test
    fun keepsExistingActiveModelUntilSecondModelIsInstalledAndExplicitlySelected() {
        val hindiArchive = archive("model-hi", modelFiles())
        val englishArchive = archive("model-en", modelFiles())
        val hindi = spec("hindi", "model-hi", hindiArchive, bundled = true)
        val english = spec("english", "model-en", englishArchive)
        val metadata = MemoryMetadata()
        val root = temporaryFolder.newFolder("active-store")
        val store = store(listOf(hindi, english), metadata, root)

        store.installArchive("hindi", hindiArchive)
        store.installArchive("english", englishArchive)

        assertEquals("hindi", store.activeModelId())
        store.activate("english")
        assertEquals("english", store(metadata = metadata, models = listOf(hindi, english), root = root).activeModelId())
    }

    @Test
    fun cancellationLeavesNoPartialInstallation() {
        val archive = archive("model-hi", modelFiles())
        val spec = spec("hindi", "model-hi", archive, bundled = true)
        val root = temporaryFolder.newFolder("cancel-store")
        val store = SpeechModelStore(SpeechModelCatalog(1, listOf(spec)), root, MemoryMetadata()) { true }

        assertThrows(ModelInstallCancelledException::class.java) {
            store.installArchive("hindi", archive, isCancelled = { true })
        }
        assertFalse(File(root, "hindi").exists())
    }

    private fun store(
        models: List<SpeechModelSpec>,
        metadata: MemoryMetadata,
        root: File = temporaryFolder.newFolder(),
    ): SpeechModelStore = SpeechModelStore(SpeechModelCatalog(1, models), root, metadata) { true }

    private fun spec(id: String, archiveRoot: String, archive: File, checksum: String? = null, bundled: Boolean = false) =
        SpeechModelSpec(
            id = id,
            language = if (id == "hindi") "hi-IN" else "en-US",
            languageName = if (id == "hindi") "Hindi" else "English",
            name = id,
            version = "1",
            archiveSizeBytes = archive.length(),
            maxExtractedBytes = 10_000,
            sha256 = checksum ?: sha256(archive),
            archiveRoot = archiveRoot,
            sourceUrl = "https://alphacephei.com/vosk/models/$archiveRoot.zip",
            license = "Apache-2.0",
            bundledAsset = if (bundled) "models/$archiveRoot.zip" else null,
        )

    private fun modelFiles() = listOf(
        "am/final.mdl" to "acoustic model",
        "conf/model.conf" to "model config",
        "graph/Gr.fst" to "graph",
        "graph/HCLr.fst" to "decoder graph",
    )

    private fun archive(root: String, files: List<Pair<String, String>>): File {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            files.forEach { (path, text) ->
                zip.putNextEntry(ZipEntry("$root/$path"))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return temporaryFolder.newFile().apply { writeBytes(bytes.toByteArray()) }
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes())
        .joinToString("") { "%02x".format(it) }
}