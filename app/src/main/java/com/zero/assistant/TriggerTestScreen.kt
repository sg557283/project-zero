package com.zero.assistant

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zero.assistant.data.ActionExecutor
import com.zero.assistant.data.AssistantActionSettings
import com.zero.assistant.data.TriggerHealthChecker

@Composable
internal fun TriggerTestScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(TriggerHealthChecker.check(context)) }
    var testMode by remember { mutableStateOf(AssistantActionSettings.testModeEnabled(context)) }
    var latestTrigger by remember { mutableStateOf("No trigger detected yet.") }
    var message by remember { mutableStateOf<String?>(null) }

    val receiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                latestTrigger = intent?.getStringExtra(ActionExecutor.EXTRA_TRIGGER_TEST_MESSAGE)
                    ?: "A trigger was detected."
            }
        }
    }
    DisposableEffect(Unit) {
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ActionExecutor.ACTION_TRIGGER_TEST_FEEDBACK),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose {
            context.unregisterReceiver(receiver)
            AssistantActionSettings.setTestModeEnabled(context, false)
            AssistantActionSettings.syncGestureService(context)
        }
    }

    fun startTestMode() {
        AssistantActionSettings.setTestModeEnabled(context, true)
        testMode = true
        AssistantActionSettings.syncGestureService(context)
            .onFailure { message = it.message ?: "Gesture sensors could not be started." }
        status = TriggerHealthChecker.check(context)
    }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startTestMode()
        else message = "Camera permission is required by Android for the foreground gesture-monitor service."
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Gesture & Shortcut Test", style = MaterialTheme.typography.titleLarge)
        Text(
            "Sensor back-tap is experimental and device-dependent. Test mode announces detections but does not run mapped actions.",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            softWrap = false,
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Accessibility service: ${if (status.accessibilityEnabled) "Enabled" else "Not enabled"}", maxLines = 1, softWrap = false)
                Text("Volume shortcut target: ${if (status.volumeShortcutTargeted) "ZERO is selected" else "Not bound to ZERO"}", maxLines = 1, softWrap = false)
                Text("Accelerometer: ${if (status.accelerometerAvailable) "Available" else "Unavailable"}", maxLines = 1, softWrap = false)
                Text("Gyroscope: ${if (status.gyroscopeAvailable) "Available" else "Unavailable (optional)"}", maxLines = 1, softWrap = false)
                Text(status.backTapNote, style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
                Button(onClick = {
                    runCatching { TriggerHealthChecker.openAccessibilitySettings(context) }
                        .onFailure { message = it.message ?: "Accessibility settings could not be opened." }
                }) { Text("Open Accessibility settings") }
                Button(onClick = { status = TriggerHealthChecker.check(context) }) { Text("Refresh checks") }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Live test mode: ${if (testMode) "Running" else "Stopped"}", maxLines = 1, softWrap = false)
                Text(latestTrigger, style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
                if (!testMode) {
                    Button(onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            startTestMode()
                        } else {
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    }) { Text("Start test mode") }
                } else {
                    Button(onClick = {
                        AssistantActionSettings.setTestModeEnabled(context, false)
                        testMode = false
                        AssistantActionSettings.syncGestureService(context)
                        message = null
                    }) { Text("Stop test mode") }
                }
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text(
            "To test Volume Up + Down, enable that shortcut below and enable ZERO Accessibility in Android settings. Android controls shortcut binding; ZERO cannot change it silently.",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            softWrap = false,
        )
    }
}
