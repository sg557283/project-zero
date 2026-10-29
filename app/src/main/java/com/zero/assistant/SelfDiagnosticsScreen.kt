package com.zero.assistant

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.StatFs
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraCharacteristics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zero.assistant.data.WakeWordSettings
import com.zero.assistant.data.VaultManager
import java.io.File

@Composable
internal fun SelfDiagnosticsScreen(modifier: Modifier, vaultManager: VaultManager, activeModelId: String?) {
    val context = LocalContext.current
    var revision by remember { mutableStateOf(0) }
    val checks = remember(revision, activeModelId) { diagnostics(context, vaultManager, activeModelId) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Self-diagnostics", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        Text("Results are generated locally and are not uploaded.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        checks.forEach { check ->
            androidx.compose.material3.Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(check.title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text(check.message)
                Text("Fix: ${check.fix}", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            } }
        }
        Button(onClick = { revision++ }) { Text("Refresh diagnostics") }
        Button(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))) }) { Text("Open app permission settings") }
    }
}

private data class Diagnostic(val title: String, val message: String, val fix: String)

private fun diagnostics(context: Context, vaultManager: VaultManager, activeModelId: String?): List<Diagnostic> {
    val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val wakeEnabled = WakeWordSettings.enabled(context)
    val wakeActive = WakeWordSettings.active(context)
    val notifications = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val stat = StatFs(context.filesDir.absolutePath)
    val availableMb = (stat.availableBytes / (1024 * 1024))
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val lowMemory = activityManager.isLowRamDevice
    val service = when { wakeActive -> "Wake service reports active."; wakeEnabled -> "Wake is enabled but service is not active."; else -> "Wake service is stopped or was not enabled." }
    val vaultHealthy = vaultManager.storageHealthy()
    val vaultState = when { vaultManager.isUnlocked() -> "Vault initialized and unlocked in memory."; vaultManager.initialized() -> "Vault initialized; currently locked."; else -> "Vault has not been initialized." }
    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val torchAvailable = runCatching { cameraManager.cameraIdList.any { cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } }.getOrDefault(false)
    return listOf(
        Diagnostic("Microphone and wake word", if (mic) service else "Microphone permission is missing. $service", if (mic) "Keep ZERO open or enable the wake service; Android power controls can still stop it." else "Grant Microphone permission in app settings, then enable wake listening."),
        Diagnostic("Speech recognition", if (activeModelId != null) "Installed recognition model: $activeModelId." else "No recognition model is reported as installed.", "Open Voice Library and install or activate an available offline model."),
        Diagnostic("Text-to-speech", "Android local voice availability is checked by Voice Library.", "Open Voice Library to inspect installed local voices and install missing voice data in Android settings."),
        Diagnostic("Vault storage", "$vaultState Available private storage: $availableMb MB.", if (availableMb < 25) "Free at least 25 MB before importing files." else "No action required."),
        Diagnostic("Notifications", if (notifications) "Notification permission is granted or not required on this Android version." else "Notification permission is missing.", "Allow Notifications in app settings so reminders and foreground controls can appear."),
        Diagnostic("Battery restrictions", if (lowMemory) "This device reports low-memory mode." else "No low-RAM device flag is reported.", "If wake listening stops, review Android battery optimization and keep the foreground notification visible."),
        Diagnostic("Camera and flashlight", when { ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED -> "Camera permission is not granted; hardware was still checked."; !torchAvailable -> "No torch-capable camera is reported by Android."; else -> "Camera permission is granted and Android reports a torch-capable camera." }, if (torchAvailable) "Grant Camera permission, then use Emergency Quick Mode." else "No flashlight fix is possible on hardware without a torch; the dialer and message actions remain available."),
        Diagnostic("Vault/private storage errors", when { !vaultManager.initialized() -> "Vault is not initialized; private directory can still be checked."; !vaultManager.isUnlocked() -> "Vault is initialized and locked; encrypted index integrity can be checked after unlock."; vaultHealthy -> "Vault encrypted index authentication passed."; else -> "Vault directory or encrypted index failed validation." }, if (vaultManager.initialized() && vaultManager.isUnlocked() && !vaultHealthy) "Lock and unlock again. If authentication fails, restore the verified backup; do not overwrite existing encrypted data." else "No Vault storage action is indicated by the current state.")
    )
}
