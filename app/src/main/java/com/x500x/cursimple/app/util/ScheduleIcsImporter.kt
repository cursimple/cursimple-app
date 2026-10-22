package com.x500x.cursimple.app.util

import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.DailySchedule
import com.x500x.cursimple.core.kernel.model.TermSchedule
import java.io.InputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/**
 * Parses iCalendar (.ics / RFC 5545) streams into [ScheduleSharePayload].
 */
object ScheduleIcsImporter {

    private val COMPACT_DATETIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val COMPACT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd")

    data class RawVEvent(
        val summary: String,
        val location: String,
        val description: String,
        val dtStart: LocalDateTime,
        val dtEnd: LocalDateTime,
        val rrule: String?,
        val exdates: List<LocalDate>,
    )

    fun parse(stream: InputStream): ScheduleSharePayload {
        val content = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parse(content)
    }

    fun parse(content: String): ScheduleSharePayload {
        val unfoldedLines = unfoldLines(content)
        val rawEvents = parseEvents(unfoldedLines)
        if (rawEvents.isEmpty()) {
            return ScheduleSharePayload(
                schedule = TermSchedule(
                    termId = "term-ics-${System.currentTimeMillis()}",
                    updatedAt = LocalDate.now().toString(),
                    dailySchedules = (1..7).map { DailySchedule(it, emptyList()) },
                    externalImport = true,
                ),
            )
        }

        // Determine earliest event date as basis for term start (aligned to Monday)
        val allDates = rawEvents.map { it.dtStart.toLocalDate() }.sorted()
        val earliestDate = allDates.first()
        val termStartMonday = earliestDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        // Expand each event's occurrences
        data class Occurrence(
            val title: String,
            val location: String,
            val teacher: String,
            val dayOfWeek: Int,
            val startTime: LocalTime,
            val endTime: LocalTime,
            val week: Int,
            val isExam: Boolean,
        )

        val occurrences = mutableListOf<Occurrence>()

        for (event in rawEvents) {
            val title = event.summary.ifBlank { "未命名课程" }
            val teacher = extractTeacher(event.description)
            val isExam = title.contains("考试") || title.contains("期末") || title.contains("测验") ||
                event.description.contains("考试")

            val eventOccurrences = expandOccurrences(event, termStartMonday)
            for (date in eventOccurrences) {
                if (date in event.exdates) continue
                val week = ChronoUnit.WEEKS.between(termStartMonday, date).toInt() + 1
                if (week in 1..40) {
                    occurrences.add(
                        Occurrence(
                            title = title,
                            location = event.location,
                            teacher = teacher,
                            dayOfWeek = date.dayOfWeek.value,
                            startTime = event.dtStart.toLocalTime(),
                            endTime = event.dtEnd.toLocalTime(),
                            week = week,
                            isExam = isExam,
                        ),
                    )
                }
            }
        }

        // Group occurrences by course characteristics
        val grouped = occurrences.groupBy {
            Triple(
                it.title to it.location,
                it.teacher to it.dayOfWeek,
                it.startTime to it.endTime,
            )
        }

        val courses = grouped.values.map { occList ->
            val sample = occList.first()
            val weeks = occList.map { it.week }.distinct().sorted()
            val (startNode, endNode) = mapTimeToNodes(sample.startTime, sample.endTime)

            CourseItem(
                id = "ics-${UUID.randomUUID()}",
                title = sample.title,
                teacher = sample.teacher,
                location = sample.location,
                weeks = weeks,
                category = if (sample.isExam) CourseCategory.Exam else CourseCategory.Course,
                time = CourseTimeSlot(
                    dayOfWeek = sample.dayOfWeek,
                    startNode = startNode,
                    endNode = endNode,
                ),
                reminderStartTime = sample.startTime.toString().substring(0, 5),
                reminderEndTime = sample.endTime.toString().substring(0, 5),
            )
        }

        val dailySchedules = (1..7).map { day ->
            DailySchedule(
                dayOfWeek = day,
                courses = courses.filter { it.time.dayOfWeek == day },
            )
        }

        val schedule = TermSchedule(
            termId = "term-ics-${System.currentTimeMillis()}",
            updatedAt = LocalDate.now().toString(),
            dailySchedules = dailySchedules,
            externalImport = true,
        )

        return ScheduleSharePayload(
            termName = "ICS 导入学期",
            termStartDate = termStartMonday.toString(),
            schedule = schedule,
            manualCourses = emptyList(),
        )
    }

    private fun unfoldLines(content: String): List<String> {
        val lines = content.lines()
        val result = mutableListOf<String>()
        val sb = StringBuilder()

        for (line in lines) {
            val trimmedEnd = line.trimEnd('\r', '\n')
            if (trimmedEnd.startsWith(" ") || trimmedEnd.startsWith("\t")) {
                sb.append(trimmedEnd.substring(1))
            } else {
                if (sb.isNotEmpty()) {
                    result.add(sb.toString())
                    sb.setLength(0)
                }
                sb.append(trimmedEnd)
            }
        }
        if (sb.isNotEmpty()) {
            result.add(sb.toString())
        }
        return result
    }

