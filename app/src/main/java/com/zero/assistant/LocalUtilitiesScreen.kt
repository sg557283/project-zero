package com.zero.assistant

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.zero.assistant.core.NoteRepository

@Composable
internal fun LocalUtilitiesScreen(modifier: Modifier, noteRepository: NoteRepository) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var clipboardText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Search stays on this device. Special Android access is never changed automatically.") }
    val matches = remember(query) {
        if (query.isBlank()) emptyList() else noteRepository.all().filter { it.text.contains(query, ignoreCase = true) }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Local utilities", style = MaterialTheme.typography.titleLarge)
        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text("Universal local search", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search saved notes") })
        if (query.isNotBlank() && matches.isEmpty()) Text("No matching local notes.")
        matches.forEach { Text(it.text, Modifier.fillMaxWidth()) }

        Text("Smart clipboard", style = MaterialTheme.typography.titleMedium)
        Button(onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboardText = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
            status = if (clipboardText.isBlank()) "The clipboard is empty or unavailable." else "Clipboard text loaded for explicit review. It is not stored as history."
        }) { Text("Read clipboard for this action") }
        if (clipboardText.isNotBlank()) {
            OutlinedTextField(clipboardText, { clipboardText = it }, Modifier.fillMaxWidth(), label = { Text("Selected text") }, minLines = 3)
            TextButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("ZERO text", clipboardText))
                status = "Text copied to the clipboard."
            }) { Text("Copy edited text") }
            TextButton(onClick = {
                noteRepository.save(com.zero.assistant.core.LocalNote(text = clipboardText))
                status = "Text saved to local notes."
                clipboardText = ""
            }) { Text("Save as local note") }
        }

        Text("Notifications and quick access", style = MaterialTheme.typography.titleMedium)
        Text("Notification access and drawing over other apps are special Android permissions. ZERO does not enable them automatically.")
        Button(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Notification access settings") }
        Button(onClick = { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) }) { Text("Overlay permission settings") }

        Text("Language helper", style = MaterialTheme.typography.titleMedium)
        Text("Offline translation is available only when a compatible Android language/model package is installed. Text is not sent anywhere by this screen.")
        Button(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = android.net.Uri.parse("package:${context.packageName}") }) }) { Text("App details") }
    }
}
