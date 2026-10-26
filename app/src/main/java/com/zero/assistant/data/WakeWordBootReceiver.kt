package com.zero.assistant.data

import android.Manifest
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
import com.zero.assistant.MainActivity
import com.zero.assistant.R

class WakeWordBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        if (!WakeWordSettings.enabled(context)) return
        WakeWordSettings.setActive(context, false)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                android.app.NotificationChannel(
                    RESUME_CHANNEL_ID,
                    "ZERO voice resume",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Tap to resume your enabled wake-word service after restart" },
            )
        }
        val resumeIntent = Intent(context, MainActivity::class.java)
            .putExtra(WakeWordSettings.EXTRA_RESUME, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            RESUME_REQUEST_CODE,
            resumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, RESUME_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_zero)
            .setContentTitle("ZERO wake listening is paused")
            .setContentText("Tap to reopen ZERO and resume microphone access.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(RESUME_NOTIFICATION_ID, notification) }
    }

    companion object {
        const val RESUME_CHANNEL_ID = "zero_voice_resume"
        private const val RESUME_NOTIFICATION_ID = 7102
        private const val RESUME_REQUEST_CODE = 7102
    }
}