package com.zero.assistant

import androidx.compose.animation.AnimatedVisibility
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
import android.widget.Toast
import android.content.IntentFilter
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zero.assistant.core.AppTarget
import com.zero.assistant.core.AssistantCommand
import com.zero.assistant.core.AssistantAction
import com.zero.assistant.core.AssistantTrigger
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
import com.zero.assistant.core.OnScreenActionParser
import com.zero.assistant.core.AssistantIdentity
import com.zero.assistant.core.HistoryEntry
import com.zero.assistant.core.HomeSuggestion
import com.zero.assistant.core.OfflineRecognitionState
import com.zero.assistant.core.OfflineRecognitionStateMachine
import com.zero.assistant.core.SpeechLanguage
import com.zero.assistant.core.SpeechOutputController
import com.zero.assistant.core.SpeechOutputEngine
import com.zero.assistant.core.SpeechOutputStatus
import com.zero.assistant.core.OfflineTtsVoice
import com.zero.assistant.core.ConversationalVoiceEngine
import com.zero.assistant.core.WakeWord
import com.zero.assistant.core.WakePhrase
import com.zero.assistant.core.WakePhraseConfiguration
import com.zero.assistant.data.PreferencesHistoryStorage
import com.zero.assistant.data.BatteryAlertManager
import com.zero.assistant.data.BatteryStatus
import com.zero.assistant.data.ProductivityPreferences
import com.zero.assistant.data.PlannerPreferences
import com.zero.assistant.data.ReminderScheduler
import com.zero.assistant.data.ModelDownloadManager
import com.zero.assistant.data.HumanoidTtsManager
import com.zero.assistant.data.KokoroSpeechOutputEngine
import com.zero.assistant.data.WakeWordService
import com.zero.assistant.data.WakeWordSettings
import com.zero.assistant.data.AiProviderSettings
import com.zero.assistant.data.OpenAiCompatibleProvider
import com.zero.assistant.data.VaultManager
import com.zero.assistant.data.HardwareActivationSettings
import com.zero.assistant.data.AssistantActionSettings
import com.zero.assistant.data.ActionExecutor
import com.zero.assistant.core.VaultVisibility
import com.zero.assistant.data.DoubleShakeService
import com.zero.assistant.data.TorchController
import com.zero.assistant.core.ShakeSensitivity
import com.zero.assistant.data.AssistantAiProvider
import com.zero.assistant.core.ReminderRepository
import com.zero.assistant.ui.GlassCard
import com.zero.assistant.ui.CradledCapsuleShape
import com.zero.assistant.ui.ThemeModeSelector
import com.zero.assistant.ui.ZeroTheme
import com.zero.assistant.ui.ZeroThemeMode
import com.zero.assistant.ui.ZeroThemePreferences
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

private val Forest = Color.White
private val Mint = Color(0xFF202020)
private val Paper = Color.Black
private val Ink = Color.White

