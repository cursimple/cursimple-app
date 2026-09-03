package com.x500x.cursimple.app.util

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 一条准备写进系统日历的事件。
 * [rrule] 为空表示只发生一次；[durationMinutes] 用于按重复规则展开时算出每次的结束时间。
 */
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
 * 把一门课整学期的上课时间压成尽量少的事件。
 *
 * 每周固定的那些合成一条按周重复的事件，中间停掉的周写进排除日期；
 * 调课挪走的那几次不在每周节奏上，各自单独成一条。
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

/** 每周重复的那条覆盖多次上课，这里数的是真正会出现在日历上的次数。 */
fun PlannedCourse.occurrenceCount(): Int = occurrences.size

private fun LocalDateTime.toUtcBasic(zone: ZoneId): String =
    UTC_BASIC.format(atZone(zone).withZoneSameInstant(ZoneOffset.UTC))
