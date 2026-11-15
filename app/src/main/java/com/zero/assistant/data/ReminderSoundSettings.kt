package com.zero.assistant.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import java.util.UUID

enum class ReminderSoundPreset(val label: String) {
    Alarm("Alarm"),
    Notification("Notification"),
    Silent("Silent"),
    Custom("Custom audio"),
}

data class ReminderSoundSelection(
    val preset: ReminderSoundPreset,
    val uri: Uri?,
)

class ReminderSoundSettings(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun selection(): ReminderSoundSelection {
        val preset = ReminderSoundPreset.entries.firstOrNull {
            it.name == preferences.getString(KEY_PRESET, ReminderSoundPreset.Alarm.name)
        } ?: ReminderSoundPreset.Alarm
        val uri = preferences.getString(KEY_CUSTOM_URI, null)?.let(Uri::parse)
        return if (preset == ReminderSoundPreset.Custom && uri == null) {
            ReminderSoundSelection(ReminderSoundPreset.Alarm, null)
        } else {
            ReminderSoundSelection(preset, uri)
        }
    }

    fun channelId(): String = preferences.getString(KEY_CHANNEL_ID, DEFAULT_CHANNEL_ID) ?: DEFAULT_CHANNEL_ID

    fun soundUri(): Uri? = soundUri(selection())

    fun selectPreset(preset: ReminderSoundPreset): Result<Unit> = runCatching {
        require(preset != ReminderSoundPreset.Custom) { "Choose an audio file for a custom reminder sound." }
        saveSelection(preset, null)
    }

    fun selectCustomSound(uri: Uri): Result<Unit> {
        var permissionTaken = false
        return runCatching {
            require(uri.scheme == CONTENT_SCHEME) { "Choose a sound from the Android file picker." }
            appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { }
                ?: throw IllegalStateException("The selected audio file is unavailable.")
            appContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            permissionTaken = true
            saveSelection(ReminderSoundPreset.Custom, uri)
        }.onFailure {
            if (permissionTaken && selection().uri != uri) releaseReadPermission(uri)
        }
    }

    fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        createChannel(channelId(), selection())
    }

    private fun saveSelection(preset: ReminderSoundPreset, uri: Uri?) {
        val oldSelection = selection()
        val oldChannelId = channelId()
        val newChannelId = "zero_reminders_${UUID.randomUUID()}"
        val newSelection = ReminderSoundSelection(preset, uri)
        createChannel(newChannelId, newSelection)
        check(
            preferences.edit()
                .putString(KEY_PRESET, preset.name)
                .putString(KEY_CUSTOM_URI, uri?.toString())
                .putString(KEY_CHANNEL_ID, newChannelId)
                .commit(),
        ) { "Reminder sound setting could not be saved." }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.getSystemService(NotificationManager::class.java)?.deleteNotificationChannel(oldChannelId)
        }
        if (oldSelection.uri != null && oldSelection.uri != uri) releaseReadPermission(oldSelection.uri)
    }

    private fun createChannel(id: String, selection: ReminderSoundSelection) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(NotificationManager::class.java)
            ?: throw IllegalStateException("Android notification service is unavailable.")
        manager.createNotificationChannel(
            NotificationChannel(id, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Audible, time-sensitive reminders scheduled on this device"
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN
                setSound(soundUri(selection), AUDIO_ATTRIBUTES)
            },
        )
    }

    private fun soundUri(selection: ReminderSoundSelection): Uri? = when (selection.preset) {
        ReminderSoundPreset.Alarm -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ReminderSoundPreset.Notification -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ReminderSoundPreset.Silent -> null
        ReminderSoundPreset.Custom -> selection.uri
    }

    private fun releaseReadPermission(uri: Uri) {
        runCatching {
            appContext.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.onFailure { Log.w(TAG, "Could not release the previous custom reminder sound permission.", it) }
    }

    private companion object {
        const val PREFERENCES = "zero_reminder_sound"
        const val KEY_PRESET = "preset"
        const val KEY_CUSTOM_URI = "custom_uri"
        const val KEY_CHANNEL_ID = "channel_id"
        const val DEFAULT_CHANNEL_ID = "zero_reminders_alarm_v2"
        const val CHANNEL_NAME = "ZERO · Reminder alarms"
        const val CONTENT_SCHEME = "content"
        const val TAG = "ZERO-Reminder"
        val VIBRATION_PATTERN = longArrayOf(0, 400, 250, 600)
        val AUDIO_ATTRIBUTES = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
