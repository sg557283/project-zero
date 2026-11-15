package com.zero.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.zero.assistant.data.WakeWordService
import com.zero.assistant.data.WakeWordSettings

class TransparentVoiceTriggerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_voice_trigger)
        findViewById<Button>(R.id.widget_voice_cancel).setOnClickListener {
            ZeroAssistantWidget.clearTemporaryStatus(this)
            finish()
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startVoiceCommand()
        } else {
            findViewById<TextView>(R.id.widget_voice_message).text = "ZERO needs microphone access to hear a command."
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
        }
    }

    @Deprecated("Permission results are delivered through this callback on the supported minimum Android version.")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_AUDIO) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startVoiceCommand()
        } else {
            ZeroAssistantWidget.setTemporaryStatus(this, "Microphone permission is required", 5_000L)
            finish()
        }
    }

    private fun startVoiceCommand() {
        try {
            ZeroAssistantWidget.setTemporaryStatus(this, "Listening...", 60_000L)
            ContextCompat.startForegroundService(
                this,
                Intent(this, WakeWordService::class.java).setAction(WakeWordSettings.ACTION_WIDGET_COMMAND),
            )
            finish()
        } catch (failure: SecurityException) {
            ZeroAssistantWidget.setTemporaryStatus(this, "Voice service permission is unavailable", 5_000L)
            finish()
        } catch (failure: IllegalStateException) {
            ZeroAssistantWidget.setTemporaryStatus(this, "ZERO could not start voice input", 5_000L)
            finish()
        }
    }

    companion object {
        private const val REQUEST_AUDIO = 8201
    }
}
