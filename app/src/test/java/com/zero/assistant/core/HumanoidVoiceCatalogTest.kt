package com.zero.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HumanoidVoiceCatalogTest {
    @Test
    fun containsAllRequestedVoicesInTheirGenderGroups() {
        val expectedFemale = setOf(
            "vits-piper-hi_IN-priyamvada-medium",
            "vits-piper-en_US-amy-medium",
            "vits-piper-en_US-lessac-medium",
            "vits-piper-en_US-kristin-medium",
            "vits-piper-en_GB-jenny_dioco-medium",
            "vits-piper-en_GB-cori-high",
        )
        val expectedMale = setOf(
            "vits-piper-hi_IN-pratham-medium",
            "vits-piper-hi_IN-rohan-medium",
            "vits-piper-en_US-ryan-high",
            "vits-piper-en_US-joe-medium",
            "vits-piper-en_US-bryce-medium",
            "vits-piper-en_GB-alan-medium",
        )

        assertEquals(expectedFemale, HumanoidVoiceCatalog.female.map { it.modelId }.toSet())
        assertEquals(expectedMale, HumanoidVoiceCatalog.male.map { it.modelId }.toSet())
        assertEquals(12, HumanoidVoiceCatalog.all.map { it.modelId }.toSet().size)
        assertTrue(HumanoidVoiceCatalog.all.all {
            it.archiveSizeBytes > 0L && it.sha256.matches(Regex("[a-f0-9]{64}")) &&
                it.downloadUrl.endsWith("${it.archiveId}.tar.bz2")
        })
    }

    @Test
    fun exposesOnlyTheDocumentedKokoroEnglishSpeakers() {
        assertEquals(
            listOf("af_maple" to 0, "af_sol" to 1, "bf_vale" to 2),
            KokoroSpeakerCatalog.english.map { it.name to it.speakerId },
        )
        assertTrue(KokoroSpeakerCatalog.english.all { it.languageTag.startsWith("en-") })
        assertTrue(KokoroSpeakerCatalog.speaker("hf_alpha") == null)
        assertTrue(KokoroSpeakerCatalog.speaker("am_adam") == null)
    }
}
