package com.zero.assistant.data

import android.content.Context
import com.zero.assistant.core.ShakeSensitivity

object HardwareActivationSettings {
    private const val PREFERENCES = "zero_hardware_activation"
    private const val KEY_VOLUME = "volume_pair_enabled"
    private const val KEY_SHAKE = "double_shake_enabled"
    private const val KEY_SENSITIVITY = "shake_sensitivity"

    fun volumePairEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_VOLUME, false)
    fun setVolumePairEnabled(context: Context, enabled: Boolean) { prefs(context).edit().putBoolean(KEY_VOLUME, enabled).apply() }

    fun doubleShakeEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_SHAKE, false)
    fun setDoubleShakeEnabled(context: Context, enabled: Boolean) { prefs(context).edit().putBoolean(KEY_SHAKE, enabled).apply() }

    fun shakeSensitivity(context: Context): ShakeSensitivity = runCatching {
        ShakeSensitivity.valueOf(prefs(context).getString(KEY_SENSITIVITY, ShakeSensitivity.NORMAL.name) ?: ShakeSensitivity.NORMAL.name)
    }.getOrDefault(ShakeSensitivity.NORMAL)
    fun setShakeSensitivity(context: Context, sensitivity: ShakeSensitivity) { prefs(context).edit().putString(KEY_SENSITIVITY, sensitivity.name).apply() }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
