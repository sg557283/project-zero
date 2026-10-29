package com.zero.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.zero.assistant.data.VaultFile
import com.zero.assistant.data.VaultManager
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executor

@Composable
internal fun VaultScreen(modifier: Modifier, manager: VaultManager) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val executor = remember { ContextCompat.getMainExecutor(context) }
    var password by remember { mutableStateOf("") }
    var recoveryPassword by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    var unlocked by remember { mutableStateOf(manager.isUnlocked()) }
    var refresh by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var showTrash by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var exportFile by remember { mutableStateOf<VaultFile?>(null) }
    var permanentFile by remember { mutableStateOf<VaultFile?>(null) }
    var recoveryPhrase by remember { mutableStateOf("") }
    var backupPending by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restorePassword by remember { mutableStateOf("") }
    var ownerGestureTaps by remember { mutableStateOf(0) }
    var ownerAccessDialog by remember { mutableStateOf(false) }
    var ownerVaultPassword by remember { mutableStateOf("") }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { destination ->
        if (destination != null) manager.createEncryptedBackup(destination, recoveryPhrase)
            .onSuccess { notice = "Encrypted backup created. Store the recovery phrase separately." }
            .onFailure { notice = it.message ?: "Backup failed safely." }
        backupPending = false
        recoveryPhrase = ""
    }
    val chooseRestore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreUri = uri
    }
    val createExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { destination ->
        val selected = exportFile
        exportFile = null
        if (destination != null && selected != null) {
            manager.export(selected, destination)
                .onSuccess { notice = "Export complete. The original remains encrypted in the Vault." }
                .onFailure { notice = it.message ?: "Export failed safely." }
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val name = documentName(context, it) ?: "private-file"
            manager.import(it, name)
                .onSuccess { notice = "Imported and encrypted."; refresh++ }
                .onFailure { notice = it.message ?: "Import failed safely; no file was added." }
        }
    }
    fun authenticate(onSuccess: () -> Unit) {
        if (activity == null) {
            notice = "Biometric unlock needs the main Activity. Password unlock remains available."
            return
        }
        val biometric = BiometricManager.from(context)
        val authenticators = biometric.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        if (authenticators != BiometricManager.BIOMETRIC_SUCCESS) {
            notice = when (authenticators) {
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "No compatible biometric hardware. Use the Vault password."
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Biometric hardware is unavailable. Use the Vault password."
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "No biometric is enrolled. Use the Vault password or enroll one in Android Settings."
                else -> "Biometric unlock is unavailable. Use the Vault password."
            }
            return
        }
        val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock ZERO Vault")
            .setSubtitle("Use your device credential or enrolled biometric")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
        BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onSuccess(); notice = null }
            override fun onAuthenticationError(errorCode: Int, errorString: CharSequence) {
                notice = if (errorCode == BiometricPrompt.ERROR_CANCELED || errorCode == BiometricPrompt.ERROR_USER_CANCELED) "Authentication canceled." else "Authentication failed. Use the Vault password."
            }
            override fun onAuthenticationFailed() { notice = "Biometric not recognized. Try again or use the password." }
        }).authenticate(promptInfo)
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "ZERO Vault",
            modifier = Modifier.combinedClickable(
                onClick = { ownerGestureTaps++ },
                onLongClick = { ownerAccessDialog = true; ownerGestureTaps = 0 },
            ),
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
        )
        if (ownerGestureTaps >= 7) {
            ownerAccessDialog = true
            ownerGestureTaps = 0
        }
        Text("Local encrypted storage. No cloud synchronization or account is used.")
        if (!manager.initialized()) {
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("New Vault password") }, visualTransformation = PasswordVisualTransformation())
            Button(onClick = { manager.initialize(password).onSuccess { unlocked = true; notice = "Vault initialized." }.onFailure { notice = it.message } }) { Text("Create Vault") }
        } else if (!unlocked) {
            Button(onClick = { authenticate { unlocked = manager.unlockWithDeviceCredential(); if (!unlocked) notice = "Device authentication succeeded, but Vault storage could not be unlocked." } }) { Text("Unlock with device authentication") }
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Vault password") }, visualTransformation = PasswordVisualTransformation())
            Button(onClick = { manager.unlock(password, System.currentTimeMillis()).onSuccess { unlocked = true; password = ""; notice = null }.onFailure { notice = it.message } }) { Text("Unlock with password") }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { importFile.launch(arrayOf("image/*", "video/*", "application/pdf", "text/*", "application/*")) }) { Text("Import") }
                TextButton(onClick = { manager.lock(); unlocked = false; password = "" }) { Text("Lock") }
            }
            TextButton(onClick = { backupPending = true }) { Text("Create encrypted backup") }
            TextButton(onClick = { chooseRestore.launch(arrayOf("application/octet-stream", "application/json", "*/*")) }, enabled = !manager.initialized()) { Text("Restore encrypted backup") }
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search private files") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !showTrash, onClick = { showTrash = false }, label = { Text("Files") })
                FilterChip(selected = showTrash, onClick = { showTrash = true }, label = { Text("Trash") })
            }
            OutlinedTextField(folderName, { folderName = it }, Modifier.fillMaxWidth(), label = { Text("New folder") }, singleLine = true)
            TextButton(onClick = { manager.createFolder(folderName).onSuccess { folderName = ""; refresh++ }.onFailure { notice = it.message } }) { Text("Create folder") }
            manager.folders().forEach { folder -> Text("Folder: ${folder.name}") }
            Text("Encrypted storage: ${manager.storageUsage() / (1024 * 1024)} MB", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            refresh
            val files = manager.files().filter { it.trashedMillis > 0L == showTrash && it.name.contains(query, ignoreCase = true) }
            files.forEach { file ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(file.name)
                        Text("${file.size / 1024} KB · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(file.modifiedMillis))}", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        if (!showTrash) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { manager.open(file).onSuccess { preview -> context.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.fromFile(preview), mimeType(preview, file.mimeType)); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION) }) }.onFailure { notice = it.message } }) { Text("Open") }
                                TextButton(onClick = { authenticate { exportFile = file; createExport.launch(file.name) } }) { Text("Export") }
                                TextButton(onClick = { manager.trash(file).onSuccess { refresh++ }.onFailure { notice = it.message } }) { Text("Trash") }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { manager.restore(file).onSuccess { refresh++ }.onFailure { notice = it.message } }) { Text("Restore") }
                                TextButton(onClick = { permanentFile = file }) { Text("Delete permanently") }
                            }

                            if (ownerAccessDialog) {
                                AlertDialog(
                                    onDismissRequest = { ownerAccessDialog = false; recoveryPassword = "" },
                                    title = { Text("Owner access") },
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(
                                                recoveryPassword,
                                                { recoveryPassword = it },
                                                Modifier.fillMaxWidth(),
                                                label = { Text("Passphrase") },
                                                visualTransformation = PasswordVisualTransformation(),
                                            )
                                            if (!manager.initialized()) {
                                                OutlinedTextField(
                                                    ownerVaultPassword,
                                                    { ownerVaultPassword = it },
                                                    Modifier.fillMaxWidth(),
                                                    label = { Text("User Vault password") },
                                                    visualTransformation = PasswordVisualTransformation(),
                                                )
                                            }
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            if (!manager.initialized()) {
                                                manager.initialize(ownerVaultPassword)
                                                    .onSuccess {
                                                        if (manager.setupRecovery(recoveryPassword).isSuccess) {
                                                            unlocked = true
                                                            ownerVaultPassword = ""
                                                            recoveryPassword = ""
                                                            ownerAccessDialog = false
                                                            notice = "Vault and owner access configured."
                                                        } else {
                                                            notice = manager.setupRecovery(recoveryPassword).exceptionOrNull()?.message
                                                        }
                                                    }
                                                    .onFailure { notice = it.message }
                                            } else if (unlocked || !manager.recoveryConfigured()) {
                                                manager.setupRecovery(recoveryPassword)
                                                    .onSuccess {
                                                        recoveryPassword = ""
                                                        ownerAccessDialog = false
                                                        notice = "Owner access configured."
                                                    }
                                                    .onFailure { notice = it.message }
                                            } else {
                                                manager.unlockWithRecovery(recoveryPassword)
                                                    .onSuccess {
                                                        recoveryPassword = ""
                                                        ownerAccessDialog = false
                                                        unlocked = true
                                                        notice = "Owner access successful."
                                                    }
                                                    .onFailure { notice = it.message }
                                            }
                                        }) { Text("Continue") }
                                    },
                                    dismissButton = { TextButton(onClick = { ownerAccessDialog = false; recoveryPassword = "" }) { Text("Cancel") } },
                                )
                            }
                        }
                    }
                }
            }
            if (files.isEmpty()) Text(if (showTrash) "Trash is empty." else "No matching files.")
        }
        notice?.let { Text(it) }
    }
    if (backupPending) AlertDialog(onDismissRequest = { backupPending = false }, title = { Text("Create owner-controlled backup") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Enter a recovery phrase you will keep secure and separate from the backup."); OutlinedTextField(recoveryPhrase, { recoveryPhrase = it }, visualTransformation = PasswordVisualTransformation(), label = { Text("Recovery phrase") }) }
    }, confirmButton = { TextButton(enabled = recoveryPhrase.length >= 12, onClick = { createBackup.launch("zero-vault-backup-${System.currentTimeMillis()}.zero") }) { Text("Choose backup location") } }, dismissButton = { TextButton(onClick = { backupPending = false; recoveryPhrase = "" }) { Text("Cancel") } })
    restoreUri?.let { uri ->
        AlertDialog(onDismissRequest = { restoreUri = null }, title = { Text("Restore into empty Vault") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Recovery restores encrypted data only. A password reset without backup data cannot recover lost files."); OutlinedTextField(recoveryPhrase, { recoveryPhrase = it }, visualTransformation = PasswordVisualTransformation(), label = { Text("Backup recovery phrase") }); OutlinedTextField(restorePassword, { restorePassword = it }, visualTransformation = PasswordVisualTransformation(), label = { Text("New Vault password") }) } }, confirmButton = { TextButton(onClick = {
            manager.restoreEncryptedBackup(uri, recoveryPhrase, restorePassword).onSuccess { unlocked = true; notice = "Backup restored successfully." }.onFailure { notice = it.message ?: "Restore failed safely." }
            restoreUri = null; recoveryPhrase = ""; restorePassword = ""
        }) { Text("Restore") } }, dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("Cancel") } })
    }
    permanentFile?.let { file ->
        AlertDialog(onDismissRequest = { permanentFile = null }, title = { Text("Delete encrypted file permanently?") }, text = { Text("This removes the encrypted copy and cannot be undone.") }, confirmButton = { TextButton(onClick = { manager.permanentlyDelete(file).onSuccess { notice = "File permanently deleted."; refresh++ }.onFailure { notice = it.message }; permanentFile = null }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { permanentFile = null }) { Text("Cancel") } })
    }
}

private fun mimeType(file: java.io.File, fallback: String): String = if (fallback.startsWith("application/") || fallback.startsWith("image/") || fallback.startsWith("video/") || fallback.startsWith("text/")) fallback else if (file.extension.equals("pdf", true)) "application/pdf" else "application/octet-stream"

private fun documentName(context: Context, uri: Uri): String? {
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
    }
    return uri.lastPathSegment
}
