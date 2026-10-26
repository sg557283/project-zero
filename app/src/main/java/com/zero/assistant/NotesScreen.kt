package com.zero.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zero.assistant.core.LocalNote
import com.zero.assistant.core.NoteRepository

@Composable
internal fun NotesScreen(modifier: Modifier, repository: NoteRepository) {
    var notes by remember { mutableStateOf(repository.all()) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<LocalNote?>(null) }

    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Notes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = draft,
            onValueChange = { if (it.length <= NoteRepository.MAX_NOTE_LENGTH) { draft = it; error = null } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Write a local note") },
            minLines = 2,
            maxLines = 5,
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                runCatching {
                    repository.save(LocalNote(id = editingId ?: java.util.UUID.randomUUID().toString(), text = draft))
                    notes = repository.all()
                    editingId = null
                    draft = ""
                }.onFailure { error = it.message ?: "Note could not be saved" }
            }, enabled = draft.isNotBlank()) { Text(if (editingId == null) "Save note" else "Update note") }
            if (editingId != null) TextButton(onClick = { editingId = null; draft = "" }) { Text("Cancel") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (notes.isEmpty()) item { Text("No notes saved on this device yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(notes, key = { it.id }) { note ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(note.text)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { editingId = note.id; draft = note.text }) { Text("Edit") }
                            TextButton(onClick = { pendingDelete = note }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete note?") },
            text = { Text("This note is stored only in ZERO on this device.") },
            confirmButton = { TextButton(onClick = {
                repository.delete(note.id)
                notes = repository.all()
                pendingDelete = null
            }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Keep") } },
        )
    }
}
