package com.zero.assistant

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.content.IntentFilter
import android.provider.MediaStore
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zero.assistant.core.AppTarget
import com.zero.assistant.core.AssistantCommand
import com.zero.assistant.core.AssistantResponseRoute
import com.zero.assistant.core.AssistantResponseRouter
import com.zero.assistant.core.BatteryAlertSettings
import com.zero.assistant.core.LocalNote
import com.zero.assistant.core.NoteRepository
import com.zero.assistant.core.ShortcutActionType
import com.zero.assistant.core.ShortcutRepository
import com.zero.assistant.core.UserShortcut
import com.zero.assistant.core.CommandHistory
import com.zero.assistant.core.CommandParser
import com.zero.assistant.core.AssistantIdentity
import com.zero.assistant.core.HistoryEntry
import com.zero.assistant.core.HomeSuggestion
import com.zero.assistant.core.HomeSuggestionCatalog
import com.zero.assistant.core.OfflineRecognitionState
import com.zero.assistant.core.OfflineRecognitionStateMachine
import com.zero.assistant.core.PlannerReminder
import com.zero.assistant.core.SpeechLanguage
import com.zero.assistant.core.SpeechOutputController
import com.zero.assistant.core.SpeechOutputEngine
import com.zero.assistant.core.SpeechOutputStatus
import com.zero.assistant.core.OfflineTtsVoice
import com.zero.assistant.core.WakeWord
import com.zero.assistant.core.WakePhrase
import com.zero.assistant.core.WakePhraseConfiguration
import com.zero.assistant.data.PreferencesHistoryStorage
import com.zero.assistant.data.BatteryAlertManager
import com.zero.assistant.data.BatteryStatus
import com.zero.assistant.data.ProductivityPreferences
import com.zero.assistant.data.PlannerPreferences
import com.zero.assistant.data.ReminderScheduler
import com.zero.assistant.data.SpeechModelManager
import com.zero.assistant.data.WakeWordService
import com.zero.assistant.data.WakeWordSettings
import com.zero.assistant.data.AiProviderSettings
import com.zero.assistant.data.OpenAiCompatibleProvider
import com.zero.assistant.data.VaultManager
import com.zero.assistant.data.HardwareActivationSettings
import com.zero.assistant.core.VaultVisibility
import com.zero.assistant.data.DoubleShakeService
import com.zero.assistant.core.ShakeSensitivity
import com.zero.assistant.data.AssistantAiProvider
import com.zero.assistant.core.ReminderRepository
import java.text.DateFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

private val Forest = Color.White
private val Mint = Color(0xFF202020)
private val Paper = Color.Black
private val Ink = Color.White

class MainActivity : FragmentActivity() {
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
        if (intent.getBooleanExtra(WakeWordSettings.EXTRA_RESUME, false) && WakeWordSettings.enabled(this)) {
            startWakeWordService()
        }
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
    private var speechCompleteListener: (() -> Unit)? = null

