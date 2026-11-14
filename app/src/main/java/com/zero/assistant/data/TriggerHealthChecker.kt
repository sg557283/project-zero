package com.zero.assistant.data

import android.content.ComponentName
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.provider.Settings

data class TriggerHealthStatus(
    val accessibilityEnabled: Boolean,
    val volumeShortcutTargeted: Boolean,
    val accelerometerAvailable: Boolean,
    val gyroscopeAvailable: Boolean,
    val backTapNote: String,
)

object TriggerHealthChecker {
    fun check(context: Context): TriggerHealthStatus {
        val serviceId = ComponentName(context, VolumeAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty().split(':').map { it.lowercase() }
        val enabled = serviceId.lowercase() in enabledServices
        val shortcutTargets = Settings.Secure.getString(
            context.contentResolver,
            "accessibility_shortcut_target_service",
        ).orEmpty().split(':').map { it.lowercase() }
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
        val gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
        return TriggerHealthStatus(
            accessibilityEnabled = enabled,
            volumeShortcutTargeted = serviceId.lowercase() in shortcutTargets,
            accelerometerAvailable = accelerometer,
            gyroscopeAvailable = gyroscope,
            backTapNote = if (accelerometer) {
                "Experimental: Android has no standard back-tap sensor; accelerometer gestures vary by device."
            } else {
                "No accelerometer is available, so shake and back-tap gestures cannot run."
            },
        )
    }

    fun openAccessibilitySettings(context: Context) {
        context.startActivity(
            android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
