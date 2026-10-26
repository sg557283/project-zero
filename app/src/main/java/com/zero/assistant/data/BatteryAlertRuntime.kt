package com.zero.assistant.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.zero.assistant.MainActivity
import com.zero.assistant.R
import com.zero.assistant.core.BatteryAlertEvent
import com.zero.assistant.core.BatteryAlertEvaluator
import com.zero.assistant.core.BatteryAlertSettings
import com.zero.assistant.core.BatterySnapshot
import java.util.concurrent.TimeUnit

data class BatteryStatus(val percent: Int, val charging: Boolean)

class BatteryAlertManager(context: Context) {
    private val appContext = context.applicationContext
    private val evaluator = BatteryAlertEvaluator(BatteryAlertPreferences(appContext))
    private var receiver: BroadcastReceiver? = null

    fun settings(): BatteryAlertSettings = evaluator.state().settings

    fun status(): BatteryStatus = readBatteryStatus(appContext)

    fun updateSettings(settings: BatteryAlertSettings) {
        evaluator.updateSettings(settings)
        synchronizeWorker()
        process(status())
    }

    fun synchronizeWorker() {
        val settings = evaluator.state().settings
        val enabled = settings.lowEnabled || settings.chargingStartedEnabled || settings.fullyChargedEnabled
        val workManager = WorkManager.getInstance(appContext)
        if (enabled) {
            val request = PeriodicWorkRequestBuilder<BatteryAlertWorker>(15, TimeUnit.MINUTES).build()
            workManager.enqueueUniquePeriodicWork(BatteryAlertWorker.WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        } else workManager.cancelUniqueWork(BatteryAlertWorker.WORK_NAME)
    }

    fun observe(owner: Context, onStatus: (BatteryStatus) -> Unit) {
        if (receiver != null) return
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val status = parseBatteryIntent(intent) ?: readBatteryStatus(context)
                process(status)
                onStatus(status)
            }
        }.also { batteryReceiver ->
            owner.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }
        onStatus(status())
    }

    fun stopObserving(owner: Context) {
        receiver?.let { runCatching { owner.unregisterReceiver(it) } }
        receiver = null
    }

    private fun process(status: BatteryStatus) {
        val events = evaluator.onSnapshot(BatterySnapshot(status.percent, status.charging))
        events.forEach { BatteryAlertNotifier.notify(appContext, it) }
    }
}

class BatteryAlertWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        val settings = BatteryAlertPreferences(applicationContext)
        val state = settings.read()
        if (!state.settings.lowEnabled && !state.settings.chargingStartedEnabled && !state.settings.fullyChargedEnabled) return Result.success()
        val status = readBatteryStatus(applicationContext)
        val evaluator = BatteryAlertEvaluator(settings)
        evaluator.onSnapshot(BatterySnapshot(status.percent, status.charging)).forEach {
            BatteryAlertNotifier.notify(applicationContext, it)
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "zero-battery-alert-monitor"
    }
}

class BatteryPowerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_POWER_CONNECTED && intent.action != Intent.ACTION_POWER_DISCONNECTED) return
        val pendingResult = goAsync()
        Thread {
            try {
                val preferences = BatteryAlertPreferences(context.applicationContext)
                val state = preferences.read()
                if (!state.settings.lowEnabled && !state.settings.chargingStartedEnabled && !state.settings.fullyChargedEnabled) return@Thread
                val status = readBatteryStatus(context.applicationContext)
                BatteryAlertEvaluator(preferences).onSnapshot(BatterySnapshot(status.percent, status.charging)).forEach {
                    BatteryAlertNotifier.notify(context.applicationContext, it)
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}

private object BatteryAlertNotifier {
    fun notify(context: Context, event: BatteryAlertEvent) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val channelManager = context.getSystemService(NotificationManager::class.java)
        channelManager?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "ZERO | Battery alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Local battery alerts from ZERO"
            },
        )
        val (notificationId, message) = when (event) {
            is BatteryAlertEvent.Low -> LOW_ID to "Battery is ${event.percent}%, at or below your ${event.threshold}% threshold."
            BatteryAlertEvent.ChargingStarted -> CHARGING_ID to "Charging started."
            BatteryAlertEvent.FullyCharged -> FULL_ID to "Battery is fully charged."
        }
        val title = when (event) {
            is BatteryAlertEvent.Low -> "LOW BATTERY"
            BatteryAlertEvent.ChargingStarted -> "CHARGING"
            BatteryAlertEvent.FullyCharged -> "CHARGED"
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_zero)
            .setContentTitle(title)
            .setContentText(message)
            .setSubText("ZERO | LOCAL")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setColor(android.graphics.Color.rgb(92, 225, 198))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notificationId, notification) }
    }

    private const val CHANNEL_ID = "zero_battery_alerts"
    private const val LOW_ID = 501
    private const val CHARGING_ID = 502
    private const val FULL_ID = 503
}

private fun readBatteryStatus(context: Context): BatteryStatus {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    return parseBatteryIntent(intent) ?: BatteryStatus(0, false)
}

private fun parseBatteryIntent(intent: Intent?): BatteryStatus? {
    intent ?: return null
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    return BatteryStatus((level * 100 / scale).coerceIn(0, 100), status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
}