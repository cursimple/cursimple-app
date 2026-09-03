package com.x500x.cursimple.app.util

import androidx.annotation.StringRes
import com.x500x.cursimple.R
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.zone.ZoneOffsetTransition
import kotlin.math.abs
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings

/** 一门无法导出的课程及原因，供界面告知用户而不是静默丢弃。[reason] 为文案资源 id。 */
data class IcsSkippedCourse(
    val title: String,
    val dayOfWeek: Int,
    val startNode: Int,
    val endNode: Int,
    @StringRes val reason: Int,
)

/**
 * ICS 生成结果。
 * [content] 始终是一份合法的 iCalendar 文本；[failureReason] 非空（文案资源 id）表示因缺少必要配置整份日历没有任何事件。
 */
data class IcsExportResult(
    val content: String,
    val eventCount: Int,
    val occurrenceCount: Int,
    val skipped: List<IcsSkippedCourse>,
    @StringRes val failureReason: Int?,
)

/**
 * 把当前学期课表渲染成 iCalendar（RFC 5545）文本的纯函数集合。
 * 不接触 Android Context 与文件 IO，便于单元测试。
 */
object ScheduleIcsBuilder {

    private const val PRODID = "-//x500x//CurSimple//CN"
    private const val UID_DOMAIN = "cursimple.x500x.com"
    private const val LINE_OCTET_LIMIT = 75

    private val BASIC_LOCAL: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val BASIC_UTC: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    private data class CourseGroup(
        val course: CourseItem,
        val slotLabel: String?,
        val occurrences: List<CourseOccurrence>,
    )

    fun build(
        termName: String?,
        termStartDate: LocalDate?,
        schedule: TermSchedule?,
        manualCourses: List<CourseItem>,
        timingProfile: TermTimingProfile?,
        overrides: List<TemporaryScheduleOverride>,
        zone: ZoneId,
        generatedAt: Instant,
        holidayCalendar: HolidayCalendarSettings = HolidayCalendarSettings.NONE,
        defaultWeekCount: Int = 20,
    ): IcsExportResult {
        val plan = planScheduleOccurrences(
            termStartDate = termStartDate,
            schedule = schedule,
            manualCourses = manualCourses,
            timingProfile = timingProfile,
            overrides = overrides,
            holidayCalendar = holidayCalendar,
            defaultWeekCount = defaultWeekCount,
        )
        if (plan.failureReason != null) {
            return IcsExportResult(
                content = emptyCalendar(termName, generatedAt),
                eventCount = 0,
                occurrenceCount = 0,
                skipped = plan.skipped,
                failureReason = plan.failureReason,
            )
        }

        val groups = plan.courses.map { CourseGroup(it.course, it.slotLabel, it.occurrences) }
        val events = mutableListOf<String>()
        var occurrenceCount = 0
        for (group in groups) {
            val (blocks, count) = group.toEvents(zone, generatedAt)
            events.addAll(blocks)
            occurrenceCount += count
        }

        val allDates = groups.flatMap { it.occurrences }.map { it.date }
        val termStartMonday = requireNotNull(termStartDate)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val tzWindowStart = allDates.minOrNull() ?: termStartMonday
        val tzWindowEnd = allDates.maxOrNull() ?: termStartMonday

        val body = buildString {
            append(calendarHeaderLines(termName, zone).joinToString("\r\n") { fold(it) })
            append("\r\n")
            if (events.isNotEmpty()) {
                append(buildVTimeZone(zone, tzWindowStart, tzWindowEnd).joinToString("\r\n") { fold(it) })
                append("\r\n")
            }
            for (event in events) {
                append(event)
                append("\r\n")
            }
            append(fold("END:VCALENDAR"))
            append("\r\n")
        }

        return IcsExportResult(
            content = body,
            eventCount = events.size,
            occurrenceCount = occurrenceCount,
            skipped = plan.skipped,
            failureReason = null,
        )
    }

