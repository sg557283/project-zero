package com.zero.assistant.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.zero.assistant.core.AssistantAction
import com.zero.assistant.core.AssistantTrigger

object AssistantActionSettings {
    private const val PREFERENCES = "zero_assistant_actions"
    private const val KEY_BACK_TAPS = "back_taps_enabled"
    private const val KEY_TEST_MODE = "trigger_test_mode"

    fun actionFor(context: Context, trigger: AssistantTrigger): AssistantAction =
        runCatching {
            AssistantAction.valueOf(preferences(context).getString(trigger.name, trigger.defaultAction.name).orEmpty())
        }.getOrDefault(trigger.defaultAction)

    fun setAction(context: Context, trigger: AssistantTrigger, action: AssistantAction) {
        preferences(context).edit().putString(trigger.name, action.name).apply()
    }

    fun backTapsEnabled(context: Context): Boolean = preferences(context).getBoolean(KEY_BACK_TAPS, false)

    fun setBackTapsEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_BACK_TAPS, enabled).apply()
    }

    fun testModeEnabled(context: Context): Boolean = preferences(context).getBoolean(KEY_TEST_MODE, false)

    fun setTestModeEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_TEST_MODE, enabled).apply()
    }

    fun shouldRunGestureService(context: Context): Boolean =
        backTapsEnabled(context) || HardwareActivationSettings.doubleShakeEnabled(context) || testModeEnabled(context)

    fun syncGestureService(context: Context): Result<Unit> = runCatching {
        val serviceIntent = Intent(context, DoubleShakeService::class.java).setAction(
            if (shouldRunGestureService(context)) DoubleShakeService.ACTION_START else DoubleShakeService.ACTION_STOP,
        )
        if (shouldRunGestureService(context)) {
            check(
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
            ) { "Camera permission is needed by Android's camera-type gesture service." }
            ContextCompat.startForegroundService(context, serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
