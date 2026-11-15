package com.zero.assistant

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.zero.assistant.data.TorchController

class TransparentWidgetCameraActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_voice_trigger)
        findViewById<TextView>(R.id.widget_voice_message).text = "Allow camera access to toggle the flashlight."
        findViewById<Button>(R.id.widget_voice_cancel).setOnClickListener {
            ZeroAssistantWidget.clearTemporaryStatus(this)
            finish()
        }
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
            ZeroAssistantWidget.setTemporaryStatus(this, "Camera permission is needed for flashlight", 5_000L)
            finish()
        }
    }

    private fun toggleTorch() {
        TorchController.toggle(this).fold(
            onSuccess = { enabled ->
                ZeroAssistantWidget.setTemporaryStatus(this, if (enabled) "Flashlight on" else "Flashlight off", 2_500L)
                finish()
            },
            onFailure = { failure ->
                ZeroAssistantWidget.setTemporaryStatus(
                    this,
                    failure.message?.take(36) ?: "Flashlight unavailable",
                    5_000L,
                )
                finish()
            },
        )
    }

    companion object {
        private const val REQUEST_CAMERA = 8202
    }
}
