package com.zero.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zero.assistant.core.ModelTransferState
import com.zero.assistant.data.SpeechModelManager
import com.zero.assistant.core.SpeechModelSpec

@Composable
internal fun SpeechModelsScreen(modifier: Modifier, manager: SpeechModelManager) {
    val states by manager.states.collectAsState()
    val activeModelId by manager.activeModelId.collectAsState()
    var confirmDownload by remember { mutableStateOf<SpeechModelSpec?>(null) }
    var confirmUninstall by remember { mutableStateOf<SpeechModelSpec?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("Speech models", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "On-device recognition models are stored privately. Text-to-speech voices are managed separately by Android.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        feedback?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(manager.catalog.models, key = { it.id }) { spec ->
                val state = states[spec.id] ?: ModelTransferState.Idle
                ModelRow(
                    spec = spec,
                    state = state,
                    isActive = activeModelId == spec.id,
                    onDownload = { confirmDownload = spec },
                    onCancel = { manager.cancelDownload(spec.id) },
                    onRetry = {
                        feedback = null
                        if (spec.bundledAsset != null) manager.ensureBundledModel()
                        else if (!manager.download(spec.id)) feedback = "Another model download is already running."
                    },
                    onSelect = {
                        feedback = if (manager.activate(spec.id)) null else "This model isn't installed or couldn't be selected."
                    },
                    onUninstall = { confirmUninstall = spec },
                )
            }
        }
    }

    confirmDownload?.let { spec ->
        AlertDialog(
            onDismissRequest = { confirmDownload = null },
            title = { Text("Download ${spec.languageName} model?") },
            text = { Text("This downloads about ${formatMegabytes(spec.archiveSizeBytes)} MB over HTTPS, verifies the archive, then installs it in app-private storage. The current active model remains in use until installation succeeds.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDownload = null
                    feedback = if (manager.download(spec.id)) null else "Download could not start. Check the model state and available storage."
                }) { Text("Download & install") }
            },
            dismissButton = { TextButton(onClick = { confirmDownload = null }) { Text("Cancel") } },
        )
    }

    confirmUninstall?.let { spec ->
        AlertDialog(
            onDismissRequest = { confirmUninstall = null },
            title = { Text("Remove ${spec.name}?") },
            text = {
                Text(
                    if (activeModelId == spec.id) "This is the active model. ZERO will switch to another installed model before removing it. The bundled Hindi model cannot be removed."
                    else "This removes the model files from ZERO's private storage. The bundled Hindi model cannot be removed.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmUninstall = null
                        feedback = if (manager.uninstall(spec.id)) null else "The model could not be removed. Install another model before removing the active model."
                    },
                    enabled = spec.bundledAsset == null,
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmUninstall = null }) { Text("Keep model") } },
        )
    }
}

@Composable
private fun ModelRow(
    spec: SpeechModelSpec,
    state: ModelTransferState,
    isActive: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onSelect: () -> Unit,
    onUninstall: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(spec.name, fontWeight = FontWeight.SemiBold)
                Text(if (spec.bundledAsset != null) "Included" else "Downloadable", style = MaterialTheme.typography.labelMedium)
            }
            Text("${spec.languageName} · v${spec.version} · ${formatMegabytes(spec.archiveSizeBytes)} MB download")
            Text("${spec.license} · Source: alphacephei.com/vosk/models", style = MaterialTheme.typography.bodySmall)
            Text(modelStateLabel(state, isActive), style = MaterialTheme.typography.bodySmall)
            if (state is ModelTransferState.Downloading) {
                val progress = if (state.totalBytes > 0) state.receivedBytes.toFloat() / state.totalBytes else 0f
                LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("${formatMegabytes(state.receivedBytes)} / ${formatMegabytes(state.totalBytes)} MB")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state) {
                    is ModelTransferState.Downloading -> OutlinedButton(onClick = onCancel) { Text("Cancel") }
                    ModelTransferState.Installing -> Text("Validating and installing…", style = MaterialTheme.typography.labelMedium)
                    ModelTransferState.Installed -> {
                        if (!isActive) Button(onClick = onSelect) { Text("Select") }
                        if (spec.bundledAsset == null) OutlinedButton(onClick = onUninstall) { Text("Uninstall") }
                    }
                    is ModelTransferState.Failed, ModelTransferState.Cancelled -> Button(onClick = onRetry) { Text("Retry") }
                    ModelTransferState.Idle -> {
                        if (spec.bundledAsset == null) Button(onClick = onDownload) { Text("Download & install") }
                        else Text("Included model installs on first launch", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

private fun modelStateLabel(state: ModelTransferState, isActive: Boolean): String = when (state) {
    ModelTransferState.Idle -> "Not installed"
    is ModelTransferState.Downloading -> "Downloading"
    ModelTransferState.Installing -> "Installing and validating"
    ModelTransferState.Installed -> if (isActive) "Installed · Active" else "Installed"
    is ModelTransferState.Failed -> "Install failed: ${state.message}"
    ModelTransferState.Cancelled -> "Download cancelled"
}

private fun formatMegabytes(bytes: Long): String = "%.1f".format(bytes / 1_000_000.0)
