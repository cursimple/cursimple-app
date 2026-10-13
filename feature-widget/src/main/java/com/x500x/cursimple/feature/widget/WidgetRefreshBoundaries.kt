package com.x500x.cursimple.feature.widget

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import com.x500x.cursimple.core.kernel.model.occurrencesOn
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Schedule class-state boundaries and a dedicated midnight refresh alongside the periodic
 * guard. [eventSpans] are the start and end of events shown on the day list.
 */
internal fun widgetRefreshBoundaries(
    slots: List<ClassSlotTime>,
    now: LocalDateTime,
    leadMinutes: Long = 5,
    soonMinutes: Long = SOON_THRESHOLD_MINUTES,
    limit: Int = 8,
    eventSpans: List<Pair<LocalDateTime, LocalDateTime>> = emptyList(),
): List<LocalDateTime> {
    if (limit <= 0) return emptyList()
    val today = now.toLocalDate()
    val midnight = today.plusDays(1).atStartOfDay()
    if (slots.isEmpty() && eventSpans.isEmpty()) return listOf(midnight)
    val boundaries = sortedSetOf<LocalDateTime>()
    for ((startAt, endAt) in eventSpans) {
        boundaries.add(startAt.minusMinutes(soonMinutes))
        boundaries.add(startAt)
        boundaries.add(endAt)
    }
    for (dayOffset in -1L..1L) {
        val date = today.plusDays(dayOffset)
        for (slot in slots) {
            val start = slot.parseTime(slot.startTime) ?: continue
            val end = slot.parseTime(slot.endTime) ?: continue
            val startAt = date.atTime(start)
            val endAt = if (end.isAfter(start)) date.atTime(end) else date.plusDays(1).atTime(end)
            boundaries.add(startAt.minusMinutes(soonMinutes))
            boundaries.add(startAt.minusMinutes(leadMinutes))
            boundaries.add(startAt)
            boundaries.add(endAt)
        }
    }
    // Reserve midnight's own slot so dense class boundaries cannot displace it.
    val upcoming = boundaries.filter { it.isAfter(now) && it != midnight }.take(limit - 1)
    return (upcoming + midnight).sorted()
}

/** Clock spans of the events on [today] and the day after, the dates a boundary can fall on. */
internal fun widgetEventSpans(
    events: List<ScheduleEvent>,
    today: LocalDate,
): List<Pair<LocalDateTime, LocalDateTime>> = (0L..1L).flatMap { offset ->
    val date = today.plusDays(offset)
    events.occurrencesOn(date).mapNotNull { event ->
        val start = event.startLocalTime ?: return@mapNotNull null
        val end = event.endLocalTime ?: return@mapNotNull null
        date.atTime(start) to date.atTime(end)
    }
}

private fun ClassSlotTime.parseTime(value: String): LocalTime? =
    runCatching { LocalTime.parse(value) }.getOrNull()
