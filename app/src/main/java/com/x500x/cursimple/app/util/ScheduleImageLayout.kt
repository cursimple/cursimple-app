package com.x500x.cursimple.app.util

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.ScheduleDayResolution
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.coursesOfDay
import com.x500x.cursimple.core.kernel.model.filterTemporaryCancelledCourses
import com.x500x.cursimple.core.kernel.model.isTermWeekNumberActive
import com.x500x.cursimple.core.kernel.model.resolveScheduleDay
import com.x500x.cursimple.core.kernel.model.resolveTermWeekNumber
import com.x500x.cursimple.core.kernel.model.visibleScheduleCourses
import com.x500x.cursimple.core.kernel.model.weekdayLabel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil
import kotlin.math.floor

/** 测量文本在给定字号下的绘制宽度，由绘制层用真实字体实现，单测里用等宽近似实现。 */
fun interface ScheduleImageTextMeasurer {
    fun measure(text: String, fontSize: Float, bold: Boolean): Float
}

/** 课表图片各区块的像素尺寸与字号，全部以最终位图像素为单位。 */
data class ScheduleImageMetrics(
    val outerPadding: Float = 44f,
    val headerHeight: Float = 176f,
    val dayHeaderHeight: Float = 118f,
    val nodeColumnWidth: Float = 136f,
    val dayColumnWidth: Float = 260f,
    val rowHeight: Float = 180f,
    val blockGap: Float = 6f,
    val blockPadding: Float = 12f,
    val headerTitleFontSize: Float = 46f,
    val headerSubtitleFontSize: Float = 28f,
    val dayNameFontSize: Float = 32f,
    val dayDateFontSize: Float = 25f,
    val dayNoteFontSize: Float = 22f,
    val nodeIndexFontSize: Float = 30f,
    val nodeTimeFontSize: Float = 22f,
    val titleFontSize: Float = 28f,
    val detailFontSize: Float = 23f,
    val titleLineHeight: Float = 36f,
    val detailLineHeight: Float = 30f,
    val holidayFontSize: Float = 28f,
    val holidayLineHeight: Float = 40f,
    val footnoteFontSize: Float = 23f,
    val footnoteLineHeight: Float = 32f,
    val footnoteGap: Float = 26f,
    val maxTitleLines: Int = 3,
    val maxLanesPerCell: Int = 3,
    val footnoteLaneThreshold: Int = 3,
)

/** 位图坐标系里的一个矩形，左上为原点。 */
data class ScheduleImageRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun inset(amount: Float): ScheduleImageRect =
        ScheduleImageRect(left + amount, top + amount, right - amount, bottom - amount)
}

enum class ScheduleImageTextRole { Title, Detail }

data class ScheduleImageTextLine(
    val text: String,
    val role: ScheduleImageTextRole,
)

/** 一门课在图上的位置与已经排好版的文本。 */
data class ScheduleImageBlock(
    val dayOfWeek: Int,
    val rect: ScheduleImageRect,
    val contentRect: ScheduleImageRect,
    val rowStart: Int,
    val rowSpan: Int,
    val startNode: Int,
    val endNode: Int,
    val laneIndex: Int,
    val laneCount: Int,
    val title: String,
    val lines: List<ScheduleImageTextLine>,
    val titleFontSize: Float,
    val detailFontSize: Float,
    val titleLineHeight: Float,
    val detailLineHeight: Float,
    val colorIndex: Int,
    val isExam: Boolean,
    val isOverflow: Boolean,
)

data class ScheduleImageDayHeader(
    val dayOfWeek: Int,
    val rect: ScheduleImageRect,
    val weekdayLabel: String,
    val dateLabel: String,
    val noteLabel: String?,
    val isWeekend: Boolean,
)

data class ScheduleImageRow(
    val rect: ScheduleImageRect,
    val slotIndex: Int,
    val nodeLabel: String,
    val startTimeLabel: String,
    val endTimeLabel: String,
)

data class ScheduleImageHoliday(
    val dayOfWeek: Int,
    val rect: ScheduleImageRect,
    val contentRect: ScheduleImageRect,
    val lines: List<String>,
    val fontSize: Float,
    val lineHeight: Float,
)