    private fun CourseGroup.toEvents(zone: ZoneId, generatedAt: Instant): Pair<List<String>, Int> {
        val blocks = mutableListOf<String>()
        var count = 0
        val baseUid = uidBase(course)

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
            val exdates: List<LocalDateTime>
            when {
                grid.size <= 1 -> {
                    rrule = null
                    exdates = emptyList()
                }
                missing.isEmpty() -> {
                    rrule = "RRULE:FREQ=WEEKLY;COUNT=${grid.size}"
                    exdates = emptyList()
                }
                else -> {
                    val untilUtc = BASIC_UTC.format(last.start.atZone(zone).withZoneSameInstant(ZoneOffset.UTC))
                    rrule = "RRULE:FREQ=WEEKLY;UNTIL=$untilUtc"
                    exdates = missing.map { it.atTime(first.start.toLocalTime()) }
                }
            }
            blocks.add(buildVEvent(zone, baseUid, first.start, first.end, generatedAt, rrule, exdates))
            count += present.size
        }

        for (occ in occurrences.filter { it.displaced }.sortedBy { it.date }) {
            val uid = "$baseUid-mk-${BASIC_LOCAL.format(occ.start).substringBefore('T')}"
            blocks.add(buildVEvent(zone, uid, occ.start, occ.end, generatedAt, rrule = null, exdates = emptyList()))
            count += 1
        }
        return blocks to count
    }

    private fun CourseGroup.buildVEvent(
        zone: ZoneId,
        uid: String,
        start: LocalDateTime,
        end: LocalDateTime,
        generatedAt: Instant,
        rrule: String?,
        exdates: List<LocalDateTime>,
    ): String {
        val tzid = zone.id
        val lines = mutableListOf<String>()
        lines.add("BEGIN:VEVENT")
        lines.add("UID:$uid@$UID_DOMAIN")
        lines.add("DTSTAMP:${BASIC_UTC.format(generatedAt.atZone(ZoneOffset.UTC))}")
        lines.add("DTSTART;TZID=$tzid:${BASIC_LOCAL.format(start)}")
        lines.add("DTEND;TZID=$tzid:${BASIC_LOCAL.format(end)}")
        if (rrule != null) lines.add(rrule)
        if (exdates.isNotEmpty()) {
            val joined = exdates.joinToString(",") { BASIC_LOCAL.format(it) }
            lines.add("EXDATE;TZID=$tzid:$joined")
        }
        lines.add("SUMMARY:${escapeText(course.title)}")
        if (course.location.isNotBlank()) lines.add("LOCATION:${escapeText(course.location)}")
        val description = buildDescription()
        if (description.isNotBlank()) lines.add("DESCRIPTION:${escapeText(description)}")
        lines.add("END:VEVENT")
        return lines.joinToString("\r\n") { fold(it) }
    }

    private fun CourseGroup.buildDescription(): String {
        val parts = mutableListOf<String>()
        if (course.category == CourseCategory.Exam) parts.add("类型：考试")
        if (course.teacher.isNotBlank()) parts.add("教师：${course.teacher}")
        parts.add("节次：${nodeLabel(course.time.startNode, course.time.endNode)}")
        slotLabel?.let { parts.add("时段：$it") }
        return parts.joinToString("\n")
    }

    private fun nodeLabel(startNode: Int, endNode: Int): String =
        if (startNode == endNode) "第${startNode}节" else "第${startNode}-${endNode}节"

    private fun uidBase(course: CourseItem): String =
        "${sanitizeUid(course.id)}-w${course.time.dayOfWeek}-n${course.time.startNode}-${course.time.endNode}"

    private fun sanitizeUid(raw: String): String =
        raw.map { ch -> if (ch.isLetterOrDigit() || ch == '-' || ch == '_') ch else '_' }.joinToString("")

    private fun calendarHeaderLines(termName: String?, zone: ZoneId): List<String> {
        val lines = mutableListOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:$PRODID",
            "CALSCALE:GREGORIAN",
            "METHOD:PUBLISH",
            "X-WR-TIMEZONE:${zone.id}",
        )
        val calName = termName?.takeIf { it.isNotBlank() } ?: "课表"
        lines.add("X-WR-CALNAME:${escapeText(calName)}")
        return lines
    }

    private fun emptyCalendar(termName: String?, generatedAt: Instant): String {
        val lines = mutableListOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:$PRODID",
            "CALSCALE:GREGORIAN",
            "METHOD:PUBLISH",
        )
        val calName = termName?.takeIf { it.isNotBlank() } ?: "课表"
        lines.add("X-WR-CALNAME:${escapeText(calName)}")
        lines.add("END:VCALENDAR")
        return lines.joinToString("\r\n") { fold(it) } + "\r\n"
    }

    /**
     * 依据设备时区的真实换算规则生成 VTIMEZONE，覆盖 [windowStart]~[windowEnd]。
     * 有夏令时的时区会按实际转换点写出 STANDARD/DAYLIGHT，无夏令时则只有一个 STANDARD。
     */
    private fun buildVTimeZone(zone: ZoneId, windowStart: LocalDate, windowEnd: LocalDate): List<String> {
        val rules = zone.rules
        val startInstant = windowStart.atStartOfDay(zone).toInstant().minusSeconds(1)
        val endInstant = windowEnd.plusDays(1).atStartOfDay(zone).toInstant()
        val initialOffset = rules.getOffset(startInstant)

        val transitions = mutableListOf<ZoneOffsetTransition>()
        var next = rules.nextTransition(startInstant)
        while (next != null && !next.instant.isAfter(endInstant)) {
            transitions.add(next)
            next = rules.nextTransition(next.instant)
        }

        val lines = mutableListOf("BEGIN:VTIMEZONE", "TZID:${zone.id}")
        val initialDaylight = rules.getDaylightSavings(startInstant).toMillis() != 0L
        lines.addAll(
            observanceLines(
                daylight = initialDaylight,
                dtStart = LocalDateTime.of(1970, 1, 1, 0, 0, 0),
                offsetFrom = initialOffset,
                offsetTo = initialOffset,
            ),
        )
        for (transition in transitions) {
            val instantAfter = transition.instant.plusSeconds(1)
            val daylight = rules.getDaylightSavings(instantAfter).toMillis() != 0L
            lines.addAll(
                observanceLines(
                    daylight = daylight,
                    dtStart = transition.dateTimeBefore,
                    offsetFrom = transition.offsetBefore,
                    offsetTo = transition.offsetAfter,
                ),
            )
        }
        lines.add("END:VTIMEZONE")
        return lines
    }

    private fun observanceLines(
        daylight: Boolean,
        dtStart: LocalDateTime,
        offsetFrom: ZoneOffset,
        offsetTo: ZoneOffset,
    ): List<String> {
        val tag = if (daylight) "DAYLIGHT" else "STANDARD"
        return listOf(
            "BEGIN:$tag",
            "DTSTART:${BASIC_LOCAL.format(dtStart)}",
            "TZOFFSETFROM:${formatOffset(offsetFrom)}",
            "TZOFFSETTO:${formatOffset(offsetTo)}",
            "TZNAME:${offsetName(offsetTo)}",
            "END:$tag",
        )
    }

    private fun formatOffset(offset: ZoneOffset): String {
        val total = offset.totalSeconds
        val sign = if (total < 0) "-" else "+"
        val absSeconds = abs(total)
        val hours = absSeconds / 3600
        val minutes = (absSeconds % 3600) / 60
        val seconds = absSeconds % 60
        return if (seconds == 0) {
            "%s%02d%02d".format(sign, hours, minutes)
        } else {
            "%s%02d%02d%02d".format(sign, hours, minutes, seconds)
        }
    }

    private fun offsetName(offset: ZoneOffset): String =
        if (offset.totalSeconds == 0) "GMT" else "GMT${offset.id}"

    /** RFC 5545 文本值转义：反斜杠、分号、逗号、换行。 */
    internal fun escapeText(value: String): String {
        val builder = StringBuilder(value.length + 8)
        var i = 0
        while (i < value.length) {
            when (val ch = value[i]) {
                '\\' -> builder.append("\\\\")
                ';' -> builder.append("\\;")
                ',' -> builder.append("\\,")
                '\n' -> builder.append("\\n")
                '\r' -> {
                    builder.append("\\n")
                    if (i + 1 < value.length && value[i + 1] == '\n') i++
                }
                else -> builder.append(ch)
            }
            i++
        }
        return builder.toString()
    }

    /**
     * 按 75 字节折行，续行以单个空格开头；以 UTF-8 字节计量，绝不从多字节字符中间切断。
     */
    internal fun fold(line: String): String {
        val builder = StringBuilder(line.length + 8)
        var lineOctets = 0
        var index = 0
        while (index < line.length) {
            val codePoint = line.codePointAt(index)
            val charCount = Character.charCount(codePoint)
            val piece = line.substring(index, index + charCount)
            val octets = piece.toByteArray(Charsets.UTF_8).size
            if (lineOctets + octets > LINE_OCTET_LIMIT) {
                builder.append("\r\n ")
                lineOctets = 1
            }
            builder.append(piece)
            lineOctets += octets
            index += charCount
        }
        return builder.toString()
    }
}
