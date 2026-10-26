package com.zero.assistant

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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zero.assistant.data.BatteryStatus
import com.zero.assistant.data.SpeechModelManager
import com.zero.assistant.core.ModelTransferState

@Composable
internal fun PrivacyDashboardScreen(
    modifier: Modifier,
    modelManager: SpeechModelManager,
    activeModelId: String?,
    microphoneGranted: Boolean,
    cameraGranted: Boolean,
    callGranted: Boolean,
    notificationsGranted: Boolean,
    listening: Boolean,
    batteryStatus: BatteryStatus,
    historyCount: Int,
    reminderCount: Int,
    noteCount: Int,
    shortcutCount: Int,
    onClearHistory: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onNavigate: (Page) -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    val installedModels by modelManager.states.collectAsState()
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Privacy & system", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        DashboardRow("Speech model", activeModelId ?: "No active model")
        val installedNames = modelManager.catalog.models.filter { installedModels[it.id] is ModelTransferState.Installed }.joinToString { it.name }
        DashboardRow("Installed speech models", installedNames.ifBlank { "None installed" })
        DashboardRow("Installed model storage", modelManager.installedStorageLabel())
        DashboardRow("Microphone", if (listening) "Listening now" else if (microphoneGranted) "Permission granted · idle" else "Permission not granted")
        DashboardRow("Camera", if (cameraGranted) "Permission granted" else "Not granted · requested for flashlight")
        DashboardRow("Phone calls", if (callGranted) "Permission granted" else "Not granted · requested only after call confirmation")
        DashboardRow("Notifications", if (notificationsGranted) "Permission granted" else "Not granted")
        DashboardRow("Battery", "${batteryStatus.percent}% · ${if (batteryStatus.charging) "charging" else "on battery"}")
        DashboardRow("Local command history", "$historyCount entries")
        DashboardRow("Local reminders", "$reminderCount saved")
        DashboardRow("Local notes", "$noteCount saved")
        DashboardRow("Enabled shortcuts", "$shortcutCount")
        DashboardRow("Network use", "Only fixed HTTPS model downloads")
        DashboardRow("Processing", "On this device · no account, analytics, ads, or cloud AI")
        Text("Permissions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Microphone is used only after tapping Mic. Camera is requested for flashlight controls. Phone permission is requested only after confirming a number. Notifications are used for reminders and battery alerts.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onNavigate(Page.SpeechModels) }) { Text("Manage speech models") }
            TextButton(onClick = onOpenAppSettings) { Text("App permissions") }
            TextButton(onClick = { confirmClear = true }) { Text("Clear history") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onNavigate(Page.Planner) }) { Text("Reminders") }
            TextButton(onClick = { onNavigate(Page.Notes) }) { Text("Notes") }
            TextButton(onClick = { onNavigate(Page.MyShortcuts) }) { Text("Shortcuts") }
        }
        if (installedModels.isEmpty()) Text("Model inventory is loading.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear local history?") },
        text = { Text("This cannot be undone. It does not remove reminders, notes, or speech models.") },
        confirmButton = { TextButton(onClick = { onClearHistory(); confirmClear = false }) { Text("Clear history") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )
}

@Composable
private fun DashboardRow(label: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Medium)
        }
    }
}
