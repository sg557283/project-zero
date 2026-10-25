package com.zero.assistant

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.zero.assistant.core.AppTarget
import com.zero.assistant.core.AssistantCommand
import com.zero.assistant.core.CommandHistory
import com.zero.assistant.core.CommandParser
import com.zero.assistant.core.HistoryEntry
import com.zero.assistant.data.PreferencesHistoryStorage
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val Forest = Color(0xFF0E5B50)
private val Mint = Color(0xFFE1F0E8)
private val Paper = Color(0xFFF7F8F5)
private val Ink = Color(0xFF18231F)

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsAvailable = false
    private val speechOutputAvailableState = mutableStateOf(false)
    private var recognizer: SpeechRecognizer? = null
    private var recognitionActive = false
    private var recognitionResult: ((String) -> Unit)? = null
    private var recognitionStateChanged: ((Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts ?: return@TextToSpeech
                val voices = engine.voices.orEmpty().filter {
                    !it.isNetworkConnectionRequired && it.locale.language in listOf("en", "hi")
                }
                val preferredLanguage = Locale.getDefault().language
                val voice = voices.firstOrNull { it.locale.language == preferredLanguage }
                    ?: voices.firstOrNull { it.locale.language == "en" }
                    ?: voices.firstOrNull { it.locale.language == "hi" }
                if (voice != null) {
                    engine.voice = voice
                    ttsAvailable = true
                }
                speechOutputAvailableState.value = ttsAvailable
                ttsReady = true
            } else {
                ttsReady = true
                speechOutputAvailableState.value = false
            }
        }
        setContent {
            ZeroApp(
                speak = { message -> if (ttsReady && ttsAvailable) tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "zero-response") },
                startListening = { languageTag, onResult, onStateChanged ->
                    if (android.os.Build.VERSION.SDK_INT >= 31) {
                        startOfflineRecognition(languageTag, onResult, onStateChanged)
                    } else {
                        onResult("Offline speech recognition requires Android 12 or newer. You can type commands instead.")
                    }
                },
                offlineSpeechAvailable = isOfflineRecognitionAvailable(),
                speechOutputAvailable = speechOutputAvailableState.value,
            )
        }
    }

    private fun isOfflineRecognitionAvailable(): Boolean =
        android.os.Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)

    @RequiresApi(31)
    private fun startOfflineRecognition(
        languageTag: String,
        onResult: (String) -> Unit,
        onStateChanged: (Boolean) -> Unit,
    ) {
        if (!isOfflineRecognitionAvailable()) {
            onResult("Offline speech recognition is not available on this device. You can type commands instead.")
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onResult("Microphone permission is needed for voice input. Tap the microphone again after granting it.")
            return
        }
        recognitionActive = true
        recognitionResult = onResult
        recognitionStateChanged = onStateChanged
        onStateChanged(true)
        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                    override fun onError(error: Int) {
                        finishRecognition(speechRecognitionErrorMessage(error))
                    }
                    override fun onResults(results: Bundle?) {
                        val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        finishRecognition(spoken ?: "I didn't catch that. Please try again.")
                    }
                })
                startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                })
            }
        } catch (_: SecurityException) {
            finishRecognition("Microphone permission is unavailable. You can type a command instead.")
        } catch (_: IllegalStateException) {
            finishRecognition("Offline voice input couldn't start. Check that the speech service is ready, or type your command.")
        }
    }

    private fun speechRecognitionErrorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            "The selected offline speech language isn't ready. Download its offline speech model in Android Settings, then try again."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Microphone permission is needed for voice input. Typed commands still work offline."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            "The microphone or speech service is busy. Close other voice apps and try again."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "The on-device speech service couldn't complete recognition. ZERO won't switch to an online recognizer; try again or type your command."
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        SpeechRecognizer.ERROR_AUDIO ->
            "I couldn't hear that clearly. Check the microphone and try again, or type your command."
        else -> "Offline speech recognition failed (error $error). Try again or type your command."
    }

    private fun finishRecognition(message: String) {
        if (!recognitionActive) return
        recognitionActive = false
        val currentRecognizer = recognizer
        recognizer = null
        currentRecognizer?.destroy()
        val result = recognitionResult
        val stateChanged = recognitionStateChanged
        recognitionResult = null
        recognitionStateChanged = null
        stateChanged?.invoke(false)
        result?.invoke(message)
    }

    override fun onDestroy() {
        finishRecognition("Voice input was stopped. Tap the microphone to try again.")
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onStop() {
        finishRecognition("Voice input was stopped when ZERO left the screen. Tap the microphone to try again.")
        super.onStop()
    }
}

