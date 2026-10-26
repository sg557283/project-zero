package com.zero.assistant.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import com.zero.assistant.core.PlannerReminder
import com.zero.assistant.core.ReminderRepository
import com.zero.assistant.core.ReminderSchedule
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class ReminderScheduler(context: Context, private val repository: ReminderRepository) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)

    fun schedule(reminder: PlannerReminder, nowMillis: Long = System.currentTimeMillis()) {
        val uniqueName = uniqueName(reminder.id)
        workManager.cancelUniqueWork(uniqueName)
        val triggerAt = ReminderSchedule.nextTriggerEpochMillis(reminder, nowMillis, ZoneId.systemDefault()) ?: return
        val delay = (triggerAt - nowMillis).coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<ReminderNotificationWorker>()
            .setInputData(workDataOf(ReminderNotificationWorker.REMINDER_ID_KEY to reminder.id))
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(uniqueName)
            .build()
        workManager.enqueueUniqueWork(uniqueName, androidx.work.ExistingWorkPolicy.REPLACE, request)
    }

    fun rescheduleAll(nowMillis: Long = System.currentTimeMillis()) {
        repository.all().forEach { schedule(it, nowMillis) }
    }

    fun cancel(reminderId: String) {
        workManager.cancelUniqueWork(uniqueName(reminderId))
    }

    private fun uniqueName(id: String) = "zero-reminder-$id"
}

class ReminderNotificationWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        val reminderId = inputData.getString(REMINDER_ID_KEY) ?: return Result.failure()
        val repository = ReminderRepository(PlannerPreferences(applicationContext))
        val reminder = repository.all().firstOrNull { it.id == reminderId } ?: return Result.success()
        if (!reminder.enabled || reminder.completed) return Result.success()
        ensureChannel()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return Result.success()
        }
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
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
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(applicationContext).notify(reminder.id.hashCode(), notification) }
        if (reminder.repeat != com.zero.assistant.core.ReminderRepeat.Never) {
            ReminderScheduler(applicationContext, repository).schedule(reminder)
        }
        return Result.success()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "ZERO · Local reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Minimal reminders scheduled on this device"
            },
        )
    }

    companion object {
        const val REMINDER_ID_KEY = "reminder_id"
        const val CHANNEL_ID = "zero_reminders"
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