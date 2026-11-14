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
        assertEquals(132_353_410L, model?.archiveSizeBytes)
        assertEquals(VoiceModelCategory.EmotionBased, model?.category)
        assertEquals("Apache-2.0", model?.license)
        assertEquals(64, model?.sha256?.length)
        assertTrue(model?.summary?.contains("no Hindi") == true)
    }
}
