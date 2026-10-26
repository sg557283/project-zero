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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zero.assistant.core.PlannerReminder
import com.zero.assistant.core.ReminderRepeat
import com.zero.assistant.core.ReminderRepository
import com.zero.assistant.core.ReminderSchedule
import com.zero.assistant.data.ReminderScheduler
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun PlannerScreen(
    modifier: Modifier,
    initialTitle: String?,
    onInitialTitleConsumed: () -> Unit,
    repository: ReminderRepository,
    scheduler: ReminderScheduler,
    notificationsGranted: Boolean,
    onRequestNotifications: () -> Unit,
    onCreateFromVoice: ((String) -> Unit) -> Unit,
) {
    var reminders by remember { mutableStateOf(repository.all()) }
    var editing by remember { mutableStateOf<PlannerReminder?>(null) }
    var title by remember(initialTitle) { mutableStateOf(initialTitle.orEmpty()) }
    var date by remember { mutableStateOf(LocalDateTime.now().plusHours(1).format(DateTimeFormatter.ISO_LOCAL_DATE)) }
    var time by remember { mutableStateOf(LocalDateTime.now().plusHours(1).format(DateTimeFormatter.ofPattern("HH:mm"))) }
    var repeat by remember { mutableStateOf(ReminderRepeat.Never) }
    var pendingDelete by remember { mutableStateOf<PlannerReminder?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun resetForm() {
        editing = null
        title = ""
        val nextHour = LocalDateTime.now().plusHours(1)
        date = nextHour.format(DateTimeFormatter.ISO_LOCAL_DATE)
        time = nextHour.format(DateTimeFormatter.ofPattern("HH:mm"))
        repeat = ReminderRepeat.Never
        error = null
    }

    fun persistReminder() {
        val reminder = PlannerReminder(
            id = editing?.id ?: java.util.UUID.randomUUID().toString(),
            title = title,
            localDateTime = "${date}T$time",
            repeat = repeat,
            enabled = editing?.enabled ?: true,
            completed = editing?.completed ?: false,
        )
        runCatching {
            repository.save(reminder)
            scheduler.schedule(reminder)
            reminders = repository.all()
            resetForm()
        }.onFailure { error = it.message ?: "Reminder could not be saved" }
    }

    androidx.compose.runtime.LaunchedEffect(initialTitle) {
        if (initialTitle != null) onInitialTitleConsumed()
    }

    val zone = ZoneId.systemDefault()
    val todayItems = reminders.filter { ReminderSchedule.isToday(it, System.currentTimeMillis(), zone) }
    val upcomingItems = reminders.filterNot { ReminderSchedule.isToday(it, System.currentTimeMillis(), zone) }

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("Daily Planner", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (!notificationsGranted) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Notifications are off", fontWeight = FontWeight.SemiBold)
                    Text("Reminders stay saved, but Android won't show them until notification permission is granted.")
                    Button(onClick = onRequestNotifications) { Text("Enable notifications") }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (editing == null) "New reminder" else "Edit reminder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(title, { title = it; error = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(date, { date = it; error = null }, modifier = Modifier.weight(1f), label = { Text("Date (YYYY-MM-DD)") }, singleLine = true)
                    OutlinedTextField(time, { time = it; error = null }, modifier = Modifier.weight(1f), label = { Text("Time (HH:MM)") }, singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReminderRepeat.entries.forEach { option ->
                        FilterChip(selected = repeat == option, onClick = { repeat = option }, label = { Text(option.name) })
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = ::persistReminder, enabled = title.isNotBlank()) { Text(if (editing == null) "Save reminder" else "Update") }
                    if (editing != null) TextButton(onClick = ::resetForm) { Text("Cancel edit") }
                    TextButton(onClick = { onCreateFromVoice { transcript -> title = transcript } }) { Text("Fill from voice") }
                }
                Text("Reminders use inexact Android scheduling. Delivery may be delayed by Android power management.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Today · ${todayItems.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            if (todayItems.isEmpty()) item { Text("Nothing planned for today.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(todayItems, key = { it.id }) { reminder ->
                ReminderRow(reminder, onEdit = {
                    editing = reminder
                    title = reminder.title
                    date = reminder.localDateTime.substringBefore('T')
                    time = reminder.localDateTime.substringAfter('T')
                    repeat = reminder.repeat
                    error = null
                }, onDelete = { pendingDelete = reminder }, onToggleEnabled = { enabled ->
                    repository.setEnabled(reminder.id, enabled)
                    val changed = reminder.copy(enabled = enabled)
                    scheduler.schedule(changed)
                    reminders = repository.all()
                }, onToggleComplete = { completed ->
                    repository.setCompleted(reminder.id, completed)
                    val changed = reminder.copy(completed = completed)
                    scheduler.schedule(changed)
                    reminders = repository.all()
                })
            }
            item { Text("Upcoming", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            if (upcomingItems.isEmpty()) item { Text("No upcoming reminders.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(upcomingItems, key = { it.id }) { reminder ->
                ReminderRow(reminder, onEdit = {
                    editing = reminder
                    title = reminder.title
                    date = reminder.localDateTime.substringBefore('T')
                    time = reminder.localDateTime.substringAfter('T')
                    repeat = reminder.repeat
                    error = null
                }, onDelete = { pendingDelete = reminder }, onToggleEnabled = { enabled ->
                    repository.setEnabled(reminder.id, enabled)
                    scheduler.schedule(reminder.copy(enabled = enabled))
                    reminders = repository.all()
                }, onToggleComplete = { completed ->
                    repository.setCompleted(reminder.id, completed)
                    scheduler.schedule(reminder.copy(completed = completed))
                    reminders = repository.all()
                })
            }
        }
    }

    pendingDelete?.let { reminder ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete reminder?") },
            text = { Text("${reminder.title} will be removed from this device.") },
            confirmButton = { TextButton(onClick = {
                repository.delete(reminder.id)
                scheduler.cancel(reminder.id)
                reminders = repository.all()
                if (editing?.id == reminder.id) resetForm()
                pendingDelete = null
            }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Keep") } },
        )
    }
}

@Composable
private fun ReminderRow(
    reminder: PlannerReminder,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleComplete: (Boolean) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(reminder.title, fontWeight = FontWeight.SemiBold)
                    Text("${reminder.localDateTime.replace('T', ' ')} · ${reminder.repeat.name} · ${if (reminder.completed) "Completed" else "Pending"}", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = reminder.enabled, onCheckedChange = onToggleEnabled, enabled = !reminder.completed)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onToggleComplete(!reminder.completed) }) { Text(if (reminder.completed) "Reopen" else "Complete") }
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}
