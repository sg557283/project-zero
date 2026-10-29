package com.zero.assistant.data

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zero.assistant.core.VaultAttemptState
import com.zero.assistant.core.VaultSecurity
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class VaultFile(
    val id: String,
    val name: String,
    val size: Long,
    val modifiedMillis: Long,
    val mimeType: String = "application/octet-stream",
    val folderId: String = "",
    val trashedMillis: Long = 0L,
)

data class VaultFolder(val id: String, val name: String, val modifiedMillis: Long)

class VaultManager(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val vaultDirectory = File(appContext.filesDir, "private_vault").apply { mkdirs() }
    private val gson = Gson()
    private var masterKey: ByteArray? = null
    private var unlockedAtMillis = 0L

    fun initialized(): Boolean = preferences.contains(KEY_MASTER)
    fun isUnlocked(): Boolean = masterKey != null
    fun lock() {
        masterKey?.fill(0)
        masterKey = null
        unlockedAtMillis = 0L
    }

    fun initialize(password: String): Result<Unit> = runCatching {
        VaultSecurity.validatePassword(password)?.let { throw IllegalArgumentException(it) }
        check(!initialized()) { "The Vault is already initialized." }
        val salt = VaultSecurity.newSalt()
        val verifier = passwordVerifier(password, salt)
        val master = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val wrapped = encryptWithKeystore(master)
        preferences.edit()
            .putString(KEY_MASTER, wrapped)
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_VERIFIER, Base64.encodeToString(verifier, Base64.NO_WRAP))
            .apply()
        masterKey = master
        saveFolders(emptyList())
        saveManifest(emptyList())
    }

    fun unlock(password: String, nowMillis: Long): Result<Unit> = runCatching {
        val attempts = attemptState()
        check(VaultSecurity.remainingLockMillis(attempts, nowMillis) == 0L) {
            "Vault is temporarily locked. Try again later."
        }
        val salt = decode(preferences.getString(KEY_SALT, null) ?: throw IllegalStateException("Vault is not initialized."))
        val expected = decode(preferences.getString(KEY_VERIFIER, null) ?: throw IllegalStateException("Vault is not initialized."))
        val actual = passwordVerifier(password, salt)
        if (!MessageDigest.isEqual(expected, actual)) {
            val failure = VaultSecurity.recordFailure(attempts, nowMillis)
            preferences.edit().putString(KEY_ATTEMPTS, attemptJson(failure)).apply()
            throw IllegalArgumentException("Incorrect Vault password.")
        }
        val wrapped = preferences.getString(KEY_MASTER, null) ?: throw IllegalStateException("Vault key is unavailable.")
        masterKey = decryptWithKeystore(wrapped)
        unlockedAtMillis = nowMillis
        preferences.edit().remove(KEY_ATTEMPTS).apply()
    }

    fun unlockWithDeviceCredential(): Boolean = runCatching {
        check(initialized()) { "Vault is not initialized." }
        val wrapped = preferences.getString(KEY_MASTER, null) ?: throw IllegalStateException("Vault key is unavailable.")
        masterKey = decryptWithKeystore(wrapped)
        unlockedAtMillis = System.currentTimeMillis()
        preferences.edit().remove(KEY_ATTEMPTS).apply()
        true
    }.getOrElse { masterKey = null; false }

    fun setupRecovery(recoveryPassword: String): Result<Unit> = runCatching {
        VaultSecurity.validatePassword(recoveryPassword)?.let { throw IllegalArgumentException(it) }
        val master = requireUnlocked()
        val salt = VaultSecurity.newSalt()
        val recoveryKey = passwordKey(recoveryPassword, salt)
        val wrapped = Base64.encodeToString(encryptBytes(master.copyOf(), recoveryKey, RECOVERY_AAD), Base64.NO_WRAP)
        preferences.edit()
            .putString(KEY_RECOVERY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_RECOVERY_VERIFIER, Base64.encodeToString(passwordVerifier(recoveryPassword, salt), Base64.NO_WRAP))
            .putString(KEY_RECOVERY_MASTER, wrapped)
            .apply()
        recoveryKey.fill(0)
    }

    fun unlockWithRecovery(recoveryPassword: String): Result<Unit> = runCatching {
        VaultSecurity.validatePassword(recoveryPassword)?.let { throw IllegalArgumentException(it) }
        check(initialized()) { "Vault is not initialized." }
        val salt = decode(preferences.getString(KEY_RECOVERY_SALT, null) ?: throw IllegalStateException("No owner recovery credential is configured."))
        val expected = decode(preferences.getString(KEY_RECOVERY_VERIFIER, null) ?: throw IllegalStateException("No owner recovery credential is configured."))
        val actual = passwordVerifier(recoveryPassword, salt)
        check(MessageDigest.isEqual(expected, actual)) { "Incorrect recovery passphrase." }
        val key = passwordKey(recoveryPassword, salt)
        val wrapped = Base64.decode(preferences.getString(KEY_RECOVERY_MASTER, null) ?: throw IllegalStateException("Recovery data is unavailable."), Base64.NO_WRAP)
        masterKey = decryptBytes(wrapped, key, RECOVERY_AAD)
        key.fill(0)
        unlockedAtMillis = System.currentTimeMillis()
    }

    fun recoveryConfigured(): Boolean =
        preferences.contains(KEY_RECOVERY_MASTER) && preferences.contains(KEY_RECOVERY_VERIFIER)

    fun changePassword(current: String, replacement: String, nowMillis: Long): Result<Unit> = runCatching {
        unlock(current, nowMillis).getOrThrow()
        VaultSecurity.validatePassword(replacement)?.let { throw IllegalArgumentException(it) }
        val salt = VaultSecurity.newSalt()
        val verifier = passwordVerifier(replacement, salt)
        preferences.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_VERIFIER, Base64.encodeToString(verifier, Base64.NO_WRAP))
            .remove(KEY_ATTEMPTS)
            .apply()
    }

    fun files(): List<VaultFile> = runCatching {
        val key = requireUnlocked()
        val manifest = encryptedManifestFile()
        if (!manifest.exists()) return emptyList()
        val json = decryptBytes(manifest.readBytes(), key, MANIFEST_AAD).toString(StandardCharsets.UTF_8)
        gson.fromJson<List<VaultFile>>(json, object : TypeToken<List<VaultFile>>() {}.type) ?: emptyList()
    }.getOrElse { emptyList() }

    fun storageHealthy(): Boolean = runCatching {
        requireUnlocked()
        val manifest = encryptedManifestFile()
        if (manifest.exists()) decryptBytes(manifest.readBytes(), requireUnlocked(), MANIFEST_AAD)
        true
    }.getOrDefault(false)

    fun folders(): List<VaultFolder> = runCatching {
        val key = requireUnlocked()
        val file = encryptedFoldersFile()
        if (!file.exists()) return emptyList()
        val json = decryptBytes(file.readBytes(), key, FOLDER_AAD).toString(StandardCharsets.UTF_8)
        gson.fromJson<List<VaultFolder>>(json, object : TypeToken<List<VaultFolder>>() {}.type) ?: emptyList()
    }.getOrElse { emptyList() }

    fun createFolder(name: String): Result<VaultFolder> = runCatching {
        requireUnlocked()
        val cleanName = name.trim().take(80)
        check(cleanName.isNotEmpty()) { "Folder name cannot be empty." }
        check(folders().none { it.name.equals(cleanName, ignoreCase = true) }) { "A folder with this name already exists." }
        val folder = VaultFolder(UUID.randomUUID().toString(), cleanName, System.currentTimeMillis())
        saveFolders(folders() + folder)
        folder
    }

    fun renameFolder(folder: VaultFolder, name: String): Result<VaultFolder> = runCatching {
        val cleanName = name.trim().take(80)
        check(cleanName.isNotEmpty()) { "Folder name cannot be empty." }
        val updated = folder.copy(name = cleanName, modifiedMillis = System.currentTimeMillis())
        saveFolders(folders().map { if (it.id == folder.id) updated else it })
        updated
    }

    fun move(file: VaultFile, folderId: String?): Result<Unit> = runCatching {
        saveManifest(files().map { if (it.id == file.id) it.copy(folderId = folderId.orEmpty(), modifiedMillis = System.currentTimeMillis()) else it })
    }

    fun import(uri: Uri, displayName: String, folderId: String = "", mimeType: String? = null): Result<VaultFile> = runCatching {
        val key = requireUnlocked()
        val id = UUID.randomUUID().toString()
        val encrypted = File(vaultDirectory, id)
        val temp = File(vaultDirectory, "$id.tmp")
        check(!encrypted.exists() && !temp.exists()) { "Vault file identifier collision." }
        try {
            val source = appContext.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("The selected file is no longer available.")
            var plaintextBytes = 0L
            source.use { input -> temp.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    val encryptedChunk = encryptBytes(buffer.copyOf(count), key, id.toByteArray())
                    output.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(encryptedChunk.size).array())
                    output.write(encryptedChunk)
                    plaintextBytes += count
                }
            }}
            check(temp.renameTo(encrypted)) { "The encrypted import could not be finalized." }
            val file = VaultFile(id, displayName.take(200), plaintextBytes, System.currentTimeMillis(), mimeType ?: appContext.contentResolver.getType(uri) ?: "application/octet-stream", folderId)
            saveManifest(files() + file)
            file
        } catch (failure: Throwable) {
            temp.delete()
            encrypted.delete()
            throw failure
        }
    }

    fun open(file: VaultFile): Result<File> = runCatching {
        val key = requireUnlocked()
        check(!file.trashedMillis.isPresent()) { "Restore the file before opening it." }
        val encrypted = File(vaultDirectory, file.id)
        check(encrypted.isFile && encrypted.length() > 0L) { "Vault file is unavailable or damaged." }
        val decrypted = File(appContext.cacheDir, "vault_preview_${file.id}")
        decrypted.delete()
        decrypted.outputStream().use { output -> output.write(decryptVaultPayload(encrypted.readBytes(), key, file.id)) }
        check(decrypted.isFile && decrypted.length() == file.size) { "Vault file authentication failed." }
        decrypted.deleteOnExit()
        decrypted
    }

    fun export(file: VaultFile, destination: Uri): Result<Unit> = runCatching {
        val key = requireUnlocked()
        check(!file.trashedMillis.isPresent()) { "Restore the file before exporting it." }
        val encrypted = File(vaultDirectory, file.id)
        check(encrypted.isFile) { "Vault file is unavailable." }
        val output = appContext.contentResolver.openOutputStream(destination, "w")
            ?: throw IllegalStateException("The export destination is unavailable.")
        output.use { it.write(decryptVaultPayload(encrypted.readBytes(), key, file.id)) }
    }

    fun trash(file: VaultFile): Result<Unit> = runCatching {
        saveManifest(files().map { if (it.id == file.id) it.copy(trashedMillis = System.currentTimeMillis()) else it })
    }

    fun restore(file: VaultFile): Result<Unit> = runCatching {
        saveManifest(files().map { if (it.id == file.id) it.copy(trashedMillis = 0L) else it })
    }

    fun permanentlyDelete(file: VaultFile): Result<Unit> = runCatching {
        check(file.trashedMillis.isPresent()) { "Move the file to Trash before permanent deletion." }
        saveManifest(files().filterNot { it.id == file.id })
        if (!File(vaultDirectory, file.id).delete()) throw IllegalStateException("The encrypted file could not be deleted.")
    }

    fun storageUsage(): Long = vaultDirectory.listFiles()?.filter { it.name != "manifest.enc" && it.name != "folders.enc" && !it.name.endsWith(".tmp") }?.sumOf { it.length() } ?: 0L

    fun createEncryptedBackup(destination: Uri, recoveryPhrase: String): Result<Unit> = runCatching {
        val master = requireUnlocked()
        check(recoveryPhrase.length >= 12) { "Use a recovery phrase of at least 12 characters." }
        val recoverySalt = VaultSecurity.newSalt()
        val (recoveryKey, _) = deriveRecoveryKey(recoveryPhrase, recoverySalt)
        val encryptedMaster = aeadWrap(master, recoveryKey)
        val payload = linkedMapOf<String, Any>(
            "version" to 1,
            "salt" to Base64.encodeToString(recoverySalt, Base64.NO_WRAP),
            "master" to encryptedMaster,
            "manifest" to Base64.encodeToString(encryptedManifestFile().readBytes(), Base64.NO_WRAP),
            "folders" to Base64.encodeToString(encryptedFoldersFile().takeIf { it.exists() }?.readBytes() ?: byteArrayOf(), Base64.NO_WRAP),
            "files" to files().associate { it.id to Base64.encodeToString(File(vaultDirectory, it.id).readBytes(), Base64.NO_WRAP) },
        )
        val output = appContext.contentResolver.openOutputStream(destination, "w") ?: throw IllegalStateException("The backup destination is unavailable.")
        output.use { it.write(gson.toJson(payload).toByteArray(StandardCharsets.UTF_8)) }
    }

    fun restoreEncryptedBackup(source: Uri, recoveryPhrase: String, newPassword: String): Result<Unit> = runCatching {
        check(!initialized()) { "Restore is blocked: this device already has a Vault. Export existing data first; restore cannot overwrite it." }
        VaultSecurity.validatePassword(newPassword)?.let { throw IllegalArgumentException(it) }
        val input = appContext.contentResolver.openInputStream(source) ?: throw IllegalStateException("The backup is unavailable.")
        val payloadJson = input.use { it.bufferedReader(StandardCharsets.UTF_8).readText() }
        val payload = gson.fromJson(payloadJson, Map::class.java)
        check(payload["version"] == 1.0 || payload["version"] == 1) { "Unsupported backup format." }
        val salt = Base64.decode(payload["salt"] as String, Base64.NO_WRAP)
        val (recoveryKey, _) = deriveRecoveryKey(recoveryPhrase, salt)
        val master = aeadUnwrap(payload["master"] as String, recoveryKey)
        val manifest = Base64.decode(payload["manifest"] as String, Base64.NO_WRAP)
        val folders = Base64.decode(payload["folders"] as String, Base64.NO_WRAP)
        decryptBytes(manifest, master, MANIFEST_AAD)
        val saltBytes = VaultSecurity.newSalt()
        val verifier = passwordVerifier(newPassword, saltBytes)
        val wrapped = encryptWithKeystore(master)
        val filesPayload = payload["files"] as Map<*, *>
        for ((id, encoded) in filesPayload) {
            check(id is String && id.matches(Regex("[a-fA-F0-9\\-]{36}"))) { "Backup contains an invalid file identifier." }
            File(vaultDirectory, id).writeBytes(Base64.decode(encoded as String, Base64.NO_WRAP))
        }
        encryptedManifestFile().writeBytes(manifest)
        if (folders.isNotEmpty()) encryptedFoldersFile().writeBytes(folders)
        preferences.edit()
            .putString(KEY_MASTER, wrapped)
            .putString(KEY_SALT, Base64.encodeToString(saltBytes, Base64.NO_WRAP))
            .putString(KEY_VERIFIER, Base64.encodeToString(verifier, Base64.NO_WRAP))
            .apply()
        masterKey = master
    }

    private fun deriveRecoveryKey(phrase: String, salt: ByteArray): Pair<ByteArray, ByteArray> {
        val spec = PBEKeySpec(phrase.toCharArray(), salt, 300_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded to salt } finally { spec.clearPassword() }
    }

    private fun aeadWrap(value: ByteArray, key: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return Base64.encodeToString(cipherPayload(cipher, cipher.doFinal(value)), Base64.NO_WRAP)
    }

    private fun aeadUnwrap(value: String, key: ByteArray): ByteArray {
        val (iv, ciphertext) = decodeBinaryCipher(Base64.decode(value, Base64.NO_WRAP))
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun decryptVaultPayload(data: ByteArray, key: ByteArray, aad: String): ByteArray {
        val payload = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val output = ByteArrayOutputStream()
        while (payload.hasRemaining()) {
            check(payload.remaining() >= 4) { "Vault file framing is damaged." }
            val encryptedSize = payload.int
            check(encryptedSize in 1..(64 * 1024 + 28)) { "Vault chunk size is invalid." }
            check(payload.remaining() >= encryptedSize) { "Vault file is truncated." }
            val encrypted = ByteArray(encryptedSize).also(payload::get)
            output.write(decryptBytes(encrypted, key, aad.toByteArray()))
        }
        return output.toByteArray()
    }

    private fun saveManifest(files: List<VaultFile>) {
        val key = requireUnlocked()
        val json = gson.toJson(files)
        val temp = File(vaultDirectory, "manifest.tmp")
        temp.writeBytes(encryptBytes(json.toByteArray(StandardCharsets.UTF_8), key, MANIFEST_AAD))
        check(temp.renameTo(encryptedManifestFile())) { "Encrypted Vault index could not be replaced." }
    }

    private fun saveFolders(folders: List<VaultFolder>) {
        val key = requireUnlocked()
        val temp = File(vaultDirectory, "folders.tmp")
        temp.writeBytes(encryptBytes(gson.toJson(folders).toByteArray(StandardCharsets.UTF_8), key, FOLDER_AAD))
        check(temp.renameTo(encryptedFoldersFile())) { "Encrypted folder index could not be replaced." }
    }

    private fun encryptedManifestFile() = File(vaultDirectory, "manifest.enc")
    private fun encryptedFoldersFile() = File(vaultDirectory, "folders.enc")

    private fun passwordVerifier(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 210_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }

    private fun requireUnlocked(): ByteArray {
        val key = masterKey ?: throw IllegalStateException("Unlock the Vault first.")
        if (unlockedAtMillis != 0L && System.currentTimeMillis() - unlockedAtMillis > SESSION_TIMEOUT_MILLIS) {
            key.fill(0)
            masterKey = null
            unlockedAtMillis = 0L
            throw IllegalStateException("Vault session timed out. Unlock it again.")
        }
        unlockedAtMillis = System.currentTimeMillis()
        return key
    }

    private fun passwordKey(password: String, salt: ByteArray): ByteArray {
        val derived = passwordVerifier(password, salt)
        return derived.copyOf(32)
    }

    private fun encryptWithKeystore(value: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return Base64.encodeToString(cipherPayload(cipher, cipher.doFinal(value)), Base64.NO_WRAP)
    }

    private fun decryptWithKeystore(value: String): ByteArray {
        val (iv, ciphertext) = decodeBinaryCipher(Base64.decode(value, Base64.NO_WRAP))
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun encryptBytes(value: ByteArray, key: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        cipher.updateAAD(aad)
        return cipherPayload(cipher, cipher.doFinal(value))
    }

    private fun decryptBytes(value: ByteArray, key: ByteArray, aad: ByteArray): ByteArray {
        val (iv, ciphertext) = decodeBinaryCipher(value)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    private fun cipherPayload(cipher: Cipher, ciphertext: ByteArray): ByteArray {
        val payload = ByteBuffer.allocate(1 + cipher.iv.size + ciphertext.size).put(cipher.iv.size.toByte()).put(cipher.iv).put(ciphertext)
        return payload.array()
    }

    private fun decodeBinaryCipher(value: ByteArray): Pair<ByteArray, ByteArray> {
        val payload = ByteBuffer.wrap(value)
        check(payload.hasRemaining() && payload.remaining() <= value.size)
        val ivSize = payload.get().toInt() and 255
        check(ivSize in 12..16 && payload.remaining() >= ivSize + 16) { "Encrypted payload framing is invalid." }
        val iv = ByteArray(ivSize).also(payload::get)
        val ciphertext = ByteArray(payload.remaining()).also(payload::get)
        return iv to ciphertext
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }

    private fun attemptState(): VaultAttemptState = runCatching {
        val parts = preferences.getString(KEY_ATTEMPTS, "0|0")!!.split('|')
        VaultAttemptState(parts[0].toInt(), parts[1].toLong())
    }.getOrDefault(VaultAttemptState())
    private fun attemptJson(state: VaultAttemptState) = "${state.failures}|${state.lockedUntilMillis}"
    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    private companion object {
        const val PREFERENCES = "zero_vault"
        const val KEY_MASTER = "keystore_master"
        const val KEY_SALT = "password_salt"
        const val KEY_VERIFIER = "password_verifier"
        const val KEY_ATTEMPTS = "attempts"
        const val KEY_RECOVERY_SALT = "recovery_salt"
        const val KEY_RECOVERY_VERIFIER = "recovery_verifier"
        const val KEY_RECOVERY_MASTER = "recovery_master"
        const val KEY_ALIAS = "zero_vault_master_v1"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        val MANIFEST_AAD = "ZERO_VAULT_MANIFEST_V1".toByteArray(StandardCharsets.UTF_8)
        val FOLDER_AAD = "ZERO_VAULT_FOLDERS_V1".toByteArray(StandardCharsets.UTF_8)
        val RECOVERY_AAD = "ZERO_VAULT_RECOVERY_V1".toByteArray(StandardCharsets.UTF_8)
        const val SESSION_TIMEOUT_MILLIS = 15L * 60L * 1000L
    }
}

private fun Long.isPresent(): Boolean = this > 0L
