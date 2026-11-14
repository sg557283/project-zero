package com.zero.assistant.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.speech.tts.TextToSpeech
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.zero.assistant.MainActivity
import com.zero.assistant.R
import com.zero.assistant.core.AssistantTrigger
import com.zero.assistant.core.BackTapPulseDetector
import com.zero.assistant.core.BackTapSequenceDetector
import com.zero.assistant.core.DoubleShakeDetector
import java.util.Locale

class DoubleShakeService : Service(), SensorEventListener {
    companion object {
        const val ACTION_START = "com.zero.assistant.action.START_SHAKE"
        const val ACTION_STOP = "com.zero.assistant.action.STOP_SHAKE"
        const val NOTIFICATION_ID = 7111
        const val CHANNEL_ID = "zero_shake"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var sensorManager: SensorManager? = null
    private var detector: DoubleShakeDetector? = null
    private val tapPulseDetector = BackTapPulseDetector()
    private val tapSequenceDetector = BackTapSequenceDetector()
    private var lastSensorAt = 0L
    private var speech: TextToSpeech? = null
    private var speechReady = false
    private val finishTapSequence = Runnable {
        tapSequenceDetector.flush(System.currentTimeMillis())?.let(::runBackTap)
    }

    override fun onCreate() {
        super.onCreate()
        speech = TextToSpeech(applicationContext) { status ->
            val engine = speech ?: return@TextToSpeech
            val localVoice = engine.voices.orEmpty().firstOrNull {
                !it.isNetworkConnectionRequired && it.locale.language == Locale.getDefault().language
            } ?: engine.voices.orEmpty().firstOrNull { !it.isNetworkConnectionRequired }
            speechReady = status == TextToSpeech.SUCCESS && localVoice != null &&
                runCatching { engine.voice = localVoice }.isSuccess
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            HardwareActivationSettings.setDoubleShakeEnabled(this, false)
            AssistantActionSettings.setBackTapsEnabled(this, false)
            AssistantActionSettings.setTestModeEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!AssistantActionSettings.shouldRunGestureService(this)) {
            stopSelf(); return START_NOT_STICKY
        }
        startForegroundNotification()
        val manager = getSystemService(SensorManager::class.java)
        val accelerometer = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (manager == null || accelerometer == null) {
            HardwareActivationSettings.setDoubleShakeEnabled(this, false)
            AssistantActionSettings.setBackTapsEnabled(this, false)
            AssistantActionSettings.setTestModeEnabled(this, false)
            stopSelf(); return START_NOT_STICKY
        }
        sensorManager = manager
        manager.unregisterListener(this)
        handler.removeCallbacks(finishTapSequence)
        tapSequenceDetector.reset()
        detector = DoubleShakeDetector(HardwareActivationSettings.shakeSensitivity(this))
        if (!manager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)) {
            HardwareActivationSettings.setDoubleShakeEnabled(this, false)
            AssistantActionSettings.setBackTapsEnabled(this, false)
            AssistantActionSettings.setTestModeEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val now = System.currentTimeMillis()
        if (now - lastSensorAt < 35L) return
        lastSensorAt = now
        val detector = detector ?: return
        if ((HardwareActivationSettings.doubleShakeEnabled(this) || AssistantActionSettings.testModeEnabled(this)) &&
            detector.sample(event.values[0], event.values[1], event.values[2], now)
        ) {
            runTrigger(AssistantTrigger.SHAKE)
        }
        if (AssistantActionSettings.backTapsEnabled(this) || AssistantActionSettings.testModeEnabled(this)) {
            if (tapPulseDetector.sample(event.values[0], event.values[1], event.values[2], now)) {
                tapSequenceDetector.recordTap(now)?.let(::runBackTap)
                handler.removeCallbacks(finishTapSequence)
                if (tapSequenceDetector.pendingTapCount > 0) {
                    handler.postDelayed(finishTapSequence, 400L)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun runBackTap(count: Int) {
        val trigger = when (count) {
            2 -> AssistantTrigger.BACK_TAP_2
            3 -> AssistantTrigger.BACK_TAP_3
            4 -> AssistantTrigger.BACK_TAP_4
            else -> return
        }
        runTrigger(trigger)
    }

    private fun runTrigger(trigger: AssistantTrigger) {
        if (AssistantActionSettings.testModeEnabled(this)) {
            val message = "${trigger.label} detected."
            speak(message)
            sendBroadcast(
                Intent(ActionExecutor.ACTION_TRIGGER_TEST_FEEDBACK)
                    .setPackage(packageName)
                    .putExtra(ActionExecutor.EXTRA_TRIGGER_TEST_MESSAGE, message),
            )
            return
        }
        val action = AssistantActionSettings.actionFor(this, trigger)
        ActionExecutor.execute(this, action, ::speak)
    }

    private fun speak(message: String) {
        if (speechReady) speech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "zero-trigger-feedback")
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "ZERO shake flashlight", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        }
        val activityIntent = PendingIntent.getActivity(this, 7111, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = PendingIntent.getService(this, 7112, Intent(this, DoubleShakeService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_zero)
            .setContentTitle("ZERO gesture triggers are active")
            .setContentText("Shake or use configured back-tap gestures")
            .setContentIntent(activityIntent)
            .addAction(0, "Disable", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        handler.removeCallbacks(finishTapSequence)
        sensorManager?.unregisterListener(this)
        speech?.stop()
        speech?.shutdown()
        speech = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
