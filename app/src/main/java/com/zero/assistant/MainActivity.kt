package com.zero.assistant

import android.Manifest
import android.os.Build
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.content.IntentFilter
import android.provider.MediaStore
import android.provider.Settings
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.core.content.ContextCompat
import com.zero.assistant.core.AppTarget
import com.zero.assistant.core.AssistantCommand
import com.zero.assistant.core.BatteryAlertSettings
import com.zero.assistant.core.LocalNote
import com.zero.assistant.core.NoteRepository
import com.zero.assistant.core.ShortcutActionType
import com.zero.assistant.core.ShortcutRepository
import com.zero.assistant.core.UserShortcut
import com.zero.assistant.core.CommandHistory
import com.zero.assistant.core.CommandParser
import com.zero.assistant.core.HistoryEntry
import com.zero.assistant.core.OfflineRecognitionState
import com.zero.assistant.core.OfflineRecognitionStateMachine
import com.zero.assistant.core.PlannerReminder
import com.zero.assistant.core.SpeechLanguage
import com.zero.assistant.core.SpeechOutputController
import com.zero.assistant.core.SpeechOutputEngine
import com.zero.assistant.core.SpeechOutputStatus
import com.zero.assistant.core.OfflineTtsVoice
import com.zero.assistant.core.WakeWord
import com.zero.assistant.data.PreferencesHistoryStorage
import com.zero.assistant.data.BatteryAlertManager
import com.zero.assistant.data.BatteryStatus
import com.zero.assistant.data.ProductivityPreferences
import com.zero.assistant.data.PlannerPreferences
import com.zero.assistant.data.ReminderScheduler
import com.zero.assistant.data.SpeechModelManager
import com.zero.assistant.data.WakeWordService
import com.zero.assistant.data.WakeWordSettings
import com.zero.assistant.core.ReminderRepository
import java.text.DateFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

private val Forest = Color.White
private val Mint = Color(0xFF202020)
private val Paper = Color.Black
private val Ink = Color.White

class MainActivity : ComponentActivity() {
    private var speechOutputController: SpeechOutputController? = null
    private val speechOutputStatus = mutableStateOf<SpeechOutputStatus>(SpeechOutputStatus.Initializing)
    private val offlineTtsVoices = mutableStateOf<List<OfflineTtsVoice>>(emptyList())
    private lateinit var speechModelManager: SpeechModelManager
    private lateinit var batteryAlertManager: BatteryAlertManager
    private val batteryStatus = mutableStateOf(BatteryStatus(0, false))
    private val pendingVoiceCommand = mutableStateOf<String?>(null)
    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null
    private var recognitionActive = false
    private var recognitionResult: ((String) -> Unit)? = null
    private var recognitionStateChanged: ((Boolean) -> Unit)? = null
    private lateinit var recognitionStateMachine: OfflineRecognitionStateMachine
    private val offlineRecognitionState = mutableStateOf<OfflineRecognitionState>(OfflineRecognitionState.Idle)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingVoiceCommand.value = intent.getStringExtra(WakeWordSettings.EXTRA_COMMAND)
        recognitionStateMachine = OfflineRecognitionStateMachine { state ->
            runOnUiThread { offlineRecognitionState.value = state }
        }
        val initialLanguage = if (Locale.getDefault().language == "hi") SpeechLanguage.Hindi else SpeechLanguage.English
        speechOutputController = SpeechOutputController(AndroidTextToSpeechEngine(applicationContext)) { status ->
            runOnUiThread {
                speechOutputStatus.value = status
                offlineTtsVoices.value = speechOutputController?.availableOfflineVoices().orEmpty()
            }
        }
        speechOutputController?.setLanguage(initialLanguage)
        speechOutputController?.initialize()
        speechModelManager = SpeechModelManager(this)
        speechModelManager.ensureBundledModel()
        batteryAlertManager = BatteryAlertManager(this)
        batteryAlertManager.synchronizeWorker()
        setContent {
            ZeroApp(
                modelManager = speechModelManager,
                speak = { message -> speechOutputController?.speak(message) },
                setSpeechLanguage = { language -> speechOutputController?.setLanguage(language) },
                testSpeech = { language -> speechOutputController?.testSpeech(language) },
                stopSpeech = { speechOutputController?.stop() },
                startListening = { onResult, onStateChanged ->
                    startOfflineRecognition(onResult, onStateChanged)
                },
                stopListening = { stopOfflineRecognition() },
                recognitionState = offlineRecognitionState.value,
                batteryAlertManager = batteryAlertManager,
                batteryStatus = batteryStatus.value,
                speechOutputStatus = speechOutputStatus.value,
                offlineTtsVoices = offlineTtsVoices.value,
                selectTtsVoice = { voiceId -> speechOutputController?.selectVoice(voiceId) == true },
                installTtsVoices = {
                    val installIntent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                    runCatching {
                        if (installIntent.resolveActivity(packageManager) == null) "No Android text-to-speech voice installer is available."
                        else {
                            startActivity(installIntent)
                            "Opened Android's voice library. Installed offline voices will appear here when you return."
                        }
                    }.getOrElse { "Android's text-to-speech voice installer could not be opened." }
                },
                voiceCommand = pendingVoiceCommand.value,
                onVoiceCommandConsumed = { pendingVoiceCommand.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingVoiceCommand.value = intent.getStringExtra(WakeWordSettings.EXTRA_COMMAND)
    }

    private fun startOfflineRecognition(
        onResult: (String) -> Unit,
        onStateChanged: (Boolean) -> Unit,
    ) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onResult("Microphone permission is needed for voice input. Tap the microphone again after granting it.")
            return
        }
        recognitionActive = true
        recognitionStateMachine.begin(speechModelManager.activeModelId.value)
        recognitionResult = onResult
        recognitionStateChanged = onStateChanged
        onStateChanged(true)
        speechModelManager.loadActiveModel { modelResult ->
            if (!recognitionActive) return@loadActiveModel
            modelResult.fold(
                onSuccess = { model ->
                    try {
                        val activeModelId = speechModelManager.activeModelId.value
                            ?: throw IllegalStateException("No speech model is installed")
                        val nextRecognizer = Recognizer(model, SAMPLE_RATE_HZ)
                        recognizer = nextRecognizer
                        val nextService = SpeechService(nextRecognizer, SAMPLE_RATE_HZ)
                        speechService = nextService
                        recognitionStateMachine.listening(activeModelId)
                        val started = nextService.startListening(object : RecognitionListener {
                            override fun onPartialResult(hypothesis: String?) = Unit
                            override fun onResult(hypothesis: String?) = finishWithHypothesis(hypothesis)
                            override fun onFinalResult(hypothesis: String?) = finishWithHypothesis(hypothesis)
                            override fun onError(exception: Exception?) {
                                finishRecognition(
                                    "Offline speech recognition failed: ${exception?.message ?: "microphone error"}. You can type your command instead.",
                                )
                            }
                            override fun onTimeout() {
                                finishRecognition("I didn't catch that. Please try again, or type your command.")
                            }
                        })
                        if (!started) finishRecognition("Offline speech recognition is already active. Try again shortly.")
                    } catch (_: SecurityException) {
                        finishRecognition("Microphone permission is unavailable. You can type a command instead.")
                    } catch (failure: Exception) {
                        finishRecognition(
                            "Offline speech recognition couldn't start: ${failure.message ?: "audio input unavailable"}. You can type your command instead.",
                        )
                    } catch (_: LinkageError) {
                        finishRecognition("Offline speech engine failed to load. Reinstall ZERO or type your command instead.")
                    }
                },
                onFailure = { failure ->
                    finishRecognition(
                        "Offline speech model couldn't load: ${failure.message ?: "model unavailable"}. Open Speech Models in Settings or type your command.",
                    )
                },
            )
        }
    }