private data class ChatLine(val fromZero: Boolean, val text: String)
private enum class Page { Home, History, Settings, Help, Privacy, Terms }

private fun isSpeechError(message: String): Boolean =
    listOf(
        "Offline speech",
        "The selected offline speech",
        "The on-device speech service",
        "Microphone permission",
        "I couldn't",
        "I didn't",
        "Voice input",
    ).any(message::startsWith)

private val chatSaver = listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<ChatLine>, String>(
    save = { lines -> lines.flatMap { listOf(it.fromZero.toString(), it.text) } },
    restore = { saved -> mutableStateListOf<ChatLine>().apply {
        saved.chunked(2).filter { it.size == 2 }.forEach { add(ChatLine(it[0].toBoolean(), it[1])) }
    } },
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ZeroApp(
    speak: (String) -> Unit,
    startListening: (String, (String) -> Unit, (Boolean) -> Unit) -> Unit,
    offlineSpeechAvailable: Boolean,
    speechOutputAvailable: Boolean,
) {
    val context = LocalContext.current
    val history = remember { CommandHistory(PreferencesHistoryStorage(context.applicationContext)) }
    var page by rememberSaveable { mutableStateOf(Page.Home) }
    var darkMode by rememberSaveable { mutableStateOf(false) }
    var speechOutput by rememberSaveable { mutableStateOf(true) }
    var speechLanguageTag by rememberSaveable {
        mutableStateOf(if (Locale.getDefault().language == "hi") "hi-IN" else "en-US")
    }
    var input by rememberSaveable { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var pendingFlashlightCommand by remember { mutableStateOf<String?>(null) }
    val lines = rememberSaveable(saver = chatSaver) { mutableStateListOf(ChatLine(true, "Hi, I'm ZERO. What can I do for you?")) }

    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening(speechLanguageTag, { spoken ->
            if (isSpeechError(spoken)) notice = spoken else {
                input = spoken
                notice = null
            }
        }, { isListening = it })
        else notice = "Microphone permission was denied. Typed commands still work offline."
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val command = pendingFlashlightCommand
        pendingFlashlightCommand = null
        if (granted && command != null) executeCommand(context, command, history, lines, speak, speechOutput) { notice = it }
        else if (!granted) notice = "Camera permission is required to control the flashlight."
    }
    val runCommand: (String) -> Unit = { command ->
        if (CommandParser.parse(command) is AssistantCommand.SetFlashlight &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingFlashlightCommand = command
            cameraPermission.launch(Manifest.permission.CAMERA)
        } else executeCommand(context, command, history, lines, speak, speechOutput) { notice = it }
    }

    MaterialTheme(colorScheme = if (darkMode) androidx.compose.material3.darkColorScheme(
        primary = Color(0xFF8AD3B7), background = Color(0xFF111916), surface = Color(0xFF1A2420), onSurface = Color(0xFFE3ECE7),
    ) else androidx.compose.material3.lightColorScheme(
        primary = Forest, background = Paper, surface = Color.White, onSurface = Ink,
    )) {
        val background = MaterialTheme.colorScheme.background
        Scaffold(containerColor = background, topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(32.dp).background(Forest, CircleShape), contentAlignment = Alignment.Center) {
                            Text("0", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(if (page == Page.Home) "ZERO" else page.name, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)
                    }
                },
                navigationIcon = { if (page != Page.Home) IconButton(onClick = { page = Page.Home }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (page == Page.Home) {
                        TextButton(onClick = { page = Page.History }) { Text("History") }
                        IconButton(onClick = { page = Page.Settings }) { Icon(Icons.Default.Settings, "Settings") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background),
            )
        }) { padding ->
            when (page) {
                Page.Home -> HomeScreen(
                    Modifier.padding(padding), lines, input, { input = it }, notice,
                    isListening,
                    onSend = {
                        val command = input.trim()
                        if (command.isNotEmpty()) {
                            input = ""
                            notice = null
                            runCommand(command)
                        }
                    },
                    onMic = {
                        if (!offlineSpeechAvailable) notice = "This device doesn't provide offline speech recognition. Typed commands remain available."
                        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            startListening(speechLanguageTag, { spoken ->
                                if (isSpeechError(spoken)) notice = spoken else {
                                    input = spoken
                                    notice = null
                                }
                            }, { isListening = it })
                        } else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onShortcut = { label ->
                        runCommand(if (label == "Torch") "torch on" else "open ${label.lowercase()}")
                    },
                    onNavigate = { page = it },
                )
                Page.History -> HistoryScreen(Modifier.padding(padding), history.recent(), onClear = { history.clear(); notice = "History cleared from this device." }, notice = notice)
                Page.Settings -> SettingsScreen(
                    Modifier.padding(padding), darkMode, { darkMode = it }, speechOutput, { speechOutput = it },
                    speechLanguageTag, { speechLanguageTag = it }, offlineSpeechAvailable, speechOutputAvailable, { page = it },
                )
                Page.Help -> TextPage(Modifier.padding(padding), "Help", "Try commands like:\n\n• Open YouTube / YouTube kholo\n• Open WhatsApp\n• Open Camera\n• Open Settings\n• Torch chalu karo / Torch band karo\n• Abhi kitne baje hain?\n\nCommands are understood on this phone. If an app isn't installed or an action isn't available, ZERO will tell you.")
                Page.Privacy -> TextPage(Modifier.padding(padding), "Privacy policy", "ZERO processes typed commands locally and has no internet permission, accounts, analytics, ads, or server. Command history is stored only in this app's private local preferences and can be cleared from History. ZERO does not save raw audio.\n\nVoice input is push-to-talk. After you grant microphone permission and tap Mic, ZERO uses Android's on-device speech-recognition API only; it does not fall back to a network recognizer. The speech service and its language models are provided and managed by Android or the device manufacturer. Recognition works offline only when that service and the selected language model are available. Microphone access stops when ZERO leaves the foreground.")
                Page.Terms -> TextPage(Modifier.padding(padding), "Terms", "ZERO is provided as-is for personal use. Actions depend on the apps and hardware available on your device. Voice recognition and text-to-speech quality vary by Android version, installed components, and downloaded language packs. ZERO cannot guarantee that every spoken command will be recognized or every external app will be available.")
            }
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    lines: List<ChatLine>,
    input: String,
    onInput: (String) -> Unit,
    notice: String?,
    isListening: Boolean,
    onSend: () -> Unit,
    onMic: () -> Unit,
    onShortcut: (String) -> Unit,
    onNavigate: (Page) -> Unit,
) {
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Your day, a little simpler.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text("Ask naturally. ZERO works on this phone.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("YouTube", "WhatsApp", "Camera", "Torch").forEach { item ->
                Button(onClick = { onShortcut(item) }, colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Forest), contentPadding = ButtonDefaults.ContentPadding) {
                    Text(item, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), reverseLayout = true) {
            items(lines.asReversed()) { line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (line.fromZero) Arrangement.Start else Arrangement.End) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = if (line.fromZero) MaterialTheme.colorScheme.surface else Mint),
                    ) {
                        Text(line.text, Modifier.padding(horizontal = 14.dp, vertical = 11.dp), color = if (line.fromZero) MaterialTheme.colorScheme.onSurface else Forest)
                    }
                }
            }
        }
        if (notice != null) Text(notice, Modifier.fillMaxWidth().padding(bottom = 6.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = input,
            onValueChange = onInput,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Ask ZERO anything…") },
            shape = RoundedCornerShape(18.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            trailingIcon = {
                    Row {
                    IconButton(onClick = onMic, enabled = !isListening) {
                        Text(if (isListening) "Listening…" else "Mic", color = if (isListening) MaterialTheme.colorScheme.error else Forest, fontSize = 12.sp)
                    }
                    IconButton(onClick = onSend, enabled = input.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, "Send command", tint = Forest) }
                }
            },
        )
        Row(Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onNavigate(Page.Help) }) { Text("Help") }
            TextButton(onClick = { onNavigate(Page.Privacy) }) { Text("Privacy") }
            TextButton(onClick = { onNavigate(Page.Terms) }) { Text("Terms") }
        }
    }
}