class MainActivity : FragmentActivity() {
    private var speechOutputController: SpeechOutputController? = null
    private val speechOutputStatus = mutableStateOf<SpeechOutputStatus>(SpeechOutputStatus.Initializing)
    private val offlineTtsVoices = mutableStateOf<List<OfflineTtsVoice>>(emptyList())
    private lateinit var voiceModelManager: ModelDownloadManager
    private lateinit var humanoidTtsManager: HumanoidTtsManager
    private lateinit var actionBrainEngine: com.zero.assistant.data.MobileActionBrainEngine
    private lateinit var batteryAlertManager: BatteryAlertManager
    private val batteryStatus = mutableStateOf(BatteryStatus(0, false))
    private val pendingVoiceCommand = mutableStateOf<String?>(null)
    private val pendingActionPage = mutableStateOf<String?>(null)
    private var speechRecognizer: SpeechRecognizer? = null
    private var recognitionActive = false
    private var recognitionResult: ((String) -> Unit)? = null
    private var recognitionStateChanged: ((Boolean) -> Unit)? = null
    private lateinit var recognitionStateMachine: OfflineRecognitionStateMachine
    private val offlineRecognitionState = mutableStateOf<OfflineRecognitionState>(OfflineRecognitionState.Idle)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingVoiceCommand.value = intent.getStringExtra(WakeWordSettings.EXTRA_COMMAND)
        pendingActionPage.value = intent.getStringExtra(ActionExecutor.EXTRA_OPEN_PAGE)
        recognitionStateMachine = OfflineRecognitionStateMachine { state ->
            runOnUiThread { offlineRecognitionState.value = state }
        }
        val initialLanguage = if (Locale.getDefault().language == "hi") SpeechLanguage.Hindi else SpeechLanguage.English
        voiceModelManager = ModelDownloadManager(this)
        humanoidTtsManager = HumanoidTtsManager(this)
        actionBrainEngine = com.zero.assistant.data.MobileActionBrainEngine(this)
        speechOutputController = SpeechOutputController(
            KokoroSpeechOutputEngine(
                AndroidTextToSpeechEngine(applicationContext),
                voiceModelManager,
                humanoidTtsManager,
            ),
        ) { status ->
            runOnUiThread {
                speechOutputStatus.value = status
                offlineTtsVoices.value = speechOutputController?.availableOfflineVoices().orEmpty()
            }
        }
        speechOutputController?.setLanguage(initialLanguage)
        speechOutputController?.initialize()
        batteryAlertManager = BatteryAlertManager(this)
        batteryAlertManager.synchronizeWorker()
        setContent {
            ZeroApp(
                voiceModelManager = voiceModelManager,
                humanoidTtsManager = humanoidTtsManager,
                actionBrainEngine = actionBrainEngine,
                refreshTtsVoices = {
                    offlineTtsVoices.value = speechOutputController?.availableOfflineVoices().orEmpty()
                },
                prepareForVoiceModelRemoval = { onReady ->
                    speechOutputController?.prepareForVoiceModelRemoval {
                        runOnUiThread { onReady() }
                    } ?: onReady()
                },
                prepareForHumanoidVoiceRemoval = { voiceId, onReady ->
                    speechOutputController?.prepareForVoiceModelRemoval(voiceId) {
                        runOnUiThread { onReady() }
                    } ?: onReady()
                },
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
                voiceCommand = pendingVoiceCommand.value,
                onVoiceCommandConsumed = { pendingVoiceCommand.value = null },
                requestedPage = pendingActionPage.value,
                onPageRequestConsumed = { pendingActionPage.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingVoiceCommand.value = intent.getStringExtra(WakeWordSettings.EXTRA_COMMAND)
        pendingActionPage.value = intent.getStringExtra(ActionExecutor.EXTRA_OPEN_PAGE)
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
        if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            onResult("Android on-device speech recognition is unavailable. You can type your command instead.")
            return
        }
        recognitionActive = true
        recognitionStateMachine.begin("Android on-device")
        recognitionResult = onResult
        recognitionStateChanged = onStateChanged
        onStateChanged(true)
        try {
            val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            speechRecognizer = recognizer
            recognitionStateMachine.listening("Android on-device")
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    finishRecognition(
                        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                            ?.takeIf(String::isNotBlank)
                            ?: "I didn't catch that. Please try again, or type your command.",
                    )
                }

                override fun onError(error: Int) {
                    finishRecognition("On-device speech recognition failed (code $error). You can type your command instead.")
                }

                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
            })
            recognizer.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                },
            )
        } catch (failure: Exception) {
            finishRecognition("On-device speech recognition couldn't start: ${failure.message ?: "recognizer unavailable"}. You can type your command.")
        } catch (failure: LinkageError) {
            finishRecognition("On-device speech recognizer could not load. You can type your command.")
        }
    }

    private fun finishRecognition(message: String) {
        if (!recognitionActive) return
        recognitionActive = false
        val currentRecognizer = speechRecognizer
        speechRecognizer = null
        runCatching { currentRecognizer?.cancel() }
        runCatching { currentRecognizer?.destroy() }
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
        if (recognitionActive) {
            finishRecognition("Voice input cancelled. You can type your command instead.")
        }
    }

    override fun onDestroy() {
        finishRecognition("Voice input was stopped. Tap the microphone to try again.")
        speechOutputController?.close()
        speechOutputController = null
        voiceModelManager.close()
        humanoidTtsManager.close()
        actionBrainEngine.close()
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

    override fun availableOfflineVoices(): List<OfflineTtsVoice> = emptyList()

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
internal enum class Page { Home, History, Settings, SpeechModels, VoiceStudio, Scanner, Planner, BatteryAlerts, Notes, MyShortcuts, LocalUtilities, Dashboard, Help, Privacy, Terms, Vault, Emergency, Diagnostics, TriggerTest }


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
    voiceModelManager: ModelDownloadManager,
    humanoidTtsManager: HumanoidTtsManager,
    actionBrainEngine: com.zero.assistant.data.MobileActionBrainEngine,
    refreshTtsVoices: () -> Unit,
    prepareForVoiceModelRemoval: (() -> Unit) -> Unit,
    prepareForHumanoidVoiceRemoval: (String, () -> Unit) -> Unit,
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
    voiceCommand: String?,
    onVoiceCommandConsumed: () -> Unit,
    requestedPage: String?,
    onPageRequestConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val history = remember { CommandHistory(PreferencesHistoryStorage(context.applicationContext)) }
    val conversationMemory = remember { com.zero.assistant.core.ConversationContextMemory() }
    val reminderRepository = remember { ReminderRepository(PlannerPreferences(context.applicationContext)) }
    val reminderScheduler = remember { ReminderScheduler(context.applicationContext, reminderRepository) }
    val productivityPreferences = remember { ProductivityPreferences(context.applicationContext) }
    val wakePreferences = remember { context.getSharedPreferences("zero_wake_word", Context.MODE_PRIVATE) }
    val ttsPreferences = remember { context.getSharedPreferences("zero_tts", Context.MODE_PRIVATE) }
    val accessibilityPreferences = remember { context.getSharedPreferences("zero_accessibility", Context.MODE_PRIVATE) }
    val themePreferences = remember { ZeroThemePreferences(context) }
    var themeMode by remember { mutableStateOf(themePreferences.mode()) }
    val aiProviderSettings = remember { AiProviderSettings(context) }
    val vaultManager = remember { VaultManager(context) }
    val aiRequestScope = rememberCoroutineScope()
    val noteRepository = remember { NoteRepository(productivityPreferences) }
    val shortcutRepository = remember { ShortcutRepository(productivityPreferences) }
    LaunchedEffect(reminderScheduler) { reminderScheduler.rescheduleAll() }
    var page by rememberSaveable { mutableStateOf(Page.Home) }
    var backDestination by rememberSaveable { mutableStateOf(Page.Home) }
    var settingsSection by rememberSaveable { mutableStateOf(SettingsSection.Hub) }
    val privacyPreferences = remember { context.getSharedPreferences(VaultVisibility.PREF_FILE, Context.MODE_PRIVATE) }
    var vaultVisible by remember { mutableStateOf(privacyPreferences.getBoolean(VaultVisibility.PREF_VISIBLE, true)) }
    fun navigateTo(destination: Page) {
        if (destination == Page.Vault && !vaultVisible) return
        if (destination == Page.Settings) settingsSection = SettingsSection.Hub
        if (destination != page) {
            backDestination = page
            page = destination
        }
    }
    fun navigateBack() {
        page = backDestination
        backDestination = Page.Home
    }
    BackHandler(enabled = page != Page.Home) {
        if (page == Page.Settings && settingsSection != SettingsSection.Hub) settingsSection = SettingsSection.Hub
        else navigateBack()
    }
    var speechOutput by rememberSaveable { mutableStateOf(true) }
    var thinkingCuesEnabled by remember { mutableStateOf(ttsPreferences.getBoolean("thinking_cues", false)) }
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
    var pendingFlashlightCommand by remember { mutableStateOf<String?>(null) }
    var pendingShortcutContinuation by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingCallNumber by remember { mutableStateOf<String?>(null) }
    var pendingCallNumberContinuation by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingCallContact by remember { mutableStateOf<String?>(null) }
    var pendingCallContactContinuation by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingWhatsAppDraft by remember { mutableStateOf<AssistantCommand.DraftWhatsAppMessage?>(null) }
    var pendingWhatsAppContinuation by remember { mutableStateOf<(() -> Unit)?>(null) }
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
    lateinit var dispatchCommandInternal: (String, (() -> Unit)?, Boolean) -> Unit

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
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val command = pendingFlashlightCommand
        pendingFlashlightCommand = null
        if (granted && command != null) executeCommand(context, command, history, lines, speak, speechOutput) { notice = it }
        if (granted && AssistantActionSettings.shouldRunGestureService(context) && command == null) {
            AssistantActionSettings.syncGestureService(context)
                .onFailure { notice = it.message ?: "Gesture service could not be started." }
        }
        else if (!granted) notice = "Camera permission was denied; background gesture monitoring cannot start."
        val continuation = pendingShortcutContinuation
        pendingShortcutContinuation = null
        continuation?.invoke()
    }
    val callPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val number = pendingCallNumber ?: return@rememberLauncherForActivityResult
        pendingCallNumber = null
        executeCommand(context, "call $number", history, lines, speak, speechOutput, allowDirectCall = granted) { notice = it }
        val continuation = pendingCallNumberContinuation
        pendingCallNumberContinuation = null
        continuation?.invoke()
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
        if (!config.enabled || !config.hasApiKey || !hasValidatedInternet(context)) {
            showResult(query, "Online AI is unavailable. ZERO can still handle supported offline commands, reminders, notes, battery status, and identity questions.")
            return
        }
        val apiKey = try {
            aiProviderSettings.apiKey()
        } catch (failure: Exception) {
            showResult(query, "The selected API key could not be decrypted (${failure.message ?: "Android Keystore error"}). Clear and save the key again in Settings.")
            return
        }
        if (apiKey.isNullOrBlank()) {
            showResult(query, "The selected API profile has no saved API key. Add one in Settings before sending an online question.")
            return
        }
        if (speechOutput && thinkingCuesEnabled) {
            speak(ConversationalVoiceEngine.thinkingCue(ConversationalVoiceEngine.languageFor(query)))
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
        dispatchCommandInternal(command, onComplete, true)
    }
    dispatchCommandInternal = commandDispatch@{ command, onComplete, allowActionBrain ->
        if (command.trim().lowercase(Locale.ROOT) in setOf("run my voice action", "run voice action", "voice action")) {
            val action = AssistantActionSettings.actionFor(context, AssistantTrigger.VOICE)
            if (action == AssistantAction.AUTO_CLICK_TARGET) {
                startListening({ spoken ->
                    if (isSpeechError(spoken)) notice = spoken
                    else dispatchCommandInternal("click $spoken", onComplete, false)
                }, { isListening = it })
                return@commandDispatch
            }
            val response = ActionExecutor.execute(context, action)
            showResult(command, response)
            onComplete?.invoke()
            return@commandDispatch
        }
        val deterministicCommand = CommandParser.parse(conversationMemory.resolveFollowUp(command))
        val isCompoundCommand = com.zero.assistant.core.MultitaskCommandExecutor.split(command).size > 1
        val isInstantCommand = deterministicCommand != AssistantCommand.Unsupported ||
            OnScreenActionParser.parse(command) != null
        if (allowActionBrain && actionBrainEngine.isActive && (isCompoundCommand || !isInstantCommand)) {
            aiRequestScope.launch {
                try {
                    val calls = actionBrainEngine.plan(conversationMemory.resolveFollowUp(command))
                    val plannedCommands = com.zero.assistant.core.MobileActionCommandRouter.toCommands(calls)
                    if (plannedCommands == null) {
                        if (calls.isNotEmpty()) {
                            showResult(command, "The offline action brain returned an unsupported action; trying ZERO's built-in parser.")
                        }
                        dispatchCommandInternal(command, onComplete, false)
                    } else {
                        val planned = plannedCommands.joinToString(" then ")
                        dispatchCommandInternal(planned, onComplete, false)
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    showResult(
                        command,
                        "The offline action brain failed (${failure.message ?: "model error"}); trying ZERO's built-in parser.",
                    )
                    dispatchCommandInternal(command, onComplete, false)
                }
            }
            return@commandDispatch
        }
        val steps = com.zero.assistant.core.MultitaskCommandExecutor.split(command)
        if (steps.size > 1) {
            var index = 0
            fun runNext() {
                if (index >= steps.size) {
                    showResult(command, "Completed ${steps.size} command steps.")
                    onComplete?.invoke()
                    return
                }
                val next = steps[index++]
                dispatchCommand(next, ::runNext)
            }
            runNext()
            return@commandDispatch
        }
        val parsed = CommandParser.parse(conversationMemory.resolveFollowUp(command))
        conversationMemory.remember(command, parsed)
        when (parsed) {
            is AssistantCommand.OpenSystemSettings -> {
                val response = openSettingsTarget(context, parsed.target)
                showResult(command, response); onComplete?.invoke()
            }
            is AssistantCommand.OpenApp -> {
                when (parsed.app) {
                    AppTarget.Scanner -> navigateTo(Page.Scanner)
                    AppTarget.Notes -> navigateTo(Page.Notes)
                    AppTarget.Vault -> navigateTo(Page.Vault)
                    else -> Unit
                }
                if (parsed.app in listOf(AppTarget.Scanner, AppTarget.Notes, AppTarget.Vault)) {
                    showResult(command, "Opening ${parsed.app.name}."); onComplete?.invoke()
                } else {
                    val response = openApp(context, parsed.app)
                    showResult(command, response); onComplete?.invoke()
                }
            }
            is AssistantCommand.CallNumber -> {
                pendingCallNumber = parsed.number
                pendingCallNumberContinuation = onComplete
            }
            is AssistantCommand.DraftCallContact -> {
                pendingCallContact = parsed.contact
                pendingCallContactContinuation = onComplete
            }
            is AssistantCommand.SearchYouTube -> {
                val response = openYouTubeSearch(context, parsed.query)
                showResult(command, response)
                onComplete?.invoke()
            }
            is AssistantCommand.DraftWhatsAppMessage -> {
                pendingWhatsAppDraft = parsed
                pendingWhatsAppContinuation = onComplete
            }
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
            AssistantCommand.ToggleFlashlight -> if (
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
            ) {
                pendingFlashlightCommand = "torch"
                pendingShortcutContinuation = onComplete
                cameraPermission.launch(Manifest.permission.CAMERA)
            } else {
                val response = toggleFlashlight(context)
                showResult(command, response)
                onComplete?.invoke()
            }
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
            is AssistantCommand.AdjustVolume -> {
                showResult(command, adjustMediaVolume(context, parsed.increase))
                onComplete?.invoke()
            }
            is AssistantCommand.SetAudioMuted -> {
                val audio = context.getSystemService(android.media.AudioManager::class.java)
                val result = runCatching {
                    val direction = if (parsed.muted) android.media.AudioManager.ADJUST_MUTE else android.media.AudioManager.ADJUST_UNMUTE
                    audio?.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, direction, android.media.AudioManager.FLAG_SHOW_UI)
                    if (audio == null) "Audio controls are unavailable." else if (parsed.muted) "Media sound muted." else "Media sound unmuted."
                }.getOrElse { "Android could not change media sound: ${it.message ?: "audio unavailable"}." }
                showResult(command, result)
                onComplete?.invoke()
            }
            is AssistantCommand.CallControl -> {
                showResult(command, com.zero.assistant.data.CallAssistantManager(context).execute(parsed.action))
                onComplete?.invoke()
            }
            is AssistantCommand.Weather -> {
                aiRequestScope.launch {
                    val response = withContext(Dispatchers.IO) {
                        com.zero.assistant.data.LiveWeatherService(context).forecast(parsed.city)
                    }
                    showResult(command, response)
                    if (speechOutput) speak(response)
                    onComplete?.invoke()
                }
            }
            else -> {
                if (OnScreenActionParser.parse(command) != null) {
                    val accessibilityManager = context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
                    val serviceEnabled = accessibilityManager
                        ?.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                        .orEmpty()
                        .any {
                            it.resolveInfo.serviceInfo.packageName == context.packageName &&
                                it.resolveInfo.serviceInfo.name == "com.zero.assistant.data.VolumeAccessibilityService"
                        }
                    when {
                        !accessibilityPreferences.getBoolean("assistant_edge_panel", false) ->
                            showResult(command, "Enable Assistant Edge Panel in Settings to use screen actions.")
                        !serviceEnabled ->
                            showResult(command, "Enable ZERO in Android Accessibility settings before using screen actions.")
                        else -> {
                            WakeWordSettings.requestOnScreenAction(context, command)
                            showResult(command, "Requested the screen action: $command")
                        }
                    }
                    onComplete?.invoke()
                } else {
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
    }
    val runCommand: (String) -> Unit = { dispatchCommand(it, null) }
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
    LaunchedEffect(requestedPage) {
        requestedPage?.let { pageName ->
            runCatching { Page.valueOf(pageName) }.getOrNull()?.let { page = it }
            onPageRequestConsumed()
        }
    }
    LaunchedEffect(speechOutputStatus, selectedTtsVoiceId, offlineTtsVoices) {
        val selectedVoiceId = selectedTtsVoiceId
        if (speechOutputStatus is SpeechOutputStatus.Ready && selectedVoiceId != null) {
            if (offlineTtsVoices.any { it.id == selectedVoiceId } && !selectTtsVoice(selectedVoiceId)) {
                selectedTtsVoiceId = null
                ttsPreferences.edit().remove("voice_id").apply()
            }
        }
    }

    ZeroTheme(themeMode) {
        val background = MaterialTheme.colorScheme.background
        Scaffold(containerColor = background, topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (page) {
                            Page.Home -> "ZERO-Assist"
                            Page.Settings -> "System Settings"
                            Page.SpeechModels -> "Voice Library"
                            Page.VoiceStudio -> "Offline Voice Studio"
                            Page.Scanner -> "Offline OCR"
                            Page.Planner -> "Planner"
                            Page.BatteryAlerts -> "Battery Alerts"
                            Page.Notes -> "Notes"
                            Page.MyShortcuts -> "My Shortcuts"
                            Page.LocalUtilities -> "Local utilities"
                            Page.Dashboard -> "Privacy & system"
                            Page.Vault -> "ZERO Vault"
                            Page.Emergency -> "Emergency Quick Mode"
                            Page.Diagnostics -> "Self-diagnostics"
                            Page.TriggerTest -> "Gesture & Shortcut Test"
                            else -> page.name
                        },
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.sp,
                    )
                },
                navigationIcon = {
                    if (page != Page.Home) {
                        IconButton(onClick = {
                            if (page == Page.Settings && settingsSection != SettingsSection.Hub) settingsSection = SettingsSection.Hub
                            else navigateBack()
                        }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    }
                },
                actions = {
                    if (page == Page.Home) {
                        TextButton(onClick = { navigateTo(Page.History) }) { Text("History", maxLines = 1, softWrap = false) }
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
                    wakeWordEnabled = wakeWordEnabled,
                    wakeWordServiceActive = wakeWordActive,
                    wakeWordError = wakeWordError,
                    onToggleWakeWord = {
                        if (!wakeWordEnabled) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                wakeWordPermission.launch(Manifest.permission.RECORD_AUDIO)
                            } else requestWakeWordStart()
                        } else {
                            stopBackgroundListening()
                            if (isListening) stopListening()
                        }
                    },
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
                    onOpenTools = {
                        navigateTo(Page.Settings)
                        settingsSection = SettingsSection.ToolsVault
                    },
                    onNavigate = { navigateTo(it) },
                )
                Page.History -> HistoryScreen(Modifier.padding(padding), history.recent(), onClear = { history.clear(); notice = "History cleared from this device." }, notice = notice)
                Page.Settings -> CapsuleSettingsScreen(
                        modifier = Modifier.padding(padding),
                        section = settingsSection,
                        onSection = { settingsSection = it },
                        speechOutput = speechOutput,
                        onSpeechOutput = { speechOutput = it },
                        thinkingCuesEnabled = thinkingCuesEnabled,
                        onThinkingCues = {
                            thinkingCuesEnabled = it
                            ttsPreferences.edit().putBoolean("thinking_cues", it).apply()
                        },
                        speechLanguageTag = speechLanguageTag,
                        onSpeechLanguage = {
                            speechLanguageTag = it
                            selectedTtsVoiceId = null
                            ttsPreferences.edit().remove("voice_id").apply()
                            setSpeechLanguage(if (it == "hi-IN") SpeechLanguage.Hindi else SpeechLanguage.English)
                        },
                        speechOutputStatus = speechOutputStatus,
                        onTestSpeech = { language ->
                            speechLanguageTag = language.tag
                            setSpeechLanguage(language)
                            testSpeech(language)
                        },
                        onStopSpeech = stopSpeech,
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
                        }
                    },
                    reducedMotion = reducedMotion,
                    onReducedMotion = {
                        reducedMotion = it
                        accessibilityPreferences.edit().putBoolean("reduced_motion", it).apply()
                    },
                    aiProviderSettings = aiProviderSettings,
                    onNavigate = { navigateTo(it) },
                    onRequestShakeCameraPermission = { cameraPermission.launch(Manifest.permission.CAMERA) },
                    themeMode = themeMode,
                    onThemeMode = { mode ->
                        themeMode = mode
                        themePreferences.setMode(mode)
                    },
                    vaultVisible = vaultVisible,
                    onVaultVisible = { visible ->
                        vaultVisible = visible
                        privacyPreferences.edit().putBoolean(VaultVisibility.PREF_VISIBLE, visible).apply()
                        if (!visible && page == Page.Vault) page = Page.Settings
                    },
                )
                Page.SpeechModels -> VoiceLibraryScreen(
                    modifier = Modifier.padding(padding),
                    voiceModelManager = voiceModelManager,
                    humanoidTtsManager = humanoidTtsManager,
                    actionBrainEngine = actionBrainEngine,
                    refreshTtsVoices = refreshTtsVoices,
                    prepareForVoiceModelRemoval = prepareForVoiceModelRemoval,
                    prepareForHumanoidVoiceRemoval = prepareForHumanoidVoiceRemoval,
                    onModelRemoved = {
                        refreshTtsVoices()
                        if (selectedTtsVoiceId?.startsWith("kokoro:") == true) {
                            selectedTtsVoiceId = null
                            ttsPreferences.edit().remove("voice_id").apply()
                        }
                    },
                    onHumanoidVoiceRemoved = { voiceId ->
                        refreshTtsVoices()
                        if (selectedTtsVoiceId == voiceId) {
                            selectedTtsVoiceId = null
                            ttsPreferences.edit().remove("voice_id").apply()
                        }
                    },
                    onHumanoidVoiceInstalled = refreshTtsVoices,
                    selectedTtsVoiceId = selectedTtsVoiceId,
                    onSelectTtsVoice = { voiceId ->
                        if (selectTtsVoice(voiceId)) {
                            selectedTtsVoiceId = voiceId
                            ttsPreferences.edit().putString("voice_id", voiceId).apply()
                            true
                        } else false
                    },
                    onPreviewTtsVoice = { voiceId ->
                        val voice = offlineTtsVoices.firstOrNull { it.id == voiceId }
                        if ((voice == null && !voiceId.startsWith("kokoro:")) || !selectTtsVoice(voiceId)) {
                            notice = "This offline voice could not be selected for preview."
                        } else {
                            selectedTtsVoiceId = voiceId
                            ttsPreferences.edit().putString("voice_id", voiceId).apply()
                            val sample = if (voice?.languageTag?.startsWith("hi", ignoreCase = true) == true) {
                                "नमस्ते। यह ${voice.displayName} की ऑफलाइन आवाज़ का नमूना है।"
                            } else {
                                "Hello. This is ${voice?.displayName ?: "ZERO's offline voice"}, running on your device."
                            }
                            speak(sample)
                        }
                    },
                )
                Page.VoiceStudio -> VoiceStudioScreen(
                    modifier = Modifier.padding(padding),
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
                Page.LocalUtilities -> LocalUtilitiesScreen(Modifier.padding(padding), noteRepository)
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
                Page.Diagnostics -> SelfDiagnosticsScreen(Modifier.padding(padding), vaultManager)
                Page.TriggerTest -> TriggerTestScreen(Modifier.padding(padding))
                Page.Help -> TextPage(Modifier.padding(padding), "Help and troubleshooting", "Try commands like:\n\n• Open YouTube / YouTube kholo\n• Torch chalu karo / Torch band karo\n• Abhi kitne baje hain? / battery status\n• note buy milk\n• remind me to call Sam, then set its date and time in Planner\n• planner / notes\n• call followed by a phone number (ZERO asks you to confirm)\n\nTo enable background wake listening, open Settings, enable Background wake listening, and grant microphone and notification permissions. Keep the persistent ZERO notification visible; its Stop action ends microphone use. Sherpa recognizes the included English wake phrases. After a wake phrase, command transcription requires Android's on-device speech recognizer (Android 12+ with an offline recognizer installed).\n\nAndroid and device manufacturers may pause or stop microphone use because of battery management, privacy controls, force-stop, or reboot policy. After reboot, Android requires you to tap a notification and reopen ZERO before microphone capture resumes. Battery optimization guidance opens Android system settings. Speech transcripts and actions are handled locally; unsupported or sensitive commands may require review in ZERO.")
                Page.Privacy -> TextPage(Modifier.padding(padding), "Privacy and permissions", "Typed commands, command execution, history, speech recognition, notes, reminders, shortcuts, and model inference run on this device. ZERO does not upload commands, audio, notes, reminders, or history; it has no account, analytics, or ads. Local records and downloaded models use app-private storage. ZERO does not retain raw audio.\n\nMicrophone capture starts after you tap Mic or explicitly enable wake listening and grant permission. When enabled, the foreground service continues Sherpa keyword spotting after the Home screen closes and shows a persistent notification. Command transcription uses Android's on-device recognizer when available. ZERO does not request network recognition. Optional online AI sends only a confirmed text query to the configured provider; microphone audio and local history are not sent.\n\nAndroid may interrupt the foreground service or defer notifications because of battery management, permission changes, force-stop, or reboot policy. After reboot, ZERO requires a user tap before microphone access resumes. Android TTS uses an installed voice that reports it does not require a network connection.")
                Page.Terms -> TextPage(Modifier.padding(padding), "Terms of Use", "ZERO is provided as-is for personal use. Speech recognition uses Sherpa keyword spotting for the included English wake phrases and Android's on-device recognizer for commands when available. Speech accuracy depends on language, microphone, device performance, and installed recognizer capabilities. Android TTS must have a compatible local voice installed. Optional online AI sends text to a provider only after confirmation and requires the user to configure and trust that provider; the provider's own terms and data handling apply. Reminder delivery may be delayed or suppressed by Android power management, notification permission, or force-stop. External app launches, flashlight hardware, and calls depend on the device. ZERO confirms a target number before direct-call permission; if permission or calling is unavailable, it opens the dialer for review.")
            }
        }
        pendingCallNumber?.let { number ->
            AlertDialog(
                onDismissRequest = {
                    pendingCallNumber = null
                    pendingCallNumberContinuation = null
                },
                title = { Text("Confirm call") },
                text = { Text("ZERO is ready to call $number. Confirm that this is the intended number.") },
                confirmButton = {
                    TextButton(onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                            pendingCallNumber = null
                            executeCommand(context, "call $number", history, lines, speak, speechOutput, allowDirectCall = true) { notice = it }
                            val continuation = pendingCallNumberContinuation
                            pendingCallNumberContinuation = null
                            continuation?.invoke()
                        } else callPermission.launch(Manifest.permission.CALL_PHONE)
                    }) { Text("Continue") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        pendingCallNumber = null
                        pendingCallNumberContinuation = null
                    }) { Text("Cancel") }
                },
            )
        }
        pendingCallContact?.let { contact ->
            AlertDialog(
                onDismissRequest = {
                    pendingCallContact = null
                    pendingCallContactContinuation = null
                },
                title = { Text("Review call request") },
                text = { Text("ZERO cannot match “$contact” to a saved phone number without contact selection. Continue to the phone app to choose the contact; no call will be placed by ZERO.") },
                confirmButton = {
                    TextButton(onClick = {
                        pendingCallContact = null
                        val opened = runCatching {
                            context.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            showResult("Call $contact", "Opened the phone app. Choose $contact and review the number before calling.")
                            true
                        }.getOrElse {
                            notice = it.message ?: "The phone app could not be opened."
                            false
                        }
                        val continuation = pendingCallContactContinuation
                        pendingCallContactContinuation = null
                        if (opened) continuation?.invoke()
                    }) { Text("Open phone") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        pendingCallContact = null
                        pendingCallContactContinuation = null
                    }) { Text("Cancel") }
                },
            )
        }
        pendingWhatsAppDraft?.let { draft ->
            AlertDialog(
                onDismissRequest = {
                    pendingWhatsAppDraft = null
                    pendingWhatsAppContinuation = null
                },
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
                        val opened = runCatching {
                            context.startActivity(Intent.createChooser(shareIntent, "Choose WhatsApp conversation"))
                            showResult("WhatsApp message draft for ${draft.contact}", "Opened the WhatsApp draft. Select ${draft.contact} and tap Send to send the message.")
                            true
                        }.getOrElse {
                            notice = "WhatsApp could not open the message draft. No message was sent."
                            false
                        }
                        val continuation = pendingWhatsAppContinuation
                        pendingWhatsAppContinuation = null
                        if (opened) continuation?.invoke()
                    }) { Text("Open draft") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        pendingWhatsAppDraft = null
                        pendingWhatsAppContinuation = null
                    }) { Text("Cancel") }
                },
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
    wakeWordEnabled: Boolean,
    wakeWordServiceActive: Boolean,
    wakeWordError: String?,
    onToggleWakeWord: () -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
    onStopListening: () -> Unit,
    onSuggestion: (HomeSuggestion) -> Unit,
    onOpenTools: () -> Unit,
    onNavigate: (Page) -> Unit,
) {
    var showKeyboard by rememberSaveable { mutableStateOf(false) }
    val latestAssistantResponse = lines.lastOrNull { it.fromZero }?.text
    val emotionCue = remember(latestAssistantResponse) { inferOrbEmotionCue(latestAssistantResponse) }
    val suggestions = remember {
        listOf(
            HomeSuggestion("Open YouTube", "open youtube"),
            HomeSuggestion("Planner", "planner"),
            HomeSuggestion("Notes", "notes"),
            HomeSuggestion("Battery", "battery status"),
        )
    }
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 18.dp).padding(bottom = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (MaterialTheme.colorScheme.background == Color.White) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        Color(0xFF23283A)
                    },
                ),
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        if (batteryStatus.percent in 0..100) "${batteryStatus.percent}%" else "--%",
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("  •  ", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                    Text(
                        "On-device speech",
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text("  •  ", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                    Text(
                        "Wake: ${if (wakeWordEnabled) "On" else "Off"}",
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onToggleWakeWord),
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (wakeWordServiceActive && MaterialTheme.colorScheme.background != Color.White) {
                            Color(0xFF56D9E7)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
            Text(
                "Welcome to ZERO · Made by ❤️ @Shree",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
            )
            AssistantOrb(
                modifier = Modifier.fillMaxWidth().weight(1f),
                activity = orbActivity,
                audioAmplitude = 0f,
                reduceMotion = reducedMotion,
                size = 1.dp,
                emotionCue = emotionCue,
            )
            val activityMessage = when (orbActivity) {
                OrbActivity.Idle -> "ZERO READY · OFFLINE COMMANDS AVAILABLE"
                OrbActivity.Listening -> "LISTENING · MICROPHONE IN USE"
                OrbActivity.Processing -> "PROCESSING LOCALLY"
                OrbActivity.Speaking -> "ZERO IS SPEAKING"
            }
            Text(
                activityMessage,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
            if (wakeWordEnabled && !wakeWordServiceActive && wakeWordError != null) {
                Text(wakeWordError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
            }
            HomeSuggestionRail(suggestions = suggestions, onSuggestion = onSuggestion)
            lines.lastOrNull()?.let { lastLine ->
                Text(
                    if (lastLine.fromZero) lastLine.text else "You: ${lastLine.text}",
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    softWrap = false,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (val state = recognitionState) {
                is OfflineRecognitionState.LoadingModel -> Text("Loading local speech model…", style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
                is OfflineRecognitionState.Listening -> Text("Listening with ${state.modelId}…", style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
                is OfflineRecognitionState.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
                OfflineRecognitionState.Idle, OfflineRecognitionState.Cancelled, is OfflineRecognitionState.Result -> Unit
            }
            if (notice != null) Text(notice, Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
            AnimatedVisibility(visible = showKeyboard || input.isNotBlank()) {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInput,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ask ZERO anything…", maxLines = 1, softWrap = false) },
                    shape = RoundedCornerShape(18.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    trailingIcon = {
                        IconButton(onClick = onSend, enabled = input.isNotBlank()) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Send command", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                )
            }
        }
        HomeCapsuleDock(
            modifier = Modifier.align(Alignment.BottomCenter),
            isListening = isListening,
            onTools = onOpenTools,
            onKeyboard = { showKeyboard = !showKeyboard },
            onPlanner = { onNavigate(Page.Planner) },
            onVault = { onNavigate(Page.Vault) },
            onMic = if (isListening) onStopListening else onMic,
        )
    }
}

@Composable
private fun HomeSuggestionRail(
    suggestions: List<HomeSuggestion>,
    onSuggestion: (HomeSuggestion) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        suggestions.forEach { suggestion ->
            FilterChip(
                selected = false,
                onClick = { onSuggestion(suggestion) },
                shape = RoundedCornerShape(50),
                label = { Text(suggestion.label, maxLines = 1, softWrap = false) },
            )
        }
    }
}

@Composable
private fun HomeCapsuleDock(
    modifier: Modifier = Modifier,
    isListening: Boolean,
    onTools: () -> Unit,
    onKeyboard: () -> Unit,
    onPlanner: () -> Unit,
    onVault: () -> Unit,
    onMic: () -> Unit,
) {
    val dockShape = CradledCapsuleShape()
    val isLight = MaterialTheme.colorScheme.background == Color.White
    Box(
        modifier
            .fillMaxWidth()
            .height(70.dp)
            .padding(horizontal = 18.dp)
            .background(MaterialTheme.colorScheme.surface, dockShape)
            .border(1.dp, MaterialTheme.colorScheme.onSurface, dockShape),
    ) {
        Row(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            DockIconButton("⊞", "Tools Grid", onTools)
            DockIconButton("⌨", "Keyboard or text input", onKeyboard)
            Spacer(Modifier.width(58.dp))
            DockIconButton("◷", "Daily Planner", onPlanner, fontSize = 30.sp)
            IconButton(onClick = onVault, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Lock, contentDescription = "ZERO Vault", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-24).dp)
                .size(56.dp)
                .background(
                    if (isLight) {
                        Brush.radialGradient(listOf(Color.White, Color.White))
                    } else {
                        Brush.radialGradient(listOf(Color(0xFF5AF3FF), Color(0xFF08AFC7), Color(0xFF17485B)))
                    },
                    CircleShape,
                )
                .border(1.dp, if (isLight) Color.Black else Color(0xFF96F6FF), CircleShape)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onMic)
                .semantics {
                    contentDescription = if (isListening) "Stop listening" else "Start voice input"
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (isListening) "STOP" else "MIC",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun DockIconButton(symbol: String, description: String, onClick: () -> Unit, fontSize: androidx.compose.ui.unit.TextUnit = 22.sp) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Text(
            symbol,
            modifier = Modifier.semantics { contentDescription = description },
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = fontSize,
            maxLines = 1,
            softWrap = false,
        )
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
    themeMode: ZeroThemeMode,
    onThemeMode: (ZeroThemeMode) -> Unit,
    aiProviderSettings: AiProviderSettings,
    onNavigate: (Page) -> Unit,
    onRequestShakeCameraPermission: () -> Unit,
    vaultVisible: Boolean,
    onVaultVisible: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var volumeShortcut by remember { mutableStateOf(HardwareActivationSettings.volumePairEnabled(context)) }
    var doubleShake by remember { mutableStateOf(HardwareActivationSettings.doubleShakeEnabled(context)) }
    var systemActionError by remember { mutableStateOf<String?>(null) }
    val accessibilityPreferences = remember {
        context.getSharedPreferences("zero_accessibility", Context.MODE_PRIVATE)
    }
    var assistantEdgePanelEnabled by remember {
        mutableStateOf(accessibilityPreferences.getBoolean("assistant_edge_panel", false))
    }
    var shakeSensitivity by remember { mutableStateOf(HardwareActivationSettings.shakeSensitivity(context)) }
    var editingPhraseId by remember { mutableStateOf<String?>(null) }
    var phraseDraft by remember { mutableStateOf("") }
    var phraseEditorVisible by remember { mutableStateOf(false) }
    var phraseEditorError by remember { mutableStateOf<String?>(null) }
    var vaultMenuExpanded by remember { mutableStateOf(false) }
    var aiConfiguration by remember { mutableStateOf(aiProviderSettings.configuration()) }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AssistantOrb(OrbActivity.Idle, reducedMotion = true, Modifier.size(58.dp))
            Column {
                Text("ZERO SYSTEM SETTINGS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Local controls · version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        GlassCard {
            Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            ThemeModeSelector(themeMode, onThemeMode)
            Text(if (themeMode == ZeroThemeMode.System) "Selected: System / Default" else "Selected: ${themeMode.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingSwitch("Reduce motion", "Keep the orb and suggestion rail static.", reducedMotion, onReducedMotion)
        val speechOutputReady = speechOutputStatus is SpeechOutputStatus.Ready || speechOutputStatus is SpeechOutputStatus.ReadyWithVoice
        val speechEngineReady = speechOutputReady || speechOutputStatus is SpeechOutputStatus.LanguageUnavailable
        SettingSwitch("Speak responses", speechOutputStatusMessage(speechOutputStatus), speechOutput, onSpeechOutput, enabled = speechOutputReady)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onTestSpeech(SpeechLanguage.English) },
                enabled = speechEngineReady,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface),
            ) { Text("Test English") }
            Button(
                onClick = { onTestSpeech(SpeechLanguage.Hindi) },
                enabled = speechEngineReady,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface),
            ) { Text("Test Hindi") }
            TextButton(onClick = onStopSpeech, enabled = speechEngineReady) { Text("Stop") }
        }
        Text("Speech recognition", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text("Push-to-talk uses Android's on-device speech recognizer when available (Android 12+); typed commands always remain available.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        TextButton(onClick = { onNavigate(Page.SpeechModels) }) { Text("Voice Library") }
        TextButton(onClick = { onNavigate(Page.VoiceStudio) }) { Text("Offline Voice Studio") }
        TextButton(onClick = { onNavigate(Page.Scanner) }) { Text("Knowledge Scanner") }
        TextButton(onClick = { onNavigate(Page.Planner) }) { Text("Daily Planner") }
        TextButton(onClick = { onNavigate(Page.BatteryAlerts) }) { Text("Battery Alerts") }
        TextButton(onClick = { onNavigate(Page.Notes) }) { Text("Notes") }
        TextButton(onClick = { onNavigate(Page.MyShortcuts) }) { Text("My Shortcuts") }
        TextButton(onClick = { onNavigate(Page.LocalUtilities) }) { Text("Local utilities & search") }
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
        Text("Voice Library lists model downloads only; it does not expose Android system TTS voices.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
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
        Text("Built-in wake phrases: Hey ZERO, OK ZERO, and Hey Shree. You can add up to three custom phrases.", style = MaterialTheme.typography.bodySmall)
        Text("Wake-word diagnostics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text("Wake engine: ${if (wakeWordActive) "Sherpa offline English KWS is running" else "Sherpa offline English KWS is stopped"}")
        Text("Microphone permission: ${if (microphonePermissionGranted) "granted" else "not granted"}")
        Text("Listening service: ${if (wakeWordActive) "running" else if (wakeWordEnabled) "enabled but not running" else "stopped"}")
        Text("Sherpa KWS detects the included English phrases only. Custom or Hindi wake phrases are not supported by this bundled KWS model.", style = MaterialTheme.typography.bodySmall)
        Text("Last detection: ${if (wakeWordLastDetection > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(wakeWordLastDetection)) else "Never"}", style = MaterialTheme.typography.bodySmall)
        wakeWordError?.let { Text("Last service error: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        wakeWordPhrases.forEach { phrase ->
            SettingSwitch(
                phrase.phrase,
                when {
                    !phrase.enabled -> "Disabled"
                    phrase.isDefault -> "Built-in English keyword; it remains configured unless you disable it."
                    phrase.isDefault -> "Built-in English wake phrase"
                    else -> "Saved phrase; not recognized by the bundled Sherpa model"
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
                enabled = wakeWordPhrases.count { !it.isDefault } < WakePhraseConfiguration.MAX_CUSTOM_PHRASES,
            ) { Text("Add custom phrase") }
            TextButton(onClick = {
                onWakeWordPhrasesChanged(WakePhraseConfiguration.enableBuiltInPhrases(wakeWordPhrases))
            }) { Text("Enable built-in phrases") }
        }
        Text("Say an enabled built-in English phrase, pause for the offline acknowledgement, then speak a command. On-device command recognition depends on an installed Android recognizer. Android may stop microphone access because of force-stop, battery controls, reboot policy, or device restrictions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        TextButton(onClick = {
            runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }) { Text("Battery optimization guidance") }
        MultiApiProviderSettings(
            settings = aiProviderSettings,
            initialConfiguration = aiConfiguration,
            onConfigurationChanged = { aiConfiguration = it },
        )
        Text("Hardware activation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        SettingSwitch("Volume Up + Volume Down shortcut", "Optional accessibility key-event shortcut. Turn it on here, then enable ZERO in Android Accessibility settings. Ordinary volume presses are not consumed; the Volume Up + Volume Down accessibility shortcut remains system-controlled.", volumeShortcut, { enabled ->
            volumeShortcut = enabled
            HardwareActivationSettings.setVolumePairEnabled(context, enabled)
            if (enabled) context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
        SettingSwitch(
            "Assistant edge panel",
            "Show a small status panel after a wake phrase over other apps. Enable ZERO in Android Accessibility settings. The lock screen only shows basic status; calls and sensitive actions need unlock and review.",
            assistantEdgePanelEnabled,
            { enabled ->
                assistantEdgePanelEnabled = enabled
                accessibilityPreferences.edit().putBoolean("assistant_edge_panel", enabled).apply()
                if (enabled) {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else {
                    WakeWordSettings.dismissAssistantPanel(context)
                }
            },
        )
        SettingSwitch("Double-shake flashlight", "Requires an accelerometer and torch. Uses a visible foreground notification while running.", doubleShake, { enabled ->
            HardwareActivationSettings.setDoubleShakeEnabled(context, enabled)
            doubleShake = enabled
            if (enabled && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                onRequestShakeCameraPermission()
                return@SettingSwitch
            }
            AssistantActionSettings.syncGestureService(context)
                .onFailure { systemActionError = it.message ?: "The gesture service could not be updated." }
        })
        systemActionError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
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
        Text("About ZERO-Assist · version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium)
    }
    if (phraseEditorVisible) {
        AlertDialog(
            onDismissRequest = { phraseEditorVisible = false },
            title = { Text(if (editingPhraseId == null) "Add custom wake phrase" else "Edit wake phrase") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(phraseDraft, { phraseDraft = it }, label = { Text("Phrase") }, singleLine = true)
                    phraseEditorError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text("The bundled Sherpa model recognizes only the built-in English phrases. Saved custom phrases are not active until a compatible wake model is added.", style = MaterialTheme.typography.bodySmall)
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

internal fun speechOutputStatusMessage(status: SpeechOutputStatus): String = when (status) {
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
        is AssistantCommand.OpenSystemSettings -> openSettingsTarget(context, command.target)
        is AssistantCommand.OpenApp -> when (command.app) {
            AppTarget.Scanner -> "ZERO Scanner is available from this command, but the action button is opening the screen."
            AppTarget.Notes -> "ZERO Notes are available from this command, but the action button is opening the screen."
            AppTarget.Vault -> "ZERO Vault is available from this command, but authentication is required."
            else -> openApp(context, command.app)
        }
        is AssistantCommand.SetFlashlight -> setFlashlight(context, command.enabled)
        is AssistantCommand.AdjustVolume -> adjustMediaVolume(context, command.increase)
        is AssistantCommand.SetAudioMuted -> {
            val audio = context.getSystemService(android.media.AudioManager::class.java)
            val direction = if (command.muted) android.media.AudioManager.ADJUST_MUTE else android.media.AudioManager.ADJUST_UNMUTE
            runCatching {
                audio?.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, direction, 0)
                if (audio == null) "Audio controls are unavailable." else if (command.muted) "Media sound muted." else "Media sound unmuted."
            }.getOrElse { "Android could not change media sound: ${it.message ?: "audio unavailable"}." }
        }
        is AssistantCommand.CallControl -> com.zero.assistant.data.CallAssistantManager(context).execute(command.action)
        is AssistantCommand.Weather -> "Weather requests are available from the voice command dispatcher."
        AssistantCommand.ToggleFlashlight -> toggleFlashlight(context)
        is AssistantCommand.CallNumber -> callNumber(context, command.number, allowDirectCall)
        is AssistantCommand.DraftCallContact -> "Choose a contact in the phone app; ZERO does not read contacts or place calls by name."
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

private fun adjustMediaVolume(context: Context, increase: Boolean): String {
    val audioManager = context.getSystemService(android.media.AudioManager::class.java)
        ?: return "Audio controls are unavailable on this device."
    val direction = if (increase) android.media.AudioManager.ADJUST_RAISE else android.media.AudioManager.ADJUST_LOWER
    return try {
        audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, direction, android.media.AudioManager.FLAG_SHOW_UI)
        if (increase) "Media volume increased." else "Media volume decreased."
    } catch (failure: SecurityException) {
        "Android denied the media volume change: ${failure.message ?: "permission unavailable"}."
    }
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
        "hotspot" -> Settings.ACTION_WIRELESS_SETTINGS
        "dnd" -> Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
        "battery_saver" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
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
        AppTarget.YouTube -> launcherIntent(context, "com.google.android.youtube") ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://youtube.com")).addCategory(Intent.CATEGORY_BROWSABLE)
        AppTarget.Facebook -> launcherIntent(context, "com.facebook.katana")
        AppTarget.Instagram -> launcherIntent(context, "com.instagram.android")
        AppTarget.WhatsApp -> launcherIntent(context, "com.whatsapp")
        AppTarget.Music -> launcherIntent(context, "com.spotify.music")
            ?: launcherIntent(context, "com.google.android.music")
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com")).addCategory(Intent.CATEGORY_BROWSABLE)
        AppTarget.Maps -> launcherIntent(context, "com.google.android.apps.maps") ?: Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))
        AppTarget.Browser -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
        AppTarget.Phone -> Intent(Intent.ACTION_DIAL)
        AppTarget.Messages -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)
        AppTarget.Calculator -> launcherIntent(context, "com.google.android.calculator") ?: launcherIntent(context, "com.android.calculator2")
        AppTarget.Gallery -> Intent(Intent.ACTION_GET_CONTENT).setType("image/*")
        AppTarget.Scanner, AppTarget.Notes, AppTarget.Vault -> null
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

private fun toggleFlashlight(context: Context): String = TorchController.toggle(context).fold(
    onSuccess = { enabled -> if (enabled) "Flashlight is on." else "Flashlight is off." },
    onFailure = { failure ->
        when (failure) {
            is SecurityException -> "Camera permission is required to control the flashlight."
            is android.hardware.camera2.CameraAccessException -> "Flashlight hardware is busy or unavailable."
            else -> failure.message ?: "Flashlight state could not be changed."
        }
    },
)

private fun setFlashlight(context: Context, enabled: Boolean): String {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
        return "Camera permission is needed to control the flashlight. Use the Torch shortcut to grant it."
    }

    val cameraManager = context.getSystemService(CameraManager::class.java)
        ?: return "This device's camera service is unavailable."
    return try {
        val cameraId = cameraManager.cameraIdList.firstOrNull { cameraManager.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            ?: return "This device doesn't have a flashlight."
        TorchController.set(context, enabled).fold(
            onSuccess = { current -> if (current) "Flashlight is on." else "Flashlight is off." },
            onFailure = { "I couldn't control the flashlight: ${it.message ?: "hardware unavailable"}." },
        )
    } catch (_: SecurityException) {
        "I couldn't control the flashlight because camera access was denied."
    } catch (_: CameraAccessException) {
        "I couldn't control the flashlight because camera hardware is busy or unavailable."
    } catch (_: IllegalArgumentException) {
        "This device doesn't support flashlight control."
    }
}