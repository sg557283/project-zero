package com.zero.assistant

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zero.assistant.core.ShakeSensitivity
import com.zero.assistant.core.SpeechLanguage
import com.zero.assistant.core.SpeechOutputStatus
import com.zero.assistant.core.WakePhrase
import com.zero.assistant.core.WakePhraseConfiguration
import com.zero.assistant.core.AiProviderContract
import com.zero.assistant.data.AiProviderSettings
import com.zero.assistant.data.HardwareActivationSettings
import com.zero.assistant.data.OverlayDesignStyle
import com.zero.assistant.data.OverlayPreferencesRepository
import com.zero.assistant.data.WakeWordSettings
import java.text.DateFormat
import java.util.Date
import java.util.Locale

internal enum class SettingsSection { Hub, VoiceAudio, WakePhrases, ToolsVault, HardwareEdge, OnlineSystem }

@Composable
internal fun CapsuleSettingsScreen(
    modifier: Modifier,
    section: SettingsSection,
    onSection: (SettingsSection) -> Unit,
    speechOutput: Boolean,
    onSpeechOutput: (Boolean) -> Unit,
    thinkingCuesEnabled: Boolean,
    onThinkingCues: (Boolean) -> Unit,
    speechLanguageTag: String,
    onSpeechLanguage: (String) -> Unit,
    activeModelId: String?,
    speechOutputStatus: SpeechOutputStatus,
    onTestSpeech: (SpeechLanguage) -> Unit,
    onStopSpeech: () -> Unit,
    wakeWordEnabled: Boolean,
    wakeWordActive: Boolean,
    wakeWordError: String?,
    wakeWordPhrases: List<WakePhrase>,
    wakeWordLastDetection: Long,
    microphonePermissionGranted: Boolean,
    onWakeWordPhrasesChanged: (List<WakePhrase>) -> Unit,
    onWakeWordEnabled: (Boolean) -> Unit,
    reducedMotion: Boolean,
    onReducedMotion: (Boolean) -> Unit,
    themeMode: com.zero.assistant.ui.ZeroThemeMode,
    onThemeMode: (com.zero.assistant.ui.ZeroThemeMode) -> Unit,
    aiProviderSettings: AiProviderSettings,
    onNavigate: (Page) -> Unit,
    onRequestShakeCameraPermission: () -> Unit,
    vaultVisible: Boolean,
    onVaultVisible: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var volumeShortcut by remember { mutableStateOf(HardwareActivationSettings.volumePairEnabled(context)) }
    var doubleShake by remember { mutableStateOf(HardwareActivationSettings.doubleShakeEnabled(context)) }
    val accessibilityPreferences = remember {
        context.getSharedPreferences("zero_accessibility", Context.MODE_PRIVATE)
    }
    var assistantEdgePanelEnabled by remember {
        mutableStateOf(accessibilityPreferences.getBoolean("assistant_edge_panel", false))
    }
    var overlayDesignStyle by remember { mutableStateOf(OverlayPreferencesRepository.style(context)) }
    var wakeSensitivity by remember { mutableStateOf(WakeWordSettings.sensitivity(context)) }
    var shakeSensitivity by remember { mutableStateOf(HardwareActivationSettings.shakeSensitivity(context)) }
    var editingPhraseId by remember { mutableStateOf<String?>(null) }
    var phraseDraft by remember { mutableStateOf("") }
    var phraseEditorVisible by remember { mutableStateOf(false) }
    var phraseEditorError by remember { mutableStateOf<String?>(null) }
    var vaultMenuExpanded by remember { mutableStateOf(false) }
    var aiConfiguration by remember { mutableStateOf(aiProviderSettings.configuration()) }
    var aiEndpoint by remember { mutableStateOf(aiConfiguration.endpoint) }
    var aiModel by remember { mutableStateOf(aiConfiguration.model) }
    var apiKeyDraft by remember { mutableStateOf("") }
    var aiSettingsMessage by remember { mutableStateOf<String?>(null) }
    var systemActionError by remember { mutableStateOf<String?>(null) }
    val speechOutputReady =
        speechOutputStatus is SpeechOutputStatus.Ready || speechOutputStatus is SpeechOutputStatus.ReadyWithVoice
    val speechEngineReady = speechOutputReady || speechOutputStatus is SpeechOutputStatus.LanguageUnavailable

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (section == SettingsSection.Hub) {
            Text("System Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            SettingsFeatureCard(
                title = "Vosk Local Engine",
                summary = activeModelId?.let { "$it (On-Device) • Hindi/English" }
                    ?: "vosk-hi-small-0.22 (On-Device) • Hindi/English · Setting up",
                badge = "OPEN",
                highlight = true,
                onClick = { onNavigate(Page.SpeechModels) },
            )
            SettingsPanel {
                Text("Appearance", fontWeight = FontWeight.Bold)
                com.zero.assistant.ui.ThemeModeSelector(themeMode, onThemeMode)
                SettingsToggle("Reduce Motion", "Keep motion effects minimal.", reducedMotion, onReducedMotion)
            }
            SettingsFeatureCard(
                "Voice & Audio",
                "${if (speechOutput) "Speak responses on" else "Speak responses off"} · ${speechOutputStatusMessage(speechOutputStatus)}",
                "OPEN",
                onClick = { onSection(SettingsSection.VoiceAudio) },
            )
            SettingsFeatureCard(
                "Wake Word & Phrases",
                "Built-in phrases and up to three custom phrases",
                if (wakeWordEnabled) "ON" else "OFF",
                onClick = { onSection(SettingsSection.WakePhrases) },
                badgeColor = if (wakeWordEnabled) Color(0xFF56D9E7) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsFeatureCard(
                "Tools & Security Vault",
                "Vault, planner, notes, scanner, shortcuts and utilities",
                "OPEN",
                onClick = { onSection(SettingsSection.ToolsVault) },
            )
            SettingsFeatureCard(
                "Hardware & Edge Panel",
                "Accessibility shortcut, edge status and shake controls",
                "OPEN",
                onClick = { onSection(SettingsSection.HardwareEdge) },
            )
            SettingsFeatureCard(
                "Online AI & System",
                "Optional BYOK endpoint, diagnostics, privacy and help",
                if (aiConfiguration.enabled) "ON" else "OPEN",
                onClick = { onSection(SettingsSection.OnlineSystem) },
            )
            SettingsPanel {
                Text("About ZERO-Assist", fontWeight = FontWeight.Bold)
                Text("Version ${BuildConfig.VERSION_NAME} · Made by @Shree", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text(
                when (section) {
                    SettingsSection.VoiceAudio -> "Voice & Audio"
                    SettingsSection.WakePhrases -> "Wake Word & Phrases"
                    SettingsSection.ToolsVault -> "Tools & Security Vault"
                    SettingsSection.HardwareEdge -> "Hardware & Edge Panel"
                    SettingsSection.OnlineSystem -> "Online AI & System"
                    SettingsSection.Hub -> "System Settings"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            when (section) {
                SettingsSection.Hub -> Unit
                SettingsSection.VoiceAudio -> {
                    SettingsPanel {
                        Text("Speech Engine", fontWeight = FontWeight.Bold)
                        Text(
                            "Android offline TTS · optional Sherpa/Kokoro · ${speechOutputStatusMessage(speechOutputStatus)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SettingsToggle("Speak Responses", speechOutputStatusMessage(speechOutputStatus), speechOutput, onSpeechOutput, speechOutputReady)
                    SettingsToggle(
                        "Natural Thinking Cues",
                        "Play a short English or Hindi cue before an online answer.",
                        thinkingCuesEnabled,
                        onThinkingCues,
                        speechOutputReady,
                    )
                    ActionPillRow(
                        listOf(
                            Triple("English", speechLanguageTag == "en-US", { onSpeechLanguage("en-US") }),
                            Triple("Hindi", speechLanguageTag == "hi-IN", { onSpeechLanguage("hi-IN") }),
                            Triple("Test EN", false, { onTestSpeech(SpeechLanguage.English) }),
                            Triple("Test HI", false, { onTestSpeech(SpeechLanguage.Hindi) }),
                            Triple("Stop", false, onStopSpeech),
                        ),
                        enabled = speechEngineReady,
                    )
                    SettingsFeatureCard(
                        "Vosk Local Recognition",
                        activeModelId?.let { "$it is active on this device. Typed commands work offline too." }
                            ?: "The local Hindi model is installing or not ready. Typed commands remain available.",
                        "MODELS",
                        onClick = { onNavigate(Page.SpeechModels) },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsFeatureCard(
                            "Voice Library",
                            "Browse offline Android voices",
                            "OPEN",
                            onClick = { onNavigate(Page.SpeechModels) },
                            modifier = Modifier.weight(1f),
                        )
                        SettingsFeatureCard(
                            "Offline Voice Studio",
                            "Manage and test local voices",
                            "OPEN",
                            onClick = { onNavigate(Page.VoiceStudio) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                SettingsSection.WakePhrases -> {
                    SettingsPanel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Live Wake Telemetry", fontWeight = FontWeight.Bold)
                                Text(
                                    "Mic: ${if (microphonePermissionGranted) "Granted" else "Not Granted"} • Service: ${if (wakeWordActive) "Running" else "Stopped"}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "Last: ${if (wakeWordLastDetection > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(wakeWordLastDetection)) else "Never"}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            CapsuleAction(
                                if (wakeWordEnabled) "Stop" else if (microphonePermissionGranted) "Start" else "Grant",
                                selected = wakeWordEnabled,
                                onClick = { onWakeWordEnabled(!wakeWordEnabled) },
                            )
                        }
                        wakeWordError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                    SettingsToggle(
                        "Background Wake Listening",
                        "Uses offline English keyword spotting with the selected Vosk model as wake fallback.",
                        wakeWordEnabled,
                        onWakeWordEnabled,
                    )
                    SettingsPanel {
                        Text("Wake Sensitivity", fontWeight = FontWeight.Bold)
                        Text(
                            "AGC when supported • Normal: KWS/VAD 0.35 • Low-Voice: 0.20 + 2.8 boost. Hindi/custom use Vosk.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            softWrap = false,
                        )
                        ActionPillRow(
                            listOf(
                                Triple("Normal", wakeSensitivity == WakeWordSettings.Sensitivity.Normal, {
                                    wakeSensitivity = WakeWordSettings.Sensitivity.Normal
                                    WakeWordSettings.setSensitivity(context, wakeSensitivity)
                                }),
                                Triple("Low-Voice / Whisper", wakeSensitivity == WakeWordSettings.Sensitivity.LowVoice, {
                                    wakeSensitivity = WakeWordSettings.Sensitivity.LowVoice
                                    WakeWordSettings.setSensitivity(context, wakeSensitivity)
                                }),
                            ),
                        )
                    }
                    wakeWordPhrases.forEach { phrase ->
                        SettingsToggle(
                            phrase.phrase,
                            when {
                                !phrase.enabled -> "Disabled"
                                phrase.isDefault -> "Built-in phrase"
                                activeModelId == null -> "Enabled · waiting for a local model"
                                else -> "Enabled · included in local wake grammar"
                            },
                            phrase.enabled,
                            { enabled ->
                                onWakeWordPhrasesChanged(WakePhraseConfiguration.setEnabled(wakeWordPhrases, phrase.id, enabled))
                            },
                        )
                        if (!phrase.isDefault) {
                            ActionPillRow(
                                listOf(
                                    Triple("Edit", false, {
                                        editingPhraseId = phrase.id
                                        phraseDraft = phrase.phrase
                                        phraseEditorError = null
                                        phraseEditorVisible = true
                                    }),
                                    Triple("Remove", false, {
                                        onWakeWordPhrasesChanged(WakePhraseConfiguration.remove(wakeWordPhrases, phrase.id))
                                    }),
                                ),
                            )
                        }
                    }
                    val customCount = wakeWordPhrases.count { !it.isDefault }
                    val activeCustomCount = wakeWordPhrases.count { !it.isDefault && it.enabled }
                    SettingsFeatureCard(
                        "Custom Wake Phrases",
                        "$activeCustomCount of ${WakePhraseConfiguration.MAX_CUSTOM_PHRASES} active · Tap to add",
                        "ADD",
                        enabled = customCount < WakePhraseConfiguration.MAX_CUSTOM_PHRASES,
                        onClick = {
                            editingPhraseId = null
                            phraseDraft = ""
                            phraseEditorError = null
                            phraseEditorVisible = true
                        },
                    )
                    CapsuleAction(
                        "Enable Built-In Phrases",
                        onClick = { onWakeWordPhrasesChanged(WakePhraseConfiguration.enableBuiltInPhrases(wakeWordPhrases)) },
                    )
                    Text(
                        "Built-ins: Hey ZERO, OK ZERO and Hey Shree. Recognition depends on the selected model's vocabulary.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    systemActionError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    ActionPillRow(
                        listOf(
                            Triple("Battery Optimization Helper", false, {
                                runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                                    .onFailure { systemActionError = it.message ?: "Android battery settings could not be opened." }
                                Unit
                            }),
                        ),
                    )
                }
                SettingsSection.ToolsVault -> {
                    Box {
                        SettingsFeatureCard(
                            "ZERO Vault",
                            if (vaultVisible) "Private files and encrypted backups" else "Hidden from navigation",
                            "⋮",
                            onClick = { onNavigate(Page.Vault) },
                            trailingAction = { vaultMenuExpanded = true },
                        )
                        DropdownMenu(expanded = vaultMenuExpanded, onDismissRequest = { vaultMenuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(if (vaultVisible) "Hide Vault" else "Show Vault", maxLines = 1, softWrap = false) },
                                onClick = { vaultMenuExpanded = false; onVaultVisible(!vaultVisible) },
                            )
                        }
                    }
                    SettingsFeatureCard("Daily Planner & Notes", "Reminders, tasks and private notes", "PLANNER", onClick = { onNavigate(Page.Planner) })
                    SettingsFeatureCard("Notes", "Browse and manage saved notes", "OPEN", onClick = { onNavigate(Page.Notes) })
                    SettingsFeatureCard("Knowledge Scanner", "Scan text into local notes", "OPEN", onClick = { onNavigate(Page.Scanner) })
                    SettingsFeatureCard("My Shortcuts", "Create and launch saved commands", "OPEN", onClick = { onNavigate(Page.MyShortcuts) })
                    SettingsFeatureCard("Local Utilities & Battery Alerts", "On-device search and power notifications", "OPEN", onClick = { onNavigate(Page.LocalUtilities) })
                    SettingsFeatureCard(
                        "Emergency Quick Mode",
                        "Review configured emergency actions",
                        "OPEN",
                        highlight = false,
                        borderColor = Color(0xFFEF4444),
                        onClick = { onNavigate(Page.Emergency) },
                    )
                    SettingsFeatureCard("Battery Alerts", "Configure low and high battery notices", "OPEN", onClick = { onNavigate(Page.BatteryAlerts) })
                }
                SettingsSection.HardwareEdge -> {
                    SettingsToggle(
                        "Volume Up + Down Shortcut",
                        "Enable ZERO in Android Accessibility settings to use this shortcut.",
                        volumeShortcut,
                        onCheckedChange = { enabled ->
                            volumeShortcut = enabled
                            HardwareActivationSettings.setVolumePairEnabled(context, enabled)
                            if (enabled) runCatching {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }.onFailure { systemActionError = it.message ?: "Android accessibility settings could not be opened." }
                        },
                    )
                    SettingsToggle(
                        "Assistant Edge Panel",
                        "Show the interactive overlay after a wake phrase over other apps.",
                        assistantEdgePanelEnabled,
                        onCheckedChange = { enabled ->
                            assistantEdgePanelEnabled = enabled
                            accessibilityPreferences.edit().putBoolean("assistant_edge_panel", enabled).apply()
                            if (enabled) runCatching {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }.onFailure { systemActionError = it.message ?: "Android accessibility settings could not be opened." }
                            else com.zero.assistant.data.WakeWordSettings.dismissAssistantPanel(context)
                        },
                    )
                    SettingsPanel {
                        Text("Overlay Design Style", fontWeight = FontWeight.Bold)
                        Text(
                            "Choose how ZERO appears over other apps.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                        )
                        ActionPillRow(
                            listOf(
                                Triple("Dynamic Notch", overlayDesignStyle == OverlayDesignStyle.DynamicNotch, {
                                    overlayDesignStyle = OverlayDesignStyle.DynamicNotch
                                    OverlayPreferencesRepository.setStyle(context, overlayDesignStyle)
                                }),
                                Triple("Right-Edge Corner", overlayDesignStyle == OverlayDesignStyle.RightEdge, {
                                    overlayDesignStyle = OverlayDesignStyle.RightEdge
                                    OverlayPreferencesRepository.setStyle(context, overlayDesignStyle)
                                }),
                            ),
                        )
                        ActionPillRow(
                            listOf(
                                Triple("Preview Overlay", false, {
                                    if (isZeroAccessibilityServiceEnabled(context)) {
                                        WakeWordSettings.updateAssistantPanel(
                                            context,
                                            "Preview overlay",
                                            dismissAfterMillis = 5_000L,
                                            preview = true,
                                            faceMode = BotFaceMode.Idle.name,
                                        )
                                        systemActionError = null
                                    } else {
                                        systemActionError = "Enable ZERO Accessibility service to preview the overlay."
                                        runCatching {
                                            context.startActivity(
                                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                            )
                                        }.onFailure { systemActionError = it.message ?: "Android accessibility settings could not be opened." }
                                    }
                                }),
                            ),
                        )
                    }
                    SettingsToggle(
                        "Double-Shake Flashlight",
                        "Requires camera permission, an accelerometer and a torch.",
                        doubleShake,
                        onCheckedChange = { enabled ->
                            HardwareActivationSettings.setDoubleShakeEnabled(context, enabled)
                            doubleShake = enabled
                            if (enabled && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                onRequestShakeCameraPermission()
                            } else {
                                val intent = Intent(context, com.zero.assistant.data.DoubleShakeService::class.java)
                                    .setAction(if (enabled) com.zero.assistant.data.DoubleShakeService.ACTION_START else com.zero.assistant.data.DoubleShakeService.ACTION_STOP)
                                runCatching {
                                    if (enabled) androidx.core.content.ContextCompat.startForegroundService(context, intent) else context.startService(intent)
                                }.onFailure { systemActionError = it.message ?: "The flashlight service could not be updated." }
                            }
                        },
                    )
                    systemActionError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    SettingsPanel {
                        Text("Sensitivity", fontWeight = FontWeight.Bold)
                        ActionPillRow(
                            ShakeSensitivity.entries.map { sensitivity ->
                                Triple(
                                    sensitivity.name.lowercase().replaceFirstChar { it.titlecase(Locale.ROOT) },
                                    shakeSensitivity == sensitivity,
                                    {
                                        shakeSensitivity = sensitivity
                                        HardwareActivationSettings.setShakeSensitivity(context, sensitivity)
                                    },
                                )
                            },
                        )
                    }
                }
                SettingsSection.OnlineSystem -> {
                    SettingsToggle(
                        "Enable Online AI (BYOK)",
                        "Sends only text after confirmation; microphone audio is never sent.",
                        aiConfiguration.enabled,
                        onCheckedChange = { enabled ->
                            aiProviderSettings.saveConfiguration(aiEndpoint, aiModel, enabled)
                                .onSuccess { aiConfiguration = aiProviderSettings.configuration(); aiSettingsMessage = null }
                                .onFailure { aiSettingsMessage = it.message ?: "AI settings could not be saved." }
                        },
                    )
                    OutlinedTextField(
                        value = aiEndpoint,
                        onValueChange = { aiEndpoint = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("HTTPS Endpoint", maxLines = 1, softWrap = false) },
                        shape = RoundedCornerShape(18.dp),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = aiModel,
                        onValueChange = { aiModel = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Model Identifier", maxLines = 1, softWrap = false) },
                        shape = RoundedCornerShape(18.dp),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = apiKeyDraft,
                        onValueChange = { apiKeyDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API Key (Keystore Encrypted)", maxLines = 1, softWrap = false) },
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(18.dp),
                        singleLine = true,
                    )
                    ActionPillRow(
                        listOf(
                            Triple("Save", false, {
                                aiProviderSettings.saveConfiguration(aiEndpoint, aiModel, aiConfiguration.enabled)
                                    .onSuccess {
                                        if (apiKeyDraft.isBlank() || aiProviderSettings.saveApiKey(apiKeyDraft)) {
                                            apiKeyDraft = ""
                                            aiConfiguration = aiProviderSettings.configuration()
                                            aiSettingsMessage = "Settings saved. Each online question still asks before sending."
                                        } else aiSettingsMessage = "The API key could not be encrypted by Android Keystore."
                                    }
                                    .onFailure { aiSettingsMessage = it.message ?: "AI settings could not be saved." }
                                Unit
                            }),
                            Triple("Clear", false, {
                                aiProviderSettings.clearApiKey()
                                aiConfiguration = aiProviderSettings.configuration()
                                aiSettingsMessage = "Saved API key removed from this device."
                            }),
                        ),
                    )
                    aiSettingsMessage?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (aiConfiguration.endpoint.isNotBlank()) {
                        Text(
                            if (AiProviderContract.validHttpsEndpoint(aiEndpoint)) "Endpoint: HTTPS verified" else "Endpoint must use HTTPS.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    SettingsFeatureCard("Self-Diagnostics", "Check local models and device services", "OPEN", onClick = { onNavigate(Page.Diagnostics) })
                    SettingsFeatureCard("Privacy Dashboard", "Review permissions and on-device data", "OPEN", onClick = { onNavigate(Page.Dashboard) })
                    ActionPillRow(
                        listOf(
                            Triple("Help", false, { onNavigate(Page.Help) }),
                            Triple("Privacy", false, { onNavigate(Page.Privacy) }),
                            Triple("Terms", false, { onNavigate(Page.Terms) }),
                        ),
                    )
                    SettingsPanel {
                        Text("About ZERO-Assist", fontWeight = FontWeight.Bold)
                        Text("Version 17.07.2007 · Made by @Shree", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                SettingsSection.Hub -> Unit
            }
        }
    }

    if (phraseEditorVisible) {
        AlertDialog(
            onDismissRequest = { phraseEditorVisible = false },
            title = { Text(if (editingPhraseId == null) "Add custom wake phrase" else "Edit wake phrase") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(phraseDraft, { phraseDraft = it }, label = { Text("Phrase") }, singleLine = true)
                    phraseEditorError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text("Sherpa KWS covers built-in English phrases; custom phrases use the selected Vosk model's vocabulary.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        val updated = editingPhraseId?.let { WakePhraseConfiguration.update(wakeWordPhrases, it, phraseDraft) }
                            ?: WakePhraseConfiguration.add(wakeWordPhrases, phraseDraft)
                        onWakeWordPhrasesChanged(updated)
                        phraseEditorVisible = false
                    }.onFailure { phraseEditorError = it.message ?: "Wake phrase could not be saved." }
                }) { Text("Save", maxLines = 1, softWrap = false) }
            },
            dismissButton = { TextButton(onClick = { phraseEditorVisible = false }) { Text("Cancel", maxLines = 1, softWrap = false) } },
        )
    }
}

private fun isZeroAccessibilityServiceEnabled(context: Context): Boolean {
    val manager = context.getSystemService(android.view.accessibility.AccessibilityManager::class.java) ?: return false
    return manager.getEnabledAccessibilityServiceList(
        android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
    ).any {
        it.resolveInfo.serviceInfo.packageName == context.packageName &&
            it.resolveInfo.serviceInfo.name == "com.zero.assistant.data.VolumeAccessibilityService"
    }
}

@Composable
private fun SettingsPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF131620),
        border = BorderStroke(1.dp, Color(0xFF23283A)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun SettingsFeatureCard(
    title: String,
    summary: String,
    badge: String,
    enabled: Boolean = true,
    highlight: Boolean = false,
    borderColor: Color = if (highlight) Color(0xFF26D7E8) else Color(0xFF23283A),
    badgeColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    trailingAction: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF131620),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            Spacer(Modifier.width(10.dp))
            if (trailingAction != null) {
                TextButton(onClick = trailingAction) { Text(badge, maxLines = 1, softWrap = false) }
            } else {
                Text(badge, color = badgeColor, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    SettingsPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}

@Composable
private fun ActionPillRow(
    actions: List<Triple<String, Boolean, () -> Unit>>,
    enabled: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { (label, selected, action) ->
            CapsuleAction(label, selected, enabled, action)
        }
    }
}

@Composable
private fun CapsuleAction(
    label: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        label = { Text(label, maxLines = 1, softWrap = false) },
    )
}
