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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.zero.assistant.core.OfflineTtsVoice
import com.zero.assistant.core.VoiceLibraryCategory
import com.zero.assistant.data.SpeechModelManager
import com.zero.assistant.core.SpeechModelSpec
import com.zero.assistant.core.VoiceModelCatalog
import com.zero.assistant.core.VoiceModelTransferState
import com.zero.assistant.data.ModelDownloadManager

@Composable
internal fun SpeechModelsScreen(
    modifier: Modifier,
    manager: SpeechModelManager,
    voiceModelManager: ModelDownloadManager,
    refreshTtsVoices: () -> Unit,
    prepareForVoiceModelRemoval: (() -> Unit) -> Unit,
    onModelRemoved: () -> Unit,
    ttsVoices: List<OfflineTtsVoice>,
    selectedTtsVoiceId: String?,
    onSelectTtsVoice: (String) -> Boolean,
    onPreviewTtsVoice: (String) -> Unit,
    onInstallTtsVoices: () -> String,
) {
    val states by manager.states.collectAsState()
    val activeModelId by manager.activeModelId.collectAsState()
    val voiceModelState by voiceModelManager.state.collectAsState()
    val activeVoiceModelId by voiceModelManager.activeModelId.collectAsState()
    var selectedCategory by remember { mutableStateOf(VoiceLibraryCategory.Simple) }
    var confirmDownload by remember { mutableStateOf<SpeechModelSpec?>(null) }
    var confirmVoiceDownload by remember { mutableStateOf(false) }
    var confirmVoiceUninstall by remember { mutableStateOf(false) }
    var confirmUninstall by remember { mutableStateOf<SpeechModelSpec?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("Voice Library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "Vosk speech recognition, Android offline voices, and downloadable Kokoro TTS.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            maxLines = 1,
            softWrap = false,
        )
        TabRow(selectedTabIndex = VoiceLibraryCategory.entries.indexOf(selectedCategory)) {
            VoiceLibraryCategory.entries.forEach { category ->
                Tab(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    text = { Text(category.label) },
                )
            }
        }
        feedback?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (selectedCategory) {
                VoiceLibraryCategory.Simple -> {
                    item { Text("Simple voices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Android offline voice packages", fontWeight = FontWeight.SemiBold)
                                Text("${ttsVoices.size} installed offline ${if (ttsVoices.size == 1) "voice" else "voices"} available")
                                Button(onClick = { feedback = onInstallTtsVoices() }) { Text("Browse / install voice data") }
                            }
                        }
                    }
                    if (ttsVoices.isEmpty()) {
                        item { Text("No installed offline voices are currently reported by the selected Android speech engine.") }
                    }
                    items(ttsVoices, key = { "tts:${it.id}" }) { voice ->
                        TtsVoiceRow(
                            voice = voice,
                            selected = selectedTtsVoiceId == voice.id,
                            onPreview = { onPreviewTtsVoice(voice.id) },
                            onSelect = {
                                feedback = if (onSelectTtsVoice(voice.id)) "Selected ${voice.displayName}." else "Android could not select that voice."
                            },
                        )
                    }
                }
                VoiceLibraryCategory.EmotionBased -> {
                    item { Text("Emotion-Based voices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                    item {
                        KokoroModelCard(
                            state = voiceModelState,
                            active = activeVoiceModelId == VoiceModelCatalog.KOKORO_ID,
                            manager = voiceModelManager,
                            onDownload = { confirmVoiceDownload = true },
                            onActivate = {
                                feedback = if (voiceModelManager.activate()) {
                                    refreshTtsVoices()
                                    "Kokoro is active."
                                }
                                else "Kokoro could not be activated."
                            },
                            onResume = {
                                if (!voiceModelManager.resume()) feedback = "Kokoro download could not resume."
                            },
                            onSelectVoice = { voiceId ->
                                feedback = if (onSelectTtsVoice(voiceId)) "Selected $voiceId." else "This Kokoro voice is not ready."
                            },
                            onPreviewVoice = { onPreviewTtsVoice("kokoro:af_nova") },
                            fallbackVoiceInstalled = ttsVoices.any { it.languageTag.startsWith("en", ignoreCase = true) },
                            onPreviewFallback = {
                                ttsVoices.firstOrNull { it.languageTag.startsWith("en", ignoreCase = true) }
                                    ?.let { onPreviewTtsVoice(it.id) }
                            },
                            onUninstall = {
                                confirmVoiceUninstall = true
                            },
                        )
                    }
                }
            }
            item { Text("Speech recognition models", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            item { Text("These STT models recognize speech; they are not text-to-speech voices.", style = MaterialTheme.typography.bodySmall) }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Wake detection · Included", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                        Text("Sherpa KWS · English built-ins · Apache-2.0", maxLines = 1, softWrap = false)
                        Text("Silero VAD v4 · 16 kHz · MIT", maxLines = 1, softWrap = false)
                        Text("KWS assets 5.48 MB · VAD 1.81 MB · custom/Hindi use Vosk", style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
                    }
                }
            }
            items(manager.catalog.models, key = { "model:${it.id}" }) { spec ->
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

    if (confirmVoiceDownload) {
        AlertDialog(
            onDismissRequest = { confirmVoiceDownload = false },
            title = { Text("Download Kokoro INT8?") },
            text = {
                Text(
                    "Downloads a 132.4 MB verified Sherpa-ONNX archive (Apache-2.0) for English and Chinese speech. " +
                        "It does not include Hindi/Hinglish or Aria/Kavya personas. Allow about 900 MiB of free space for safe installation.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmVoiceDownload = false
                    feedback = if (voiceModelManager.download()) null else "Kokoro download could not start."
                }) { Text("Download") }
            },
            dismissButton = { TextButton(onClick = { confirmVoiceDownload = false }) { Text("Cancel") } },
        )
    }

    if (confirmVoiceUninstall) {
        AlertDialog(
            onDismissRequest = { confirmVoiceUninstall = false },
            title = { Text("Remove Kokoro?") },
            text = { Text("ZERO will stop Kokoro playback, switch to Android's offline voice engine, then remove the downloaded model files.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmVoiceUninstall = false
                    prepareForVoiceModelRemoval {
                        if (!voiceModelManager.uninstall { removed ->
                                if (removed) {
                                    onModelRemoved()
                                    feedback = "Kokoro removed. Android offline TTS is now the fallback."
                                } else {
                                    feedback = "Kokoro could not be removed."
                                }
                            }
                        ) {
                            feedback = "Kokoro removal could not start."
                        }
                    }
                }) { Text("Switch and remove") }
            },
            dismissButton = { TextButton(onClick = { confirmVoiceUninstall = false }) { Text("Keep model") } },
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
private fun KokoroModelCard(
    state: VoiceModelTransferState,
    active: Boolean,
    manager: ModelDownloadManager,
    onDownload: () -> Unit,
    onActivate: () -> Unit,
    onResume: () -> Unit,
    onSelectVoice: (String) -> Unit,
    onPreviewVoice: () -> Unit,
    fallbackVoiceInstalled: Boolean,
    onPreviewFallback: () -> Unit,
    onUninstall: () -> Unit,
) {
    val model = VoiceModelCatalog.models.first()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(model.name, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            Text(model.summary, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            Text(
                "${model.language} · ${formatMegabytes(model.archiveSizeBytes)} MB · ${model.license}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                softWrap = false,
            )
            Text("Sherpa-ONNX · 53 speakers · Source: k2-fsa/sherpa-onnx", style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
            Text("Before download, preview uses an installed Android system voice.", style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
            OutlinedButton(onClick = onPreviewFallback, enabled = fallbackVoiceInstalled) {
                Text(if (fallbackVoiceInstalled) "Preview system fallback" else "No system preview voice", maxLines = 1, softWrap = false)
            }
            when (state) {
                VoiceModelTransferState.Idle -> Text("Not installed")
                is VoiceModelTransferState.Downloading -> {
                    val progress = if (state.totalBytes > 0) state.receivedBytes.toFloat() / state.totalBytes else 0f
                    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text(
                        "${(progress * 100).toInt()}% · ${formatMegabytes(state.receivedBytes)} / ${formatMegabytes(state.totalBytes)} MB",
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                VoiceModelTransferState.Paused -> Text("Download paused")
                VoiceModelTransferState.Installing -> Text("Verifying and installing")
                VoiceModelTransferState.Removing -> Text("Switching engine and removing files")
                VoiceModelTransferState.Installed -> Text(if (active) "Installed · Active" else "Installed")
                is VoiceModelTransferState.Failed -> Text("Install failed: ${state.message}", maxLines = 2)
                is VoiceModelTransferState.RemoveFailed -> Text(state.message, maxLines = 2)
                VoiceModelTransferState.Cancelled -> Text("Download cancelled")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state) {
                    is VoiceModelTransferState.Downloading -> {
                        OutlinedButton(onClick = manager::pause) { Text("Pause", maxLines = 1, softWrap = false) }
                        OutlinedButton(onClick = manager::cancel) { Text("Cancel", maxLines = 1, softWrap = false) }
                    }
                    VoiceModelTransferState.Paused -> {
                        Button(onClick = onResume) { Text("Resume", maxLines = 1, softWrap = false) }
                        OutlinedButton(onClick = manager::cancel) { Text("Cancel", maxLines = 1, softWrap = false) }
                    }
                    VoiceModelTransferState.Installing, VoiceModelTransferState.Removing -> Unit
                    VoiceModelTransferState.Installed -> {
                        if (!active) Button(onClick = onActivate) { Text("Activate", maxLines = 1, softWrap = false) }
                        if (active) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("Bella", "Nova", "Heart").forEach { voice ->
                                    OutlinedButton(
                                        onClick = { onSelectVoice("kokoro:af_${voice.lowercase()}") },
                                        modifier = Modifier.weight(1f),
                                    ) { Text(voice, maxLines = 1, softWrap = false) }
                                }
                            }
                            OutlinedButton(onClick = onPreviewVoice) { Text("Preview Nova", maxLines = 1, softWrap = false) }
                        }
                        OutlinedButton(onClick = onUninstall) { Text("Remove", maxLines = 1, softWrap = false) }
                    }
                    is VoiceModelTransferState.Failed, VoiceModelTransferState.Cancelled ->
                        Button(onClick = onDownload) { Text("Retry", maxLines = 1, softWrap = false) }
                    is VoiceModelTransferState.RemoveFailed ->
                        Button(onClick = onUninstall) { Text("Retry removal", maxLines = 1, softWrap = false) }
                    VoiceModelTransferState.Idle ->
                        Button(onClick = onDownload) { Text("Download", maxLines = 1, softWrap = false) }
                }
            }
        }
    }
}

@Composable
private fun TtsVoiceRow(
    voice: OfflineTtsVoice,
    selected: Boolean,
    onPreview: () -> Unit,
    onSelect: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(voice.languageLabel, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            Text("${voice.displayName} · ${voice.languageTag} · offline", style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
            Text("Quality ${voice.quality} · installed", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
            if (selected) Text("Selected", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPreview) { Text("Preview", maxLines = 1, softWrap = false) }
                if (!selected) OutlinedButton(onClick = onSelect) { Text("Select voice", maxLines = 1, softWrap = false) }
            }
        }
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
                Text(spec.name, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                Text(if (spec.bundledAsset != null) "Included" else "Downloadable", style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
            }
            Text("${spec.languageName} · v${spec.version} · ${formatMegabytes(spec.archiveSizeBytes)} MB download", maxLines = 1, softWrap = false)
            Text("${spec.license} · Source: alphacephei.com/vosk/models", style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
            Text(modelStateLabel(state, isActive), style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
            if (state is ModelTransferState.Downloading) {
                val progress = if (state.totalBytes > 0) state.receivedBytes.toFloat() / state.totalBytes else 0f
                LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("${formatMegabytes(state.receivedBytes)} / ${formatMegabytes(state.totalBytes)} MB", maxLines = 1, softWrap = false)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state) {
                    is ModelTransferState.Downloading -> OutlinedButton(onClick = onCancel) { Text("Cancel", maxLines = 1, softWrap = false) }
                    ModelTransferState.Installing -> Text("Validating and installing…", style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                    ModelTransferState.Installed -> {
                        if (!isActive) Button(onClick = onSelect) { Text("Select", maxLines = 1, softWrap = false) }
                        if (spec.bundledAsset == null) OutlinedButton(onClick = onUninstall) { Text("Uninstall", maxLines = 1, softWrap = false) }
                    }
                    is ModelTransferState.Failed, ModelTransferState.Cancelled -> Button(onClick = onRetry) { Text("Retry", maxLines = 1, softWrap = false) }
                    ModelTransferState.Idle -> {
                        if (spec.bundledAsset == null) Button(onClick = onDownload) { Text("Download & install", maxLines = 1, softWrap = false) }
                        else Text("Included model installs on first launch", style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
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
