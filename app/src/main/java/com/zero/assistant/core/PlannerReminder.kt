package com.zero.assistant.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

enum class ReminderRepeat { Never, Daily, Weekly }

data class PlannerReminder(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val localDateTime: String,
    val repeat: ReminderRepeat = ReminderRepeat.Never,
    val enabled: Boolean = true,
    val completed: Boolean = false,
)

interface ReminderStorage {
    fun read(): List<PlannerReminder>
    fun write(reminders: List<PlannerReminder>)
}

class ReminderRepository(private val storage: ReminderStorage) {
    fun all(): List<PlannerReminder> = storage.read().sortedBy { it.localDateTime }

    fun save(reminder: PlannerReminder) {
        require(reminder.title.isNotBlank()) { "Reminder title cannot be empty" }
        require(reminder.title.length <= 160) { "Reminder title is too long" }
        require(runCatching { LocalDateTime.parse(reminder.localDateTime) }.isSuccess) {
            "Reminder date and time are invalid"
        }
        val updated = storage.read().filterNot { it.id == reminder.id } + reminder.copy(title = reminder.title.trim())
        storage.write(updated)
    }

    fun setEnabled(id: String, enabled: Boolean) {
        update(id) { it.copy(enabled = enabled) }
    }

    fun setCompleted(id: String, completed: Boolean) {
        update(id) { it.copy(completed = completed) }
    }

    fun delete(id: String) = storage.write(storage.read().filterNot { it.id == id })

    private fun update(id: String, transform: (PlannerReminder) -> PlannerReminder) {
        val current = storage.read()
        val reminder = current.firstOrNull { it.id == id } ?: return
        storage.write(current.map { if (it.id == id) transform(reminder) else it })
    }
}

object ReminderSchedule {
    fun nextTriggerEpochMillis(reminder: PlannerReminder, nowMillis: Long, zoneId: ZoneId): Long? {
        if (!reminder.enabled || reminder.completed) return null
        val first = LocalDateTime.parse(reminder.localDateTime)
        val startDate = first.toLocalDate()
        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val next = when (reminder.repeat) {
            ReminderRepeat.Never -> first.atZone(zoneId).takeIf { it.toInstant().toEpochMilli() > nowMillis }
            ReminderRepeat.Daily -> {
                var candidateDate = if (now.toLocalDate().isBefore(startDate)) startDate else now.toLocalDate()
                var candidate = LocalDateTime.of(candidateDate, first.toLocalTime()).atZone(zoneId)
                if (candidate.toInstant().toEpochMilli() <= nowMillis) {
                    candidateDate = candidateDate.plusDays(1)
                    candidate = LocalDateTime.of(candidateDate, first.toLocalTime()).atZone(zoneId)
                }
                candidate
            }
            ReminderRepeat.Weekly -> {
                val earliestDate = if (now.toLocalDate().isBefore(startDate)) startDate else now.toLocalDate()
                var date = earliestDate.with(java.time.temporal.TemporalAdjusters.nextOrSame(first.dayOfWeek))
                var candidate = LocalDateTime.of(date, first.toLocalTime()).atZone(zoneId)
                if (candidate.toInstant().toEpochMilli() <= nowMillis) {
                    date = date.plusWeeks(1)
                    candidate = LocalDateTime.of(date, first.toLocalTime()).atZone(zoneId)
                }
                candidate
            }
        }
        return next?.toInstant()?.toEpochMilli()
    }

    fun isToday(reminder: PlannerReminder, nowMillis: Long, zoneId: ZoneId): Boolean {
        val reminderDateTime = LocalDateTime.parse(reminder.localDateTime)
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        return when (reminder.repeat) {
            ReminderRepeat.Never -> reminderDateTime.toLocalDate() == today
            ReminderRepeat.Daily -> !today.isBefore(reminderDateTime.toLocalDate())
            ReminderRepeat.Weekly -> !today.isBefore(reminderDateTime.toLocalDate()) && today.dayOfWeek == reminderDateTime.dayOfWeek
        }
    }

    fun localTime(reminder: PlannerReminder): LocalTime = LocalDateTime.parse(reminder.localDateTime).toLocalTime()
    fun localDate(reminder: PlannerReminder): LocalDate = LocalDateTime.parse(reminder.localDateTime).toLocalDate()
}