    private fun finishWithHypothesis(hypothesis: String?) {
        val text = runCatching { JSONObject(hypothesis.orEmpty()).optString("text").trim() }.getOrDefault("")
        if (text.isNotBlank()) finishRecognition(text)
        else finishRecognition("I didn't catch that. Please try again, or type your command.")
    }

    private fun finishRecognition(message: String) {
        if (!recognitionActive) return
        recognitionActive = false
        val currentService = speechService
        speechService = null
        if (currentService != null) {
            runCatching { currentService.cancel() }
            runCatching { currentService.shutdown() }
        }
        recognizer?.close()
        recognizer = null
        val result = recognitionResult
        val stateChanged = recognitionStateChanged
        recognitionResult = null
        recognitionStateChanged = null
        stateChanged?.invoke(false)
        if (message.startsWith("Voice input cancelled")) recognitionStateMachine.cancel()
        else if (isSpeechError(message) || message.startsWith("I didn't")) recognitionStateMachine.fail(message)
        else recognitionStateMachine.result(message)
        result?.invoke(message)
    }

    private fun stopOfflineRecognition() {
        val service = speechService
        if (recognitionActive && service != null) {
            runCatching { service.cancel() }
            finishRecognition("Voice input cancelled. You can type your command instead.")
        } else if (recognitionActive) {
            finishRecognition("Voice input cancelled. You can type your command instead.")
        }
    }

    override fun onDestroy() {
        finishRecognition("Voice input was stopped. Tap the microphone to try again.")
        recognizer?.close()
        speechOutputController?.close()
        speechOutputController = null
        speechModelManager.close()
        super.onDestroy()
    }

    override fun onStop() {
        finishRecognition("Voice input was stopped when ZERO left the screen. Tap the microphone to try again.")
        batteryAlertManager.stopObserving(this)
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        if (::batteryAlertManager.isInitialized) {
            batteryAlertManager.observe(this) { status -> runOnUiThread { batteryStatus.value = status } }
            batteryStatus.value = batteryAlertManager.status()
        }
        if (WakeWordSettings.enabled(this) && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startWakeWordService()
        }
    }

    override fun onResume() {
        super.onResume()
        offlineTtsVoices.value = speechOutputController?.availableOfflineVoices().orEmpty()
    }

    private fun startWakeWordService() {
        runCatching {
            ContextCompat.startForegroundService(
                this,
                Intent(this, WakeWordService::class.java).setAction(WakeWordSettings.ACTION_START),
            )
        }.onFailure { WakeWordSettings.setActive(this, false, it.message ?: "Android could not start background listening.") }
    }

    private fun stopWakeWordService() {
        runCatching {
            startService(Intent(this, WakeWordService::class.java).setAction(WakeWordSettings.ACTION_STOP))
        }.onFailure {
            WakeWordSettings.setEnabled(this, false)
            stopService(Intent(this, WakeWordService::class.java))
        }
    }

    private companion object {
        const val SAMPLE_RATE_HZ = 16_000f
    }
}

private class AndroidTextToSpeechEngine(context: Context) : SpeechOutputEngine {
    private val appContext = context.applicationContext
    private var textToSpeech: TextToSpeech? = null
    private var initializedCallback: ((Boolean) -> Unit)? = null

    override fun initialize(onInitialized: (Boolean) -> Unit) {
        initializedCallback = onInitialized
        try {
            textToSpeech = TextToSpeech(appContext) { result ->
                val callback = initializedCallback
                initializedCallback = null
                callback?.invoke(result == TextToSpeech.SUCCESS)
            }
        } catch (_: Exception) {
            initializedCallback = null
            onInitialized(false)
        }
    }

    override fun selectOfflineVoice(languageTag: String): Boolean {
        val engine = textToSpeech ?: return false
        val requested = Locale.forLanguageTag(languageTag)
        val voice = engine.voices.orEmpty()
            .filter { !it.isNetworkConnectionRequired && it.locale.language == requested.language }
            .sortedByDescending { it.locale.country == requested.country }
            .firstOrNull() ?: return false
        return runCatching {
            engine.voice = voice
            true
        }.getOrDefault(false)
    }