/** 一整张课表图片的排版结果，绘制层只按坐标画，不再做任何计算。 */
data class ScheduleImageLayoutResult(
    val width: Int,
    val height: Int,
    val metrics: ScheduleImageMetrics,
    val title: String,
    val subtitle: String,
    val weekNumber: Int,
    val gridRect: ScheduleImageRect,
    val bodyRect: ScheduleImageRect,
    val nodeColumnRect: ScheduleImageRect,
    val dayHeaders: List<ScheduleImageDayHeader>,
    val rows: List<ScheduleImageRow>,
    val holidays: List<ScheduleImageHoliday>,
    val blocks: List<ScheduleImageBlock>,
    val footnotes: List<String>,
    val footnoteTop: Float,
    val courseCount: Int,
    val failureReason: String?,
)

/**
 * 把某一教学周的课表换算成绘制坐标的纯函数集合。
 * 不引用任何 Android 类型，文本宽度经 [ScheduleImageTextMeasurer] 外部注入。
 */
object ScheduleImageLayout {

    /** 课程底色的可选数量，绘制层的调色板长度必须与之一致。 */
    const val PALETTE_SIZE = 8

    private const val DEFAULT_WEEK_COUNT = 20
    private const val MIN_DAY_COLUMNS = 5

    private data class PlacedCourse(
        val course: CourseItem,
        val rowStart: Int,
        val rowEnd: Int,
    )

    /** [date] 落在第几教学周，开学之前按第 1 周处理。 */
    fun currentWeekNumber(termStartDate: LocalDate, date: LocalDate): Int =
        resolveTermWeekNumber(termStartDate, date).coerceAtLeast(1)

    /** 课表里出现过的最大周次，全部课程都不限周次时用默认周数。 */
    fun maxWeekNumber(schedule: TermSchedule?, manualCourses: List<CourseItem>): Int {
        val all = (1..7).flatMap { schedule?.coursesOfDay(it).orEmpty() } + manualCourses
        val declared = all.mapNotNull { it.weeks.maxOrNull() }.maxOrNull()
        return (declared ?: DEFAULT_WEEK_COUNT).coerceAtLeast(1)
    }

    /** 第 [weekNumber] 教学周的周一。 */
    fun weekStartDate(termStartDate: LocalDate, weekNumber: Int): LocalDate =
        termStartDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .plusWeeks((weekNumber - 1).toLong())

