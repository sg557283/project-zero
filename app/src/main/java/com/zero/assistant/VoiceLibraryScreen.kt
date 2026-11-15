package com.zero.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.zero.assistant.core.MobileActionBrainState
import com.zero.assistant.core.HumanoidVoiceCatalog
import com.zero.assistant.core.HumanoidVoiceGender
import com.zero.assistant.core.HumanoidVoiceSpec
import com.zero.assistant.core.KokoroSpeakerCatalog
import com.zero.assistant.core.VoiceLibraryCategory
import com.zero.assistant.core.VoiceModelCatalog
import com.zero.assistant.core.VoiceModelTransferState
import com.zero.assistant.data.HumanoidTtsManager
import com.zero.assistant.data.ModelDownloadManager
import com.zero.assistant.data.MobileActionBrainEngine
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun VoiceLibraryScreen(
    modifier: Modifier,
    voiceModelManager: ModelDownloadManager,
    humanoidTtsManager: HumanoidTtsManager,
    actionBrainEngine: MobileActionBrainEngine,
    refreshTtsVoices: () -> Unit,
    prepareForVoiceModelRemoval: (() -> Unit) -> Unit,
    onModelRemoved: () -> Unit,
    prepareForHumanoidVoiceRemoval: (String, () -> Unit) -> Unit,
    onHumanoidVoiceRemoved: (String) -> Unit,
    onHumanoidVoiceInstalled: () -> Unit,
    selectedTtsVoiceId: String?,
    onSelectTtsVoice: (String) -> Boolean,
    onPreviewTtsVoice: (String) -> Unit,
) {
    val voiceModelState by voiceModelManager.state.collectAsState()
    val activeVoiceModelId by voiceModelManager.activeModelId.collectAsState()
    val activeHumanoidVoiceId by humanoidTtsManager.activeVoiceId.collectAsState()
    val preferredGender by humanoidTtsManager.preferredGender.collectAsState()
    val actionBrainState by actionBrainEngine.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var selectedCategory by remember {
        mutableStateOf(
            when (humanoidTtsManager.preferredGender.value) {
                HumanoidVoiceGender.Male -> VoiceLibraryCategory.HumanoidMale
                else -> VoiceLibraryCategory.HumanoidFemale
            },
        )
    }
    var confirmVoiceDownload by remember { mutableStateOf(false) }
    var confirmVoiceUninstall by remember { mutableStateOf(false) }
    var confirmBrainDownload by remember { mutableStateOf(false) }
    var confirmBrainUninstall by remember { mutableStateOf(false) }
    var pendingHumanoidRemoval by remember { mutableStateOf<HumanoidVoiceSpec?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("Voice Library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "Neural voices run offline after download; downloads need internet. No speech API or key is used.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            maxLines = 1,
            softWrap = false,
        )
        ScrollableTabRow(selectedTabIndex = VoiceLibraryCategory.entries.indexOf(selectedCategory)) {
            VoiceLibraryCategory.entries.forEach { category ->
                Tab(
                    selected = selectedCategory == category,
                    onClick = {
                        selectedCategory = category
                        when (category) {
                            VoiceLibraryCategory.HumanoidFemale ->
                                if (!humanoidTtsManager.setPreferredGender(HumanoidVoiceGender.Female)) {
                                    feedback = "Your voice preference could not be saved."
                                }
                            VoiceLibraryCategory.HumanoidMale ->
                                if (!humanoidTtsManager.setPreferredGender(HumanoidVoiceGender.Male)) {
                                    feedback = "Your voice preference could not be saved."
                                }
                            else -> Unit
                        }
                    },
                    text = { Text(category.label) },
                )
            }
        }
        feedback?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (selectedCategory) {
                VoiceLibraryCategory.HumanoidFemale -> {
                    item { Text("Humanoid female voices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                    items(HumanoidVoiceCatalog.female, key = { it.voiceId }) { voice ->
                        val state by humanoidTtsManager.state(voice.voiceId).collectAsState()
                        HumanoidVoiceCard(
                            voice = voice,
                            state = state,
                            active = activeHumanoidVoiceId == voice.voiceId,
                            selected = selectedTtsVoiceId == voice.voiceId,
                            onDownload = {
                                feedback = if (humanoidTtsManager.download(voice.voiceId)) null
                                else "This voice could not start downloading. Finish or cancel the current transfer first."
                            },
                            onCancel = { humanoidTtsManager.cancel(voice.voiceId) },
                            onInstalled = onHumanoidVoiceInstalled,
                            onPreview = { onPreviewTtsVoice(voice.voiceId) },
                            onSelect = {
                                feedback = if (onSelectTtsVoice(voice.voiceId)) {
                                    refreshTtsVoices()
                                    "Selected ${voice.name}."
                                } else "Download ${voice.name} before selecting it."
                            },
                            onRemove = { pendingHumanoidRemoval = voice },
                        )
                    }
                }
                VoiceLibraryCategory.HumanoidMale -> {
                    item { Text("Humanoid male voices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                    items(HumanoidVoiceCatalog.male, key = { it.voiceId }) { voice ->
                        val state by humanoidTtsManager.state(voice.voiceId).collectAsState()
                        HumanoidVoiceCard(
                            voice = voice,
                            state = state,
                            active = activeHumanoidVoiceId == voice.voiceId,
                            selected = selectedTtsVoiceId == voice.voiceId,
                            onDownload = {
                                feedback = if (humanoidTtsManager.download(voice.voiceId)) null
                                else "This voice could not start downloading. Finish or cancel the current transfer first."
                            },
                            onCancel = { humanoidTtsManager.cancel(voice.voiceId) },
                            onInstalled = onHumanoidVoiceInstalled,
                            onPreview = { onPreviewTtsVoice(voice.voiceId) },
                            onSelect = {
                                feedback = if (onSelectTtsVoice(voice.voiceId)) {
                                    refreshTtsVoices()
                                    "Selected ${voice.name}."
                                } else "Download ${voice.name} before selecting it."
                            },
                            onRemove = { pendingHumanoidRemoval = voice },
                        )
                    }
                }
                VoiceLibraryCategory.MultiSpeaker -> {
                    item { Text("Multi-speaker studio voice", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                    item {
                        KokoroModelCard(
                            state = voiceModelState,
                            active = activeVoiceModelId == VoiceModelCatalog.KOKORO_ID,
                            selectedVoiceId = selectedTtsVoiceId,
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
                            onPreviewVoice = onPreviewTtsVoice,
                            onUninstall = {
                                confirmVoiceUninstall = true
                            },
                        )
                    }
                }
                VoiceLibraryCategory.OfflineAI -> {
                    item { Text("Offline AI Action Brain", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                    item {
                        MobileActionBrainCard(
                            state = actionBrainState,
                            manager = actionBrainEngine,
                            onDownload = { confirmBrainDownload = true },
                            onActivate = {
                                coroutineScope.launch {
                                    val result = actionBrainEngine.activate()
                                    feedback = result.exceptionOrNull()?.message
                                        ?: "The offline action brain is active."
                                }
                            },
                            onResume = {
                                if (!actionBrainEngine.resume()) feedback = "Action brain download could not resume."
                            },
                            onUninstall = { confirmBrainUninstall = true },
                        )
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Wake detection · Included", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                        Text("Sherpa KWS · English built-ins · Apache-2.0", maxLines = 1, softWrap = false)
                        Text("16 kHz mono · custom/Hindi wake phrases are unsupported", style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }

    if (preferredGender == null) {
        ModalBottomSheet(onDismissRequest = {}) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Choose Your Assistant Voice",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Choose a preferred voice group first. You can switch groups using the tabs and select any downloaded voice.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = {
                        if (humanoidTtsManager.setPreferredGender(HumanoidVoiceGender.Female)) {
                            selectedCategory = VoiceLibraryCategory.HumanoidFemale
                        } else {
                            feedback = "Your voice preference could not be saved."
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Humanoid Female · English & Hindi")
                }
                OutlinedButton(
                    onClick = {
                        if (humanoidTtsManager.setPreferredGender(HumanoidVoiceGender.Male)) {
                            selectedCategory = VoiceLibraryCategory.HumanoidMale
                        } else {
                            feedback = "Your voice preference could not be saved."
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Humanoid Male · English & Hindi")
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (confirmVoiceDownload) {
        val model = VoiceModelCatalog.models.first()
        AlertDialog(
            onDismissRequest = { confirmVoiceDownload = false },
            title = { Text("Download Kokoro v1.1 INT8?") },
            text = {
                Text(
                    "Downloads a ${formatMegabytes(model.archiveSizeBytes)} MB verified Sherpa-ONNX archive " +
                        "with 103 English/Chinese speakers. This catalog exposes three verified English voices; " +
                        "Hindi is unsupported by this Kokoro pack. Allow about 900 MiB of free space for installation.",
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

    if (confirmBrainDownload) {
        val model = VoiceModelCatalog.actionBrain
        AlertDialog(
            onDismissRequest = { confirmBrainDownload = false },
            title = { Text("Download offline action brain?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${model.subtitle} · ${formatMegabytes(model.sizeBytes)} MB")
                    Text("This is a third-party FunctionGemma fine-tune, not a Google-hosted model. Source: ${model.source}.")
                    Text("Gemma Terms of Use apply: ${model.licenseUrl}")
                    if (!actionBrainEngine.meetsRecommendedMemory) {
                        Text("This device has less than 4 GB RAM; the download and activation are disabled.")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmBrainDownload = false
                        feedback = if (actionBrainEngine.download()) null else "Action brain download could not start."
                    },
                    enabled = actionBrainEngine.meetsRecommendedMemory,
                ) { Text("Accept terms & download", maxLines = 1, softWrap = false) }
            },
            dismissButton = { TextButton(onClick = { confirmBrainDownload = false }) { Text("Cancel") } },
        )
    }

    if (confirmBrainUninstall) {
        AlertDialog(
            onDismissRequest = { confirmBrainUninstall = false },
            title = { Text("Remove offline action brain?") },
            text = { Text("ZERO will unload the LiteRT-LM engine before deleting the downloaded model and cache.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmBrainUninstall = false
                    coroutineScope.launch {
                        feedback = if (actionBrainEngine.uninstall()) {
                            "Offline action brain removed."
                        } else {
                            "Offline action brain could not be removed."
                        }
                    }
                }) { Text("Unload & remove") }
            },
            dismissButton = { TextButton(onClick = { confirmBrainUninstall = false }) { Text("Keep model") } },
        )
    }

    if (confirmVoiceUninstall) {
        AlertDialog(
            onDismissRequest = { confirmVoiceUninstall = false },
            title = { Text("Remove Kokoro?") },
            text = { Text("ZERO will stop Kokoro playback before removing its downloaded model files.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmVoiceUninstall = false
                    prepareForVoiceModelRemoval {
                        if (!voiceModelManager.uninstall { removed ->
                                if (removed) {
                                    onModelRemoved()
                                    feedback = "Kokoro model removed."
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

    pendingHumanoidRemoval?.let { voice ->
        AlertDialog(
            onDismissRequest = { pendingHumanoidRemoval = null },
            title = { Text("Remove ${voice.name}?") },
            text = { Text("ZERO will stop using this voice, then delete its downloaded model files from app storage.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingHumanoidRemoval = null
                    prepareForHumanoidVoiceRemoval(voice.voiceId) {
                        if (!humanoidTtsManager.remove(voice.voiceId) { removed ->
                                if (removed) {
                                    onHumanoidVoiceRemoved(voice.voiceId)
                                    feedback = "${voice.name} removed."
                                } else {
                                    feedback = "${voice.name} could not be removed."
                                }
                            }
                        ) {
                            feedback = "${voice.name} could not be removed."
                        }
                    }
                }) { Text("Stop and remove") }
            },
            dismissButton = { TextButton(onClick = { pendingHumanoidRemoval = null }) { Text("Keep voice") } },
        )
    }

}

@Composable
private fun HumanoidVoiceCard(
    voice: HumanoidVoiceSpec,
    state: VoiceModelTransferState,
    active: Boolean,
    selected: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstalled: () -> Unit,
    onPreview: () -> Unit,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    LaunchedEffect(state) {
        if (state == VoiceModelTransferState.Installed) onInstalled()
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(voice.name, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    Text(
                        "${voice.accent} · ${voice.style}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        voice.gender.label.uppercase(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Text(
                "${voice.languageLabel} · INT8 · ${formatMegabytes(voice.archiveSizeBytes)} MB",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                softWrap = false,
            )
            when (state) {
                VoiceModelTransferState.Idle -> Text("Not downloaded")
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
                VoiceModelTransferState.Removing -> Text("Removing voice files")
                VoiceModelTransferState.Installed -> Text(
                    when {
                        selected -> "Downloaded · Selected"
                        active -> "Downloaded · Active"
                        else -> "Downloaded · Ready to select"
                    },
                )
                is VoiceModelTransferState.Failed -> Text(
                    "Download failed: ${state.message}",
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                )
                is VoiceModelTransferState.RemoveFailed -> Text(state.message, color = MaterialTheme.colorScheme.error, maxLines = 2)
                VoiceModelTransferState.Cancelled -> Text("Download cancelled")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state) {
                    is VoiceModelTransferState.Downloading -> OutlinedButton(onClick = onCancel) { Text("Cancel") }
                    VoiceModelTransferState.Installing, VoiceModelTransferState.Removing -> Unit
                    VoiceModelTransferState.Installed -> {
                        OutlinedButton(onClick = onPreview) { Text("Preview") }
                        if (!selected) Button(onClick = onSelect) { Text("Use voice") }
                        OutlinedButton(onClick = onRemove) { Text("Delete") }
                    }
                    is VoiceModelTransferState.RemoveFailed -> OutlinedButton(onClick = onRemove) { Text("Retry delete") }
                    else -> Button(onClick = onDownload) {
                        Text(if (state is VoiceModelTransferState.Failed || state == VoiceModelTransferState.Cancelled) "Retry" else "Download")
                    }
                }
            }
        }
    }
}

@Composable
private fun KokoroModelCard(
    state: VoiceModelTransferState,
    active: Boolean,
    selectedVoiceId: String?,
    manager: ModelDownloadManager,
    onDownload: () -> Unit,
    onActivate: () -> Unit,
    onResume: () -> Unit,
    onSelectVoice: (String) -> Unit,
    onPreviewVoice: (String) -> Unit,
    onUninstall: () -> Unit,
) {
    val model = VoiceModelCatalog.models.first()
    val speakers = manager.availableVoices()
    val selectedSpeaker = speakers.firstOrNull { "kokoro:${it.first}" == selectedVoiceId } ?: speakers.firstOrNull()
    var speakerMenuExpanded by remember { mutableStateOf(false) }
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
            Text(
                "Sherpa-ONNX · 3 selectable English voices · Hindi unsupported",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
            )
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
                            if (selectedSpeaker != null) {
                                OutlinedButton(onClick = { speakerMenuExpanded = true }) {
                                    Text(kokoroSpeakerLabel(selectedSpeaker.first), maxLines = 1, softWrap = false)
                                }
                                DropdownMenu(
                                    expanded = speakerMenuExpanded,
                                    onDismissRequest = { speakerMenuExpanded = false },
                                    modifier = Modifier.heightIn(max = 320.dp),
                                ) {
                                    speakers.forEach { (name, _) ->
                                        DropdownMenuItem(
                                            text = { Text(kokoroSpeakerLabel(name)) },
                                            onClick = {
                                                speakerMenuExpanded = false
                                                onSelectVoice("kokoro:$name")
                                            },
                                            trailingIcon = {
                                                val gender = KokoroSpeakerCatalog.speaker(name)?.gender
                                                Text(
                                                    if (gender == HumanoidVoiceGender.Male) "M" else "F",
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            },
                                        )
                                    }
                                }
                                OutlinedButton(onClick = { onPreviewVoice("kokoro:${selectedSpeaker.first}") }) {
                                    Text("Preview selected")
                                }
                            }
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

private fun kokoroSpeakerLabel(name: String): String =
    KokoroSpeakerCatalog.speaker(name)?.let { "${it.displayName} · ${it.gender.label} · ${it.languageLabel}" }
        ?: name

@Composable
private fun MobileActionBrainCard(
    state: MobileActionBrainState,
    manager: MobileActionBrainEngine,
    onDownload: () -> Unit,
    onActivate: () -> Unit,
    onResume: () -> Unit,
    onUninstall: () -> Unit,
) {
    val model = VoiceModelCatalog.actionBrain
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(model.title, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            Text(model.subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
            Text(
                "${formatMegabytes(model.sizeBytes)} MB · INT8 · ${model.license}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                softWrap = false,
            )
            Text("Third-party fine-tune · ${model.source}", style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
            Text("Gemma terms: ${model.licenseUrl}", style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
            if (!manager.meetsRecommendedMemory) {
                Text(
                    "At least 4 GB device RAM is recommended; download and activation are disabled.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            when (state) {
                MobileActionBrainState.Idle -> Text("Not downloaded", maxLines = 1, softWrap = false)
                is MobileActionBrainState.Downloading -> {
                    val progress = if (state.totalBytes > 0) state.receivedBytes.toFloat() / state.totalBytes else 0f
                    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text(
                        "${formatMegabytes(state.receivedBytes)} / ${formatMegabytes(state.totalBytes)} MB",
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                MobileActionBrainState.Paused -> Text("Download paused", maxLines = 1, softWrap = false)
                MobileActionBrainState.Loading -> Text("Loading on-device model…", maxLines = 1, softWrap = false)
                MobileActionBrainState.Installed -> Text("Downloaded · not active", maxLines = 1, softWrap = false)
                MobileActionBrainState.Active -> Text("Downloaded · active", maxLines = 1, softWrap = false)
                MobileActionBrainState.Removing -> Text("Unloading and removing…", maxLines = 1, softWrap = false)
                is MobileActionBrainState.Failed -> Text(
                    "Unavailable: ${state.message}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    softWrap = false,
                )
                is MobileActionBrainState.RemoveFailed -> Text(state.message, maxLines = 1, softWrap = false)
                MobileActionBrainState.Cancelled -> Text("Download cancelled", maxLines = 1, softWrap = false)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state) {
                    is MobileActionBrainState.Downloading -> {
                        OutlinedButton(onClick = manager::pause) { Text("Pause", maxLines = 1, softWrap = false) }
                        OutlinedButton(onClick = manager::cancel) { Text("Cancel", maxLines = 1, softWrap = false) }
                    }
                    MobileActionBrainState.Paused -> {
                        Button(onClick = onResume) { Text("Resume", maxLines = 1, softWrap = false) }
                        OutlinedButton(onClick = manager::cancel) { Text("Cancel", maxLines = 1, softWrap = false) }
                    }
                    MobileActionBrainState.Loading, MobileActionBrainState.Removing -> Unit
                    MobileActionBrainState.Installed, MobileActionBrainState.Active -> {
                        if (manager.meetsRecommendedMemory && state != MobileActionBrainState.Active) {
                            Button(onClick = onActivate) { Text("Activate", maxLines = 1, softWrap = false) }
                        }
                        OutlinedButton(onClick = onUninstall) { Text("Remove", maxLines = 1, softWrap = false) }
                    }
                    is MobileActionBrainState.Failed -> {
                        if (manager.isInstalled()) {
                            Button(onClick = onActivate, enabled = manager.meetsRecommendedMemory) {
                                Text("Retry activation", maxLines = 1, softWrap = false)
                            }
                            OutlinedButton(onClick = onUninstall) { Text("Remove", maxLines = 1, softWrap = false) }
                        } else {
                            Button(onClick = onDownload, enabled = manager.meetsRecommendedMemory) {
                                Text("Retry download", maxLines = 1, softWrap = false)
                            }
                        }
                    }
                    is MobileActionBrainState.RemoveFailed ->
                        Button(onClick = onUninstall) { Text("Retry removal", maxLines = 1, softWrap = false) }
                    MobileActionBrainState.Idle, MobileActionBrainState.Cancelled ->
                        Button(onClick = onDownload, enabled = manager.meetsRecommendedMemory) {
                            Text(if (state == MobileActionBrainState.Idle) "Download" else "Retry download", maxLines = 1, softWrap = false)
                        }
                }
            }
        }
    }
}

private fun formatMegabytes(bytes: Long): String = "%.1f".format(bytes / 1_000_000.0)
