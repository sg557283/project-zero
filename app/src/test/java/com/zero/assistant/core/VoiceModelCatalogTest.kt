package com.zero.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceModelCatalogTest {
    @Test
    fun onlyAdvertisesTheVerifiedSherpaKokoroPack() {
        assertEquals(1, VoiceModelCatalog.models.size)
        val model = VoiceModelCatalog.model(VoiceModelCatalog.KOKORO_ID)

        assertTrue(model != null)
        assertEquals(147_052_523L, model?.archiveSizeBytes)
        assertEquals(VoiceModelCategory.EmotionBased, model?.category)
        assertEquals("Apache-2.0", model?.license)
        assertEquals(64, model?.sha256?.length)
        assertTrue(model?.summary?.contains("no Hindi") == true)
    }

    @Test
    fun actionBrainIsPinnedAndStoredAsAnOptionalDownload() {
        val model = VoiceModelCatalog.actionBrain

        assertEquals("functiongemma-270m-mobile-actions", model.id)
        assertEquals(284_426_240L, model.sizeBytes)
        assertEquals(64, model.sha256.length)
        assertTrue(model.downloadUrl.contains("/resolve/5d08507ee504b40476f8ab68486050837a093003/"))
        assertTrue(model.licenseUrl.startsWith("https://ai.google.dev/"))
    }
}