    fun compute(
        termName: String?,
        termStartDate: LocalDate,
        weekNumber: Int,
        schedule: TermSchedule?,
        manualCourses: List<CourseItem>,
        timingProfile: TermTimingProfile,
        overrides: List<TemporaryScheduleOverride>,
        holidayCalendar: HolidayCalendarSettings,
        measurer: ScheduleImageTextMeasurer,
        metrics: ScheduleImageMetrics = ScheduleImageMetrics(),
    ): ScheduleImageLayoutResult {
        val slots = timingProfile.slotTimes
            .filter { it.endNode >= it.startNode }
            .sortedWith(compareBy({ it.startNode }, { it.endNode }))
        val safeWeek = weekNumber.coerceAtLeast(1)
        if (slots.isEmpty()) {
            return emptyResult(metrics, safeWeek, termName, "未设置节次上课时间")
        }

        val weekMonday = weekStartDate(termStartDate, safeWeek)
        val dayDates = (1..7).associateWith { weekMonday.plusDays((it - 1).toLong()) }
        val resolutions = dayDates.mapValues { (_, date) ->
            resolveScheduleDay(date, overrides, holidayCalendar)
        }

        val importedByDay = (1..7).associateWith { day ->
            schedule?.coursesOfDay(day).orEmpty().visibleScheduleCourses()
        }
        val visibleManual = manualCourses.visibleScheduleCourses()

        val placedByDay = (1..7).associateWith { day ->
            collectDay(
                date = dayDates.getValue(day),
                resolution = resolutions.getValue(day),
                termStartDate = termStartDate,
                importedByDay = importedByDay,
                visibleManual = visibleManual,
                overrides = overrides,
                slots = slots,
            )
        }

        val lastCourseDay = (7 downTo 1).firstOrNull { placedByDay.getValue(it).isNotEmpty() } ?: 0
        val dayCount = maxOf(MIN_DAY_COLUMNS, lastCourseDay)

        val shown = (1..dayCount).flatMap { placedByDay.getValue(it) }
        val firstRow = shown.minOfOrNull { it.rowStart } ?: 0
        val lastRow = shown.maxOfOrNull { it.rowEnd } ?: slots.lastIndex
        val rowCount = lastRow - firstRow + 1

        val gridLeft = metrics.outerPadding
        val gridTop = metrics.outerPadding + metrics.headerHeight
        val bodyTop = gridTop + metrics.dayHeaderHeight
        val bodyBottom = bodyTop + rowCount * metrics.rowHeight
        val gridRight = gridLeft + metrics.nodeColumnWidth + dayCount * metrics.dayColumnWidth

        fun columnLeft(day: Int): Float =
            gridLeft + metrics.nodeColumnWidth + (day - 1) * metrics.dayColumnWidth

        val dayHeaders = (1..dayCount).map { day ->
            val date = dayDates.getValue(day)
            val resolution = resolutions.getValue(day)
            val left = columnLeft(day)
            ScheduleImageDayHeader(
                dayOfWeek = day,
                rect = ScheduleImageRect(left, gridTop, left + metrics.dayColumnWidth, bodyTop),
                weekdayLabel = weekdayLabel(day),
                dateLabel = "${date.monthValue}月${date.dayOfMonth}日",
                noteLabel = when {
                    resolution.isHoliday -> null
                    resolution.sourceDate != date -> "调${weekdayLabel(resolution.sourceDate.dayOfWeek.value)}"
                    else -> null
                },
                isWeekend = day >= DayOfWeek.SATURDAY.value,
            )
        }

        val rows = (firstRow..lastRow).mapIndexed { offset, slotIndex ->
            val slot = slots[slotIndex]
            val top = bodyTop + offset * metrics.rowHeight
            ScheduleImageRow(
                rect = ScheduleImageRect(gridLeft, top, gridLeft + metrics.nodeColumnWidth, top + metrics.rowHeight),
                slotIndex = slotIndex,
                nodeLabel = nodeLabel(slot.startNode, slot.endNode),
                startTimeLabel = slot.startTime,
                endTimeLabel = slot.endTime,
            )
        }

        val holidays = (1..dayCount).mapNotNull { day ->
            val resolution = resolutions.getValue(day)
            if (!resolution.isHoliday) return@mapNotNull null
            val left = columnLeft(day)
            val rect = ScheduleImageRect(left, bodyTop, left + metrics.dayColumnWidth, bodyBottom)
            val content = rect.inset(metrics.blockPadding + metrics.blockGap)
            val name = resolution.holidayName?.takeIf { it.isNotBlank() } ?: "假日"
            val nameLines = ScheduleImageText.wrap(
                text = name,
                maxWidth = content.width,
                maxLines = 3,
                fontSize = metrics.holidayFontSize,
                bold = true,
                measurer = measurer,
            )
            ScheduleImageHoliday(
                dayOfWeek = day,
                rect = rect,
                contentRect = content,
                lines = nameLines + "全天无课",
                fontSize = metrics.holidayFontSize,
                lineHeight = metrics.holidayLineHeight,
            )
        }

        val blocks = mutableListOf<ScheduleImageBlock>()
        val footnoteSources = mutableListOf<String>()
        for (day in 1..dayCount) {
            val placed = placedByDay.getValue(day)
            if (placed.isEmpty()) continue
            val left = columnLeft(day)
            for (group in overlapGroups(placed)) {
                val overflow = group.size > metrics.maxLanesPerCell
                val laneCount = if (overflow) metrics.maxLanesPerCell else group.size
                val drawn = if (overflow) group.take(metrics.maxLanesPerCell - 1) else group
                val laneWidth = (metrics.dayColumnWidth - (laneCount + 1) * metrics.blockGap) / laneCount
                val scale = laneFontScale(laneCount)

                drawn.forEachIndexed { lane, item ->
                    blocks.add(
                        buildBlock(
                            day = day,
                            item = item,
                            columnLeft = left,
                            laneIndex = lane,
                            laneCount = laneCount,
                            laneWidth = laneWidth,
                            bodyTop = bodyTop,
                            firstRow = firstRow,
                            scale = scale,
                            metrics = metrics,
                            measurer = measurer,
                        ),
                    )
                }
                if (overflow) {
                    blocks.add(
                        buildOverflowBlock(
                            day = day,
                            group = group,
                            hiddenCount = group.size - drawn.size,
                            columnLeft = left,
                            laneIndex = laneCount - 1,
                            laneCount = laneCount,
                            laneWidth = laneWidth,
                            bodyTop = bodyTop,
                            firstRow = firstRow,
                            scale = scale,
                            metrics = metrics,
                            measurer = measurer,
                        ),
                    )
                }
                if (group.size >= metrics.footnoteLaneThreshold) {
                    val startNode = group.minOf { it.course.time.startNode }
                    val endNode = group.maxOf { it.course.time.endNode }
                    val titles = group.joinToString("、") { it.course.title }
                    footnoteSources.add(
                        "${weekdayLabel(day)} ${nodeLabel(startNode, endNode)}节同时有 ${group.size} 门：$titles",
                    )
                }
            }
        }

        val footnoteWidth = gridRight - gridLeft
        val footnotes = footnoteSources.flatMap { note ->
            ScheduleImageText.wrap(
                text = note,
                maxWidth = footnoteWidth,
                maxLines = 2,
                fontSize = metrics.footnoteFontSize,
                bold = false,
                measurer = measurer,
            )
        }
        val footnoteTop = bodyBottom + metrics.footnoteGap
        val contentBottom = if (footnotes.isEmpty()) {
            bodyBottom
        } else {
            footnoteTop + footnotes.size * metrics.footnoteLineHeight
        }

        val courseCount = shown.size
        val weekEnd = weekMonday.plusDays((dayCount - 1).toLong())
        return ScheduleImageLayoutResult(
            width = ceil(gridRight + metrics.outerPadding).toInt(),
            height = ceil(contentBottom + metrics.outerPadding).toInt(),
            metrics = metrics,
            title = termName?.trim()?.takeIf { it.isNotEmpty() } ?: "课表",
            subtitle = "第 $safeWeek 周 · ${dateRangeLabel(weekMonday, weekEnd)}",
            weekNumber = safeWeek,
            gridRect = ScheduleImageRect(gridLeft, gridTop, gridRight, bodyBottom),
            bodyRect = ScheduleImageRect(gridLeft, bodyTop, gridRight, bodyBottom),
            nodeColumnRect = ScheduleImageRect(gridLeft, gridTop, gridLeft + metrics.nodeColumnWidth, bodyBottom),
            dayHeaders = dayHeaders,
            rows = rows,
            holidays = holidays,
            blocks = blocks,
            footnotes = footnotes,
            footnoteTop = footnoteTop,
            courseCount = courseCount,
            failureReason = if (courseCount == 0 && holidays.isEmpty()) "第 $safeWeek 周没有课程" else null,
        )
    }

