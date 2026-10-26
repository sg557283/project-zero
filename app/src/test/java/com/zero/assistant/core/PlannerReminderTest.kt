package com.zero.assistant.core

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerReminderTest {
    private class MemoryStorage : ReminderStorage {
        var values = emptyList<PlannerReminder>()
        override fun read() = values
        override fun write(reminders: List<PlannerReminder>) { values = reminders.toList() }
    }

    @Test
    fun persistsCreateEditCompleteEnableAndDelete() {
        val storage = MemoryStorage()
        val repository = ReminderRepository(storage)
        val reminder = PlannerReminder(id = "one", title = "  Call Sam  ", localDateTime = "2026-10-26T16:30")
        repository.save(reminder)
        assertEquals("Call Sam", repository.all().single().title)

        repository.save(reminder.copy(title = "Call Sam tomorrow"))
        repository.setCompleted("one", true)
        repository.setEnabled("one", false)
        assertEquals("Call Sam tomorrow", repository.all().single().title)
        assertTrue(repository.all().single().completed)
        assertFalse(repository.all().single().enabled)

        repository.delete("one")
        assertTrue(repository.all().isEmpty())
    }

    @Test
    fun rejectsEmptyAndInvalidReminders() {
        val repository = ReminderRepository(MemoryStorage())
        assertTrue(runCatching { repository.save(PlannerReminder(title = " ", localDateTime = "2026-01-01T10:00")) }.isFailure)
        assertTrue(runCatching { repository.save(PlannerReminder(title = "Valid", localDateTime = "not a date")) }.isFailure)
    }

    @Test
    fun calculatesOneTimeDailyAndWeeklyOccurrences() {
        val zone = ZoneId.of("UTC")
        val now = LocalDateTime.parse("2026-10-26T12:00").atZone(zone).toInstant().toEpochMilli()
        val once = PlannerReminder(title = "Once", localDateTime = "2026-10-26T13:00")
        val daily = once.copy(repeat = ReminderRepeat.Daily)
        val weekly = once.copy(localDateTime = "2026-10-27T11:00", repeat = ReminderRepeat.Weekly)

        assertEquals(LocalDateTime.parse("2026-10-26T13:00").atZone(zone).toInstant().toEpochMilli(), ReminderSchedule.nextTriggerEpochMillis(once, now, zone))
        assertEquals(LocalDateTime.parse("2026-10-26T13:00").atZone(zone).toInstant().toEpochMilli(), ReminderSchedule.nextTriggerEpochMillis(daily, now, zone))
        val afterDailyTime = LocalDateTime.parse("2026-10-26T14:00").atZone(zone).toInstant().toEpochMilli()
        assertEquals(LocalDateTime.parse("2026-10-27T13:00").atZone(zone).toInstant().toEpochMilli(), ReminderSchedule.nextTriggerEpochMillis(daily, afterDailyTime, zone))
        assertEquals(LocalDateTime.parse("2026-10-27T11:00").atZone(zone).toInstant().toEpochMilli(), ReminderSchedule.nextTriggerEpochMillis(weekly, now, zone))
    }

    @Test
    fun doesNotScheduleDisabledCompletedOrPastOneTimeReminders() {
        val zone = ZoneId.of("UTC")
        val now = LocalDateTime.parse("2026-10-26T12:00").atZone(zone).toInstant().toEpochMilli()
        val reminder = PlannerReminder(title = "Past", localDateTime = "2026-10-26T11:00")

        assertNull(ReminderSchedule.nextTriggerEpochMillis(reminder, now, zone))
        assertNull(ReminderSchedule.nextTriggerEpochMillis(reminder.copy(enabled = false), now, zone))
        assertNull(ReminderSchedule.nextTriggerEpochMillis(reminder.copy(completed = true, repeat = ReminderRepeat.Daily), now, zone))
    }

    @Test
    fun repeatingRemindersRespectTheirStartDateAndAppearOnMatchingDays() {
        val zone = ZoneId.of("UTC")
        val beforeStart = LocalDateTime.parse("2026-10-26T12:00").atZone(zone).toInstant().toEpochMilli()
        val daily = PlannerReminder(title = "Future daily", localDateTime = "2026-10-28T13:00", repeat = ReminderRepeat.Daily)
        val weekly = PlannerReminder(title = "Future weekly", localDateTime = "2026-10-28T13:00", repeat = ReminderRepeat.Weekly)

        assertEquals(LocalDateTime.parse("2026-10-28T13:00").atZone(zone).toInstant().toEpochMilli(), ReminderSchedule.nextTriggerEpochMillis(daily, beforeStart, zone))
        assertEquals(LocalDateTime.parse("2026-10-28T13:00").atZone(zone).toInstant().toEpochMilli(), ReminderSchedule.nextTriggerEpochMillis(weekly, beforeStart, zone))
        val followingWeekday = LocalDateTime.parse("2026-11-04T12:00").atZone(zone).toInstant().toEpochMilli()
        assertTrue(ReminderSchedule.isToday(weekly, followingWeekday, zone))
    }
}