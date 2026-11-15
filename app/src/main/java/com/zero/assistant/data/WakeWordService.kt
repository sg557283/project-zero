package com.zero.assistant.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zero.assistant.MainActivity
import com.zero.assistant.R
import com.zero.assistant.FloatingBotOverlayService
import com.zero.assistant.core.AssistantCommand
import com.zero.assistant.core.CommandParser
import com.zero.assistant.core.AssistantIdentity
import com.zero.assistant.core.OnScreenActionParser
import com.zero.assistant.core.WakeWord
import com.zero.assistant.data.TorchController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class WakeWordService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var commandRecognizer: SpeechRecognizer? = null
    private var audioCapture: VoiceAudioCapture? = null
    private val voiceCaptureEffects = VoiceCaptureEffects()
    private var serviceRunning = false
    private var awaitingCommand = false
    private var commandCapture = false
    private var oneShotCommand = false
    private var oneShotCommandRequested = false
    private var captureActive = false
    private var transitioningFromWake = false
    private var speaking = false
    private var restartPending = false
    private var lastWakeAt = 0L
    private var localTts: TextToSpeech? = null
    private var smartAudioFocus: SmartAudioFocusManager? = null
    private var microphoneAvailable = true
    private var cpuWakeLock: PowerManager.WakeLock? = null
    private var localTtsReady = false
    private var localTtsUnavailable = false
    private var pendingSpeech: Pair<String, () -> Unit>? = null
    private var speechContinuation: (() -> Unit)? = null
    private val phraseChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (serviceRunning && !awaitingCommand && !commandCapture &&
                (intent?.action == WakeWordSettings.ACTION_PHRASES_CHANGED ||
                    intent?.action == WakeWordSettings.ACTION_WAKE_SENSITIVITY_CHANGED)
            ) {
                startCapture(commandMode = false)
            }
        }
    }
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (!serviceRunning) return
            if (captureActive || awaitingCommand || speaking) {
                WakeWordSettings.recordHeartbeat(this@WakeWordService)
                acquireCpuWakeLock()
            }
            handler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }
    private val commandCaptureTimeout = Runnable {
        if (captureActive && commandCapture) handleCommandTimeout()
    }

    override fun onCreate() {
        super.onCreate()
        smartAudioFocus = SmartAudioFocusManager(this) { available ->
            handler.post {
                if (microphoneAvailable == available) return@post
                microphoneAvailable = available
                if (!available && captureActive) {
                    stopCapture()
                    WakeWordSettings.updateAssistantPanel(this, "Microphone is busy or a phone call is active. ZERO will resume when available.")
                } else if (available && serviceRunning && !captureActive && !speaking) {
                    startCapture(commandMode = awaitingCommand)
                }
            }
        }.also { manager ->
            runCatching { manager.start() }
                .onFailure { Log.w(TAG, "Microphone coexistence monitoring is unavailable.", it) }
        }
        createNotificationChannel()
        initializeLocalTts()
        ContextCompat.registerReceiver(
            this,
            phraseChangedReceiver,
            IntentFilter().apply {
                addAction(WakeWordSettings.ACTION_PHRASES_CHANGED)
                addAction(WakeWordSettings.ACTION_WAKE_SENSITIVITY_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == WakeWordSettings.ACTION_ONE_SHOT_COMMAND) {
            oneShotCommand = true
            oneShotCommandRequested = true
        }
        if (intent?.action == WakeWordSettings.ACTION_ACTIVATE) {
            WakeWordSettings.setEnabled(this, true)
        }
        if (intent?.action == WakeWordSettings.ACTION_STOP) {
            WakeWordSettings.setEnabled(this, false)
            stopListeningService()
            return START_NOT_STICKY
        }
        if (intent?.action == WakeWordSettings.ACTION_START) WakeWordSettings.setEnabled(this, true)
        if (!WakeWordSettings.enabled(this) && !oneShotCommand) {
            stopListeningService()
            return START_NOT_STICKY
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            WakeWordSettings.setActive(this, false, "Microphone permission is required to resume background listening.")
            WakeWordSettings.setEnabled(this, false)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            WakeWordSettings.setActive(this, false, "Notification permission is required to show persistent microphone controls.")
            WakeWordSettings.setEnabled(this, false)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        serviceRunning = true
        try {
            startForegroundCompat(buildNotification("Wake listening is active. Microphone access is in use."))
        } catch (failure: SecurityException) {
            handleForegroundStartFailure(startId, failure)
            return START_NOT_STICKY
        } catch (failure: IllegalStateException) {
            handleForegroundStartFailure(startId, failure)
            return START_NOT_STICKY
        }
        acquireCpuWakeLock()
        handler.removeCallbacks(heartbeatRunnable)
        handler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS)
        if (oneShotCommandRequested) {
            oneShotCommandRequested = false
            awaitingCommand = true
            smartAudioFocus?.requestCommandFocus()
            if (!startCapture(commandMode = true)) {
                awaitingCommand = false
                finishOneShotCommand()
            }
            return START_NOT_STICKY
        }
        if (audioCapture == null && !awaitingCommand) {
            startCapture(commandMode = false)
            return START_STICKY
        }
        beginLoadingModel()
        return START_STICKY
    }

    private fun handleForegroundStartFailure(startId: Int, failure: RuntimeException) {
        Log.e(TAG, "Microphone foreground service could not start.", failure)
        serviceRunning = false
        WakeWordSettings.setEnabled(this, false)
        WakeWordSettings.setActive(
            this,
            false,
            failure.message ?: "Android denied microphone foreground-service startup. Enable wake listening again while ZERO is visible.",
        )
        releaseCpuWakeLock()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf(startId)
    }

    private fun beginLoadingModel() {
        if (!serviceRunning) return
        if (!startCapture(commandMode = false)) {
            updateNotification("Sherpa offline wake detection could not start.")
        }
    }

    private fun startCapture(commandMode: Boolean): Boolean {
        if (!serviceRunning || (!WakeWordSettings.enabled(this) && !oneShotCommand)) return false
        if (!microphoneAvailable) return false
        if (!commandMode) smartAudioFocus?.releaseCommandFocus()
        stopCapture()
        return try {
            if (commandMode) return startOnDeviceCommandRecognition()
            val enabledPhrases = WakeWordSettings.phrases(this).filter { it.enabled }
            if (enabledPhrases.isEmpty()) {
                markEngineUnavailable("Enable at least one wake phrase in Settings.")
                updateNotification("No wake phrases are enabled.")
                return false
            }
            val sensitivity = WakeWordSettings.sensitivity(this)
            val capture = VoiceAudioCapture(
                context = this,
                sensitivity = sensitivity,
                onKeyword = { keyword -> handler.post { handleWakeKeyword(keyword) } },
                onFailure = { failure -> handler.post { handleCaptureFailure(failure) } },
            )
            if (!capture.hasKeywordSpotter) {
                capture.stop()
                markEngineUnavailable(capture.initializationWarnings.joinToString(". ").ifBlank { "Sherpa wake model is unavailable." })
                return false
            }
            audioCapture = capture
            smartAudioFocus?.setOwnAudioSessionId(capture.audioSessionId)
            voiceCaptureEffects.attach(
                capture.audioSessionId,
                enabled = true,
            )
            captureActive = true
            capture.start()
            val initializationWarning = capture.initializationWarnings.joinToString(". ")
            if (initializationWarning.isNotBlank()) Log.w(TAG, initializationWarning)
            acquireCpuWakeLock()
            WakeWordSettings.setActive(this, true, initializationWarning.takeIf { it.isNotBlank() })
            handler.removeCallbacks(heartbeatRunnable)
            handler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS)
            val phraseText = enabledPhrases.joinToString(", ") { it.phrase }
            updateNotification("Listening for $phraseText using Sherpa English KWS.")
            true
        } catch (failure: Exception) {
            stopCapture()
            markEngineUnavailable(failure.message ?: "Microphone could not start.")
            updateNotification("Microphone could not start: ${failure.message ?: "audio input unavailable"}")
            false
        } catch (failure: LinkageError) {
            stopCapture()
            markEngineUnavailable("Offline audio runtime could not be loaded.")
            updateNotification("Offline audio runtime could not be loaded: ${failure.message ?: "native library unavailable"}")
            false
        }
    }

    private fun startOnDeviceCommandRecognition(): Boolean {
        if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            updateNotification("On-device speech recognition is unavailable on this device. Open ZERO to type the command.")
            WakeWordSettings.updateAssistantPanel(this, "On-device command recognition is unavailable. You can type the command.")
            return false
        }
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        commandRecognizer = recognizer
        commandCapture = true
        captureActive = true
        smartAudioFocus?.setOwnAudioSessionId(0)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                updateNotification("Say your command. On-device recognition is active.")
                WakeWordSettings.updateAssistantPanel(this@WakeWordService, "Listening. Say your command now.")
            }

            override fun onResults(results: Bundle?) {
                val transcript = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (transcript.isBlank()) handleCommandTimeout() else handleCapturedCommand(transcript)
            }

            override fun onError(error: Int) {
                handleCaptureFailure(IllegalStateException("On-device speech recognition failed (code $error)."))
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
        })
        val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        recognizer.startListening(request)
        handler.postDelayed(commandCaptureTimeout, COMMAND_CAPTURE_TIMEOUT_MS.toLong())
        updateNotification("On-device speech recognition is listening.")
        return true
    }

    private fun handleWakeKeyword(keyword: String) {
        if (!serviceRunning || !captureActive || commandCapture || speaking || transitioningFromWake) return
        val match = WakeWord.match(keyword, WakeWordSettings.phrases(this)) ?: return
        val now = System.currentTimeMillis()
        if (now - lastWakeAt < WAKE_DEBOUNCE_MS) return
        lastWakeAt = now
        WakeWordSettings.recordDetection(this)
        smartAudioFocus?.requestCommandFocus()
        WakeWordSettings.updateAssistantPanel(this, "Wake phrase recognized. ZERO is getting ready.")
        beginCommandSession(match.second.command)
    }

    private fun handleCaptureFailure(failure: Exception) {
        if (!captureActive) return
        val wasCommandCapture = commandCapture
        val message = "Microphone error: ${failure.message ?: "audio input unavailable"}"
        stopCapture()
        WakeWordSettings.setActive(this, false, message)
        releaseCpuWakeLock()
        updateNotification(message)
        if (wasCommandCapture) handleCommandTimeout() else scheduleRestart()
    }

    private fun beginCommandSession(commandAfterWake: String) {
        if (awaitingCommand) return
        awaitingCommand = true
        transitioningFromWake = true
        stopCapture()
        updateNotification("Wake phrase heard. Preparing command capture.")
        speakThen("Yes boss.") {
            transitioningFromWake = false
            if (commandAfterWake.isNotBlank()) handleCapturedCommand(commandAfterWake)
            else if (!startCapture(commandMode = true)) handleCommandTimeout()
        }
    }

    private fun handleCapturedCommand(text: String) {
        if (!serviceRunning || !awaitingCommand) return
        awaitingCommand = false
        stopCapture()
        WakeWordSettings.updateAssistantPanel(
            this,
            "Thinking about your command…",
            faceMode = com.zero.assistant.BotFaceMode.Thinking.name,
        )
        if (OnScreenActionParser.parse(text) != null) {
            if (!getSharedPreferences("zero_accessibility", Context.MODE_PRIVATE)
                    .getBoolean("assistant_edge_panel", false)
            ) {
                requestForegroundReview(text, "Enable Assistant Edge Panel and ZERO Accessibility service for screen actions.")
                return
            }
            val accessibilityManager = getSystemService(android.view.accessibility.AccessibilityManager::class.java)
            val serviceEnabled = accessibilityManager
                ?.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .orEmpty()
                .any {
                    it.resolveInfo.serviceInfo.packageName == packageName &&
                        it.resolveInfo.serviceInfo.name == "com.zero.assistant.data.VolumeAccessibilityService"
                }
            if (!serviceEnabled) {
                requestForegroundReview(text, "Enable ZERO in Android Accessibility settings before using screen actions.")
                return
            }
            WakeWordSettings.updateAssistantPanel(
                this,
                "Acting on screen…",
                faceMode = com.zero.assistant.BotFaceMode.ActingOnScreen.name,
            )
            WakeWordSettings.requestOnScreenAction(this, text)
            transitioningFromWake = true
            speakThen("Done boss.") {
                transitioningFromWake = false
                startCapture(commandMode = false)
            }
            return
        }
        when (val command = CommandParser.parse(text)) {
            AssistantCommand.Identity -> respondAndResume(AssistantIdentity.RESPONSE)
            AssistantCommand.TellTime -> respondAndResume("It is ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())}.")
            AssistantCommand.BatteryStatus -> respondAndResume(readBatteryStatus())
            is AssistantCommand.AdjustVolume -> respondAndResume(adjustMediaVolume(command.increase))
            is AssistantCommand.SetAudioMuted -> respondAndResume(setMediaMuted(command.muted))
            is AssistantCommand.CallControl ->
                respondAndResume(CallAssistantManager(this).execute(command.action))
            is AssistantCommand.Weather -> serviceScope.launch {
                val forecast = withContext(Dispatchers.IO) {
                    LiveWeatherService(this@WakeWordService).forecast(command.city)
                }
                respondAndResume(forecast)
            }
            is AssistantCommand.DraftCallContact ->
                requestForegroundReview(text, "Open ZERO to review the contact request. ZERO will not call a contact without choosing a number.")
            AssistantCommand.ToggleFlashlight -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    requestForegroundReview(text, "Camera permission is needed. Tap the ZERO notification to review this command.")
                } else respondAndResume(toggleFlashlight())
            }
            is AssistantCommand.SetFlashlight -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    requestForegroundReview(text, "Camera permission is needed. Tap the ZERO notification to review this command.")
                } else respondAndResume(setFlashlight(command.enabled))
            }
            else -> requestForegroundReview(text, "Command captured. Tap the ZERO notification to review and continue.")
        }
    }

    private fun handleCommandTimeout() {
        if (!serviceRunning || !awaitingCommand) return
        awaitingCommand = false
        transitioningFromWake = true
        stopCapture()
        updateNotification("No command was recognized. Returning to wake listening.")
        WakeWordSettings.updateAssistantPanel(this, "I didn't catch that. Say the command again.", 6_000L)
        speakThen("I didn't catch that. Please try again.") {
            resumeAfterCommand()
        }
    }

    private fun respondAndResume(message: String) {
        updateNotification(message)
        WakeWordSettings.updateAssistantPanel(
            this,
            "Done boss. $message",
            10_000L,
            faceMode = com.zero.assistant.BotFaceMode.Success.name,
        )
        transitioningFromWake = true
        speakThen("Done boss. $message") {
            resumeAfterCommand()
        }
    }

    private fun requestForegroundReview(command: String, message: String) {
        transitioningFromWake = true
        WakeWordSettings.updateAssistantPanel(this, "This action needs review. Unlock and open ZERO's notification.", 10_000L)
        postReviewNotification(command, message)
        speakThen("I captured that. Open ZERO to review the command.") {
            resumeAfterCommand()
        }
    }

    private fun resumeAfterCommand() {
        transitioningFromWake = false
        if (oneShotCommand) {
            finishOneShotCommand()
            return
        }
        if (!serviceRunning) return
        if (!awaitingCommand && !captureActive) startCapture(commandMode = false)
    }

    private fun finishOneShotCommand() {
        oneShotCommand = false
        oneShotCommandRequested = false
        awaitingCommand = false
        if (WakeWordSettings.enabled(this)) {
            if (serviceRunning && !captureActive) startCapture(commandMode = false)
        } else {
            stopListeningService()
        }
    }

    private fun initializeLocalTts() {
        localTts = runCatching { TextToSpeech(applicationContext) { status ->
            val engine = localTts
            val availableVoices = engine?.voices.orEmpty()
                .filterNot { it.isNetworkConnectionRequired }
            val selectedVoiceId = getSharedPreferences("zero_tts", Context.MODE_PRIVATE).getString("voice_id", null)
            val offlineVoice = availableVoices.firstOrNull { it.name == selectedVoiceId }
                ?: availableVoices.sortedByDescending { it.locale.language == Locale.ENGLISH.language }.firstOrNull()
            if (status == TextToSpeech.SUCCESS && engine != null && offlineVoice != null &&
                runCatching { engine.voice = offlineVoice }.isSuccess
            ) {
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) = completeSpeech()
                    @Suppress("OVERRIDE_DEPRECATION")
                    override fun onError(utteranceId: String?) = completeSpeech()
                })
                localTtsReady = true
                pendingSpeech?.let { (text, continuation) ->
                    pendingSpeech = null
                    speakThen(text, continuation)
                }
            } else {
                localTtsReady = false
                localTtsUnavailable = true
                val queued = pendingSpeech
                pendingSpeech = null
                if (queued != null) {
                    updateNotification("No offline TTS voice is available; say the command after the wake phrase.")
                    queued.second()
                }
            }
        } }.getOrNull()
    }

    private fun speakThen(text: String, continuation: () -> Unit) {
        val engine = localTts
        if (engine == null || !localTtsReady) {
            if (localTtsUnavailable) {
                updateNotification("No offline TTS voice is available; continuing without spoken response.")
                continuation()
                return
            }
            pendingSpeech = text to continuation
            return
        }
        speaking = true
        FloatingBotOverlayService.updateExpression(this, com.zero.assistant.BotFaceMode.Speaking.name)
        speechContinuation = continuation
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, TTS_UTTERANCE_ID)
        if (result != TextToSpeech.SUCCESS) {
            completeSpeech()
        } else {
            handler.removeCallbacks(speechWatchdog)
            handler.postDelayed(speechWatchdog, SPEECH_WATCHDOG_MS)
        }
    }

    private val speechWatchdog = Runnable { completeSpeech() }

    private fun completeSpeech() {
        handler.removeCallbacks(speechWatchdog)
        handler.post {
            if (!serviceRunning) return@post
            speaking = false
            FloatingBotOverlayService.updateExpression(
                this,
                if (WakeWordSettings.enabled(this)) {
                    com.zero.assistant.BotFaceMode.Listening.name
                } else {
                    com.zero.assistant.BotFaceMode.Idle.name
                },
            )
            val continuation = speechContinuation
            speechContinuation = null
            continuation?.invoke()
        }
    }

    private fun stopCapture() {
        captureActive = false
        handler.removeCallbacks(commandCaptureTimeout)
        commandCapture = false
        val currentCommandRecognizer = commandRecognizer
        commandRecognizer = null
        runCatching { currentCommandRecognizer?.cancel() }
        runCatching { currentCommandRecognizer?.destroy() }
        val currentCapture = audioCapture
        audioCapture = null
        currentCapture?.stop()
        smartAudioFocus?.setOwnAudioSessionId(0)
        voiceCaptureEffects.release()
    }

    private fun postReviewNotification(command: String, message: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val reviewIntent = Intent(this, MainActivity::class.java).apply {
            action = WakeWordSettings.ACTION_COMMAND
            putExtra(WakeWordSettings.EXTRA_COMMAND, command)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            REVIEW_REQUEST_CODE,
            reviewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching {
            NotificationManagerCompat.from(this).notify(
                REVIEW_NOTIFICATION_ID,
                NotificationCompat.Builder(this, WakeWordSettings.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_zero)
                    .setContentTitle("ZERO heard a command")
                    .setContentText(message)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build(),
            )
        }
        updateNotification("Listening for wake phrases. Tap the command notification to review the captured request.")
    }

    private fun toggleFlashlight(): String = TorchController.toggle(this).fold(
        onSuccess = { if (it) "Flashlight turned on." else "Flashlight turned off." },
        onFailure = { it.message ?: "Flashlight state could not be changed." },
    )

    private fun adjustMediaVolume(increase: Boolean): String {
        val audioManager = getSystemService(android.media.AudioManager::class.java)
            ?: return "Audio controls are unavailable on this device."
        val direction = if (increase) android.media.AudioManager.ADJUST_RAISE else android.media.AudioManager.ADJUST_LOWER
        return try {
            audioManager.adjustStreamVolume(
                android.media.AudioManager.STREAM_MUSIC,
                direction,
                android.media.AudioManager.FLAG_SHOW_UI,
            )
            if (increase) "Media volume increased." else "Media volume decreased."
        } catch (failure: SecurityException) {
            "Android denied the media volume change: ${failure.message ?: "permission unavailable"}."
        }
    }

    private fun setMediaMuted(muted: Boolean): String {
        val audioManager = getSystemService(android.media.AudioManager::class.java)
            ?: return "Audio controls are unavailable on this device."
        val direction = if (muted) android.media.AudioManager.ADJUST_MUTE else android.media.AudioManager.ADJUST_UNMUTE
        return runCatching {
            audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, direction, 0)
            if (muted) "Media sound muted." else "Media sound unmuted."
        }.getOrElse { "Android could not change media sound: ${it.message ?: "audio unavailable"}." }
    }

    private fun setFlashlight(enabled: Boolean): String = TorchController.set(this, enabled).fold(
        onSuccess = { if (it) "Flashlight turned on." else "Flashlight turned off." },
        onFailure = { it.message ?: "Android could not change flashlight state." },
    )

    private fun readBatteryStatus(): String {
        val manager = getSystemService(BatteryManager::class.java) ?: return "Battery status is unavailable."
        val level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (level !in 0..100) return "Battery status is unavailable."
        return "Battery is at $level%."
    }

    private fun scheduleRestart() {
        if (!serviceRunning || restartPending) return
        restartPending = true
        handler.postDelayed({
            restartPending = false
            if (!serviceRunning || !WakeWordSettings.enabled(this)) return@postDelayed
            startCapture(commandMode = false)
        }, RESTART_DELAY_MS)
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(WakeWordSettings.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(WakeWordSettings.NOTIFICATION_ID, notification)
        }
    }

    private fun acquireCpuWakeLock() {
        try {
            val powerManager = getSystemService(PowerManager::class.java) ?: return
            val wakeLock = cpuWakeLock ?: powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "$packageName:WakeRecognition",
            ).apply {
                setReferenceCounted(false)
                cpuWakeLock = this
            }
            if (wakeLock.isHeld) wakeLock.release()
            wakeLock.acquire(HEARTBEAT_INTERVAL_MS + WAKE_LOCK_RENEWAL_MARGIN_MS)
        } catch (failure: SecurityException) {
            Log.w(TAG, "CPU wake lock is unavailable; Android may pause microphone processing.", failure)
        }
    }

    private fun releaseCpuWakeLock() {
        runCatching { cpuWakeLock?.takeIf { it.isHeld }?.release() }
        cpuWakeLock = null
    }

    private fun markEngineUnavailable(message: String) {
        WakeWordSettings.setActive(this, false, message)
        releaseCpuWakeLock()
    }

    private fun updateNotification(message: String, command: String? = null) {
        if (!serviceRunning) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        runCatching {
            NotificationManagerCompat.from(this).notify(WakeWordSettings.NOTIFICATION_ID, buildNotification(message, command))
        }
    }

    private fun buildNotification(message: String, command: String? = null): android.app.Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            if (command != null) {
                action = WakeWordSettings.ACTION_COMMAND
                putExtra(WakeWordSettings.EXTRA_COMMAND, command)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            CONTENT_REQUEST_CODE,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            Intent(this, WakeWordService::class.java).setAction(WakeWordSettings.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, WakeWordSettings.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_zero)
            .setContentTitle("ZERO voice assistant is active")
            .setContentText(message)
            .setSubText("MICROPHONE ACTIVE")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setColor(android.graphics.Color.rgb(92, 225, 198))
            .addAction(0, "Stop listening", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(WakeWordSettings.CHANNEL_ID, "ZERO background voice", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Persistent notification and controls while ZERO uses the microphone"
                setShowBadge(false)
            },
        )
    }

    private fun stopListeningService() {
        serviceRunning = false
        handler.removeCallbacksAndMessages(null)
        serviceScope.cancel()
        stopCapture()
        localTts?.stop()
        localTts?.shutdown()
        localTts = null
        releaseCpuWakeLock()
        smartAudioFocus?.close()
        smartAudioFocus = null
        WakeWordSettings.setActive(this, false)
        WakeWordSettings.dismissAssistantPanel(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(phraseChangedReceiver) }
        if (serviceRunning) {
            serviceRunning = false
            handler.removeCallbacksAndMessages(null)
            serviceScope.cancel()
            stopCapture()
            localTts?.stop()
            localTts?.shutdown()
            localTts = null
            releaseCpuWakeLock()
            WakeWordSettings.setActive(this, false)
            WakeWordSettings.dismissAssistantPanel(this)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val TAG = "ZERO-Voice"
        const val RESTART_DELAY_MS = 1_500L
        const val SPEECH_WATCHDOG_MS = 8_000L
        const val COMMAND_CAPTURE_TIMEOUT_MS = 10_000
        const val WAKE_DEBOUNCE_MS = 2_500L
        const val DUPLICATE_WINDOW_MS = 1_500L
        const val HEARTBEAT_INTERVAL_MS = 8_000L
        const val WAKE_LOCK_RENEWAL_MARGIN_MS = 5_000L
        const val CONTENT_REQUEST_CODE = 7103
        const val STOP_REQUEST_CODE = 7104
        const val REVIEW_REQUEST_CODE = 7105
        const val REVIEW_NOTIFICATION_ID = 7105
        const val TTS_UTTERANCE_ID = "zero-wake-response"
    }
}