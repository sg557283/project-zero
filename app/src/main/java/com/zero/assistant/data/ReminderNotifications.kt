package com.zero.assistant.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.zero.assistant.MainActivity
import com.zero.assistant.R
import com.zero.assistant.ZeroAssistantWidget
import com.zero.assistant.core.PlannerReminder
import com.zero.assistant.core.ReminderRepository
import com.zero.assistant.core.ReminderSchedule
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class ReminderScheduler(context: Context, private val repository: ReminderRepository) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    fun schedule(reminder: PlannerReminder, nowMillis: Long = System.currentTimeMillis()) {
        val uniqueName = uniqueName(reminder.id)
        workManager.cancelUniqueWork(uniqueName)
        alarmManager?.cancel(alarmIntent(reminder.id))
        val triggerAt = ReminderSchedule.nextTriggerEpochMillis(reminder, nowMillis, ZoneId.systemDefault())
        if (triggerAt == null) {
            ZeroAssistantWidget.refresh(appContext)
            return
        }
        val delay = (triggerAt - nowMillis).coerceAtLeast(0)
        if (alarmManager != null && canScheduleExactAlarms()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    alarmIntent(reminder.id),
                )
                ZeroAssistantWidget.refresh(appContext)
                return
            } catch (failure: SecurityException) {
                android.util.Log.w("ZERO-Reminder", "Exact-alarm access changed; using WorkManager fallback.", failure)
            }
        }
        val request = OneTimeWorkRequestBuilder<ReminderNotificationWorker>()
            .setInputData(workDataOf(ReminderNotificationWorker.REMINDER_ID_KEY to reminder.id))
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(uniqueName)
            .build()
        workManager.enqueueUniqueWork(uniqueName, androidx.work.ExistingWorkPolicy.REPLACE, request)
        ZeroAssistantWidget.refresh(appContext)
    }

    fun rescheduleAll(nowMillis: Long = System.currentTimeMillis()) {
        repository.all().forEach { schedule(it, nowMillis) }
    }

    fun cancel(reminderId: String) {
        workManager.cancelUniqueWork(uniqueName(reminderId))
        alarmManager?.cancel(alarmIntent(reminderId))
        ZeroAssistantWidget.refresh(appContext)
    }

    private fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < 31 || alarmManager?.canScheduleExactAlarms() == true

    private fun alarmIntent(id: String): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            id.hashCode(),
            Intent(appContext, ReminderAlarmReceiver::class.java)
                .putExtra(ReminderNotificationWorker.REMINDER_ID_KEY, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun uniqueName(id: String) = "zero-reminder-$id"
}

class ReminderNotificationWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        val reminderId = inputData.getString(REMINDER_ID_KEY) ?: return Result.failure()
        val repository = ReminderRepository(PlannerPreferences(applicationContext))
        val reminder = repository.all().firstOrNull { it.id == reminderId } ?: return Result.success()
        if (!reminder.enabled || reminder.completed) return Result.success()
        ReminderNotificationPublisher.post(applicationContext, reminder)
        if (reminder.repeat != com.zero.assistant.core.ReminderRepeat.Never) {
            ReminderScheduler(applicationContext, repository).schedule(reminder)
        }
        return Result.success()
    }

    companion object {
        const val REMINDER_ID_KEY = "reminder_id"
        const val CHANNEL_ID = "zero_reminders"
    }
}

private object ReminderNotificationPublisher {
    fun post(context: Context, reminder: PlannerReminder) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(
                    ReminderNotificationWorker.CHANNEL_ID,
                    "ZERO · Local reminders",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Time-sensitive reminders scheduled on this device"
                    enableVibration(true)
                },
            )
        }
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, ReminderNotificationWorker.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_zero)
            .setContentTitle(reminder.title)
            .setContentText("ZERO reminder · scheduled on this device")
            .setSubText("ZERO · LOCAL")
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.title).setSummaryText("ZERO · LOCAL REMINDER"))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setColor(android.graphics.Color.rgb(92, 225, 198))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(reminder.id.hashCode(), notification)
        ZeroAssistantWidget.refresh(context)
    }
}

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ReminderNotificationWorker.REMINDER_ID_KEY) ?: return
        val pending = goAsync()
        Thread {
            val wakeLock = context.getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "${context.packageName}:Reminder")
            try {
                wakeLock?.acquire(10_000L)
                val repository = ReminderRepository(PlannerPreferences(context.applicationContext))
                val reminder = repository.all().firstOrNull { it.id == id } ?: return@Thread
                if (!reminder.enabled || reminder.completed) return@Thread
                runCatching { ReminderNotificationPublisher.post(context, reminder) }
                    .onFailure { android.util.Log.e("ZERO-Reminder", "Reminder notification could not be posted.", it) }
                if (reminder.repeat != com.zero.assistant.core.ReminderRepeat.Never) {
                    ReminderScheduler(context, repository).schedule(reminder)
                }
            } finally {
                if (wakeLock?.isHeld == true) wakeLock.release()
                pending.finish()
            }
        }.start()
    }
}

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) return
        val pendingResult = goAsync()
        Thread {
            try {
                val repository = ReminderRepository(PlannerPreferences(context.applicationContext))
                ReminderScheduler(context, repository).rescheduleAll()
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}