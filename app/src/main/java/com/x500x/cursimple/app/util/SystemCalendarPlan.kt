package com.x500x.cursimple.app.util

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Null [rrule] means one occurrence; [durationMinutes] determines recurring end times. */
data class CalendarEventDraft(
    val title: String,
    val description: String,
    val location: String,
    val start: LocalDateTime,
    val durationMinutes: Long,
    val rrule: String?,
    val exdatesUtc: List<String>,
)

private val UTC_BASIC: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

/**
 * Compress regular classes into recurrence rules with exclusions; moved occurrences remain
 * separate.
 */
fun PlannedCourse.toCalendarDrafts(zone: ZoneId, description: String): List<CalendarEventDraft> {
    val drafts = mutableListOf<CalendarEventDraft>()
    val mainstream = occurrences.filterNot { it.displaced }.sortedBy { it.date }
    if (mainstream.isNotEmpty()) {
        val first = mainstream.first()
        val last = mainstream.last()
        val present = mainstream.map { it.date }.toSet()
        val grid = generateSequence(first.date) { it.plusDays(7) }
            .takeWhile { !it.isAfter(last.date) }
            .toList()
        val missing = grid.filterNot { it in present }
        val rrule: String?
        val exdates: List<String>
        when {
            grid.size <= 1 -> {
                rrule = null
                exdates = emptyList()
            }
            missing.isEmpty() -> {
                rrule = "FREQ=WEEKLY;COUNT=${grid.size}"
                exdates = emptyList()
            }
            else -> {
                rrule = "FREQ=WEEKLY;UNTIL=${last.start.toUtcBasic(zone)}"
                exdates = missing.map { it.atTime(first.start.toLocalTime()).toUtcBasic(zone) }
            }
        }
        drafts.add(
            CalendarEventDraft(
                title = course.title,
                description = description,
                location = course.location,
                start = first.start,
                durationMinutes = java.time.Duration.between(first.start, first.end).toMinutes(),
                rrule = rrule,
                exdatesUtc = exdates,
            ),
        )
    }

    occurrences.filter { it.displaced }.sortedBy { it.date }.forEach { occurrence ->
        drafts.add(
            CalendarEventDraft(
                title = course.title,
                description = description,
                location = course.location,
                start = occurrence.start,
                durationMinutes = java.time.Duration.between(occurrence.start, occurrence.end).toMinutes(),
                rrule = null,
                exdatesUtc = emptyList(),
            ),
        )
    }
    return drafts
}

/** Count expanded occurrences rather than recurring event rows. */
fun PlannedCourse.occurrenceCount(): Int = occurrences.size

private fun LocalDateTime.toUtcBasic(zone: ZoneId): String =
    UTC_BASIC.format(atZone(zone).withZoneSameInstant(ZoneOffset.UTC))