    private fun collectDay(
        date: LocalDate,
        resolution: ScheduleDayResolution,
        termStartDate: LocalDate,
        importedByDay: Map<Int, List<CourseItem>>,
        visibleManual: List<CourseItem>,
        overrides: List<TemporaryScheduleOverride>,
        slots: List<ClassSlotTime>,
    ): List<PlacedCourse> {
        if (resolution.isHoliday) return emptyList()
        val sourceDate = resolution.sourceDate
        val sourceDay = sourceDate.dayOfWeek.value
        val sourceWeek = resolveTermWeekNumber(termStartDate, sourceDate)
        val candidates = filterTemporaryCancelledCourses(
            date = date,
            courses = importedByDay[sourceDay].orEmpty() +
                visibleManual.filter { it.time.dayOfWeek == sourceDay },
            overrides = overrides,
        ).filter { isTermWeekNumberActive(sourceWeek, it.weeks) }

        return candidates
            .map { course ->
                val start = rowIndexOf(slots, course.time.startNode)
                val end = rowIndexOf(slots, course.time.endNode)
                PlacedCourse(course, minOf(start, end), maxOf(start, end))
            }
            .sortedWith(
                compareBy(
                    { it.rowStart },
                    { it.rowEnd },
                    { it.course.time.startNode },
                    { it.course.title },
                    { it.course.id },
                ),
            )
    }

    /** 节次落在哪一行；落在两个节次之间或超出范围时贴到最近的一行。 */
    private fun rowIndexOf(slots: List<ClassSlotTime>, node: Int): Int {
        val covering = slots.indexOfFirst { node in it.startNode..it.endNode }
        if (covering >= 0) return covering
        if (node < slots.first().startNode) return 0
        val previous = slots.indexOfLast { it.endNode < node }
        return if (previous >= 0) previous else slots.lastIndex
    }