@Composable
private fun HistoryScreen(modifier: Modifier, entries: List<HistoryEntry>, onClear: () -> Unit, notice: String?) {
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Recent commands", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onClear, enabled = entries.isNotEmpty()) { Text("Clear history") }
        }
        if (notice != null) Text(notice, color = Forest)
        if (entries.isEmpty()) Text("Your recent commands will appear here.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries) { entry ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(entry.command, fontWeight = FontWeight.Medium)
                        Text(entry.response, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    darkMode: Boolean,
    onDarkMode: (Boolean) -> Unit,
    speechOutput: Boolean,
    onSpeechOutput: (Boolean) -> Unit,
    speechLanguageTag: String,
    onSpeechLanguage: (String) -> Unit,
    offlineSpeechAvailable: Boolean,
    speechOutputAvailable: Boolean,
    onNavigate: (Page) -> Unit,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Preferences", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        SettingSwitch("Dark theme", "Use a darker interface", darkMode, onDarkMode)
        SettingSwitch("Speak responses", if (speechOutputAvailable) "Uses an installed local voice" else "No suitable local voice is available", speechOutput, onSpeechOutput, enabled = speechOutputAvailable)
        Text("Voice input", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = speechLanguageTag == "en-US", onClick = { onSpeechLanguage("en-US") }, label = { Text("English") })
            FilterChip(selected = speechLanguageTag == "hi-IN", onClick = { onSpeechLanguage("hi-IN") }, label = { Text("Hindi") })
        }
        Text(
            if (offlineSpeechAvailable) "Push-to-talk uses Android's on-device recognizer for the selected language. The matching offline speech model must be installed on the phone."
            else "This device does not report an on-device speech recognizer. Typed commands are always available.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Text("If voice input says the language is unavailable, open Android Settings and search for “offline speech recognition” or “speech services” to install the language model. ZERO does not download models or use an online fallback.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text("Offline voice output", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text("To install a local Hindi or English voice, open Android Settings, search for “Text-to-speech output,” choose the preferred engine, and install its voice data. ZERO only selects voices that report they do not require a network connection.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text("Wake word", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text("“Hey ZERO” listening is not enabled. No compatible wake-word engine or model is bundled, so ZERO never listens in the background.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        TextButton(onClick = { onNavigate(Page.Privacy) }) { Text("Privacy policy") }
        TextButton(onClick = { onNavigate(Page.Terms) }) { Text("Terms") }
    }
}

@Composable
private fun SettingSwitch(label: String, supporting: String, checked: Boolean, onChecked: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium)
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
        }
        Switch(checked, onCheckedChange = onChecked, enabled = enabled)
    }
}

