package com.zero.assistant.data

import android.content.Context

internal enum class OverlayDesignStyle {
    DynamicNotch,
    RightEdge,
}

internal object OverlayPreferencesRepository {
    private const val PREFERENCES = "zero_accessibility"
    private const val KEY_OVERLAY_STYLE = "overlay_design_style"

    fun style(context: Context): OverlayDesignStyle {
        val saved = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_OVERLAY_STYLE, OverlayDesignStyle.DynamicNotch.name)
        return OverlayDesignStyle.entries.firstOrNull { it.name == saved } ?: OverlayDesignStyle.DynamicNotch
    }

    fun setStyle(context: Context, style: OverlayDesignStyle) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_OVERLAY_STYLE, style.name)
            .apply()
    }
}
