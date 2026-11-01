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
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.Duration
import java.time.Instant
import com.x500x.cursimple.core.kernel.time.BeijingTime
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
        val dtStart: ZonedDateTime,
        val dtEnd: ZonedDateTime,
        val rrule: String?,
        val exdates: Set<Instant>,
    )

    fun parse(stream: InputStream): ScheduleSharePayload {
        val content = stream.use { String(ScheduleImportLimits.read(it), Charsets.UTF_8) }
        return parse(content)
    }

    fun parse(content: String): ScheduleSharePayload {
        ScheduleImportLimits.validateText(content)
        val unfoldedLines = unfoldLines(content)
        val rawEvents = parseEvents(unfoldedLines)
        require(rawEvents.isNotEmpty()) { "ICS 文件中没有可导入的定时课程" }

        // Determine earliest event date as basis for term start (aligned to Monday)
        val allDates = rawEvents.map { it.dtStart.withZoneSameInstant(BeijingTime.zone).toLocalDate() }.sorted()
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
            val wallDuration = Duration.between(event.dtStart.toLocalDateTime(),
                event.dtEnd.withZoneSameInstant(event.dtStart.zone).toLocalDateTime())
            require(!wallDuration.isNegative && !wallDuration.isZero) { "ICS 课程结束时间必须晚于开始时间" }
            for (sourceStart in eventOccurrences) {
                if (sourceStart.toInstant() in event.exdates) continue
                val start = sourceStart.withZoneSameInstant(BeijingTime.zone)
                val end = sourceStart.toLocalDateTime().plus(wallDuration).atZone(sourceStart.zone)
                    .withZoneSameInstant(BeijingTime.zone)
                require(start.toLocalDate() == end.toLocalDate()) { "暂不支持跨午夜的 ICS 课程" }
                val date = start.toLocalDate()
                val week = ChronoUnit.WEEKS.between(termStartMonday, date).toInt() + 1
                if (week in 1..40) {
                    require(occurrences.size < ScheduleImportLimits.MAX_OCCURRENCES) { "课程重复次数过多" }
                    occurrences.add(
                        Occurrence(
                            title = title,
                            location = event.location,
                            teacher = teacher,
                            dayOfWeek = date.dayOfWeek.value,
                            startTime = start.toLocalTime(),
                            endTime = end.toLocalTime(),
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
            require(sb.length <= 65_536 && result.size <= 100_000) { "ICS 行数或行长度超限" }
        }
        if (sb.isNotEmpty()) {
            result.add(sb.toString())
        }
        return result
    }

    private fun parseEvents(lines: List<String>): List<RawVEvent> {
        val events = mutableListOf<RawVEvent>()
        var inEvent = false
        var nestedDepth = 0
        var summary = ""
        var location = ""
        var description = ""
        var dtStart: ZonedDateTime? = null
        var dtEnd: ZonedDateTime? = null
        var duration: Duration? = null
        var rrule: String? = null
        val exdates = mutableSetOf<Instant>()

        require(lines.firstOrNull()?.uppercase() == "BEGIN:VCALENDAR" &&
            lines.lastOrNull()?.uppercase() == "END:VCALENDAR") { "不是有效的 ICS 日历文件" }

        for (line in lines) {
            val upper = line.uppercase()
            // Alarm descriptions and summaries belong to VALARM, not to the course.
            if (inEvent && upper.startsWith("BEGIN:") && upper != "BEGIN:VEVENT") {
                nestedDepth++
                continue
            }
            if (nestedDepth > 0) {
                if (upper.startsWith("END:")) nestedDepth--
                continue
            }
            when {
                upper == "BEGIN:VEVENT" -> {
                    require(!inEvent) { "ICS 事件嵌套错误" }
                    inEvent = true
                    summary = ""
                    location = ""
                    description = ""
                    dtStart = null
                    dtEnd = null
                    duration = null
                    rrule = null
                    exdates.clear()
                }
                upper == "END:VEVENT" -> {
                    require(inEvent && dtStart != null) { "ICS 事件缺少有效的开始时间" }
                    if (dtStart != null) {
                        require(dtEnd == null || duration == null) { "ICS 结束时间与时长不能同时指定" }
                        val validEnd = dtEnd ?: dtStart.plus(duration ?: Duration.ofMinutes(45))
                        require(validEnd.isAfter(dtStart)) { "ICS 结束时间必须晚于开始时间" }
                        require(events.size < ScheduleImportLimits.MAX_COURSES) { "课程数量过多" }
                        events.add(
                            RawVEvent(
                                summary = unescape(summary),
                                location = unescape(location),
                                description = unescape(description),
                                dtStart = dtStart,
                                dtEnd = validEnd,
                                rrule = rrule,
                                exdates = exdates.toSet(),
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
                            "DTSTART" -> dtStart = parseDateTime(propValue, propKey)
                            "DTEND" -> dtEnd = parseDateTime(propValue, propKey)
                            "DURATION" -> duration = Duration.parse(propValue)
                            "RRULE" -> rrule = propValue
                            "RDATE", "EXRULE", "RECURRENCE-ID" -> error("暂不支持此 ICS 例外规则：$name")
                            "EXDATE" -> {
                                propValue.split(',').forEach { part ->
                                    exdates.add(parseDateTime(part, propKey).toInstant())
                                }
                            }
                        }
                    }
                }
            }
        }
        require(!inEvent) { "ICS 事件未结束" }
        return events
    }

    private fun parseDateTime(raw: String, property: String = "", defaultZone: ZoneId = BeijingTime.zone): ZonedDateTime {
        val clean = raw.trim().trimEnd('Z')
        require(clean.contains('T')) { "暂不支持全天 ICS 事件，请导入含上课时间的课程" }
        val parsed = LocalDateTime.parse(clean, COMPACT_DATETIME)
        val tzid = property.split(';').firstOrNull { it.startsWith("TZID=", ignoreCase = true) }
            ?.substringAfter('=')?.trim('"')
        val sourceZone = when {
            raw.trim().endsWith('Z') -> ZoneOffset.UTC
            tzid != null -> ZoneId.of(tzid)
            else -> defaultZone
        }
        return parsed.atZone(sourceZone)
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

    private fun expandOccurrences(event: RawVEvent, termStartMonday: LocalDate): List<ZonedDateTime> {
        val initialDate = event.dtStart.toLocalDate()
        val rrule = event.rrule
        if (rrule.isNullOrBlank()) {
            return listOf(event.dtStart)
        }

        val rules = rrule.split(';').associate {
            val parts = it.split('=')
            require(parts.size == 2 && parts.all { part -> part.isNotBlank() }) { "无效的 ICS 重复规则" }
            parts[0].uppercase() to parts[1].uppercase()
        }

        val freq = rules["FREQ"]
        require(freq == "WEEKLY") { "暂不支持此 ICS 重复频率：$freq" }
        require(rules.keys.all { it in setOf("FREQ", "INTERVAL", "COUNT", "UNTIL", "BYDAY", "WKST") }) {
            "暂不支持此 ICS 重复规则"
        }

        val interval = rules["INTERVAL"]?.let { it.toLong().also { n -> require(n > 0) { "无效的 ICS INTERVAL" } } } ?: 1L
        val count = rules["COUNT"]?.let { it.toLong().also { n -> require(n > 0) { "无效的 ICS COUNT" } } }
        require(count == null || rules["UNTIL"] == null) { "ICS COUNT 与 UNTIL 不能同时指定" }
        val until = rules["UNTIL"]?.let { parseDateTime(it, defaultZone = event.dtStart.zone).toInstant() }

        val weekdays = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
        val byDays = rules["BYDAY"]?.split(',')?.map {
            require(it in weekdays) { "不支持的 ICS BYDAY：$it" }
            weekdays.indexOf(it) + 1
        }?.toSet() ?: setOf(initialDate.dayOfWeek.value)
        val weekStart = rules["WKST"] ?: "MO"
        require(weekStart in weekdays) { "无效的 ICS WKST" }
        val firstWeek = initialDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.of(weekdays.indexOf(weekStart) + 1)))
        val dates = mutableListOf<ZonedDateTime>()
        var curr = initialDate
        val termEnd = termStartMonday.plusWeeks(40)
        while (dates.size.toLong() < (count ?: ScheduleImportLimits.MAX_OCCURRENCES.toLong()) && curr.isBefore(termEnd.plusDays(1))) {
            val candidate = curr.atTime(event.dtStart.toLocalTime()).atZone(event.dtStart.zone)
            if (until != null && candidate.toInstant().isAfter(until)) break
            val weekIndex = ChronoUnit.WEEKS.between(firstWeek, curr)
            if (weekIndex % interval == 0L && curr.dayOfWeek.value in byDays) dates.add(candidate)
            curr = curr.plusDays(1)
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
