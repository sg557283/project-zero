package com.zero.assistant.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechModelCatalogTest {
    @Test
    fun officialIndianEnglishMobileModelHasVerifiedArchiveMetadata() {
        val catalogFile = File("src/main/assets/speech-models.json")
        val catalog = SpeechModelCatalogParser.parse(catalogFile.readText())
        val model = requireNotNull(catalog.model("vosk-en-in-small-0.4"))

        assertEquals("en-IN", model.language)
        assertEquals(37_573_330L, model.archiveSizeBytes)
        assertEquals(
            "20663dcac4d5cb783a579c54d98339344a688e4ec6e1b4a4b059fd1235454cc7",
            model.sha256,
        )
        assertEquals(
            "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip",
            model.sourceUrl,
        )
    }

    @Test
    fun parsesCuratedBundledHindiAndDownloadableEnglishModels() {
        val catalog = SpeechModelCatalogParser.parse(catalogJson())

        assertEquals(1, catalog.version)
        assertEquals(listOf("hindi", "english"), catalog.models.map { it.id })
        assertEquals("Apache-2.0", catalog.model("hindi")?.license)
        assertNull(catalog.model("english")?.bundledAsset)
    }

    @Test
    fun rejectsNonHttpsOrUncuratedSources() {
        val json = catalogJson().replace("https://alphacephei.com", "https://example.com")

        assertThrows(IllegalArgumentException::class.java) { SpeechModelCatalogParser.parse(json) }
    }

    @Test
    fun rejectsDuplicateIdsAndUnsupportedLicenses() {
        val duplicate = catalogJson().replace("\"id\": \"english\"", "\"id\": \"hindi\"")
        assertThrows(IllegalArgumentException::class.java) { SpeechModelCatalogParser.parse(duplicate) }

        val unknownLicense = catalogJson().replace("Apache-2.0", "Unknown")
        assertThrows(IllegalArgumentException::class.java) { SpeechModelCatalogParser.parse(unknownLicense) }
    }

    @Test
    fun preventsConcurrentDownloadsAndReportsProgressAndCompletion() {
        val machine = ModelTransferStateMachine()
        assertTrue(machine.begin("english"))
        assertEquals(false, machine.begin("hindi"))
        machine.progress("english", 50, 100)
        assertEquals(ModelTransferState.Downloading(50, 100), machine.state("english"))
        machine.installing("english")
        assertEquals(ModelTransferState.Installing, machine.state("english"))
        machine.finish("english", ModelTransferState.Installed)
        assertEquals(ModelTransferState.Installed, machine.state("english"))
        assertTrue(machine.begin("hindi"))
    }

      @Test
      fun clearsFailedOrCancelledTransfersAndAllowsRetry() {
        val machine = ModelTransferStateMachine()
        assertTrue(machine.begin("english"))
        machine.finish("english", ModelTransferState.Failed("Network unavailable"))
        assertEquals(ModelTransferState.Failed("Network unavailable"), machine.state("english"))
        assertTrue(machine.begin("english"))
        machine.finish("english", ModelTransferState.Cancelled)
        assertEquals(ModelTransferState.Cancelled, machine.state("english"))
        assertTrue(machine.begin("english"))
      }

    private fun catalogJson(): String = """
        {
          "version": 1,
          "models": [
            {
              "id": "hindi", "language": "hi-IN", "languageName": "Hindi",
              "name": "Hindi Small", "version": "0.22", "archiveSizeBytes": 100,
              "maxExtractedBytes": 1000,
              "sha256": "${"a".repeat(64)}", "archiveRoot": "vosk-model-hi",
              "sourceUrl": "https://alphacephei.com/vosk/models/vosk-model-hi.zip",
              "license": "Apache-2.0", "bundledAsset": "models/vosk-model-hi.zip"
            },
            {
              "id": "english", "language": "en-US", "languageName": "English (US)",
              "name": "English Small", "version": "0.15", "archiveSizeBytes": 100,
              "maxExtractedBytes": 1000,
              "sha256": "${"b".repeat(64)}", "archiveRoot": "vosk-model-en",
              "sourceUrl": "https://alphacephei.com/vosk/models/vosk-model-en.zip",
              "license": "Apache-2.0", "bundledAsset": null
            }
          ]
        }
    """.trimIndent()
}