    override fun initialize(onInitialized: (Boolean) -> Unit) {
        initializedCallback = onInitialized
        try {
            textToSpeech = TextToSpeech(appContext) { result ->
                val callback = initializedCallback
                initializedCallback = null
                if (result == TextToSpeech.SUCCESS) {
                    textToSpeech?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit
                        override fun onDone(utteranceId: String?) { speechCompleteListener?.invoke() }
                        @Suppress("OVERRIDE_DEPRECATION")
                        override fun onError(utteranceId: String?) { speechCompleteListener?.invoke() }
                        @Suppress("OVERRIDE_DEPRECATION")
                        override fun onError(utteranceId: String?, errorCode: Int) { speechCompleteListener?.invoke() }
                        override fun onStop(utteranceId: String?, interrupted: Boolean) { speechCompleteListener?.invoke() }
                    })
                }
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

    override fun setOnSpeechComplete(listener: () -> Unit) {
        speechCompleteListener = listener
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
internal enum class Page { Home, History, Settings, SpeechModels, VoiceStudio, Scanner, Planner, BatteryAlerts, Notes, MyShortcuts, Dashboard, Help, Privacy, Terms, Vault, Emergency, Diagnostics }

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
    val accessibilityPreferences = remember { context.getSharedPreferences("zero_accessibility", Context.MODE_PRIVATE) }
    val aiProviderSettings = remember { AiProviderSettings(context) }
    val vaultManager = remember { VaultManager(context) }
    val aiRequestScope = rememberCoroutineScope()
    val noteRepository = remember { NoteRepository(productivityPreferences) }
    val shortcutRepository = remember { ShortcutRepository(productivityPreferences) }
    val activeModelId by modelManager.activeModelId.collectAsState()
    LaunchedEffect(reminderScheduler) { reminderScheduler.rescheduleAll() }
    var page by rememberSaveable { mutableStateOf(Page.Home) }
    var backDestination by rememberSaveable { mutableStateOf(Page.Home) }
    val privacyPreferences = remember { context.getSharedPreferences(VaultVisibility.PREF_FILE, Context.MODE_PRIVATE) }
    var vaultVisible by remember { mutableStateOf(privacyPreferences.getBoolean(VaultVisibility.PREF_VISIBLE, true)) }
    fun navigateTo(destination: Page) {
        if (destination == Page.Vault && !vaultVisible) return
        if (destination != page) {
            backDestination = page
            page = destination
        }
    }
    fun navigateBack() {
        page = backDestination
        backDestination = Page.Home
    }
    BackHandler(enabled = page != Page.Home) { navigateBack() }
    var speechOutput by rememberSaveable { mutableStateOf(true) }
        var reducedMotion by remember { mutableStateOf(accessibilityPreferences.getBoolean("reduced_motion", false)) }
    var speechLanguageTag by rememberSaveable {
        mutableStateOf(if (Locale.getDefault().language == "hi") "hi-IN" else "en-US")
    }
    var wakeWordEnabled by remember { mutableStateOf(WakeWordSettings.enabled(context)) }
    var wakeWordActive by remember { mutableStateOf(WakeWordSettings.active(context)) }
    var wakeWordPhrases by remember { mutableStateOf(WakeWordSettings.phrases(context)) }
    var wakeWordError by remember { mutableStateOf(WakeWordSettings.error(context)) }
    var wakeWordLastDetection by remember { mutableLongStateOf(WakeWordSettings.lastDetection(context)) }
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
    var pendingOnlineQuestion by remember { mutableStateOf<String?>(null) }
    var pendingOnlineContinuation by remember { mutableStateOf<(() -> Unit)?>(null) }
    var onlineRequestRunning by remember { mutableStateOf(false) }
    var onlineRequestJob by remember { mutableStateOf<Job?>(null) }
    val lines = rememberSaveable(saver = chatSaver) { mutableStateListOf(ChatLine(true, "Hi, I'm ZERO. What can I do for you?")) }
    lateinit var dispatchCommand: (String, (() -> Unit)?) -> Unit

    DisposableEffect(wakePreferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "enabled" -> wakeWordEnabled = WakeWordSettings.enabled(context)
                "active" -> wakeWordActive = WakeWordSettings.active(context)
                "error" -> wakeWordError = WakeWordSettings.error(context)
                "last_detection" -> wakeWordLastDetection = WakeWordSettings.lastDetection(context)
                "phrases_v2" -> wakeWordPhrases = WakeWordSettings.phrases(context)
            }
        }
        wakePreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { wakePreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    LaunchedEffect(wakeWordEnabled) {
        while (wakeWordEnabled) {
            delay(10_000)
            wakeWordActive = WakeWordSettings.active(context)
            wakeWordLastDetection = WakeWordSettings.lastDetection(context)
            wakeWordError = WakeWordSettings.error(context)
        }
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
    val pendingShakePermission = remember { mutableStateOf(false) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val command = pendingFlashlightCommand
        pendingFlashlightCommand = null
        if (granted && command != null) executeCommand(context, command, history, lines, speak, speechOutput) { notice = it }
        if (pendingShakePermission.value) {
            pendingShakePermission.value = false
            if (!granted) HardwareActivationSettings.setDoubleShakeEnabled(context, false)
        }
        if (granted && HardwareActivationSettings.doubleShakeEnabled(context) && command == null) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, DoubleShakeService::class.java).setAction(DoubleShakeService.ACTION_START),
                )
            }
        }
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

