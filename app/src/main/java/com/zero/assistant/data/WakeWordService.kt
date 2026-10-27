package com.zero.assistant.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zero.assistant.MainActivity
import com.zero.assistant.R
import com.zero.assistant.core.AssistantCommand
import com.zero.assistant.core.CommandParser
import com.zero.assistant.core.WakeWord
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class WakeWordService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var modelManager: SpeechModelManager? = null
    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null
    private var serviceRunning = false
    private var awaitingCommand = false
    private var restartPending = false
    private var lastTranscript = ""
    private var lastTranscriptAt = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == WakeWordSettings.ACTION_STOP) {
            WakeWordSettings.setEnabled(this, false)
            stopListeningService()
            return START_NOT_STICKY
        }
        if (intent?.action == WakeWordSettings.ACTION_START) WakeWordSettings.setEnabled(this, true)
        if (!WakeWordSettings.enabled(this)) {
            stopListeningService()
            return START_NOT_STICKY
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            WakeWordSettings.setActive(this, false, "Microphone permission is required to resume background listening.")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            WakeWordSettings.setActive(this, false, "Notification permission is required to show persistent microphone controls.")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        serviceRunning = true
        WakeWordSettings.setActive(this, true)
        startForegroundCompat(buildNotification("Wake listening is active. Microphone access is in use."))
        beginLoadingModel()
        return START_STICKY
    }

    private fun beginLoadingModel() {
        if (modelManager != null || !serviceRunning) return
        val manager = runCatching { SpeechModelManager(applicationContext) }.getOrElse {
            updateNotification("Offline speech model could not be loaded.")
            return
        }
        modelManager = manager
        manager.ensureBundledModel()
        serviceScope.launch {
            try {
                val modelId = manager.activeModelId.first { it != null }
                if (!serviceRunning) return@launch
                manager.loadActiveModel { result ->
                    result.fold(
                        onSuccess = { model -> startRecognizer(model, modelId ?: return@fold) },
                        onFailure = { failure -> updateNotification("Speech model unavailable: ${failure.message ?: "open ZERO to repair"}") },
                    )
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                updateNotification("Speech model unavailable: ${failure.message ?: "open ZERO to repair"}")
            }
        }
    }

    private fun startRecognizer(model: Model, modelId: String) {
        if (!serviceRunning || !WakeWordSettings.enabled(this)) return
        runCatching {
            recognizer?.close()
            recognizer = Recognizer(model, SAMPLE_RATE_HZ)
            startCapture()
            updateNotification("Listening for ${WakeWordSettings.phrase(this)} using $modelId.")
        }.onFailure { failure ->
            updateNotification("Offline listening failed: ${failure.message ?: "check the microphone"}")
            scheduleRestart()
        }
    }

    private fun startCapture() {
        if (!serviceRunning || !WakeWordSettings.enabled(this)) return
        val currentRecognizer = recognizer ?: return
        runCatching {
            val service = SpeechService(currentRecognizer, SAMPLE_RATE_HZ)
            speechService = service
            val started = service.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) = Unit
                override fun onResult(hypothesis: String?) = handleHypothesis(hypothesis)
                override fun onFinalResult(hypothesis: String?) = handleHypothesis(hypothesis)
                override fun onError(exception: Exception?) {
                    updateNotification("Microphone error: ${exception?.message ?: "audio input unavailable"}")
                    scheduleRestart()
                }
                override fun onTimeout() = scheduleRestart()
            })
            if (!started) scheduleRestart()
        }.onFailure { failure ->
            updateNotification("Microphone could not start: ${failure.message ?: "audio input unavailable"}")
            scheduleRestart()
        }
    }

    private fun handleHypothesis(hypothesis: String?) {
        if (!serviceRunning || !WakeWordSettings.enabled(this)) return
        val transcript = runCatching { JSONObject(hypothesis.orEmpty()).optString("text").trim() }.getOrDefault("")
        if (transcript.isBlank()) return
        val now = System.currentTimeMillis()
        if (transcript.equals(lastTranscript, ignoreCase = true) && now - lastTranscriptAt < DUPLICATE_WINDOW_MS) return
        lastTranscript = transcript
        lastTranscriptAt = now

        val match = WakeWord.match(transcript, WakeWordSettings.phrase(this))
        if (match != null) {
            if (match.command.isBlank()) {
                awaitingCommand = true
                handler.removeCallbacks(clearAwaitingCommand)
                handler.postDelayed(clearAwaitingCommand, COMMAND_WINDOW_MS)
                updateNotification("Wake phrase heard. Say your command now.")
            } else {
                awaitingCommand = false
                handler.removeCallbacks(clearAwaitingCommand)
                executeRecognizedCommand(match.command)
            }
            return
        }
        if (awaitingCommand) {
            awaitingCommand = false
            handler.removeCallbacks(clearAwaitingCommand)
            executeRecognizedCommand(transcript)
        }
    }

    private val clearAwaitingCommand = Runnable {
        awaitingCommand = false
        if (serviceRunning) updateNotification("Listening for ${WakeWordSettings.phrase(this)}.")
    }

    private fun executeRecognizedCommand(text: String) {
        when (val command = CommandParser.parse(text)) {
            is AssistantCommand.SetFlashlight -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    requestForegroundReview(text, "Camera permission is required. Tap to review this flashlight command in ZERO.")
                } else {
                    updateNotification(setFlashlight(command.enabled))
                }
            }
            AssistantCommand.TellTime -> updateNotification("It is ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())}.")
            AssistantCommand.BatteryStatus -> updateNotification(readBatteryStatus())
            else -> requestForegroundReview(text, "Command heard. Tap to review and continue in ZERO.")
        }
    }

    private fun requestForegroundReview(command: String, message: String) {
        updateNotification(message, command)
    }

    private fun setFlashlight(enabled: Boolean): String {
        val camera = getSystemService(CameraManager::class.java) ?: return "Camera service is unavailable."
        return try {
            val id = camera.cameraIdList.firstOrNull {
                camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "This device has no flashlight."
            camera.setTorchMode(id, enabled)
            if (enabled) "Flashlight turned on." else "Flashlight turned off."
        } catch (_: Exception) {
            "Android could not change flashlight state."
        }
    }

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
            runCatching { speechService?.shutdown() }
            speechService = null
            runCatching { recognizer?.reset() }
            startCapture()
        }, RESTART_DELAY_MS)
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(WakeWordSettings.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(WakeWordSettings.NOTIFICATION_ID, notification)
        }
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
        runCatching { speechService?.cancel() }
        runCatching { speechService?.shutdown() }
        speechService = null
        runCatching { recognizer?.close() }
        recognizer = null
        modelManager?.close()
        modelManager = null
        WakeWordSettings.setActive(this, false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (serviceRunning) {
            serviceRunning = false
            handler.removeCallbacksAndMessages(null)
            serviceScope.cancel()
            runCatching { speechService?.cancel() }
            runCatching { speechService?.shutdown() }
            runCatching { recognizer?.close() }
            modelManager?.close()
            WakeWordSettings.setActive(this, false)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val SAMPLE_RATE_HZ = 16_000f
        const val RESTART_DELAY_MS = 1_500L
        const val COMMAND_WINDOW_MS = 7_000L
        const val DUPLICATE_WINDOW_MS = 1_500L
        const val CONTENT_REQUEST_CODE = 7103
        const val STOP_REQUEST_CODE = 7104
    }
}