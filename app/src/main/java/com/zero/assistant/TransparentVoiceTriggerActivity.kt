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
        setContentView(R.layout.activity_transparent_trigger)
        findViewById<Button>(R.id.trigger_cancel).setOnClickListener { finish() }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startVoiceCommand()
        } else {
            findViewById<TextView>(R.id.trigger_message).text = "ZERO needs microphone access to hear a command."
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
            finish()
        }
    }

    private fun startVoiceCommand() {
        try {
            ContextCompat.startForegroundService(
                this,
                Intent(this, WakeWordService::class.java).setAction(WakeWordSettings.ACTION_ONE_SHOT_COMMAND),
            )
            finish()
        } catch (failure: SecurityException) {
            findViewById<TextView>(R.id.trigger_message).text =
                failure.message ?: "Android denied microphone service startup."
            findViewById<Button>(R.id.trigger_cancel).text = "Close"
        } catch (failure: IllegalStateException) {
            findViewById<TextView>(R.id.trigger_message).text =
                failure.message ?: "ZERO could not start voice input."
            findViewById<Button>(R.id.trigger_cancel).text = "Close"
        }
    }

    companion object {
        private const val REQUEST_AUDIO = 8201
    }
}