    /** 把一列里行区间相互重叠的课程连成一组，同组内的课并排显示。 */
    private fun overlapGroups(placed: List<PlacedCourse>): List<List<PlacedCourse>> {
        val groups = mutableListOf<MutableList<PlacedCourse>>()
        var reach = Int.MIN_VALUE
        for (item in placed) {
            val current = groups.lastOrNull()
            if (current == null || item.rowStart > reach) {
                groups.add(mutableListOf(item))
                reach = item.rowEnd
            } else {
                current.add(item)
                reach = maxOf(reach, item.rowEnd)
            }
        }
        return groups
    }

    private fun buildBlock(
        day: Int,
        item: PlacedCourse,
        columnLeft: Float,
        laneIndex: Int,
        laneCount: Int,
        laneWidth: Float,
        bodyTop: Float,
        firstRow: Int,
        scale: Float,
        metrics: ScheduleImageMetrics,
        measurer: ScheduleImageTextMeasurer,
    ): ScheduleImageBlock {
        val rect = laneRect(columnLeft, laneIndex, laneWidth, bodyTop, firstRow, item.rowStart, item.rowEnd, metrics)
        val content = rect.inset(metrics.blockPadding)
        val titleFontSize = metrics.titleFontSize * scale
        val detailFontSize = metrics.detailFontSize * scale
        val titleLineHeight = metrics.titleLineHeight * scale
        val detailLineHeight = metrics.detailLineHeight * scale
        val course = item.course
        val lines = composeBlockLines(
            title = course.title,
            details = listOf(course.location, course.teacher),
            content = content,
            titleFontSize = titleFontSize,
            detailFontSize = detailFontSize,
            titleLineHeight = titleLineHeight,
            detailLineHeight = detailLineHeight,
            maxTitleLines = metrics.maxTitleLines,
            measurer = measurer,
        )
        return ScheduleImageBlock(
            dayOfWeek = day,
            rect = rect,
            contentRect = content,
            rowStart = item.rowStart,
            rowSpan = item.rowEnd - item.rowStart + 1,
            startNode = course.time.startNode,
            endNode = course.time.endNode,
            laneIndex = laneIndex,
            laneCount = laneCount,
            title = course.title,
            lines = lines,
            titleFontSize = titleFontSize,
            detailFontSize = detailFontSize,
            titleLineHeight = titleLineHeight,
            detailLineHeight = detailLineHeight,
            colorIndex = paletteIndexOf(course.title),
            isExam = course.category == CourseCategory.Exam,
            isOverflow = false,
        )
    }

    private fun buildOverflowBlock(
        day: Int,
        group: List<PlacedCourse>,
        hiddenCount: Int,
        columnLeft: Float,
        laneIndex: Int,
        laneCount: Int,
        laneWidth: Float,
        bodyTop: Float,
        firstRow: Int,
        scale: Float,
        metrics: ScheduleImageMetrics,
        measurer: ScheduleImageTextMeasurer,
    ): ScheduleImageBlock {
        val rowStart = group.minOf { it.rowStart }
        val rowEnd = group.maxOf { it.rowEnd }
        val rect = laneRect(columnLeft, laneIndex, laneWidth, bodyTop, firstRow, rowStart, rowEnd, metrics)
        val content = rect.inset(metrics.blockPadding)
        val titleFontSize = metrics.titleFontSize * scale
        val detailFontSize = metrics.detailFontSize * scale
        val titleLineHeight = metrics.titleLineHeight * scale
        val detailLineHeight = metrics.detailLineHeight * scale
        val title = "还有 $hiddenCount 门"
        val lines = composeBlockLines(
            title = title,
            details = listOf("见图下备注"),
            content = content,
            titleFontSize = titleFontSize,
            detailFontSize = detailFontSize,
            titleLineHeight = titleLineHeight,
            detailLineHeight = detailLineHeight,
            maxTitleLines = metrics.maxTitleLines,
            measurer = measurer,
        )
        return ScheduleImageBlock(
            dayOfWeek = day,
            rect = rect,
            contentRect = content,
            rowStart = rowStart,
            rowSpan = rowEnd - rowStart + 1,
            startNode = group.minOf { it.course.time.startNode },
            endNode = group.maxOf { it.course.time.endNode },
            laneIndex = laneIndex,
            laneCount = laneCount,
            title = title,
            lines = lines,
            titleFontSize = titleFontSize,
            detailFontSize = detailFontSize,
            titleLineHeight = titleLineHeight,
            detailLineHeight = detailLineHeight,
            colorIndex = 0,
            isExam = false,
            isOverflow = true,
        )
    }

