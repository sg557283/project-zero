package com.zero.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zero.assistant.core.ShortcutAction
import com.zero.assistant.core.ShortcutActionType
import com.zero.assistant.core.ShortcutRepository
import com.zero.assistant.core.ShortcutValidator
import com.zero.assistant.core.UserShortcut

@Composable
internal fun MyShortcutsScreen(
    modifier: Modifier,
    repository: ShortcutRepository,
    onRun: (UserShortcut) -> Unit,
) {
    var shortcuts by remember { mutableStateOf(repository.all()) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    val actions = remember { mutableStateListOf<ShortcutAction>() }
    var actionType by remember { mutableStateOf(ShortcutActionType.OpenApp) }
    var actionValue by remember { mutableStateOf("youtube") }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<UserShortcut?>(null) }

    fun reset() {
        editingId = null
        name = ""
        actions.clear()
        error = null
    }

    fun persist() {
        val previousEnabled = editingId?.let { id -> repository.all().firstOrNull { it.id == id }?.enabled } ?: true
        val shortcut = UserShortcut(
            id = editingId ?: java.util.UUID.randomUUID().toString(),
            name = name,
            actions = actions.toList(),
            enabled = previousEnabled,
        )
        runCatching {
            ShortcutValidator.validate(shortcut)
            repository.save(shortcut)
            shortcuts = repository.all()
            reset()
        }.onFailure { error = it.message ?: "Shortcut could not be saved" }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("My Shortcuts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (editingId == null) "Build a routine" else "Edit routine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(name, { name = it; error = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Shortcut name") }, singleLine = true)
                Text("Add an action", style = MaterialTheme.typography.labelLarge)
                LazyColumn(Modifier.height(92.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(ShortcutActionType.entries.toList()) { type ->
                        FilterChip(selected = actionType == type, onClick = {
                            actionType = type
                            actionValue = when (type) {
                                ShortcutActionType.OpenApp -> "youtube"
                                ShortcutActionType.OpenSettings -> "wifi"
                                ShortcutActionType.SpeakText -> ""
                                else -> ""
                            }
                        }, label = { Text(type.label()) })
                    }
                }
                if (actionType == ShortcutActionType.OpenApp || actionType == ShortcutActionType.OpenSettings || actionType == ShortcutActionType.SpeakText) {
                    OutlinedTextField(
                        value = actionValue,
                        onValueChange = { actionValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(when (actionType) {
                                ShortcutActionType.OpenApp -> "youtube, whatsapp, camera, settings"
                                ShortcutActionType.OpenSettings -> "wifi, bluetooth, sound, display, accessibility, text_to_speech"
                                else -> "Text to speak"
                            })
                        },
                    )
                }
                TextButton(onClick = {
                    val action = ShortcutAction(actionType, if (actionType == ShortcutActionType.SpeakText) actionValue.trim() else actionValue.trim())
                    val candidate = UserShortcut(name = name.ifBlank { "Draft" }, actions = actions + action)
                    runCatching { ShortcutValidator.validate(candidate) }
                        .onSuccess { actions.add(action); error = null }
                        .onFailure { error = it.message ?: "Action is invalid" }
                }) { Text("Add action") }
                Text("Selected sequence", fontWeight = FontWeight.Medium)
                if (actions.isEmpty()) Text("No actions added.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                actions.forEachIndexed { index, action ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}. ${action.label()}", modifier = Modifier.weight(1f))
                        TextButton(onClick = { if (index > 0) actions.add(index - 1, actions.removeAt(index)) }, enabled = index > 0) { Text("Up") }
                        TextButton(onClick = { if (index < actions.lastIndex) actions.add(index + 1, actions.removeAt(index)) }, enabled = index < actions.lastIndex) { Text("Down") }
                        TextButton(onClick = { actions.removeAt(index) }) { Text("Remove") }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = ::persist, enabled = name.isNotBlank() && actions.isNotEmpty()) { Text("Save shortcut") }
                    if (editingId != null) TextButton(onClick = ::reset) { Text("Cancel") }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (shortcuts.isEmpty()) item { Text("No shortcuts yet. Build a routine from supported actions above.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(shortcuts, key = { it.id }) { shortcut ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(shortcut.name, fontWeight = FontWeight.SemiBold)
                                Text(shortcut.actions.joinToString("  →  ") { it.label() }, style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(shortcut.enabled, onCheckedChange = { enabled ->
                                repository.setEnabled(shortcut.id, enabled)
                                shortcuts = repository.all()
                            })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onRun(shortcut) }, enabled = shortcut.enabled) { Text("Run") }
                            TextButton(onClick = {
                                editingId = shortcut.id
                                name = shortcut.name
                                actions.clear()
                                actions.addAll(shortcut.actions)
                                error = null
                            }) { Text("Edit") }
                            TextButton(onClick = {
                                runCatching { repository.duplicate(shortcut.id, "${shortcut.name} Copy") }
                                    .onSuccess { shortcuts = repository.all() }
                                    .onFailure { error = it.message }
                            }) { Text("Duplicate") }
                            TextButton(onClick = { pendingDelete = shortcut }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { shortcut ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete shortcut?") },
            text = { Text("${shortcut.name} will be removed from this device.") },
            confirmButton = { TextButton(onClick = {
                repository.delete(shortcut.id)
                shortcuts = repository.all()
                if (editingId == shortcut.id) reset()
                pendingDelete = null
            }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Keep") } },
        )
    }
}

private fun ShortcutActionType.label(): String = when (this) {
    ShortcutActionType.OpenApp -> "Open app"
    ShortcutActionType.OpenSettings -> "Open Settings"
    ShortcutActionType.ShowTodayTasks -> "Show today's tasks"
    ShortcutActionType.ShowBatteryStatus -> "Battery status"
    ShortcutActionType.TurnFlashlightOn -> "Flashlight on"
    ShortcutActionType.TurnFlashlightOff -> "Flashlight off"
    ShortcutActionType.ShowReminders -> "Show reminders"
    ShortcutActionType.ShowNotes -> "Show notes"
    ShortcutActionType.SpeakText -> "Speak text"
}

private fun ShortcutAction.label(): String = when (type) {
    ShortcutActionType.OpenApp -> "Open ${value.replaceFirstChar(Char::uppercase)}"
    ShortcutActionType.OpenSettings -> "Open ${value.replace('_', ' ')} settings"
    ShortcutActionType.SpeakText -> "Speak: $value"
    else -> type.label()
}
