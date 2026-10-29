package com.zero.assistant.data

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.zero.assistant.core.HardwareActivationDebouncer

class VolumeAccessibilityService : AccessibilityService() {
    private var volumeUpDown = false
    private var lastVolumeDown = 0L
    private var lastActivation = -2_000L

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo?.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            eventTypes = 0
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        } ?: AccessibilityServiceInfo().apply {
            flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (!HardwareActivationSettings.volumePairEnabled(this) || event == null) return false
        val now = System.currentTimeMillis()
        val isVolumeUp = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        val isVolumeDown = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (event.action == KeyEvent.ACTION_DOWN && isVolumeUp && volumeUpDown) {
            volumeUpDown = false
            if (HardwareActivationDebouncer.accept(now, lastActivation)) activateAssistant()
            return false
        }
        if (event.action == KeyEvent.ACTION_DOWN && isVolumeDown) {
            volumeUpDown = true
            lastVolumeDown = now
        } else if (event.action == KeyEvent.ACTION_UP && isVolumeDown) {
            if (now - lastVolumeDown > 1_200L) volumeUpDown = false
        } else if (isVolumeUp || isVolumeDown) {
            volumeUpDown = false
        }
        return false
    }

    private fun activateAssistant() {
        lastActivation = System.currentTimeMillis()
        ContextCompat.startForegroundService(this, Intent(this, WakeWordService::class.java).apply {
            action = WakeWordSettings.ACTION_ACTIVATE
        })
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() { volumeUpDown = false }
}
