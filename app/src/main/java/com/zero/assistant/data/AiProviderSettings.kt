package com.zero.assistant.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.zero.assistant.core.AiProviderContract
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AiProviderConfiguration(
    val enabled: Boolean,
    val endpoint: String,
    val model: String,
    val hasApiKey: Boolean,
)

class AiProviderSettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun configuration(): AiProviderConfiguration = AiProviderConfiguration(
        enabled = preferences.getBoolean(KEY_ENABLED, false),
        endpoint = preferences.getString(KEY_ENDPOINT, "").orEmpty(),
        model = preferences.getString(KEY_MODEL, "").orEmpty(),
        hasApiKey = preferences.contains(KEY_ENCRYPTED_KEY),
    )

    fun saveConfiguration(endpoint: String, model: String, enabled: Boolean): Result<Unit> = runCatching {
        val cleanEndpoint = endpoint.trim()
        val cleanModel = model.trim()
        require(cleanEndpoint.isEmpty() || AiProviderContract.validHttpsEndpoint(cleanEndpoint)) {
            "Use a complete HTTPS OpenAI-compatible Chat Completions endpoint."
        }
        require(cleanModel.length <= MAX_MODEL_LENGTH) { "Model name is too long." }
        require(!enabled || (cleanEndpoint.isNotBlank() && cleanModel.isNotBlank())) {
            "Enter an HTTPS endpoint and model before enabling online AI."
        }
        require(!enabled || preferences.contains(KEY_ENCRYPTED_KEY)) {
            "Save an API key before enabling online AI."
        }
        preferences.edit()
            .putString(KEY_ENDPOINT, cleanEndpoint)
            .putString(KEY_MODEL, cleanModel)
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun saveApiKey(value: String): Boolean {
        if (value.isBlank()) return false
        return runCatching {
            val encrypted = encrypt(value.trim())
            preferences.edit().putString(KEY_ENCRYPTED_KEY, encrypted).apply()
        }.isSuccess
    }

    fun clearApiKey() {
        preferences.edit().remove(KEY_ENCRYPTED_KEY).putBoolean(KEY_ENABLED, false).apply()
    }

    fun apiKey(): String? = runCatching {
        val encrypted = preferences.getString(KEY_ENCRYPTED_KEY, null) ?: return null
        decrypt(encrypted)
    }.getOrNull()

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val payload = ByteBuffer.allocate(1 + iv.size + ciphertext.size)
            .put(iv.size.toByte())
            .put(iv)
            .put(ciphertext)
            .array()
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val payload = ByteBuffer.wrap(Base64.decode(value, Base64.NO_WRAP))
        val ivSize = payload.get().toInt() and 0xFF
        require(ivSize in MIN_IV_SIZE..MAX_IV_SIZE && payload.remaining() > ivSize)
        val iv = ByteArray(ivSize).also(payload::get)
        val ciphertext = ByteArray(payload.remaining()).also(payload::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES = "zero_ai_provider"
        const val KEY_ENABLED = "enabled"
        const val KEY_ENDPOINT = "endpoint"
        const val KEY_MODEL = "model"
        const val KEY_ENCRYPTED_KEY = "encrypted_key"
        const val KEY_ALIAS = "zero_ai_provider_key_v1"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val MIN_IV_SIZE = 12
        const val MAX_IV_SIZE = 16
        const val MAX_MODEL_LENGTH = 120
    }
}