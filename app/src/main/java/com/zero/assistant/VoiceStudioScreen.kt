package com.zero.assistant

import android.Manifest
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.zero.assistant.core.OfflineTtsVoice
import com.zero.assistant.core.SpeechOutputStatus
import com.zero.assistant.core.VoiceStudioCommands

@Composable
internal fun VoiceStudioScreen(
    modifier: Modifier,
    offlineVoices: List<OfflineTtsVoice>,
    speechStatus: SpeechOutputStatus,
    startListening: ((String) -> Unit, (Boolean) -> Unit) -> Unit,
    stopListening: () -> Unit,
    speak: (String) -> Unit,
    stopSpeech: () -> Unit,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Recognition uses the installed offline Vosk model.") }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            listening = false
            status = "Microphone permission was denied."
        } else {
            listening = true
            startListening({ result ->
                listening = false
                val command = result.trim().lowercase()
                text = if (command in setOf("full stop", "comma", "new line", "delete last sentence")) {
                    VoiceStudioCommands.process(text, command)
                } else {
                    if (text.isBlank()) result.trim() else "$text ${result.trim()}"
                }
            }, { listening = it })
        }
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Offline Voice Studio", style = MaterialTheme.typography.titleLarge)
        Text("On-device Vosk recognition and installed Android offline voices.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 6,
            label = { Text("Voice typing / text to read") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    listening = true
                    startListening({ result ->
                        listening = false
                        val command = result.trim().lowercase()
                        text = if (command in setOf("full stop", "comma", "new line", "delete last sentence")) {
                            VoiceStudioCommands.process(text, command)
                        } else {
                            if (text.isBlank()) result.trim() else "$text ${result.trim()}"
                        }
                    }, { listening = it })
                } else {
                    microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }, enabled = !listening) { Text(if (listening) "Listening..." else "Dictate") }
            TextButton(onClick = {
                listening = false
                stopListening()
            }, enabled = listening) { Text("Stop") }
            Button(onClick = { speak(text) }, enabled = text.isNotBlank()) { Text("Read aloud") }
            TextButton(onClick = stopSpeech) { Text("Stop speech") }
        }
        Text("Voice controls: “full stop”, “comma”, “new line”, “delete last sentence”.", style = MaterialTheme.typography.bodySmall)
        Text(
            if (offlineVoices.isNotEmpty()) "${offlineVoices.size} installed Android offline voice(s)."
            else "No installed offline Android voice is currently reported.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    },
                )
            }, enabled = text.isNotBlank()) { Text("Share") }
            TextButton(onClick = {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("ZERO Voice Studio", text))
                status = "Copied to the clipboard."
            }, enabled = text.isNotBlank()) { Text("Copy") }
        }
    }
}
