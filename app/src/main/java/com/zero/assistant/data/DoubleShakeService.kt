package com.zero.assistant.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.zero.assistant.MainActivity
import com.zero.assistant.R
import com.zero.assistant.core.DoubleShakeDetector
import com.zero.assistant.data.TorchController

class DoubleShakeService : Service(), SensorEventListener {
    companion object {
        const val ACTION_START = "com.zero.assistant.action.START_SHAKE"
        const val ACTION_STOP = "com.zero.assistant.action.STOP_SHAKE"
        const val NOTIFICATION_ID = 7111
        const val CHANNEL_ID = "zero_shake"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var sensorManager: SensorManager? = null
    private var torchCameraId: String? = null
    private var torchEnabled = false
    private var detector: DoubleShakeDetector? = null
    private var lastSensorAt = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !HardwareActivationSettings.doubleShakeEnabled(this)) {
            stopSelf(); return START_NOT_STICKY
        }
        startForegroundNotification()
        val manager = getSystemService(SensorManager::class.java)
        val camera = getSystemService(CameraManager::class.java)
        val accelerometer = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val cameraId = camera?.cameraIdList?.firstOrNull { camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        if (manager == null || accelerometer == null || camera == null || cameraId == null) {
            HardwareActivationSettings.setDoubleShakeEnabled(this, false)
            stopSelf(); return START_NOT_STICKY
        }
        sensorManager = manager
        torchCameraId = cameraId
        detector = DoubleShakeDetector(HardwareActivationSettings.shakeSensitivity(this))
        manager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val now = System.currentTimeMillis()
        if (now - lastSensorAt < 35L) return
        lastSensorAt = now
        val detector = detector ?: return
        if (detector.sample(event.values[0], event.values[1], event.values[2], now)) toggleTorch()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun toggleTorch() {
        TorchController.toggle(this).onSuccess { torchEnabled = it }.onFailure { torchEnabled = false }
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
            .setContentTitle("Double-shake flashlight is active")
            .setContentText("Shake twice deliberately to toggle the torch")
            .setContentIntent(activityIntent)
            .addAction(0, "Disable", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA) else startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
        runCatching { torchCameraId?.let { getSystemService(CameraManager::class.java)?.setTorchMode(it, false) } }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