    private fun laneRect(
        columnLeft: Float,
        laneIndex: Int,
        laneWidth: Float,
        bodyTop: Float,
        firstRow: Int,
        rowStart: Int,
        rowEnd: Int,
        metrics: ScheduleImageMetrics,
    ): ScheduleImageRect {
        val left = columnLeft + metrics.blockGap + laneIndex * (laneWidth + metrics.blockGap)
        val top = bodyTop + (rowStart - firstRow) * metrics.rowHeight + metrics.blockGap
        val bottom = bodyTop + (rowEnd - firstRow + 1) * metrics.rowHeight - metrics.blockGap
        return ScheduleImageRect(left, top, left + laneWidth, bottom)
    }

    /**
     * 课程块内的文本排版：课名优先占满可用行数，剩余高度依次留给地点和教师。
     * 每一行都按 [content] 的宽度换行或省略，不会越出格子。
     */
    private fun composeBlockLines(
        title: String,
        details: List<String>,
        content: ScheduleImageRect,
        titleFontSize: Float,
        detailFontSize: Float,
        titleLineHeight: Float,
        detailLineHeight: Float,
        maxTitleLines: Int,
        measurer: ScheduleImageTextMeasurer,
    ): List<ScheduleImageTextLine> {
        if (content.width <= 0f || content.height <= 0f) return emptyList()
        val usableDetails = details.mapNotNull { it.trim().takeIf { value -> value.isNotEmpty() } }
        val reserved = minOf(usableDetails.size, 2) * detailLineHeight
        val titleBudget = (content.height - reserved).coerceAtLeast(titleLineHeight)
        val titleAllowed = floor(titleBudget / titleLineHeight).toInt().coerceIn(1, maxTitleLines)

        val titleLines = ScheduleImageText.wrap(
            text = title,
            maxWidth = content.width,
            maxLines = titleAllowed,
            fontSize = titleFontSize,
            bold = true,
            measurer = measurer,
        )
        val result = titleLines.map { ScheduleImageTextLine(it, ScheduleImageTextRole.Title) }.toMutableList()
        var used = titleLines.size * titleLineHeight
        for (detail in usableDetails) {
            if (used + detailLineHeight > content.height) break
            val line = ScheduleImageText.singleLine(
                text = detail,
                maxWidth = content.width,
                fontSize = detailFontSize,
                bold = false,
                measurer = measurer,
            )
            if (line.isEmpty()) continue
            result.add(ScheduleImageTextLine(line, ScheduleImageTextRole.Detail))
            used += detailLineHeight
        }
        return result
    }

    /** 并排课程越多，字号缩得越小，保证窄格子里仍能放下可读的文字。 */
    private fun laneFontScale(laneCount: Int): Float = when {
        laneCount <= 1 -> 1f
        laneCount == 2 -> 0.84f
        else -> 0.72f
    }

    /** 同名课程在任意一周都取到同一个底色。 */
    internal fun paletteIndexOf(title: String): Int {
        var hash = 0
        for (ch in title) {
            hash = (hash * 31 + ch.code) and 0x7FFFFFFF
        }
        return hash % PALETTE_SIZE
    }

    private fun nodeLabel(startNode: Int, endNode: Int): String =
        if (startNode == endNode) "$startNode" else "$startNode-$endNode"

    private fun dateRangeLabel(start: LocalDate, end: LocalDate): String =
        "${start.monthValue}月${start.dayOfMonth}日 - ${end.monthValue}月${end.dayOfMonth}日"