    override fun availableOfflineVoices(): List<OfflineTtsVoice> = textToSpeech?.voices.orEmpty()
        .filterNot { it.isNetworkConnectionRequired }
        .map { voice ->
            OfflineTtsVoice(
                id = voice.name,
                displayName = voice.name.replace('_', ' '),
                languageTag = voice.locale.toLanguageTag(),
                languageLabel = voice.locale.getDisplayName(voice.locale),
                quality = voice.quality,
            )
        }
        .distinctBy { it.id }
        .sortedWith(compareBy<OfflineTtsVoice> { it.languageLabel }.thenBy { it.displayName })

    override fun selectVoice(voiceId: String): Boolean {
        val engine = textToSpeech ?: return false
        val voice = engine.voices.orEmpty().firstOrNull { it.name == voiceId && !it.isNetworkConnectionRequired } ?: return false
        return runCatching {
            engine.voice = voice
            true
        }.getOrDefault(false)
    }

    override fun speak(text: String): Boolean =
        textToSpeech?.speak(text, TextToSpeech.QUEUE_ADD, null, "zero-${System.nanoTime()}") == TextToSpeech.SUCCESS

    override fun stop() {
        textToSpeech?.stop()
    }

    override fun shutdown() {
        initializedCallback = null
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}

private data class ChatLine(val fromZero: Boolean, val text: String)
internal enum class Page { Home, History, Settings, SpeechModels, Planner, BatteryAlerts, Notes, MyShortcuts, Dashboard, Help, Privacy, Terms }

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
    modelManager: SpeechModelManager,
    speak: (String) -> Unit,
    setSpeechLanguage: (SpeechLanguage) -> Unit,
    testSpeech: (SpeechLanguage) -> Unit,
    stopSpeech: () -> Unit,
    stopListening: () -> Unit,
    startListening: ((String) -> Unit, (Boolean) -> Unit) -> Unit,
    recognitionState: OfflineRecognitionState,
    batteryAlertManager: BatteryAlertManager,
    batteryStatus: BatteryStatus,
    speechOutputStatus: SpeechOutputStatus,
    offlineTtsVoices: List<OfflineTtsVoice>,
    selectTtsVoice: (String) -> Boolean,
    installTtsVoices: () -> String,
    voiceCommand: String?,
    onVoiceCommandConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val history = remember { CommandHistory(PreferencesHistoryStorage(context.applicationContext)) }
    val reminderRepository = remember { ReminderRepository(PlannerPreferences(context.applicationContext)) }
    val reminderScheduler = remember { ReminderScheduler(context.applicationContext, reminderRepository) }
    val productivityPreferences = remember { ProductivityPreferences(context.applicationContext) }
    val wakePreferences = remember { context.getSharedPreferences("zero_wake_word", Context.MODE_PRIVATE) }
    val ttsPreferences = remember { context.getSharedPreferences("zero_tts", Context.MODE_PRIVATE) }
    val noteRepository = remember { NoteRepository(productivityPreferences) }
    val shortcutRepository = remember { ShortcutRepository(productivityPreferences) }
    val activeModelId by modelManager.activeModelId.collectAsState()
    LaunchedEffect(reminderScheduler) { reminderScheduler.rescheduleAll() }
    var page by rememberSaveable { mutableStateOf(Page.Home) }
    var speechOutput by rememberSaveable { mutableStateOf(true) }
    var speechLanguageTag by rememberSaveable {
        mutableStateOf(if (Locale.getDefault().language == "hi") "hi-IN" else "en-US")
    }
    var wakeWordEnabled by remember { mutableStateOf(WakeWordSettings.enabled(context)) }
    var wakeWordActive by remember { mutableStateOf(WakeWordSettings.active(context)) }
    var wakeWordPhrase by remember { mutableStateOf(wakePreferences.getString("phrase", WakeWord.DEFAULT_PHRASE) ?: WakeWord.DEFAULT_PHRASE) }
    var wakeWordError by remember { mutableStateOf(WakeWordSettings.error(context)) }
    var selectedTtsVoiceId by remember { mutableStateOf(ttsPreferences.getString("voice_id", null)) }
    var input by rememberSaveable { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var showIntro by remember { mutableStateOf(true) }
    var pendingFlashlightCommand by remember { mutableStateOf<String?>(null) }
    var pendingShortcutContinuation by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingCallNumber by remember { mutableStateOf<String?>(null) }
    var pendingWhatsAppDraft by remember { mutableStateOf<AssistantCommand.DraftWhatsAppMessage?>(null) }
    var plannerDraftTitle by remember { mutableStateOf<String?>(null) }
    var plannerVoiceCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var notificationsGranted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    var pendingWakeStart by remember { mutableStateOf(false) }
    val lines = rememberSaveable(saver = chatSaver) { mutableStateListOf(ChatLine(true, "Hi, I'm ZERO. What can I do for you?")) }
    lateinit var dispatchCommand: (String, (() -> Unit)?) -> Unit

    DisposableEffect(wakePreferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "enabled" -> wakeWordEnabled = WakeWordSettings.enabled(context)
                "active" -> wakeWordActive = WakeWordSettings.active(context)
                "error" -> wakeWordError = WakeWordSettings.error(context)
            }
        }
        wakePreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { wakePreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun startBackgroundListening() {
        WakeWordSettings.setEnabled(context, true)
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, WakeWordService::class.java).setAction(WakeWordSettings.ACTION_START),
            )
        } catch (failure: Exception) {
            WakeWordSettings.setActive(context, false, failure.message ?: "Android could not start the microphone service.")
            notice = WakeWordSettings.error(context)
        }
    }

    fun stopBackgroundListening() {
        WakeWordSettings.setEnabled(context, false)
        runCatching { context.startService(Intent(context, WakeWordService::class.java).setAction(WakeWordSettings.ACTION_STOP)) }
    }

    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening({ spoken ->
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
        val continuation = pendingShortcutContinuation
        pendingShortcutContinuation = null
        continuation?.invoke()
    }
    val callPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val number = pendingCallNumber ?: return@rememberLauncherForActivityResult
        pendingCallNumber = null
        executeCommand(context, "call $number", history, lines, speak, speechOutput, allowDirectCall = granted) { notice = it }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsGranted = granted
        if (granted) reminderScheduler.rescheduleAll()
        if (pendingWakeStart) {
            pendingWakeStart = false
            if (granted) startBackgroundListening()
            else {
                WakeWordSettings.setEnabled(context, false)
                notice = "Notification permission is required for the persistent microphone controls. Background listening remains off."
            }
        } else if (!granted) notice = "Notifications are disabled. Reminders remain saved but Android won't display them."
    }
    fun requestWakeWordStart() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingWakeStart = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else startBackgroundListening()
    }
    val plannerMicrophonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val callback = plannerVoiceCallback
        plannerVoiceCallback = null
        if (granted && callback != null) startListening({ callback(it) }, { isListening = it })
        else if (!granted) notice = "Microphone permission was denied. You can type the reminder title instead."
    }
    val wakeWordPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            requestWakeWordStart()
            page = Page.Home
        } else {
            notice = "Microphone permission was denied. Wake-word listening remains off."
        }
    }
    fun showResult(command: String, response: String) {
        lines.add(ChatLine(false, command))
        lines.add(ChatLine(true, response))
        history.add(command, response)
        if (speechOutput) speak(response)
    }

    dispatchCommand = { command, onComplete ->
        when (val parsed = CommandParser.parse(command)) {
            is AssistantCommand.CallNumber -> pendingCallNumber = parsed.number
            is AssistantCommand.SearchYouTube -> {
                val response = openYouTubeSearch(context, parsed.query)
                showResult(command, response)
                onComplete?.invoke()
            }
            is AssistantCommand.DraftWhatsAppMessage -> pendingWhatsAppDraft = parsed
            is AssistantCommand.RunShortcut -> {
                val shortcut = shortcutRepository.all().firstOrNull { it.enabled && it.name.equals(parsed.name, ignoreCase = true) }
                if (shortcut == null) {
                    showResult(command, "I couldn't find an enabled shortcut named ${parsed.name}.")
                    onComplete?.invoke()
                } else executeShortcut(shortcut, context, dispatchCommand, { message -> showResult(command, message) }, { page = it }, onComplete ?: {})
            }
            is AssistantCommand.SaveNote -> runCatching { noteRepository.save(LocalNote(text = parsed.text)) }
                .onSuccess { showResult(command, "Saved that note on this device."); onComplete?.invoke() }
                .onFailure { showResult(command, it.message ?: "The note could not be saved."); onComplete?.invoke() }
            is AssistantCommand.DraftReminder -> {
                plannerDraftTitle = parsed.title
                page = Page.Planner
                showResult(command, "Added the reminder title to the planner. Set its date and time to save it.")
                onComplete?.invoke()
            }
            AssistantCommand.ShowPlanner -> { page = Page.Planner; showResult(command, "Showing your planner."); onComplete?.invoke() }
            AssistantCommand.ShowNotes -> { page = Page.Notes; showResult(command, "Showing your notes."); onComplete?.invoke() }
            is AssistantCommand.SetFlashlight -> if (
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
            ) {
                pendingFlashlightCommand = command
                pendingShortcutContinuation = onComplete
                cameraPermission.launch(Manifest.permission.CAMERA)
            } else {
                executeCommand(context, command, history, lines, speak, speechOutput) { notice = it }
                onComplete?.invoke()
            }
            else -> {
                executeCommand(context, command, history, lines, speak, speechOutput) { notice = it }
                onComplete?.invoke()
            }
        }
    }
    val runCommand: (String) -> Unit = { dispatchCommand(it, null) }
    LaunchedEffect(Unit) {
        delay(1_000)
        showIntro = false
    }
    LaunchedEffect(voiceCommand) {
        voiceCommand?.let {
            page = Page.Home
            dispatchCommand(it, null)
            onVoiceCommandConsumed()
        }
    }
    LaunchedEffect(speechOutputStatus, selectedTtsVoiceId) {
        val selectedVoiceId = selectedTtsVoiceId
        if (speechOutputStatus is SpeechOutputStatus.Ready && selectedVoiceId != null) {
            if (offlineTtsVoices.any { it.id == selectedVoiceId } && !selectTtsVoice(selectedVoiceId)) {
                selectedTtsVoiceId = null
                ttsPreferences.edit().remove("voice_id").apply()
            }
        }
    }

    MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme(
        primary = Color.White,
        onPrimary = Color.Black,
        secondary = Color(0xFFBDBDBD),
        background = Paper,
        surface = Color(0xFF111111),
        onSurface = Ink,
        error = Color(0xFFFF6B6B),
    )) {
        if (showIntro) {
            IntroScreen()
        } else {
        val background = MaterialTheme.colorScheme.background
        Scaffold(containerColor = background, topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (page) {
                            Page.Home -> "ZERO-Assist 17.7.07"
                            Page.SpeechModels -> "Voice Library"
                            Page.Planner -> "Planner"
                            Page.BatteryAlerts -> "Battery Alerts"
                            Page.Notes -> "Notes"
                            Page.MyShortcuts -> "My Shortcuts"
                            Page.Dashboard -> "Privacy & system"
                            else -> page.name
                        },
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.sp,
                    )
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
                    recognitionState, isListening || wakeWordActive,
                    batteryStatus = batteryStatus,
                    reminders = reminderRepository.all(),
                    wakeWordEnabled = wakeWordEnabled,
                    onSend = {
                        val command = input.trim()
                        if (command.isNotEmpty()) {
                            input = ""
                            notice = null
                            runCommand(command)
                        }
                    },
                    onMic = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            startListening({ spoken ->
                                if (isSpeechError(spoken)) notice = spoken else {
                                    input = spoken
                                    notice = null
                                }
                            }, { isListening = it })
                        } else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onStopListening = {
                        if (wakeWordEnabled) stopBackgroundListening()
                        stopListening()
                    },
                    onShortcut = { label ->
                        when (label) {
                            "Wi-Fi" -> notice = openSettingsTarget(context, "wifi")
                            "Bluetooth" -> notice = openSettingsTarget(context, "bluetooth")
                            "Call" -> runCatching {
                                context.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                notice = "Opened the dialer. Review the number before placing a call."
                            }.onFailure { notice = "The Android dialer is unavailable." }
                            else -> runCommand(when (label) {
                                "Torch on" -> "torch on"
                                "Torch off" -> "torch off"
                                "Battery" -> "battery status"
                                else -> "open ${label.lowercase()}"
                            })
                        }
                    },
                    onNavigate = { page = it },
                )
                Page.History -> HistoryScreen(Modifier.padding(padding), history.recent(), onClear = { history.clear(); notice = "History cleared from this device." }, notice = notice)
                Page.Settings -> SettingsScreen(
                    Modifier.padding(padding), speechOutput, { speechOutput = it },
                    speechLanguageTag, {
                        speechLanguageTag = it
                        selectedTtsVoiceId = null
                        ttsPreferences.edit().remove("voice_id").apply()
                        setSpeechLanguage(if (it == "hi-IN") SpeechLanguage.Hindi else SpeechLanguage.English)
                    }, activeModelId, speechOutputStatus, { language ->
                        speechLanguageTag = language.tag
                        setSpeechLanguage(language)
                        testSpeech(language)
                    }, stopSpeech,
                    wakeWordEnabled = wakeWordEnabled,
                    wakeWordActive = wakeWordActive,
                    wakeWordError = wakeWordError,
                    wakeWordPhrase = wakeWordPhrase,
                    onWakeWordPhrase = {
                        wakeWordPhrase = it
                        WakeWordSettings.setPhrase(context, it)
                    },
                    onWakeWordEnabled = { enabled ->
                        if (enabled && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            wakeWordPermission.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            if (enabled) requestWakeWordStart() else stopBackgroundListening()
                            if (!enabled && isListening) stopListening()
                            if (enabled) page = Page.Home
                        }
                    },
                    onNavigate = { page = it },
                )
                Page.SpeechModels -> SpeechModelsScreen(
                    modifier = Modifier.padding(padding),
                    manager = modelManager,
                    ttsVoices = offlineTtsVoices,
                    selectedTtsVoiceId = selectedTtsVoiceId,
                    onSelectTtsVoice = { voiceId ->
                        if (selectTtsVoice(voiceId)) {
                            selectedTtsVoiceId = voiceId
                            ttsPreferences.edit().putString("voice_id", voiceId).apply()
                            true
                        } else false
                    },
                    onInstallTtsVoices = installTtsVoices,
                )
                Page.Planner -> PlannerScreen(
                    modifier = Modifier.padding(padding),
                    initialTitle = plannerDraftTitle,
                    onInitialTitleConsumed = { plannerDraftTitle = null },
                    repository = reminderRepository,
                    scheduler = reminderScheduler,
                    notificationsGranted = notificationsGranted,
                    onRequestNotifications = {
                        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else notificationsGranted = true
                    },
                    onCreateFromVoice = { fillTitle ->
                        plannerVoiceCallback = fillTitle
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            plannerVoiceCallback = null
                            startListening({ fillTitle(it) }, { isListening = it })
                        } else plannerMicrophonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                )
                Page.BatteryAlerts -> BatteryAlertsScreen(
                    modifier = Modifier.padding(padding),
                    status = batteryStatus,
                    settings = batteryAlertManager.settings(),
                    notificationsGranted = notificationsGranted,
                    onSettingsChanged = batteryAlertManager::updateSettings,
                    onRequestNotifications = {
                        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else notificationsGranted = true
                    },
                )
                Page.Notes -> NotesScreen(Modifier.padding(padding), noteRepository)
                Page.MyShortcuts -> MyShortcutsScreen(Modifier.padding(padding), shortcutRepository) { shortcut ->
                    executeShortcut(shortcut, context, dispatchCommand, { message ->
                        lines.add(ChatLine(true, message))
                        if (speechOutput) speak(message)
                    }, { destination ->
                        page = destination
                    }) {}
                }
                Page.Dashboard -> PrivacyDashboardScreen(
                    modifier = Modifier.padding(padding),
                    modelManager = modelManager,
                    activeModelId = activeModelId,
                    microphoneGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
                    cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
                    callGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED,
                    notificationsGranted = notificationsGranted,
                    listening = isListening,
                    batteryStatus = batteryStatus,
                    historyCount = history.recent().size,
                    reminderCount = reminderRepository.all().size,
                    noteCount = noteRepository.all().size,
                    shortcutCount = shortcutRepository.all().count { it.enabled },
                    onClearHistory = { history.clear(); notice = "History cleared from this device." },
                    onOpenAppSettings = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                        }.onFailure { notice = "Android app settings could not be opened." }
                    },
                    onNavigate = { page = it },
                )
                Page.Help -> TextPage(Modifier.padding(padding), "Help", "Try commands like:\n\n• Open YouTube / YouTube kholo\n• Torch chalu karo / Torch band karo\n• Abhi kitne baje hain? / battery status\n• note buy milk\n• remind me to call Sam, then set its date and time in Planner\n• planner / notes\n• run Morning Routine\n• call followed by a phone number (ZERO asks you to confirm)\n\nVoice transcripts appear in the command field for review. Unsupported or ambiguous actions remain typed-only or ask for clarification.")
                Page.Privacy -> TextPage(Modifier.padding(padding), "Privacy policy", "Typed commands, command execution, history, speech, notes, reminders, shortcuts, and model inference are processed on this device. ZERO does not upload commands, audio, notes, reminders, or history and has no accounts, cloud AI, analytics, or ads. Local records use app-private storage and can be removed in-app. ZERO does not retain raw audio.\n\nMicrophone capture starts after you tap Mic or explicitly enable wake listening and grant permission. Wake listening is active only while ZERO is in the foreground. Recognition uses the selected local Vosk model; it does not use an online recognizer. The Hindi model is bundled. English model downloads use the fixed HTTPS URL and checksum in ZERO's catalog, then remain in private app storage. Internet access is only for those model downloads. Microphone capture stops when you cancel recognition or ZERO leaves the foreground.\n\nReminders and battery alerts are scheduled locally. Android may defer notifications, and force-stopping ZERO prevents delivery until it is opened again. Text-to-speech uses an Android voice that reports it does not require a network connection. Voice packages are managed by Android and are not part of ZERO's recognition-model store.")
                Page.Terms -> TextPage(Modifier.padding(padding), "Terms", "ZERO is provided as-is for personal use. Recognition is processed locally using the selected compatible Vosk model. Speech accuracy depends on language, microphone, device performance, and environment; automatic Hinglish recognition is not promised. Android TTS must have a compatible local voice installed. Reminder delivery is inexact and can be delayed or suppressed by Android power management, notification permission, or force-stop. Battery alerts are periodically checked and are not guaranteed after force-stop. External app launches, flashlight hardware, and call placement depend on the device. ZERO confirms the target number before direct call permission; if permission or calling is unavailable, it opens the dialer for review.")
            }
        }
        pendingCallNumber?.let { number ->
            AlertDialog(
            onDismissRequest = { pendingCallNumber = null },
                title = { Text("Confirm call") },
                text = { Text("ZERO is ready to call $number. Confirm that this is the intended number.") },
                confirmButton = {
                    TextButton(onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                            pendingCallNumber = null
                            executeCommand(context, "call $number", history, lines, speak, speechOutput, allowDirectCall = true) { notice = it }
                        } else callPermission.launch(Manifest.permission.CALL_PHONE)
                    }) { Text("Continue") }
                },
                dismissButton = { TextButton(onClick = { pendingCallNumber = null }) { Text("Cancel") } },
            )
        }
        pendingWhatsAppDraft?.let { draft ->
            AlertDialog(
                onDismissRequest = { pendingWhatsAppDraft = null },
                title = { Text("Review WhatsApp message") },
                text = { Text("Open WhatsApp with this draft for ${draft.contact}? You will choose the conversation and press Send in WhatsApp.\n\n${draft.message}") },
                confirmButton = {
                    TextButton(onClick = {
                        pendingWhatsAppDraft = null
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, draft.message)
                            setPackage("com.whatsapp")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(shareIntent, "Choose WhatsApp conversation"))
                            showResult("WhatsApp message draft for ${draft.contact}", "Opened the WhatsApp draft. Select ${draft.contact} and tap Send to send the message.")
                        }.onFailure {
                            notice = "WhatsApp could not open the message draft. No message was sent."
                        }
                    }) { Text("Open draft") }
                },
                dismissButton = { TextButton(onClick = { pendingWhatsAppDraft = null }) { Text("Cancel") } },
            )
        }
        }
    }
}

