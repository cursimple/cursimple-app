package com.x500x.cursimple.app.notice

import com.x500x.cursimple.core.data.EventReminderPreferences
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import java.time.ZonedDateTime
import java.time.ZoneId

data class EventReminderPlan(
    val eventId: String,
    val occurrenceDate: String,
    val triggerAtMillis: Long,
    val advanceMinutes: Int,
    val title: String,
    val location: String,
    val startTime: String,
    val endTime: String,
)

object ScheduleEventReminderPlanner {
    fun nextReminder(
        event: ScheduleEvent,
        now: ZonedDateTime,
        global: EventReminderPreferences,
        zone: ZoneId = now.zone,
    ): EventReminderPlan? {
        if (!global.enabled || !event.isValid || event.reminderMinutes == -1) return null
        val advance = EventReminderPreferences.coerceAdvanceMinutes(
            event.reminderMinutes ?: global.advanceMinutes,
        )
        val first = event.localDate ?: return null
        val lastDate = event.repeatUntil?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
        val searchStart = now.toLocalDate()
        val occurrence = generateSequence(searchStart) { it.plusDays(1) }
            .take(370)
            .map { it }
            .firstOrNull { date ->
                !date.isBefore(first) &&
                    (lastDate == null || !date.isAfter(lastDate)) &&
                    event.occursOn(date) &&
                    event.startLocalTime != null &&
                    ZonedDateTime.of(date, event.startLocalTime, zone)
                        .minusMinutes(advance.toLong())
                        .isAfter(now)
            } ?: return null
        return planForOccurrence(event, occurrence.toString(), global, zone)
    }

    fun planForOccurrence(
        event: ScheduleEvent,
        occurrenceDate: String,
        global: EventReminderPreferences,
        zone: ZoneId,
    ): EventReminderPlan? {
        if (!global.enabled || !event.isValid || event.reminderMinutes == -1 || !event.occursOn(java.time.LocalDate.parse(occurrenceDate))) return null
        val advance = EventReminderPreferences.coerceAdvanceMinutes(event.reminderMinutes ?: global.advanceMinutes)
        val occurrence = java.time.LocalDate.parse(occurrenceDate)
        val start = event.startLocalTime ?: return null
        val trigger = ZonedDateTime.of(occurrence, start, zone).minusMinutes(advance.toLong())
        return EventReminderPlan(
            eventId = event.id,
            occurrenceDate = occurrence.toString(),
            triggerAtMillis = trigger.toInstant().toEpochMilli(),
            advanceMinutes = advance,
            title = event.title,
            location = event.location,
            startTime = event.startTime,
            endTime = event.endTime,
        )
    }
}