    private fun emptyResult(
        metrics: ScheduleImageMetrics,
        weekNumber: Int,
        termName: String?,
        reason: String,
    ): ScheduleImageLayoutResult {
        val right = metrics.outerPadding + metrics.nodeColumnWidth + MIN_DAY_COLUMNS * metrics.dayColumnWidth
        val bottom = metrics.outerPadding + metrics.headerHeight + metrics.dayHeaderHeight
        val empty = ScheduleImageRect(metrics.outerPadding, metrics.outerPadding, right, bottom)
        return ScheduleImageLayoutResult(
            width = ceil(right + metrics.outerPadding).toInt(),
            height = ceil(bottom + metrics.outerPadding).toInt(),
            metrics = metrics,
            title = termName?.trim()?.takeIf { it.isNotEmpty() } ?: "课表",
            subtitle = "第 $weekNumber 周",
            weekNumber = weekNumber,
            gridRect = empty,
            bodyRect = empty,
            nodeColumnRect = empty,
            dayHeaders = emptyList(),
            rows = emptyList(),
            holidays = emptyList(),
            blocks = emptyList(),
            footnotes = emptyList(),
            footnoteTop = bottom,
            courseCount = 0,
            failureReason = reason,
        )
    }
}

/**
 * 中文优先的换行与省略。中文没有词边界，逐字断行；连续的拉丁字母与数字视为整体，
 * 整体放得下就不从中间切开，放不下才逐字符切。
 */
internal object ScheduleImageText {

    private const val ELLIPSIS = "…"

    fun wrap(
        text: String,
        maxWidth: Float,
        maxLines: Int,
        fontSize: Float,
        bold: Boolean,
        measurer: ScheduleImageTextMeasurer,
    ): List<String> {
        val normalized = text.replace('\n', ' ').replace('\t', ' ').replace('\r', ' ').trim()
        if (normalized.isEmpty() || maxLines <= 0 || maxWidth <= 0f) return emptyList()

        val lines = mutableListOf<String>()
        val current = StringBuilder()

        fun flush() {
            val value = current.toString().trimEnd()
            if (value.isNotEmpty()) lines.add(value)
            current.setLength(0)
        }

        fun fits(candidate: String): Boolean = measurer.measure(candidate, fontSize, bold) <= maxWidth

        for (token in tokenize(normalized)) {
            if (token == " " && current.isEmpty()) continue
            if (current.isNotEmpty() && fits(current.toString() + token)) {
                current.append(token)
                continue
            }
            if (current.isNotEmpty()) {
                flush()
                if (token == " ") continue
            }
            if (fits(token)) {
                current.append(token)
                continue
            }
            for (ch in token) {
                if (current.isNotEmpty() && !fits(current.toString() + ch)) flush()
                current.append(ch)
            }
        }
        flush()

        if (lines.size <= maxLines) return lines
        val kept = lines.take(maxLines).toMutableList()
        kept[maxLines - 1] = ellipsize(kept[maxLines - 1], maxWidth, fontSize, bold, measurer)
        return kept
    }

    fun singleLine(
        text: String,
        maxWidth: Float,
        fontSize: Float,
        bold: Boolean,
        measurer: ScheduleImageTextMeasurer,
    ): String = wrap(text, maxWidth, 1, fontSize, bold, measurer).firstOrNull().orEmpty()

    /** 在末尾补省略号，必要时回退删字直到整行放得下。 */
    fun ellipsize(
        text: String,
        maxWidth: Float,
        fontSize: Float,
        bold: Boolean,
        measurer: ScheduleImageTextMeasurer,
    ): String {
        if (text.isEmpty()) return text
        if (measurer.measure(text + ELLIPSIS, fontSize, bold) <= maxWidth) return text + ELLIPSIS
        var end = text.length
        while (end > 0) {
            end--
            val candidate = text.substring(0, end).trimEnd() + ELLIPSIS
            if (measurer.measure(candidate, fontSize, bold) <= maxWidth) return candidate
        }
        return ELLIPSIS
    }

    private fun tokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        val buffer = StringBuilder()
        for (ch in text) {
            if (isLatinPart(ch)) {
                buffer.append(ch)
            } else {
                if (buffer.isNotEmpty()) {
                    tokens.add(buffer.toString())
                    buffer.setLength(0)
                }
                tokens.add(ch.toString())
            }
        }
        if (buffer.isNotEmpty()) tokens.add(buffer.toString())
        return tokens
    }

    private fun isLatinPart(ch: Char): Boolean =
        ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '.' || ch == '\'' || ch == '_'
}