@Composable
private fun IntroScreen() {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Text(
            "ZERO-Assist",
            Modifier.align(Alignment.Center),
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
        )
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("version (17.7.07)", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Text("Made by Love @Shree❤️", color = Color.White, style = MaterialTheme.typography.bodyMedium)
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
    recognitionState: OfflineRecognitionState,
    isListening: Boolean,
    batteryStatus: BatteryStatus,
    reminders: List<PlannerReminder>,
    wakeWordEnabled: Boolean,
    onSend: () -> Unit,
    onMic: () -> Unit,
    onStopListening: () -> Unit,
    onShortcut: (String) -> Unit,
    onNavigate: (Page) -> Unit,
) {
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMillis = System.currentTimeMillis()
        }
    }
    val nextReminder = reminders.mapNotNull { reminder ->
        val trigger = runCatching {
            com.zero.assistant.core.ReminderSchedule.nextTriggerEpochMillis(reminder, nowMillis, ZoneId.systemDefault())
        }.getOrNull()
        trigger?.let { it to reminder }
    }.minByOrNull { it.first }?.let { (trigger, reminder) ->
        reminder.title to DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(trigger))
    }
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("LOCAL TIME", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f))
                    Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(nowMillis)), fontSize = 42.sp, fontWeight = FontWeight.Light, lineHeight = 48.sp)
                    Text(DateFormat.getDateInstance(DateFormat.FULL).format(Date(nowMillis)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (isListening) "MIC LIVE" else "MIC IDLE", style = MaterialTheme.typography.labelMedium, color = if (isListening) Color(0xFF5CE1C6) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                    Text(if (wakeWordEnabled) "WAKE · ON" else "WAKE · OFF", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("BATTERY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f))
                    Text(if (batteryStatus.percent in 0..100) "${batteryStatus.percent}%${if (batteryStatus.charging) " · Charging" else ""}" else "Unavailable", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("NEXT REMINDER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f))
                    Text(nextReminder?.first ?: "Nothing scheduled", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    if (nextReminder != null) Text(nextReminder.second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                listOf("YouTube", "WhatsApp", "Camera"),
                listOf("Torch on", "Torch off", "Battery"),
                listOf("Wi-Fi", "Bluetooth", "Call"),
            ).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { item ->
                        Button(
                            onClick = { onShortcut(item) },
                            modifier = Modifier.weight(1f).height(42.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Forest),
                            contentPadding = ButtonDefaults.ContentPadding,
                        ) { Text(item, maxLines = 1, fontSize = 12.sp) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        when (val state = recognitionState) {
            is OfflineRecognitionState.LoadingModel -> Text("Loading local speech model…", style = MaterialTheme.typography.bodySmall)
            is OfflineRecognitionState.Listening -> Text("Listening with ${state.modelId}…", style = MaterialTheme.typography.bodySmall)
            is OfflineRecognitionState.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            OfflineRecognitionState.Idle, OfflineRecognitionState.Cancelled, is OfflineRecognitionState.Result -> Unit
        }
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
                    IconButton(onClick = if (isListening) onStopListening else onMic) {
                        Text(if (isListening) "Stop" else "Mic", color = if (isListening) MaterialTheme.colorScheme.error else Forest, fontSize = 12.sp)
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
    speechOutput: Boolean,
    onSpeechOutput: (Boolean) -> Unit,
    speechLanguageTag: String,
    onSpeechLanguage: (String) -> Unit,
    activeModelId: String?,
    speechOutputStatus: SpeechOutputStatus,
    onTestSpeech: (SpeechLanguage) -> Unit,
    onStopSpeech: () -> Unit,
    wakeWordEnabled: Boolean,
    wakeWordActive: Boolean,
    wakeWordError: String?,
    wakeWordPhrase: String,
    onWakeWordPhrase: (String) -> Unit,
    onWakeWordEnabled: (Boolean) -> Unit,
    onNavigate: (Page) -> Unit,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Preferences", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        val speechOutputReady = speechOutputStatus is SpeechOutputStatus.Ready || speechOutputStatus is SpeechOutputStatus.ReadyWithVoice
        val speechEngineReady = speechOutputReady || speechOutputStatus is SpeechOutputStatus.LanguageUnavailable
        SettingSwitch("Speak responses", speechOutputStatusMessage(speechOutputStatus), speechOutput, onSpeechOutput, enabled = speechOutputReady)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onTestSpeech(SpeechLanguage.English) }, enabled = speechEngineReady) { Text("Test English") }
            Button(onClick = { onTestSpeech(SpeechLanguage.Hindi) }, enabled = speechEngineReady) { Text("Test Hindi") }
            TextButton(onClick = onStopSpeech, enabled = speechEngineReady) { Text("Stop") }
        }
        Text("Speech recognition", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text(
            if (activeModelId != null) "Push-to-talk uses the installed Vosk model (${activeModelId}). Recognition runs on this device; typed commands always remain available."
            else "The included Hindi model is being installed or no model is ready yet. Typed commands always remain available.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        TextButton(onClick = { onNavigate(Page.SpeechModels) }) { Text("Voice Library") }
        TextButton(onClick = { onNavigate(Page.Planner) }) { Text("Daily Planner") }
        TextButton(onClick = { onNavigate(Page.BatteryAlerts) }) { Text("Battery Alerts") }
        TextButton(onClick = { onNavigate(Page.Notes) }) { Text("Notes") }
        TextButton(onClick = { onNavigate(Page.MyShortcuts) }) { Text("My Shortcuts") }
        TextButton(onClick = { onNavigate(Page.Dashboard) }) { Text("Privacy & system dashboard") }
        Text("Text-to-speech language", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = speechLanguageTag == "en-US", onClick = { onSpeechLanguage("en-US") }, label = { Text("English") })
            FilterChip(selected = speechLanguageTag == "hi-IN", onClick = { onSpeechLanguage("hi-IN") }, label = { Text("Hindi") })
        }
        Text("Offline voice output", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text("Use Voice Library to browse the offline voices installed by Android's speech engine, select one, or open its voice-data installer. ZERO only selects voices that report they do not require a network connection.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text("Wake word", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        SettingSwitch(
            "Background wake listening",
            when {
                wakeWordActive -> "Microphone is active in the background. Use the persistent notification to stop."
                wakeWordError != null -> wakeWordError
                wakeWordEnabled -> "Enabled; waiting for microphone access or a visible app launch to resume."
                else -> "Off by default. A persistent notification stays visible while listening."
            },
            wakeWordEnabled,
            onWakeWordEnabled,
        )
        OutlinedTextField(
            value = wakeWordPhrase,
            onValueChange = onWakeWordPhrase,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Wake phrase") },
            singleLine = true,
        )
        TextButton(onClick = { onWakeWordPhrase(WakeWord.DEFAULT_PHRASE) }) { Text("Use default: ${WakeWord.DEFAULT_PHRASE}") }
        Text("Say the phrase followed by a command, for example: Hey ZERO, open YouTube. This uses the selected offline speech model and Android microphone permission. Continuous software listening can increase battery use; turn it off in Settings or use Stop in its persistent notification when you do not need it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text("ZERO-Assist version 17.7.07", style = MaterialTheme.typography.labelMedium)
        TextButton(onClick = { onNavigate(Page.Privacy) }) { Text("Privacy policy") }
        TextButton(onClick = { onNavigate(Page.Terms) }) { Text("Terms") }
    }
}

private fun speechOutputStatusMessage(status: SpeechOutputStatus): String = when (status) {
    SpeechOutputStatus.Initializing -> "Initializing the Android speech engine…"
    is SpeechOutputStatus.Ready -> "Using an installed offline ${status.language.label} voice."
    is SpeechOutputStatus.ReadyWithVoice -> "Using installed offline ${status.voice.displayName} (${status.voice.languageTag})."
    is SpeechOutputStatus.LanguageUnavailable ->
        "No offline ${status.language.label} voice is installed. Open Voice Library to install voice data from Android's speech engine."
    SpeechOutputStatus.InitializationFailed -> "Android text-to-speech could not initialize. Check the installed TTS engine in Android Settings."
    SpeechOutputStatus.SpeakFailed -> "The local TTS engine could not speak. Check its installed voice data in Android Settings."
    SpeechOutputStatus.Closed -> "Speech output is stopped."
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
    allowDirectCall: Boolean = false,
    onError: (String) -> Unit,
) {
    lines.add(ChatLine(false, input))
    val command = CommandParser.parse(input)
    val response = when (command) {
        is AssistantCommand.OpenApp -> openApp(context, command.app)
        is AssistantCommand.SetFlashlight -> setFlashlight(context, command.enabled)
        is AssistantCommand.CallNumber -> callNumber(context, command.number, allowDirectCall)
        is AssistantCommand.SearchYouTube -> openYouTubeSearch(context, command.query)
        is AssistantCommand.DraftWhatsAppMessage -> "Review and confirm the WhatsApp message before opening its draft."
        is AssistantCommand.SaveNote -> "I couldn't save that note. Try again in Notes."
        is AssistantCommand.DraftReminder -> "Open the planner to choose a date and time for that reminder."
        is AssistantCommand.RunShortcut -> "I couldn't find an enabled shortcut named ${command.name}."
        AssistantCommand.TellTime -> "It's ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())}."
        AssistantCommand.BatteryStatus -> batteryStatus(context)
        AssistantCommand.ShowPlanner -> "Open the planner to view your reminders and tasks."
        AssistantCommand.ShowNotes -> "Open Notes to view your saved notes."
        AssistantCommand.Help -> "Try opening YouTube, WhatsApp, Camera or Settings; ask for the time or battery status; say torch chalu karo / torch band karo; or say call followed by a phone number. Calls always ask for confirmation."
        AssistantCommand.Unsupported -> "I couldn't match that to a supported offline command. Did you mean open an app, check time or battery, create a note or reminder, or run a saved shortcut?"
    }
    lines.add(ChatLine(true, response))
    history.add(input, response)
    if (speechOutput) speak(response)
    if (response.startsWith("I couldn't") || response.startsWith("That app") || response.startsWith("This device")) onError(response)
}

private fun openYouTubeSearch(context: Context, query: String): String {
    val uri = Uri.parse("https://www.youtube.com/results").buildUpon()
        .appendQueryParameter("search_query", query)
        .build()
    val youtube = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.youtube")
    val intent = if (youtube.resolveActivity(context.packageManager) != null) youtube else Intent(Intent.ACTION_VIEW, uri)
    return try {
        if (intent.resolveActivity(context.packageManager) == null) return "No app is available to open YouTube search."
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        "Opened YouTube search for $query."
    } catch (_: ActivityNotFoundException) {
        "No app is available to open YouTube search."
    } catch (_: SecurityException) {
        "Android denied the YouTube search request."
    }
}

private fun batteryStatus(context: Context): String {
    val batteryManager = context.getSystemService(BatteryManager::class.java)
    val capacity = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    if (capacity !in 0..100) return "Battery status is unavailable on this device."
    val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    return "Battery is at $capacity%." + if (charging) " It is charging." else ""
}

private fun callNumber(context: Context, number: String, allowDirectCall: Boolean): String {
    if (allowDirectCall && ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
        try {
            context.startActivity(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return "Calling $number."
        } catch (_: SecurityException) {
            return openDialer(context, number, "Direct calling was denied")
        } catch (_: ActivityNotFoundException) {
            return openDialer(context, number, "Direct calling is unavailable")
        }
    }
    return openDialer(context, number, "Direct-call permission was not granted")
}

private fun executeShortcut(
    shortcut: UserShortcut,
    context: Context,
    dispatchCommand: (String, (() -> Unit)?) -> Unit,
    onOutput: (String) -> Unit,
    onNavigate: (Page) -> Unit,
    onComplete: () -> Unit,
) {
    if (!shortcut.enabled) return
    fun runAction(index: Int) {
        if (index >= shortcut.actions.size) {
            onComplete()
            return
        }
        val action = shortcut.actions[index]
        val next = { runAction(index + 1) }
        when (action.type) {
            ShortcutActionType.OpenApp -> dispatchCommand("open ${action.value}", next)
            ShortcutActionType.OpenSettings -> { onOutput(openSettingsTarget(context, action.value)); next() }
            ShortcutActionType.ShowTodayTasks -> { onOutput("Showing today's planner."); onNavigate(Page.Planner); next() }
            ShortcutActionType.ShowBatteryStatus -> dispatchCommand("battery status", next)
            ShortcutActionType.TurnFlashlightOn -> dispatchCommand("torch on", next)
            ShortcutActionType.TurnFlashlightOff -> dispatchCommand("torch off", next)
            ShortcutActionType.ShowReminders -> { onOutput("Showing your reminders."); onNavigate(Page.Planner); next() }
            ShortcutActionType.ShowNotes -> { onOutput("Showing your local notes."); onNavigate(Page.Notes); next() }
            ShortcutActionType.SpeakText -> { onOutput(action.value); next() }
        }
    }
    runAction(0)
}

private fun openSettingsTarget(context: Context, target: String): String {
    val action = when (target) {
        "wifi" -> Settings.ACTION_WIFI_SETTINGS
        "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
        "sound" -> Settings.ACTION_SOUND_SETTINGS
        "display" -> Settings.ACTION_DISPLAY_SETTINGS
        "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
        "text_to_speech" -> "com.android.settings.TTS_SETTINGS"
        else -> return "That Settings screen is not supported."
    }
    return try {
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) "That Settings screen isn't available on this device."
        else {
            context.startActivity(intent)
            "Opening Android Settings."
        }
    } catch (_: ActivityNotFoundException) {
        "That Settings screen isn't available on this device."
    } catch (_: SecurityException) {
        "Android denied access to that Settings screen."
    }
}

private fun openDialer(context: Context, number: String, reason: String): String = try {
    val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (intent.resolveActivity(context.packageManager) == null) "Unable to open the dialer for $number."
    else {
        context.startActivity(intent)
        "$reason. Opened the dialer for $number; review it before placing the call."
    }
} catch (_: ActivityNotFoundException) {
    "Unable to open the dialer for $number."
} catch (_: SecurityException) {
    "Android denied access to the dialer for $number."
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