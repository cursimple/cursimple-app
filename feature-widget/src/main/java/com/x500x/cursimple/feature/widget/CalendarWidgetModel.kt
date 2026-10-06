package com.x500x.cursimple.feature.widget

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

internal enum class CalendarWidgetMode { Week, Month }

internal data class CalendarCourseBlock(
    val id: String,
    val title: String,
    val location: String,
    val startNode: Int,
    val endNode: Int,
    val isExam: Boolean,
    val colorSeed: String,
    val inactive: Boolean,
    val lane: Int = 0,
    val laneCount: Int = 1,
)

internal data class CalendarDay(
    val date: LocalDate,
    val isToday: Boolean,
    val onHoliday: Boolean,
    val makeUpWorkday: Boolean,
    val courses: List<CourseItem>,
    val eventCount: Int,
) {
    val hasExam: Boolean get() = !onHoliday && courses.any { it.category == CourseCategory.Exam }
    val classCount: Int get() = if (onHoliday) 0 else courses.count { it.category != CourseCategory.Exam }
}

/** One timing-profile row with its current localized or user-defined label. */
internal data class CalendarRow(
    val startNode: Int,
    val endNode: Int,
    val label: String,
    val startTime: String,
)

internal data class CalendarWeekData(
    val days: List<CalendarDay>,
    val blocks: List<List<CalendarCourseBlock>>,
    val rows: List<CalendarRow>,
) {
    /**
     * Resolve span rows around timing gaps by the nearest containing start and end boundaries.
     */
    fun rowSpanOf(block: CalendarCourseBlock): IntRange? {
        val top = rows.indexOfFirst { it.endNode >= block.startNode }.takeIf { it >= 0 } ?: return null
        val bottom = rows.indexOfLast { it.startNode <= block.endNode }.takeIf { it >= 0 } ?: return null
        return if (bottom >= top) top..bottom else null
    }
}

internal data class CalendarMonthData(
    val month: YearMonth,
    val days: List<CalendarDay>,
) {
    val rows: Int get() = days.size / 7
    fun inMonth(day: CalendarDay): Boolean = YearMonth.from(day.date) == month
}

internal fun weekStartOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

internal fun monthGridDates(month: YearMonth): List<LocalDate> {
    val first = weekStartOf(month.atDay(1))
    val last = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    return generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
}

/** Hide empty weekends only when no classes, events or workday overrides require them. */
internal fun visibleWeekDays(days: List<CalendarDay>, weekendVisible: Boolean): List<CalendarDay> {
    if (weekendVisible) return days
    val weekendBusy = days.drop(5).any { it.courses.isNotEmpty() || it.eventCount > 0 || it.makeUpWorkday }
    return if (weekendBusy) days else days.take(5)
}

/**
 * Use sorted timing rows and localized labels; pad uncovered course periods and limit empty
 * default rows.
 */
internal fun calendarRows(
    profileSlots: List<ClassSlotTime>,
    days: List<CalendarDay>,
    labelOf: (slot: ClassSlotTime, index: Int) -> String,
    fallbackLabel: (node: Int) -> String,
    maxDefaultRows: Int = 10,
): List<CalendarRow> {
    val lastCourseNode = days.flatMap { it.courses }.maxOfOrNull { it.time.endNode } ?: 0
    val sorted = profileSlots.sortedWith(
        compareBy({ looseClock(it.startTime) ?: LocalTime.MAX }, { it.startNode }, { it.endNode }),
    )
    val base = sorted.mapIndexed { index, slot ->
        CalendarRow(slot.startNode, slot.endNode, labelOf(slot, index + 1), slot.startTime.trim())
    }
    val coveredMax = sorted.maxOfOrNull { it.endNode } ?: 0
    val extraUntil = if (base.isEmpty()) maxOf(lastCourseNode, maxDefaultRows) else lastCourseNode
    val extras = (coveredMax + 1..extraUntil).map { node -> CalendarRow(node, node, fallbackLabel(node), "") }
    val all = base + extras
    val lastNeeded = all.indexOfLast { it.startNode <= lastCourseNode }
    val keep = maxOf(lastNeeded + 1, minOf(all.size, maxDefaultRows), minOf(all.size, MIN_CALENDAR_ROWS))
    return all.take(keep)
}

private const val MIN_CALENDAR_ROWS = 4

internal fun calendarBlocksOf(day: CalendarDay): List<CalendarCourseBlock> {
    val sorted = day.courses
        .filter { it.time.startNode >= 1 && it.time.endNode >= it.time.startNode }
        .sortedWith(compareBy({ it.time.startNode }, { -it.time.endNode }, { it.title }))
    val laneEnds = mutableListOf<Int>()
    val assigned = sorted.map { course ->
        val lane = laneEnds.indexOfFirst { end -> end < course.time.startNode }.takeIf { it >= 0 } ?: laneEnds.size
        if (lane == laneEnds.size) laneEnds += course.time.endNode else laneEnds[lane] = course.time.endNode
        course to lane
    }
    // Split lanes only within overlapping clusters, preserving full width elsewhere.
    return assigned.map { (course, lane) ->
        val overlapping = assigned.filter { (other, _) ->
            other.time.startNode <= course.time.endNode && course.time.startNode <= other.time.endNode
        }
        CalendarCourseBlock(
            id = course.id,
            title = course.title,
            location = course.location,
            startNode = course.time.startNode,
            endNode = course.time.endNode,
            isExam = course.category == CourseCategory.Exam,
            colorSeed = course.title,
            inactive = day.onHoliday,
            lane = lane,
            laneCount = (overlapping.maxOf { it.second } + 1).coerceAtLeast(lane + 1),
        )
    }
}

/** Show only markers present in the displayed week. */
internal data class CalendarWeekLegend(
    val events: Boolean,
    val exam: Boolean,
    val holiday: Boolean,
    val makeUp: Boolean,
) {
    val isEmpty: Boolean get() = !events && !exam && !holiday && !makeUp
}

internal fun CalendarWeekData.legend(): CalendarWeekLegend = CalendarWeekLegend(
    events = days.any { it.eventCount > 0 },
    exam = blocks.any { day -> day.any { it.isExam && !it.inactive } },
    holiday = days.any { it.onHoliday },
    makeUp = days.any { it.makeUpWorkday },
)

internal fun looseClock(raw: String): LocalTime? {
    val parts = raw.trim().split(':')
    if (parts.size != 2) return null
    val hour = parts[0].trim().toIntOrNull() ?: return null
    val minute = parts[1].trim().toIntOrNull() ?: return null
    return if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null
}