    private fun parseEvents(lines: List<String>): List<RawVEvent> {
        val events = mutableListOf<RawVEvent>()
        var inEvent = false
        var summary = ""
        var location = ""
        var description = ""
        var dtStart: LocalDateTime? = null
        var dtEnd: LocalDateTime? = null
        var rrule: String? = null
        val exdates = mutableListOf<LocalDate>()

        for (line in lines) {
            val upper = line.uppercase()
            when {
                upper == "BEGIN:VEVENT" -> {
                    inEvent = true
                    summary = ""
                    location = ""
                    description = ""
                    dtStart = null
                    dtEnd = null
                    rrule = null
                    exdates.clear()
                }
                upper == "END:VEVENT" -> {
                    if (inEvent && dtStart != null) {
                        val validEnd = dtEnd ?: dtStart.plusMinutes(45)
                        events.add(
                            RawVEvent(
                                summary = unescape(summary),
                                location = unescape(location),
                                description = unescape(description),
                                dtStart = dtStart,
                                dtEnd = validEnd,
                                rrule = rrule,
                                exdates = exdates.toList(),
                            ),
                        )
                    }
                    inEvent = false
                }
                inEvent -> {
                    val colonIdx = line.indexOf(':')
                    if (colonIdx > 0) {
                        val propKey = line.substring(0, colonIdx)
                        val propValue = line.substring(colonIdx + 1)
                        val name = propKey.substringBefore(';').uppercase()

                        when (name) {
                            "SUMMARY" -> summary = propValue
                            "LOCATION" -> location = propValue
                            "DESCRIPTION" -> description = propValue
                            "DTSTART" -> dtStart = parseDateTime(propValue)
                            "DTEND" -> dtEnd = parseDateTime(propValue)
                            "RRULE" -> rrule = propValue
                            "EXDATE" -> {
                                propValue.split(',').forEach { part ->
                                    parseDateTime(part)?.toLocalDate()?.let { exdates.add(it) }
                                }
                            }
                        }
                    }
                }
            }
        }
        return events
    }

    private fun parseDateTime(raw: String): LocalDateTime? {
        val clean = raw.trim().trimEnd('Z')
        return runCatching {
            if (clean.contains('T')) {
                LocalDateTime.parse(clean, COMPACT_DATETIME)
            } else {
                LocalDate.parse(clean, COMPACT_DATE).atStartOfDay()
            }
        }.getOrNull()
    }

    private fun unescape(text: String): String {
        return text.replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\N", "\n")
            .trim()
    }

    private fun extractTeacher(description: String): String {
        if (description.isBlank()) return ""
        val patterns = listOf(
            Regex("(?:教师|老师|任课教师|授课教师|Instructor|Teacher)[：:]\\s*([^\\n,，;；]+)"),
            Regex("^(?:教师|老师)[：:]\\s*(.+)"),
        )
        for (pattern in patterns) {
            val match = pattern.find(description)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        return if (!description.contains("\n") && description.length <= 15) description.trim() else ""
    }

    private fun expandOccurrences(event: RawVEvent, termStartMonday: LocalDate): List<LocalDate> {
        val initialDate = event.dtStart.toLocalDate()
        val rrule = event.rrule
        if (rrule.isNullOrBlank()) {
            return listOf(initialDate)
        }

        val rules = rrule.split(';').associate {
            val parts = it.split('=')
            parts[0].uppercase() to parts.getOrElse(1) { "" }
        }

        val freq = rules["FREQ"]
        if (freq != "WEEKLY") {
            return listOf(initialDate)
        }

        val interval = rules["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val count = rules["COUNT"]?.toIntOrNull()
        val untilDate = rules["UNTIL"]?.let { parseDateTime(it)?.toLocalDate() }

        val dates = mutableListOf<LocalDate>()
        var curr = initialDate
        var iter = 0
        val maxIters = count ?: 30

        while (iter < maxIters) {
            if (untilDate != null && curr.isAfter(untilDate)) break
            dates.add(curr)
            curr = curr.plusWeeks(interval.toLong())
            iter++
        }

        return dates
    }

    /**
     * Map start and end time to timetable nodes 1..12 or compute proportional period.
     */
    fun mapTimeToNodes(startTime: LocalTime, endTime: LocalTime): Pair<Int, Int> {
        val startMins = startTime.hour * 60 + startTime.minute
        val endMins = endTime.hour * 60 + endTime.minute

        // Standard academic periods approximation (8:00 start)
        val baseStart = 8 * 60 // 08:00
        val periodDuration = 50 // 45m class + 5m/10m break

        val rawStartNode = ((startMins - baseStart) / periodDuration + 1).coerceIn(1, 12)
        val spanNodes = ((endMins - startMins + 10) / periodDuration).coerceAtLeast(1)
        val rawEndNode = (rawStartNode + spanNodes - 1).coerceIn(rawStartNode, 14)

        return rawStartNode to rawEndNode
    }
}
