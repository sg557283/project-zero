package com.zero.assistant

import android.appwidget.AppWidgetManager
import android.content.ComponentName
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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zero.assistant.core.ShakeSensitivity
import com.zero.assistant.core.AssistantAction
import com.zero.assistant.core.AssistantTrigger
import com.zero.assistant.core.SpeechLanguage
import com.zero.assistant.core.SpeechOutputStatus
import com.zero.assistant.core.WakePhrase
import com.zero.assistant.core.WakePhraseConfiguration
import com.zero.assistant.core.AiProviderContract
import com.zero.assistant.data.AiProviderSettings
import com.zero.assistant.data.HardwareActivationSettings
import com.zero.assistant.data.AssistantActionSettings
import com.zero.assistant.data.OverlayDesignStyle
import com.zero.assistant.data.OverlayPreferencesRepository
import com.zero.assistant.data.WakeWordSettings
import java.text.DateFormat
import java.util.Date
import java.util.Locale

internal enum class SettingsSection { Hub, VoiceAudio, WakePhrases, ToolsVault, HardwareEdge, FloatingBot, OnlineSystem }

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
    var backTapsEnabled by remember { mutableStateOf(AssistantActionSettings.backTapsEnabled(context)) }
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
    var widgetPinInstructions by remember { mutableStateOf(false) }
    var widgetActionMenu by remember { mutableStateOf<Int?>(null) }
    val widgetActions = remember(context) {
        mutableStateListOf<WidgetQuickAction>().apply { addAll(WidgetActionSettings.actions(context)) }
    }
    var overlayPermissionGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var floatingBotVolumeEnabled by remember { mutableStateOf(FloatingBotSettings.volumeToggleEnabled(context)) }
    var bubbleAddMenuExpanded by remember { mutableStateOf(false) }
    val bubbleActions = remember(context) {
        mutableStateListOf<BotBubbleAction>().apply { addAll(BotBubbleConfigManager.actions(context)) }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayPermissionGranted = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
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
                "Emergency Quick Mode",
                "Review configured emergency actions",
                "OPEN",
                highlight = false,
                borderColor = Color(0xFFEF4444),
                onClick = { onNavigate(Page.Emergency) },
            )
            SettingsPanel {
                Text("Home Screen Widget", fontWeight = FontWeight.Bold)
                Text(
                    "Pin ZERO for one-tap voice commands and customizable shortcuts.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = {
                    val manager = AppWidgetManager.getInstance(context)
                    val requested = runCatching {
                        manager.isRequestPinAppWidgetSupported &&
                            manager.requestPinAppWidget(
                                ComponentName(context, ZeroAssistantWidget::class.java),
                                null,
                                null,
                            )
                    }.getOrElse {
                        systemActionError = it.message ?: "The launcher could not pin the widget."
                        false
                    }
                    if (!requested) widgetPinInstructions = true
                }) {
                    Text("Add Home Screen Widget")
                }
                widgetActions.forEachIndexed { slot, selectedAction ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Quick action ${slot + 1}", modifier = Modifier.weight(1f), fontSize = 13.sp)
                        Box {
                            OutlinedButton(onClick = { widgetActionMenu = slot }) {
                                Text(selectedAction.label, maxLines = 1)
                            }
                            DropdownMenu(
                                expanded = widgetActionMenu == slot,
                                onDismissRequest = { widgetActionMenu = null },
                            ) {
                                WidgetQuickAction.entries.forEach { action ->
                                    DropdownMenuItem(
                                        text = { Text(action.label) },
                                        onClick = {
                                            WidgetActionSettings.setAction(context, slot, action)
                                            widgetActions[slot] = action
                                            widgetActionMenu = null
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                systemActionError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            SettingsFeatureCard(
                title = "On-device Speech Recognition",
                summary = "Android offline recognizer when installed · Android 12+",
                badge = "INFO",
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
                "Customize Floating Bot Bubbles",
                "System-wide dotted bot, volume-key toggle and radial actions",
                "OPEN",
                onClick = { onSection(SettingsSection.FloatingBot) },
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
                    SettingsSection.FloatingBot -> "Customize Floating Bot Bubbles"
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
                        "Android On-device Recognition",
                        "Voice commands use Android's on-device recognizer when available. Typed commands always work.",
                        "MODELS",
                        onClick = { onNavigate(Page.SpeechModels) },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsFeatureCard(
                            "Voice Library",
                            "Browse optional model downloads",
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
                        Text("Microphone, call & location privacy", fontWeight = FontWeight.Bold)
                        Text(
                            "Speech recognition runs on-device. ZERO pauses wake listening while another app records or a phone call is active.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Answer/end call controls require Android's Answer phone calls permission. Weather uses location only when location permission is granted; otherwise say a city.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
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
                        "Uses bundled Sherpa offline keyword spotting for the built-in English phrases.",
                        wakeWordEnabled,
                        onWakeWordEnabled,
                    )
                    SettingsPanel {
                        Text("Wake Sensitivity", fontWeight = FontWeight.Bold)
                        Text(
                            "Sherpa KWS sensitivity adjusts English wake-phrase detection; custom and Hindi wake phrases are not recognized.",
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
                                else -> "Saved phrase; bundled Sherpa recognizes only its built-in phrases"
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
                    SettingsFeatureCard("Battery Alerts", "Configure low and high battery notices", "OPEN", onClick = { onNavigate(Page.BatteryAlerts) })
                }
                SettingsSection.HardwareEdge -> {
                    SettingsToggle(
                        "Volume Up + Down Shortcut",
                        "Enabled here, then select ZERO as Android's Accessibility shortcut. Volume keys are never consumed.",
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
                        "Back-Tap 2x / 3x / 4x",
                        "Experimental accelerometer gestures; requires an accelerometer and Android camera permission for the foreground sensor service.",
                        backTapsEnabled,
                        onCheckedChange = { enabled ->
                            backTapsEnabled = enabled
                            AssistantActionSettings.setBackTapsEnabled(context, enabled)
                            if (enabled && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                onRequestShakeCameraPermission()
                            } else {
                                AssistantActionSettings.syncGestureService(context)
                                    .onFailure { systemActionError = it.message ?: "Gesture service could not be updated." }
                            }
                        },
                    )
                    TriggerActionSettingsPanel()
                    TextButton(onClick = { onNavigate(Page.TriggerTest) }) { Text("Gesture & Shortcut Test") }
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
                        "Double-Shake Trigger",
                        "Requires Android camera permission for its foreground sensor service and an accelerometer.",
                        doubleShake,
                        onCheckedChange = { enabled ->
                            HardwareActivationSettings.setDoubleShakeEnabled(context, enabled)
                            doubleShake = enabled
                            if (enabled && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                onRequestShakeCameraPermission()
                            } else {
                                AssistantActionSettings.syncGestureService(context)
                                    .onFailure { systemActionError = it.message ?: "The gesture service could not be updated." }
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
                SettingsSection.FloatingBot -> {
                    SettingsPanel {
                        Text("System-wide Floating Bot", fontWeight = FontWeight.Bold)
                        Text(
                            if (overlayPermissionGranted) {
                                "Display-over-other-apps access is granted."
                            } else {
                                "ZERO needs Android's display-over-other-apps access to draw above other apps."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(onClick = {
                            if (overlayPermissionGranted) {
                                FloatingBotOverlayService.show(context)
                            } else {
                                FloatingBotOverlayService.requestOverlayPermission(context)
                            }
                        }) {
                            Text(if (overlayPermissionGranted) "Preview Floating Bot" else "Grant Overlay Permission")
                        }
                    }
                    SettingsToggle(
                        "Volume Up + Down toggles the bot",
                        "When enabled, the volume chord shows or hides ZERO. Disable it to restore the configured volume-shortcut action.",
                        floatingBotVolumeEnabled,
                        onCheckedChange = {
                            FloatingBotSettings.setVolumeToggleEnabled(context, it)
                            floatingBotVolumeEnabled = it
                        },
                    )
                    TextButton(onClick = { onSection(SettingsSection.HardwareEdge) }) {
                        Text("Configure the Volume Up + Down shortcut")
                    }
                    SettingsPanel {
                        Text("Radial Action Bubbles", fontWeight = FontWeight.Bold)
                        Text(
                            "Hold the floating bot, drag to an action, then release. Keep up to ${BotBubbleConfigManager.MAX_BUBBLES} bubbles.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        bubbleActions.forEachIndexed { index, action ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${index + 1}. ${action.label}", modifier = Modifier.weight(1f))
                                TextButton(
                                    onClick = {
                                        val updated = bubbleActions.toMutableList().apply {
                                            add(index - 1, removeAt(index))
                                        }
                                        BotBubbleConfigManager.save(context, updated)
                                        bubbleActions.clear()
                                        bubbleActions.addAll(updated)
                                    },
                                    enabled = index > 0,
                                ) { Text("Up") }
                                TextButton(
                                    onClick = {
                                        val updated = bubbleActions.toMutableList().apply {
                                            add(index + 1, removeAt(index))
                                        }
                                        BotBubbleConfigManager.save(context, updated)
                                        bubbleActions.clear()
                                        bubbleActions.addAll(updated)
                                    },
                                    enabled = index < bubbleActions.lastIndex,
                                ) { Text("Down") }
                                TextButton(
                                    onClick = {
                                        val updated = bubbleActions.toMutableList().apply { removeAt(index) }
                                        BotBubbleConfigManager.save(context, updated)
                                        bubbleActions.clear()
                                        bubbleActions.addAll(updated)
                                    },
                                ) { Text("Remove") }
                            }
                        }
                        Box {
                            Button(
                                onClick = { bubbleAddMenuExpanded = true },
                                enabled = bubbleActions.size < BotBubbleConfigManager.MAX_BUBBLES,
                            ) { Text("Add Bubble") }
                            DropdownMenu(
                                expanded = bubbleAddMenuExpanded,
                                onDismissRequest = { bubbleAddMenuExpanded = false },
                            ) {
                                BotBubbleAction.entries.filterNot(bubbleActions::contains).forEach { action ->
                                    DropdownMenuItem(
                                        text = { Text(action.label) },
                                        onClick = {
                                            val updated = bubbleActions + action
                                            BotBubbleConfigManager.save(context, updated)
                                            bubbleActions.clear()
                                            bubbleActions.addAll(updated)
                                            bubbleAddMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
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
                    Text("Sherpa KWS covers the included English phrases. Saved custom phrases are not recognized by the bundled model.", style = MaterialTheme.typography.bodySmall)
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
    if (widgetPinInstructions) {
        AlertDialog(
            onDismissRequest = { widgetPinInstructions = false },
            title = { Text("Add ZERO to your home screen") },
            text = {
                Text("This launcher does not support one-tap pin requests. Long-press an empty area of the home screen, choose Widgets, find ZERO Assistant, then drag the widget onto the home screen.")
            },
            confirmButton = {
                TextButton(onClick = { widgetPinInstructions = false }) { Text("Got it") }
            },
        )
    }
}

@Composable
private fun TriggerActionSettingsPanel() {
    val context = LocalContext.current
    var actions by remember {
        mutableStateOf(AssistantTrigger.entries.associateWith { AssistantActionSettings.actionFor(context, it) })
    }
    SettingsPanel {
        Text("Action mappings", fontWeight = FontWeight.Bold)
        AssistantTrigger.entries.forEach { trigger ->
            var expanded by remember(trigger) { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(trigger.label, modifier = Modifier.weight(1f), maxLines = 1, softWrap = false)
                Box {
                    OutlinedButton(onClick = { expanded = true }) {
                        Text(actions[trigger]?.label ?: trigger.defaultAction.label, maxLines = 1, softWrap = false)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        AssistantAction.entries.forEach { action ->
                            DropdownMenuItem(
                                text = { Text(action.label, maxLines = 1, softWrap = false) },
                                onClick = {
                                    expanded = false
                                    AssistantActionSettings.setAction(context, trigger, action)
                                    actions = actions + (trigger to action)
                                },
                            )
                        }
                    }
                }
            }
        }
        Text(
            "Voice mapping runs when you say “run my voice action.” Calls open the dialer for review; emergency SMS is not satellite service.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
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
