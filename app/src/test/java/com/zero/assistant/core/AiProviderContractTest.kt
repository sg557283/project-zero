package com.zero.assistant.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderContractTest {
    @Test
    fun acceptsOnlyCredentialSafeHttpsEndpoints() {
        assertTrue(AiProviderContract.validHttpsEndpoint("https://provider.example/v1/chat/completions"))
        assertFalse(AiProviderContract.validHttpsEndpoint("http://provider.example/v1/chat/completions"))
        assertFalse(AiProviderContract.validHttpsEndpoint("https://user:secret@provider.example/v1/chat/completions"))
        assertFalse(AiProviderContract.validHttpsEndpoint("https://provider.example/path#fragment"))
    }
}