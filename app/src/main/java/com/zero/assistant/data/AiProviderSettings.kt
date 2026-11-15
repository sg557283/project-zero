package com.zero.assistant.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.zero.assistant.core.AiProviderContract
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AiProviderProfile(
    val id: String,
    val name: String,
    val endpoint: String,
    val model: String,
    val hasApiKey: Boolean,
)

data class AiProviderConfiguration(
    val enabled: Boolean,
    val endpoint: String,
    val model: String,
    val hasApiKey: Boolean,
    val selectedProfileId: String?,
    val profiles: List<AiProviderProfile>,
)

class AiProviderSettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    @Synchronized
    fun configuration(): AiProviderConfiguration {
        migrateLegacyConfiguration()
        val profiles = readProfiles()
        val selected = profiles.firstOrNull { it.id == selectedProfileId() }
        return AiProviderConfiguration(
            enabled = preferences.getBoolean(KEY_ENABLED, false) && selected?.hasApiKey == true,
            endpoint = selected?.endpoint.orEmpty(),
            model = selected?.model.orEmpty(),
            hasApiKey = selected?.hasApiKey == true,
            selectedProfileId = selected?.id,
            profiles = profiles,
        )
    }

    @Synchronized
    fun profiles(): List<AiProviderProfile> {
        migrateLegacyConfiguration()
        return readProfiles()
    }

    @Synchronized
    fun saveProfile(
        id: String?,
        name: String,
        endpoint: String,
        model: String,
        apiKey: String,
    ): Result<String> = runCatching {
        migrateLegacyConfiguration()
        val cleanName = name.trim().take(MAX_NAME_LENGTH)
        val cleanEndpoint = endpoint.trim()
        val cleanModel = model.trim()
        require(cleanName.isNotBlank()) { "Enter a name for this API profile." }
        require(AiProviderContract.validHttpsEndpoint(cleanEndpoint)) {
            "Use a complete HTTPS OpenAI-compatible Chat Completions endpoint."
        }
        require(cleanModel.isNotBlank() && cleanModel.length <= MAX_MODEL_LENGTH) {
            "Enter a model identifier of at most $MAX_MODEL_LENGTH characters."
        }
        val currentProfiles = readProfiles()
        val existing = id?.let { profileId -> currentProfiles.firstOrNull { it.id == profileId } }
        require(id == null || existing != null) { "The selected API profile no longer exists." }
        val profileId = existing?.id ?: UUID.randomUUID().toString()
        require(existing != null || currentProfiles.size < MAX_PROFILES) {
            "ZERO can store up to $MAX_PROFILES API profiles."
        }
        val existingEncryptedKey = encryptedApiKey(profileId)
        val encryptedKey = if (apiKey.isNotBlank()) encrypt(apiKey.trim()) else existingEncryptedKey
        require(!encryptedKey.isNullOrBlank()) { "Save an API key before using this provider." }
        val ids = currentProfiles.map { it.id }.let { current ->
            if (existing == null) current + profileId else current
        }
        val editor = preferences.edit()
            .putString(KEY_PROFILE_IDS, ids.joinToString(","))
            .putString(KEY_SELECTED_PROFILE, profileId)
            .putString(profileKey(profileId, FIELD_NAME), cleanName)
            .putString(profileKey(profileId, FIELD_ENDPOINT), cleanEndpoint)
            .putString(profileKey(profileId, FIELD_MODEL), cleanModel)
            .putString(profileKey(profileId, FIELD_API_KEY), encryptedKey)
        if (existing == null) editor.putBoolean(KEY_ENABLED, false)
        check(editor.commit()) { "API profile could not be saved to device storage." }
        profileId
    }

    @Synchronized
    fun selectProfile(id: String): Result<Unit> = runCatching {
        migrateLegacyConfiguration()
        require(readProfiles().any { it.id == id }) { "The selected API profile no longer exists." }
        check(preferences.edit().putString(KEY_SELECTED_PROFILE, id).commit()) {
            "The API profile selection could not be saved."
        }
    }

    @Synchronized
    fun deleteProfile(id: String): Result<Unit> = runCatching {
        migrateLegacyConfiguration()
        val profiles = readProfiles()
        require(profiles.any { it.id == id }) { "The selected API profile no longer exists." }
        val ids = profiles.map { it.id }.filterNot { it == id }
        val editor = preferences.edit().putString(KEY_PROFILE_IDS, ids.joinToString(","))
        PROFILE_FIELDS.forEach { editor.remove(profileKey(id, it)) }
        if (selectedProfileId() == id) {
            editor.putString(KEY_SELECTED_PROFILE, ids.firstOrNull())
            editor.putBoolean(KEY_ENABLED, false)
        }
        check(editor.commit()) { "The API profile could not be removed from device storage." }
    }

    @Synchronized
    fun saveConfiguration(endpoint: String, model: String, enabled: Boolean): Result<Unit> = runCatching {
        migrateLegacyConfiguration()
        val profileId = selectedProfileId()
            ?: throw IllegalStateException("Add an API profile before enabling online AI.")
        val cleanEndpoint = endpoint.trim()
        val cleanModel = model.trim()
        require(cleanEndpoint.isEmpty() || AiProviderContract.validHttpsEndpoint(cleanEndpoint)) {
            "Use a complete HTTPS OpenAI-compatible Chat Completions endpoint."
        }
        require(cleanModel.length <= MAX_MODEL_LENGTH) { "Model name is too long." }
        require(!enabled || (cleanEndpoint.isNotBlank() && cleanModel.isNotBlank())) {
            "Enter an HTTPS endpoint and model before enabling online AI."
        }
        require(!enabled || !encryptedApiKey(profileId).isNullOrBlank()) {
            "Save an API key before enabling online AI."
        }
        check(
            preferences.edit()
                .putString(profileKey(profileId, FIELD_ENDPOINT), cleanEndpoint)
                .putString(profileKey(profileId, FIELD_MODEL), cleanModel)
                .putBoolean(KEY_ENABLED, enabled)
                .commit(),
        ) { "Online AI settings could not be saved." }
    }

    @Synchronized
    fun saveApiKey(value: String): Boolean {
        if (value.isBlank()) return false
        return runCatching {
            migrateLegacyConfiguration()
            val profileId = selectedProfileId() ?: return false
            preferences.edit()
                .putString(profileKey(profileId, FIELD_API_KEY), encrypt(value.trim()))
                .commit()
        }.getOrDefault(false)
    }

    @Synchronized
    fun clearApiKey() {
        migrateLegacyConfiguration()
        val profileId = selectedProfileId() ?: return
        preferences.edit()
            .remove(profileKey(profileId, FIELD_API_KEY))
            .putBoolean(KEY_ENABLED, false)
            .apply()
    }

    @Synchronized
    fun apiKey(): String? {
        migrateLegacyConfiguration()
        val profileId = selectedProfileId() ?: return null
        return encryptedApiKey(profileId)?.let(::decrypt)
    }

    private fun selectedProfileId(): String? {
        val ids = profileIds()
        val selected = preferences.getString(KEY_SELECTED_PROFILE, null)
        return selected?.takeIf(ids::contains) ?: ids.firstOrNull()
    }

    private fun readProfiles(): List<AiProviderProfile> = profileIds().map { id ->
        AiProviderProfile(
            id = id,
            name = preferences.getString(profileKey(id, FIELD_NAME), null) ?: "API ${id.take(6)}",
            endpoint = preferences.getString(profileKey(id, FIELD_ENDPOINT), "").orEmpty(),
            model = preferences.getString(profileKey(id, FIELD_MODEL), "").orEmpty(),
            hasApiKey = !encryptedApiKey(id).isNullOrBlank(),
        )
    }

    private fun profileIds(): List<String> =
        preferences.getString(KEY_PROFILE_IDS, "").orEmpty()
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()

    private fun encryptedApiKey(id: String): String? =
        preferences.getString(profileKey(id, FIELD_API_KEY), null)

    private fun migrateLegacyConfiguration() {
        if (preferences.contains(KEY_PROFILE_IDS)) return
        val endpoint = preferences.getString(LEGACY_ENDPOINT, "").orEmpty()
        val model = preferences.getString(LEGACY_MODEL, "").orEmpty()
        val encryptedKey = preferences.getString(LEGACY_API_KEY, null)
        val hasLegacyConfiguration = endpoint.isNotBlank() || model.isNotBlank() || encryptedKey != null
        val editor = preferences.edit()
            .putString(KEY_PROFILE_IDS, if (hasLegacyConfiguration) LEGACY_PROFILE_ID else "")
            .remove(LEGACY_ENDPOINT)
            .remove(LEGACY_MODEL)
            .remove(LEGACY_API_KEY)
        if (hasLegacyConfiguration) {
            editor
                .putString(KEY_SELECTED_PROFILE, LEGACY_PROFILE_ID)
                .putString(profileKey(LEGACY_PROFILE_ID, FIELD_NAME), "Default")
                .putString(profileKey(LEGACY_PROFILE_ID, FIELD_ENDPOINT), endpoint)
                .putString(profileKey(LEGACY_PROFILE_ID, FIELD_MODEL), model)
            if (encryptedKey != null) editor.putString(profileKey(LEGACY_PROFILE_ID, FIELD_API_KEY), encryptedKey)
        }
        check(editor.commit()) { "Existing online AI settings could not be migrated." }
    }

    private fun profileKey(id: String, field: String) = "$PROFILE_PREFIX$id.$field"

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

    companion object {
        const val MAX_PROFILES = 8
        private const val PREFERENCES = "zero_ai_provider"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_PROFILE_IDS = "profile_ids"
        private const val KEY_SELECTED_PROFILE = "selected_profile"
        private const val PROFILE_PREFIX = "profile."
        private const val FIELD_NAME = "name"
        private const val FIELD_ENDPOINT = "endpoint"
        private const val FIELD_MODEL = "model"
        private const val FIELD_API_KEY = "api_key"
        private const val LEGACY_ENDPOINT = "endpoint"
        private const val LEGACY_MODEL = "model"
        private const val LEGACY_API_KEY = "encrypted_key"
        private const val LEGACY_PROFILE_ID = "legacy"
        private const val KEY_ALIAS = "zero_ai_provider_key_v1"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val MIN_IV_SIZE = 12
        private const val MAX_IV_SIZE = 16
        private const val MAX_NAME_LENGTH = 40
        private const val MAX_MODEL_LENGTH = 120
        private val PROFILE_FIELDS = listOf(FIELD_NAME, FIELD_ENDPOINT, FIELD_MODEL, FIELD_API_KEY)
    }
}