@Composable
private fun TextPage(modifier: Modifier, title: String, body: String) {
    Column(modifier.fillMaxSize().padding(22.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, lineHeight = 25.sp)
    }
}

private fun executeCommand(
    context: Context,
    input: String,
    history: CommandHistory,
    lines: androidx.compose.runtime.snapshots.SnapshotStateList<ChatLine>,
    speak: (String) -> Unit,
    speechOutput: Boolean,
    onError: (String) -> Unit,
) {
    lines.add(ChatLine(false, input))
    val command = CommandParser.parse(input)
    val response = when (command) {
        is AssistantCommand.OpenApp -> openApp(context, command.app)
        is AssistantCommand.SetFlashlight -> setFlashlight(context, command.enabled)
        AssistantCommand.TellTime -> "It's ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())}."
        AssistantCommand.Help -> "Try opening YouTube, WhatsApp, Camera or Settings; ask for the time; or say torch chalu karo / torch band karo."
        AssistantCommand.Unsupported -> "I don't know that command yet. Try “open YouTube”, “torch chalu karo”, or “abhi kitne baje hain?”."
    }
    lines.add(ChatLine(true, response))
    history.add(input, response)
    if (speechOutput) speak(response)
    if (response.startsWith("I couldn't") || response.startsWith("That app") || response.startsWith("This device")) onError(response)
}

private fun openApp(context: Context, app: AppTarget): String {
    val intent = when (app) {
        AppTarget.Settings -> Intent(Settings.ACTION_SETTINGS)
        AppTarget.Camera -> Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        AppTarget.YouTube -> launcherIntent(context, "com.google.android.youtube")
        AppTarget.WhatsApp -> launcherIntent(context, "com.whatsapp")
    } ?: return "That app isn't available on this device."
    if (intent.resolveActivity(context.packageManager) == null) return "That app isn't available on this device."

    return try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        "Opening ${app.name}."
    } catch (_: ActivityNotFoundException) {
        "That app isn't available on this device."
    } catch (_: SecurityException) {
        "I couldn't open ${app.name} because Android denied the request."
    }
}

private fun launcherIntent(context: Context, packageName: String): Intent? =
    context.packageManager.getLaunchIntentForPackage(packageName)

private fun setFlashlight(context: Context, enabled: Boolean): String {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
        return "Camera permission is needed to control the flashlight. Use the Torch shortcut to grant it."
    }

    val cameraManager = context.getSystemService(CameraManager::class.java)
        ?: return "This device's camera service is unavailable."
    return try {
        val cameraId = cameraManager.cameraIdList.firstOrNull { cameraManager.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            ?: return "This device doesn't have a flashlight."
        cameraManager.setTorchMode(cameraId, enabled)
        if (enabled) "Flashlight is on." else "Flashlight is off."
    } catch (_: SecurityException) {
        "I couldn't control the flashlight because camera access was denied."
    } catch (_: CameraAccessException) {
        "I couldn't control the flashlight because camera hardware is busy or unavailable."
    } catch (_: IllegalArgumentException) {
        "This device doesn't support flashlight control."
    }
}