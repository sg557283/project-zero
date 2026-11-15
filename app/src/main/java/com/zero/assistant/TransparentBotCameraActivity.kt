package com.zero.assistant

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.zero.assistant.data.TorchController

class TransparentBotCameraActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transparent_trigger)
        findViewById<TextView>(R.id.trigger_message).text = "Allow camera access to toggle the flashlight."
        findViewById<Button>(R.id.trigger_cancel).setOnClickListener { finish() }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            toggleTorch()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
        }
    }

    @Deprecated("Permission results are delivered through this callback on the supported minimum Android version.")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CAMERA) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            toggleTorch()
        } else {
            findViewById<TextView>(R.id.trigger_message).text = "Camera permission is needed for flashlight."
            findViewById<Button>(R.id.trigger_cancel).text = "Close"
        }
    }

    private fun toggleTorch() {
        TorchController.toggle(this).fold(
            onSuccess = { enabled ->
                findViewById<TextView>(R.id.trigger_message).text = if (enabled) "Flashlight on." else "Flashlight off."
                findViewById<Button>(R.id.trigger_cancel).text = "Close"
            },
            onFailure = { failure ->
                findViewById<TextView>(R.id.trigger_message).text = failure.message ?: "Flashlight unavailable."
                findViewById<Button>(R.id.trigger_cancel).text = "Close"
            },
        )
    }

    companion object {
        private const val REQUEST_CAMERA = 8202
    }
}
