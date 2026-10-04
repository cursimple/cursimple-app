package com.x500x.cursimple.feature.widget

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** 课程日历小组件的两种视图，标题栏上的按钮来回切换 */
internal enum class CalendarWidgetMode { Week, Month }

/** 周视图里的一块课程，按节次占格 */
internal data class CalendarCourseBlock(
    val id: String,
    val title: String,
    val location: String,
    val startNode: Int,
    val endNode: Int,
    val isExam: Boolean,
    /** 与课表网格同一个取色：按课名在调色板里挑 */
    val colorSeed: String,
    /** 放假当天照常画出，按不可用态显示 */
    val inactive: Boolean,
    /** 同一时段有几门课并排时，它在第几列、一共几列 */
    val lane: Int = 0,
    val laneCount: Int = 1,
)

/** 一天：周视图里是一列，月视图里是一格 */
internal data class CalendarDay(
    val date: LocalDate,
    val isToday: Boolean,
    /** 放假（且没有调课推翻） */
    val onHoliday: Boolean,
    /** 调休补班日 */
    val makeUpWorkday: Boolean,
    val courses: List<CourseItem>,
    val eventCount: Int,
) {
    val hasExam: Boolean get() = !onHoliday && courses.any { it.category == CourseCategory.Exam }
    val classCount: Int get() = if (onHoliday) 0 else courses.count { it.category != CourseCategory.Exam }
}

/** 周视图左侧的一行：作息表里的一个时段，名字跟 App 课表左栏一致（用户改过名就显示改后的） */
internal data class CalendarRow(
    val startNode: Int,
    val endNode: Int,
    val label: String,
    /** 开始时间，如「08:00」；作息表以外补出来的行为空 */
    val startTime: String,
)

internal data class CalendarWeekData(
    val days: List<CalendarDay>,
    val blocks: List<List<CalendarCourseBlock>>,
    val rows: List<CalendarRow>,
) {
    /**
     * 课程块占哪几行：从包含开始节的那一行到包含结束节的那一行。
     * 作息表时段之间有空档时，开始节落到它之后的第一行、结束节落到它之前的最后一行。
     */
    fun rowSpanOf(block: CalendarCourseBlock): IntRange? {
        val top = rows.indexOfFirst { it.endNode >= block.startNode }.takeIf { it >= 0 } ?: return null
        val bottom = rows.indexOfLast { it.startNode <= block.endNode }.takeIf { it >= 0 } ?: return null
        return if (bottom >= top) top..bottom else null
    }
}

internal data class CalendarMonthData(
    val month: YearMonth,
    /** 从包含 1 号那一周的周一起，整周排满；5 或 6 行 */
    val days: List<CalendarDay>,
) {
    val rows: Int get() = days.size / 7
    fun inMonth(day: CalendarDay): Boolean = YearMonth.from(day.date) == month
}

internal fun weekStartOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** [anchor] 所在月份的格子日期：整周对齐，周一开头 */
internal fun monthGridDates(month: YearMonth): List<LocalDate> {
    val first = weekStartOf(month.atDay(1))
    val last = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    return generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
}

/**
 * 周视图要画几列：周末没课且设置里隐藏了周末时只画周一到周五。
 * 有周末课（或周末有事务、调休补班）时照画，免得课被藏掉。
 */
internal fun visibleWeekDays(days: List<CalendarDay>, weekendVisible: Boolean): List<CalendarDay> {
    if (weekendVisible) return days
    val weekendBusy = days.drop(5).any { it.courses.isNotEmpty() || it.eventCount > 0 || it.makeUpWorkday }
    return if (weekendBusy) days else days.take(5)
}

/**
 * 周视图左侧的行，与 App 课表左栏同一个来源：作息表里的时段按开始时间排好，名字用作息表里的
 * （[labelOf] 给出，带当前语言）；课排到作息表以外的节次时，逐节补行，课不会被藏掉。
 * 没有作息表时按节号逐行。
 *
 * 行数：至少画到这周最后一门课所在的那一行；作息表更长时多画几行，但不超过 [maxDefaultRows]，
 * 免得只有上午课的一周被十几行空格压扁。
 */
internal fun calendarRows(
    profileSlots: List<ClassSlotTime>,
    days: List<CalendarDay>,
    labelOf: (slot: ClassSlotTime, index: Int) -> String,
    fallbackLabel: (node: Int) -> String,
    maxDefaultRows: Int = 10,
): List<CalendarRow> {
    val lastCourseNode = days.flatMap { it.courses }.maxOfOrNull { it.time.endNode } ?: 0
    // 与 App 课表左栏同样按开始时间排：直接比字符串会把「10:00」排到「8:00」前面
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

/** 一天里的课程块：节次重叠的并排分列，互不遮挡 */
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
    // 只有真正互相重叠的那一簇才分列，同一天别的时段照样占满整列
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

/** 周视图标题栏上的图例要列哪几项：只列这周真的出现的记号 */
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

/** 「8:00」「 08:00 」都认：导入的作息表不一定补零、前后可能带空格 */
internal fun looseClock(raw: String): LocalTime? {
    val parts = raw.trim().split(':')
    if (parts.size != 2) return null
    val hour = parts[0].trim().toIntOrNull() ?: return null
    val minute = parts[1].trim().toIntOrNull() ?: return null
    return if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null
}
