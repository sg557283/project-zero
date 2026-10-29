package com.zero.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.zero.assistant.core.EmergencyValidation
import com.zero.assistant.data.EmergencyPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun EmergencyScreen(modifier: Modifier) {
    val context = LocalContext.current
    val preferences = remember { EmergencyPreferences(context) }
    val savedEmergency = remember { preferences.load() }
    var contact by remember { mutableStateOf(savedEmergency.first) }
    var message by remember { mutableStateOf(savedEmergency.second) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var action by remember { mutableStateOf<String?>(null) }
    var flashlightOn by remember { mutableStateOf(false) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        confirmation = if (granted) toggleFlashlight(context, !flashlightOn).also { flashlightOn = it == "enabled" }
            else "Camera permission is required for the flashlight."
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Emergency Quick Mode", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        Text("Emergency contacts and approved message stay in app-private local settings. Calls and messages are handled by Android apps outside ZERO.")
        OutlinedTextField(contact, { contact = it }, Modifier.fillMaxWidth(), label = { Text("Emergency phone number") }, singleLine = true)
        OutlinedTextField(message, { message = it }, Modifier.fillMaxWidth(), label = { Text("Approved help message") }, minLines = 3)
        Button(onClick = {
            val normalized = EmergencyValidation.normalizePhone(contact)
            val messageError = EmergencyValidation.validateMessage(message)
            when {
                normalized == null -> confirmation = "Enter a valid phone number with 7 to 15 digits."
                messageError != null -> confirmation = messageError
                else -> {
                    contact = normalized
                    preferences.save(normalized, message.trim())
                    confirmation = "Emergency details saved on this device."
                }
            }
        }) { Text("Save emergency details") }
        Button(onClick = { action = "call" }, enabled = contact.trim().isNotEmpty()) { Text("Call emergency contact") }
        Button(onClick = { action = "message" }, enabled = contact.trim().isNotEmpty() && message.trim().isNotEmpty()) { Text("Prepare help message") }
        Button(onClick = { action = "dialer" }) { Text("Open phone dialer") }
        Button(onClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                confirmation = toggleFlashlight(context, !flashlightOn).also { if (it == "enabled") flashlightOn = true else if (it == "disabled") flashlightOn = false }
            } else cameraPermission.launch(Manifest.permission.CAMERA)
        }) { Text(if (flashlightOn) "Turn flashlight off" else "Turn flashlight on") }
        confirmation?.let { Text(it) }
    }
    action?.let { requested ->
        AlertDialog(onDismissRequest = { action = null }, title = { Text("Confirm emergency action") }, text = {
            Text(if (requested == "call") "Open the Android dialer for the saved contact? You will confirm the call in Android." else "Prepare a help message for review in your messaging app?")
        }, confirmButton = { TextButton(onClick = {
            if (requested == "call" || requested == "dialer") {
                val phone = if (requested == "call") EmergencyValidation.normalizePhone(contact) else null
                if (requested == "call" && phone == null) { confirmation = "Contact is invalid. Update and save it first."; action = null; return@TextButton }
                val uri = Uri.parse("tel:${phone.orEmpty()}")
                val intent = Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(context.packageManager) == null) { confirmation = "No phone dialer app is available on this device."; action = null; return@TextButton }
                context.startActivity(intent)
            } else if (requested == "message") {
                val phone = EmergencyValidation.normalizePhone(contact)
                val messageError = EmergencyValidation.validateMessage(message)
                if (phone == null || messageError != null) { confirmation = messageError ?: "Contact is invalid. Update and save it first."; action = null; return@TextButton }
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).apply {
                    putExtra("sms_body", message.trim())
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (intent.resolveActivity(context.packageManager) == null) { confirmation = "No messaging app is available to prepare this message."; action = null; return@TextButton }
                context.startActivity(intent)
            }
            confirmation = "Android opened for review. ZERO cannot confirm that a message was sent or a call was completed."
            action = null
        }) { Text("Continue") } }, dismissButton = { TextButton(onClick = { action = null }) { Text("Cancel") } })
    }
}

private fun toggleFlashlight(context: Context, enable: Boolean): String = try {
    val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val cameraId = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        ?: return "No torch-capable camera is available on this device."
    manager.setTorchMode(cameraId, enable)
    if (enable) "enabled" else "disabled"
} catch (_: Exception) {
    "Flashlight could not be changed. Check camera access and device hardware."
}