    fun answerOnlineQuestion(query: String) {
        val config = aiProviderSettings.configuration()
        val apiKey = aiProviderSettings.apiKey()
        if (!config.enabled || apiKey.isNullOrBlank() || !hasValidatedInternet(context)) {
            showResult(query, "Online AI is unavailable. ZERO can still handle supported offline commands, reminders, notes, battery status, and identity questions.")
            return
        }
        onlineRequestRunning = true
        onlineRequestJob = aiRequestScope.launch {
            try {
                val provider: AssistantAiProvider = OpenAiCompatibleProvider(config.endpoint, config.model, apiKey)
                val response = provider.answer(query)
                showResult(query, "Online AI: $response")
            } catch (_: CancellationException) {
                showResult(query, "Online request canceled. ZERO's offline commands remain available.")
            } catch (failure: Exception) {
                val reason = failure.message ?: "network or response error"
                showResult(query, "Online AI failed ($reason). I don't have an offline language model for that question; supported local commands still work.")
            } finally {
                onlineRequestRunning = false
                onlineRequestJob = null
            }
        }
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
                navigateTo(Page.Planner)
                showResult(command, "Added the reminder title to the planner. Set its date and time to save it.")
                onComplete?.invoke()
            }
            AssistantCommand.ShowPlanner -> { navigateTo(Page.Planner); showResult(command, "Showing your planner."); onComplete?.invoke() }
            AssistantCommand.ShowNotes -> { navigateTo(Page.Notes); showResult(command, "Showing your notes."); onComplete?.invoke() }
            AssistantCommand.Identity -> { showResult(command, AssistantIdentity.RESPONSE); onComplete?.invoke() }
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
                val config = aiProviderSettings.configuration()
                val route = AssistantResponseRouter.select(
                    command = parsed,
                    onlineEnabled = config.enabled && config.model.isNotBlank(),
                    keyConfigured = config.hasApiKey,
                    endpointValid = com.zero.assistant.core.AiProviderContract.validHttpsEndpoint(config.endpoint),
                    internetAvailable = hasValidatedInternet(context),
                )
                if (route == AssistantResponseRoute.ConfirmOnline) {
                    pendingOnlineQuestion = command
                    pendingOnlineContinuation = onComplete
                } else {
                    executeCommand(context, command, history, lines, speak, speechOutput) { notice = it }
                    onComplete?.invoke()
                }
            }
        }
    }
    val runCommand: (String) -> Unit = { dispatchCommand(it, null) }
    LaunchedEffect(Unit) {
        delay(1_000)
        showIntro = false
    }
    DisposableEffect(page) {
        val window = (context as? android.app.Activity)?.window
        if (page == Page.Vault) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        else window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
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
                            Page.VoiceStudio -> "Offline Voice Studio"
                            Page.Scanner -> "Knowledge Scanner"
                            Page.Planner -> "Planner"
                            Page.BatteryAlerts -> "Battery Alerts"
                            Page.Notes -> "Notes"
                            Page.MyShortcuts -> "My Shortcuts"
                            Page.Dashboard -> "Privacy & system"
                            Page.Vault -> "ZERO Vault"
                            Page.Emergency -> "Emergency Quick Mode"
                            Page.Diagnostics -> "Self-diagnostics"
                            else -> page.name
                        },
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.sp,
                    )
                },
                navigationIcon = { if (page != Page.Home) IconButton(onClick = { navigateBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (page == Page.Home) {
                        TextButton(onClick = { navigateTo(Page.History) }) { Text("History") }
                        IconButton(onClick = { navigateTo(Page.Settings) }) { Icon(Icons.Default.Settings, "Settings") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background),
            )
        }) { padding ->
            when (page) {
                Page.Home -> HomeScreen(
                    Modifier.padding(padding), lines, input, { input = it }, notice,
                    recognitionState, isListening || wakeWordActive,
                    orbActivity = when {
                        speechOutputStatus == SpeechOutputStatus.Speaking -> OrbActivity.Speaking
                        isListening || wakeWordActive -> OrbActivity.Listening
                        onlineRequestRunning || recognitionState is OfflineRecognitionState.LoadingModel -> OrbActivity.Processing
                        else -> OrbActivity.Idle
                    },
                    reducedMotion = reducedMotion,
                    batteryStatus = batteryStatus,
                    reminders = reminderRepository.all(),
                    wakeWordEnabled = wakeWordEnabled,
                    wakeWordServiceActive = wakeWordActive,
                    wakeWordError = wakeWordError,
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
                    onSuggestion = { suggestion -> runCommand(suggestion.command) },
                    onNavigate = { navigateTo(it) },
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
                    wakeWordPhrases = wakeWordPhrases,
                    wakeWordLastDetection = wakeWordLastDetection,
                    microphonePermissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
                    onWakeWordPhrasesChanged = {
                        wakeWordPhrases = it
                        WakeWordSettings.setPhrases(context, it)
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
                    reducedMotion = reducedMotion,
                    onReducedMotion = {
                        reducedMotion = it
                        accessibilityPreferences.edit().putBoolean("reduced_motion", it).apply()
                    },
                    aiProviderSettings = aiProviderSettings,
                    onNavigate = { navigateTo(it) },
                    onRequestShakeCameraPermission = {
                        pendingShakePermission.value = true
                        cameraPermission.launch(Manifest.permission.CAMERA)
                    },
                    vaultVisible = vaultVisible,
                    onVaultVisible = { visible ->
                        vaultVisible = visible
                        privacyPreferences.edit().putBoolean(VaultVisibility.PREF_VISIBLE, visible).apply()
                        if (!visible && page == Page.Vault) page = Page.Settings
                    },
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
                Page.VoiceStudio -> VoiceStudioScreen(
                    modifier = Modifier.padding(padding),
                    offlineVoices = offlineTtsVoices,
                    speechStatus = speechOutputStatus,
                    startListening = startListening,
                    stopListening = stopListening,
                    speak = speak,
                    stopSpeech = stopSpeech,
                )
                Page.Scanner -> ScannerScreen(Modifier.padding(padding), noteRepository)
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
                    onNavigate = { navigateTo(it) },
                )
                Page.Vault -> VaultScreen(Modifier.padding(padding), vaultManager)
                Page.Emergency -> EmergencyScreen(Modifier.padding(padding))
                Page.Diagnostics -> SelfDiagnosticsScreen(Modifier.padding(padding), vaultManager, activeModelId)
                Page.Help -> TextPage(Modifier.padding(padding), "Help and troubleshooting", "Try commands like:\n\n• Open YouTube / YouTube kholo\n• Torch chalu karo / Torch band karo\n• Abhi kitne baje hain? / battery status\n• note buy milk\n• remind me to call Sam, then set its date and time in Planner\n• planner / notes\n• call followed by a phone number (ZERO asks you to confirm)\n\nTo enable background wake listening, open Settings, enable Background wake listening, and grant microphone and notification permissions. Keep the persistent ZERO notification visible; its Stop action ends microphone use. Enabled phrases are supplied to Vosk as a constrained grammar; recognition depends on the selected model's vocabulary and language.\n\nAndroid and device manufacturers may pause or stop microphone use because of battery management, privacy controls, force-stop, or reboot policy. After reboot, Android requires you to tap a notification and reopen ZERO before microphone capture resumes. Battery optimization guidance opens Android system settings. Speech transcripts and actions are handled locally; unsupported or sensitive commands may require review in ZERO.")
                Page.Privacy -> TextPage(Modifier.padding(padding), "Privacy and permissions", "Typed commands, command execution, history, speech recognition, notes, reminders, shortcuts, and model inference run on this device. ZERO does not upload commands, audio, notes, reminders, or history; it has no account, analytics, or ads. Local records and downloaded models use app-private storage. ZERO does not retain raw audio.\n\nMicrophone capture starts after you tap Mic or explicitly enable wake listening and grant permission. When enabled, the foreground service continues wake recognition after the Home screen closes and shows a persistent notification. Vosk recognition and wake-phrase grammar matching are local. Hindi recognition is bundled; the optional English model is downloaded from the fixed HTTPS URL and checksum in the catalog. Optional online AI sends only a confirmed text query to the configured provider; microphone audio and local history are not sent.\n\nAndroid may interrupt the foreground service or defer notifications because of battery management, permission changes, force-stop, or reboot policy. After reboot, ZERO requires a user tap before microphone access resumes. Android TTS uses an installed voice that reports it does not require a network connection.")
                Page.Terms -> TextPage(Modifier.padding(padding), "Terms of Use", "ZERO is provided as-is for personal use. Recognition is processed locally using the selected compatible Vosk model. Speech accuracy depends on language, microphone, device performance, and environment; automatic Hinglish recognition is not promised. Android TTS must have a compatible local voice installed. Optional online AI sends text to a provider only after confirmation and requires the user to configure and trust that provider; the provider's own terms and data handling apply. Reminder delivery is inexact and may be delayed or suppressed by Android power management, notification permission, or force-stop. External app launches, flashlight hardware, and calls depend on the device. ZERO confirms a target number before direct-call permission; if permission or calling is unavailable, it opens the dialer for review.")
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
        pendingOnlineQuestion?.let { query ->
            val host = runCatching { java.net.URI(aiProviderSettings.configuration().endpoint).host }.getOrNull().orEmpty()
            AlertDialog(
                onDismissRequest = {
                    pendingOnlineQuestion = null
                    pendingOnlineContinuation?.invoke()
                    pendingOnlineContinuation = null
                    executeCommand(context, query, history, lines, speak, speechOutput) { notice = it }
                },
                title = { Text("Send text to online AI?") },
                text = {
                    Text("ZERO will send only this question over HTTPS${if (host.isNotBlank()) " to $host" else ""}. Microphone audio and local history are not sent. Choose Stay offline to cancel transmission.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        pendingOnlineQuestion = null
                        pendingOnlineContinuation?.invoke()
                        pendingOnlineContinuation = null
                        answerOnlineQuestion(query)
                    }) { Text("Send text") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        pendingOnlineQuestion = null
                        pendingOnlineContinuation?.invoke()
                        pendingOnlineContinuation = null
                        executeCommand(context, query, history, lines, speak, speechOutput) { notice = it }
                    }) { Text("Stay offline") }
                },
            )
        }
        if (onlineRequestRunning) {
            AlertDialog(
                onDismissRequest = { onlineRequestJob?.cancel() },
                title = { Text("Online AI is responding") },
                text = { Text("The request is in progress. You can cancel it. Microphone audio is never sent.") },
                confirmButton = { TextButton(onClick = { onlineRequestJob?.cancel() }) { Text("Cancel request") } },
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
    orbActivity: OrbActivity,
    reducedMotion: Boolean,
    batteryStatus: BatteryStatus,
    reminders: List<PlannerReminder>,
    wakeWordEnabled: Boolean,
    wakeWordServiceActive: Boolean,
    wakeWordError: String?,
    onSend: () -> Unit,
    onMic: () -> Unit,
    onStopListening: () -> Unit,
    onSuggestion: (HomeSuggestion) -> Unit,
    onNavigate: (Page) -> Unit,
) {
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showMoreSuggestions by rememberSaveable { mutableStateOf(false) }
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
    val primarySuggestions = HomeSuggestionCatalog.primary
    val moreSuggestions = HomeSuggestionCatalog.more
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(nowMillis)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Light)
                Text("${if (batteryStatus.percent in 0..100) "BATTERY ${batteryStatus.percent}%" else "BATTERY UNAVAILABLE"}  ·  ${nextReminder?.let { "NEXT ${it.second}" } ?: "NO UPCOMING REMINDER"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (wakeWordServiceActive) "WAKE ACTIVE" else if (wakeWordEnabled) "WAKE NOT RUNNING" else "WAKE OFF", style = MaterialTheme.typography.labelSmall, color = if (wakeWordServiceActive) Color(0xFF54D7E8) else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AssistantOrb(
            activity = orbActivity,
            reducedMotion = reducedMotion,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 2.dp),
        )
        val activityMessage = when (orbActivity) {
            OrbActivity.Idle -> "ZERO READY · OFFLINE COMMANDS AVAILABLE"
            OrbActivity.Listening -> "LISTENING · MICROPHONE IN USE"
            OrbActivity.Processing -> "PROCESSING LOCALLY"
            OrbActivity.Speaking -> "ZERO IS SPEAKING"
        }
        Text(activityMessage, Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (wakeWordEnabled && !wakeWordServiceActive) {
            Text(wakeWordError ?: "Wake listening is enabled but not currently running.", Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
        HomeSuggestionRail(
            suggestions = if (showMoreSuggestions) primarySuggestions + moreSuggestions else primarySuggestions,
            expanded = showMoreSuggestions,
            animate = !reducedMotion && orbActivity == OrbActivity.Idle && !isListening && input.isBlank() && !showMoreSuggestions,
            onSuggestion = onSuggestion,
            onToggleMore = { showMoreSuggestions = !showMoreSuggestions },
        )
        lines.lastOrNull()?.let { lastLine ->
            Text(if (lastLine.fromZero) lastLine.text else "You: ${lastLine.text}", Modifier.fillMaxWidth(), maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when (val state = recognitionState) {
            is OfflineRecognitionState.LoadingModel -> Text("Loading local speech model…", style = MaterialTheme.typography.bodySmall)
            is OfflineRecognitionState.Listening -> Text("Listening with ${state.modelId}…", style = MaterialTheme.typography.bodySmall)
            is OfflineRecognitionState.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            OfflineRecognitionState.Idle, OfflineRecognitionState.Cancelled, is OfflineRecognitionState.Result -> Unit
        }
        if (notice != null) Text(notice, Modifier.fillMaxWidth().padding(bottom = 4.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
    }
}

@Composable
private fun HomeSuggestionRail(
    suggestions: List<HomeSuggestion>,
    expanded: Boolean,
    animate: Boolean,
    onSuggestion: (HomeSuggestion) -> Unit,
    onToggleMore: () -> Unit,
) {
    val phase = if (animate) {
        val transition = rememberInfiniteTransition(label = "suggestion-orbit")
        val animatedPhase by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(11_000, easing = LinearEasing), RepeatMode.Restart),
            label = "suggestion-phase",
        )
        animatedPhase
    } else 0f
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        suggestions.forEachIndexed { index, suggestion ->
            val bob = if (animate) sin(phase + index * 0.8f) * 4f else 0f
            FilterChip(
                selected = false,
                onClick = { onSuggestion(suggestion) },
                modifier = Modifier.offset(y = bob.dp),
                label = { Text(suggestion.label, maxLines = 1) },
            )
        }
        TextButton(onClick = onToggleMore) { Text(if (expanded) "Less" else "More") }
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
    wakeWordPhrases: List<WakePhrase>,
    wakeWordLastDetection: Long,
    microphonePermissionGranted: Boolean,
    onWakeWordPhrasesChanged: (List<WakePhrase>) -> Unit,
    onWakeWordEnabled: (Boolean) -> Unit,
    reducedMotion: Boolean,
    onReducedMotion: (Boolean) -> Unit,
    aiProviderSettings: AiProviderSettings,
    onNavigate: (Page) -> Unit,
    onRequestShakeCameraPermission: () -> Unit,
    vaultVisible: Boolean,
    onVaultVisible: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var volumeShortcut by remember { mutableStateOf(HardwareActivationSettings.volumePairEnabled(context)) }
    var doubleShake by remember { mutableStateOf(HardwareActivationSettings.doubleShakeEnabled(context)) }
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
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AssistantOrb(OrbActivity.Idle, reducedMotion = true, Modifier.size(58.dp))
            Column {
                Text("ZERO SYSTEM SETTINGS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Local controls · version 17.7.07", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SettingSwitch("Reduce motion", "Keep the orb and suggestion rail static.", reducedMotion, onReducedMotion)
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
        TextButton(onClick = { onNavigate(Page.VoiceStudio) }) { Text("Offline Voice Studio") }
        TextButton(onClick = { onNavigate(Page.Scanner) }) { Text("Knowledge Scanner") }
        TextButton(onClick = { onNavigate(Page.Planner) }) { Text("Daily Planner") }
        TextButton(onClick = { onNavigate(Page.BatteryAlerts) }) { Text("Battery Alerts") }
        TextButton(onClick = { onNavigate(Page.Notes) }) { Text("Notes") }
        TextButton(onClick = { onNavigate(Page.MyShortcuts) }) { Text("My Shortcuts") }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (vaultVisible) {
                TextButton(onClick = { onNavigate(Page.Vault) }) { Text("ZERO Vault") }
            } else {
                Text("ZERO Vault hidden", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { vaultMenuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Vault visibility menu")
                }
                DropdownMenu(expanded = vaultMenuExpanded, onDismissRequest = { vaultMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(if (vaultVisible) "Hide Vault" else "Show Vault") },
                        onClick = {
                            vaultMenuExpanded = false
                            onVaultVisible(!vaultVisible)
                        },
                    )
                }
            }
        }
        TextButton(onClick = { onNavigate(Page.Emergency) }) { Text("Emergency Quick Mode") }
        TextButton(onClick = { onNavigate(Page.Diagnostics) }) { Text("Self-diagnostics") }
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
                wakeWordActive -> "Service is running with microphone access. Use its persistent notification to stop."
                wakeWordError != null -> wakeWordError
                wakeWordEnabled -> "Enabled; waiting for microphone access or a visible app launch to resume."
                else -> "Off by default. A persistent notification stays visible while listening."
            },
            wakeWordEnabled,
            onWakeWordEnabled,
        )
        Text("Wake-word diagnostics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text("Wake engine: ${when { wakeWordActive && activeModelId != null -> "running with $activeModelId"; activeModelId != null -> "$activeModelId selected; capture is not running"; else -> "unavailable until an offline STT model is ready" }}")
        Text("Microphone permission: ${if (microphonePermissionGranted) "granted" else "not granted"}")
        Text("Listening service: ${if (wakeWordActive) "running" else if (wakeWordEnabled) "enabled but not running" else "stopped"}")
        Text("Enabled phrases use Vosk's constrained grammar; phrase accuracy depends on the selected model and language.", style = MaterialTheme.typography.bodySmall)
        Text("Last detection: ${if (wakeWordLastDetection > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(wakeWordLastDetection)) else "Never"}", style = MaterialTheme.typography.bodySmall)
        wakeWordError?.let { Text("Last service error: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        wakeWordPhrases.forEach { phrase ->
            SettingSwitch(
                phrase.phrase,
                when {
                    !phrase.enabled -> "Disabled"
                    phrase.isDefault -> "Default phrase; it remains configured unless you disable it."
                    activeModelId == null -> "Enabled; awaiting a compatible local speech model"
                    else -> "Enabled; included in the active Vosk grammar"
                },
                phrase.enabled,
                { enabled -> onWakeWordPhrasesChanged(WakePhraseConfiguration.setEnabled(wakeWordPhrases, phrase.id, enabled)) },
            )
            if (!phrase.isDefault) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = {
                        editingPhraseId = phrase.id
                        phraseDraft = phrase.phrase
                        phraseEditorError = null
                        phraseEditorVisible = true
                    }) { Text("Edit") }
                    TextButton(onClick = { onWakeWordPhrasesChanged(WakePhraseConfiguration.remove(wakeWordPhrases, phrase.id)) }) { Text("Remove") }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = {
                    editingPhraseId = null
                    phraseDraft = ""
                    phraseEditorError = null
                    phraseEditorVisible = true
                },
                enabled = wakeWordPhrases.size < 3,
            ) { Text("Add custom phrase") }
            TextButton(onClick = {
                onWakeWordPhrasesChanged(WakePhraseConfiguration.setEnabled(wakeWordPhrases, "default", true))
            }) { Text("Restore Hey ZERO") }
        }
        Text("Say an enabled phrase, pause for the offline acknowledgement, then speak a command. Vosk uses a grammar built from enabled phrases; accuracy varies by model. Android may stop microphone access because of force-stop, battery controls, reboot policy, or device restrictions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        TextButton(onClick = {
            runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }) { Text("Battery optimization guidance") }
        Text("Optional online AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        SettingSwitch(
            "Enable online AI",
            if (aiConfiguration.hasApiKey) "Disabled by default. Each unsupported question requires confirmation before text is sent." else "Disabled. Save an API key before enabling; local commands remain available without one.",
            aiConfiguration.enabled,
            { enabled ->
                aiProviderSettings.saveConfiguration(aiEndpoint, aiModel, enabled)
                    .onSuccess { aiConfiguration = aiProviderSettings.configuration(); aiSettingsMessage = null }
                    .onFailure { aiSettingsMessage = it.message ?: "AI settings could not be saved." }
            },
        )
        OutlinedTextField(
            value = aiEndpoint,
            onValueChange = { aiEndpoint = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("HTTPS Chat Completions endpoint") },
            singleLine = true,
        )
        OutlinedTextField(
            value = aiModel,
            onValueChange = { aiModel = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Provider model identifier") },
            singleLine = true,
        )
        OutlinedTextField(
            value = apiKeyDraft,
            onValueChange = { apiKeyDraft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (aiConfiguration.hasApiKey) "Replace API key" else "API key") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                aiProviderSettings.saveConfiguration(aiEndpoint, aiModel, aiConfiguration.enabled)
                    .onSuccess {
                        if (apiKeyDraft.isBlank() || aiProviderSettings.saveApiKey(apiKeyDraft)) {
                            apiKeyDraft = ""
                            aiConfiguration = aiProviderSettings.configuration()
                            aiSettingsMessage = "Settings saved. Each online question still asks before sending."
                        } else aiSettingsMessage = "The API key could not be encrypted by Android Keystore."
                    }
                    .onFailure { aiSettingsMessage = it.message ?: "AI settings could not be saved." }
            }) { Text("Save AI settings") }
            TextButton(onClick = {
                aiProviderSettings.clearApiKey()
                aiConfiguration = aiProviderSettings.configuration()
                aiSettingsMessage = "Saved API key removed from this device."
            }, enabled = aiConfiguration.hasApiKey) { Text("Clear key") }
        }
        Text("The key is encrypted with Android Keystore, but secrets used by a distributed app cannot be guaranteed safe from extraction. Enter only a provider endpoint and key you trust; ZERO sends text only after confirmation and never sends microphone audio.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        aiSettingsMessage?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        Text("Hardware activation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        SettingSwitch("Volume Up + Volume Down shortcut", "Optional accessibility key-event shortcut. Turn it on here, then enable ZERO in Android Accessibility settings. Ordinary volume presses are not consumed; the Volume Up + Volume Down accessibility shortcut remains system-controlled.", volumeShortcut, { enabled ->
            volumeShortcut = enabled
            HardwareActivationSettings.setVolumePairEnabled(context, enabled)
            if (enabled) context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
        SettingSwitch("Double-shake flashlight", "Requires an accelerometer and torch. Uses a visible foreground notification while running.", doubleShake, { enabled ->
            HardwareActivationSettings.setDoubleShakeEnabled(context, enabled)
            doubleShake = enabled
            if (enabled && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                onRequestShakeCameraPermission()
                return@SettingSwitch
            }
            val shakeIntent = Intent(context, DoubleShakeService::class.java).setAction(if (enabled) DoubleShakeService.ACTION_START else DoubleShakeService.ACTION_STOP)
            runCatching {
                if (enabled) ContextCompat.startForegroundService(context, shakeIntent) else context.startService(shakeIntent)
            }
        })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShakeSensitivity.values().forEach { sensitivity ->
                FilterChip(selected = shakeSensitivity == sensitivity, onClick = {
                    shakeSensitivity = sensitivity
                    HardwareActivationSettings.setShakeSensitivity(context, sensitivity)
                }, label = { Text(sensitivity.name.lowercase().replaceFirstChar { it.titlecase(Locale.ROOT) }) })
            }
        }
        Text("Settings shortcuts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        TextButton(onClick = { onNavigate(Page.Help) }) { Text("Help and Troubleshooting") }
        TextButton(onClick = { onNavigate(Page.Privacy) }) { Text("Privacy and Permissions") }
        TextButton(onClick = { onNavigate(Page.Terms) }) { Text("Terms of Use") }
        TextButton(onClick = { onNavigate(Page.SpeechModels) }) { Text("Voice Library and storage") }
        Text("About ZERO-Assist · version 17.7.07", style = MaterialTheme.typography.labelMedium)
    }
    if (phraseEditorVisible) {
        AlertDialog(
            onDismissRequest = { phraseEditorVisible = false },
            title = { Text(if (editingPhraseId == null) "Add custom wake phrase" else "Edit wake phrase") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(phraseDraft, { phraseDraft = it }, label = { Text("Phrase") }, singleLine = true)
                    phraseEditorError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text("Vosk grammar matching is constrained by the selected recognition model's vocabulary.", style = MaterialTheme.typography.bodySmall)
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
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { phraseEditorVisible = false }) { Text("Cancel") } },
        )
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
    SpeechOutputStatus.Speaking -> "ZERO is speaking with the installed local voice."
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
        AssistantCommand.Identity -> AssistantIdentity.RESPONSE
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

private fun hasValidatedInternet(context: Context): Boolean {
    val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val network = connectivity.activeNetwork ?: return false
    val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
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