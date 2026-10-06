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
import com.x500x.cursimple.core.kernel.model.coursesMovedTo
import com.x500x.cursimple.core.kernel.model.isCourseMovedAwayFrom
import com.x500x.cursimple.core.kernel.model.isTermWeekNumberActive
import com.x500x.cursimple.core.kernel.model.locationForWeek
import com.x500x.cursimple.core.kernel.model.resolveScheduleDay
import com.x500x.cursimple.core.kernel.model.resolveTermWeekNumber
import com.x500x.cursimple.core.kernel.model.temporaryScheduleCourseSourceDate
import com.x500x.cursimple.core.kernel.model.visibleScheduleCourses
import com.x500x.cursimple.core.kernel.time.WeekStartDay
import com.x500x.cursimple.core.kernel.time.columnDate
import com.x500x.cursimple.core.kernel.time.columnDayOfWeeks
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil
import kotlin.math.floor

fun interface ScheduleImageTextMeasurer {
    fun measure(text: String, fontSize: Float, bold: Boolean): Float
}

class ScheduleImageLabels(
    val defaultTitle: String,
    val holidayFallbackName: String,
    val holidayNameOfRes: (Int) -> String,
    val holidayAllDayOff: String,
    val overflowMoreDetail: String,
    /** Missing period configuration. */
    val noTimingFailure: String,
    val weekdayName: (Int) -> String,
    val dateLabel: (LocalDate) -> String,
    val weekLabel: (Int) -> String,
    val allWeeksSubtitle: String,
    val weeksDetail: (String) -> String,
    val sharedCellFootnote: (weekday: String, nodeLabel: String, titles: List<String>) -> String,
    val makeUpNote: (String) -> String,
    val overflowTitle: (Int) -> String,
    val conflictFootnote: (weekday: String, nodeLabel: String, titles: List<String>) -> String,
    /** No courses in the selected week. */
    val emptyWeekFailure: (Int) -> String,
    /** No courses across all weeks. */
    val emptyAllWeeksFailure: String,
)

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

/** Resolved image geometry; rendering performs no layout calculations. */
data class ScheduleImageLayoutResult(
    val width: Int,
    val height: Int,
    val metrics: ScheduleImageMetrics,
    val title: String,
    val subtitle: String,
    val weekNumber: Int,
    val allWeeks: Boolean,
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

/** Pure coordinate layout with externally supplied [ScheduleImageTextMeasurer]. */
object ScheduleImageLayout {

    /** Renderer palette size must match this color count. */
    const val PALETTE_SIZE = 8

    private const val DEFAULT_WEEK_COUNT = 20
    private const val DAYS_PER_WEEK = 7

    private data class PlacedCourse(
        val course: CourseItem,
        val rowStart: Int,
        val rowEnd: Int,
    )

    fun currentWeekNumber(termStartDate: LocalDate, date: LocalDate): Int =
        resolveTermWeekNumber(termStartDate, date).coerceAtLeast(1)

    fun maxWeekNumber(schedule: TermSchedule?, manualCourses: List<CourseItem>): Int {
        val all = (1..7).flatMap { schedule?.coursesOfDay(it).orEmpty() } + manualCourses
        val declared = all.mapNotNull { it.weeks.maxOrNull() }.maxOrNull()
        return (declared ?: DEFAULT_WEEK_COUNT).coerceAtLeast(1)
    }

    fun weekStartDate(termStartDate: LocalDate, weekNumber: Int): LocalDate =
        termStartDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .plusWeeks((weekNumber - 1).toLong())

    fun compute(
        termName: String?,
        termStartDate: LocalDate,
        weekNumber: Int,
        weekStartDay: WeekStartDay = WeekStartDay.Monday,
        schedule: TermSchedule?,
        manualCourses: List<CourseItem>,
        timingProfile: TermTimingProfile,
        overrides: List<TemporaryScheduleOverride>,
        holidayCalendar: HolidayCalendarSettings,
        measurer: ScheduleImageTextMeasurer,
        labels: ScheduleImageLabels,
        metrics: ScheduleImageMetrics = ScheduleImageMetrics(),
        /** All-week mode ignores date-specific holidays and temporary overrides. */
        allWeeks: Boolean = false,
    ): ScheduleImageLayoutResult {
        val slots = timingProfile.slotTimes
            .filter { it.endNode >= it.startNode }
            .sortedWith(compareBy({ it.startNode }, { it.endNode }))
        val safeWeek = weekNumber.coerceAtLeast(1)
        if (slots.isEmpty()) {
            return emptyResult(metrics, safeWeek, allWeeks, termName, labels, labels.noTimingFailure)
        }

        val weekMonday = weekStartDate(termStartDate, safeWeek)
        val displayWeekStart = if (weekStartDay == WeekStartDay.Sunday) weekMonday.minusDays(1) else weekMonday
        val orderedDayOfWeeks = columnDayOfWeeks(
            weekStart = weekStartDay,
            weekendVisible = true,
            saturdayVisible = true,
        )
        val dayDates = orderedDayOfWeeks.associateWith { columnDate(displayWeekStart, it) }
        val resolutions = dayDates.mapValues { (_, date) ->
            if (allWeeks) {
                ScheduleDayResolution(date = date, sourceDate = date, isHoliday = false, holidayName = null)
            } else {
                resolveScheduleDay(date, overrides, holidayCalendar)
            }
        }

        val importedByDay = orderedDayOfWeeks.associateWith { day ->
            schedule?.coursesOfDay(day).orEmpty().visibleScheduleCourses()
        }
        val visibleManual = manualCourses.visibleScheduleCourses()

        val placedByDay = orderedDayOfWeeks.associateWith { day ->
            collectDay(
                date = dayDates.getValue(day),
                resolution = resolutions.getValue(day),
                termStartDate = termStartDate,
                importedByDay = importedByDay,
                visibleManual = visibleManual,
                overrides = overrides,
                slots = slots,
                allWeeks = allWeeks,
            )
        }

        // Show all seven days and configured periods, including empty rows.
        val columns = orderedDayOfWeeks
        val dayCount = columns.size
        val shown = columns.flatMap { placedByDay.getValue(it) }

        val firstRow = 0
        val lastRow = slots.lastIndex
        val rowCount = lastRow - firstRow + 1

        val gridLeft = metrics.outerPadding
        val gridTop = metrics.outerPadding + metrics.headerHeight
        val bodyTop = gridTop + metrics.dayHeaderHeight
        val bodyBottom = bodyTop + rowCount * metrics.rowHeight
        val gridRight = gridLeft + metrics.nodeColumnWidth + dayCount * metrics.dayColumnWidth

        fun columnLeft(columnIndex: Int): Float =
            gridLeft + metrics.nodeColumnWidth + columnIndex * metrics.dayColumnWidth

        val dayHeaders = columns.mapIndexed { columnIndex, day ->
            val date = dayDates.getValue(day)
            val resolution = resolutions.getValue(day)
            val left = columnLeft(columnIndex)
            ScheduleImageDayHeader(
                dayOfWeek = day,
                rect = ScheduleImageRect(left, gridTop, left + metrics.dayColumnWidth, bodyTop),
                weekdayLabel = labels.weekdayName(day),
                dateLabel = if (allWeeks) "" else labels.dateLabel(date),
                noteLabel = when {
                    resolution.isHoliday -> null
                    resolution.sourceDate != date ->
                        labels.makeUpNote(labels.weekdayName(resolution.sourceDate.dayOfWeek.value))
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

        val holidays = columns.mapIndexedNotNull { columnIndex, day ->
            val resolution = resolutions.getValue(day)
            if (!resolution.isHoliday) return@mapIndexedNotNull null
            if (placedByDay.getValue(day).isNotEmpty()) return@mapIndexedNotNull null
            val left = columnLeft(columnIndex)
            val rect = ScheduleImageRect(left, bodyTop, left + metrics.dayColumnWidth, bodyBottom)
            val content = rect.inset(metrics.blockPadding + metrics.blockGap)
            val name = resolution.holidayNameRes?.let(labels.holidayNameOfRes)
                ?: resolution.holidayName?.takeIf { it.isNotBlank() }
                ?: labels.holidayFallbackName
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
                lines = nameLines + labels.holidayAllDayOff,
                fontSize = metrics.holidayFontSize,
                lineHeight = metrics.holidayLineHeight,
            )
        }

        val blocks = mutableListOf<ScheduleImageBlock>()
        val footnoteSources = mutableListOf<String>()
        columns.forEachIndexed { columnIndex, day ->
            val placed = placedByDay.getValue(day)
            if (placed.isEmpty()) return@forEachIndexed
            val left = columnLeft(columnIndex)
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
                            labels = labels,
                            showWeeks = allWeeks,
                            locationWeek = if (allWeeks) null else safeWeek,
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
                            labels = labels,
                        ),
                    )
                }
                if (group.size >= metrics.footnoteLaneThreshold) {
                    val startNode = group.minOf { it.course.time.startNode }
                    val endNode = group.maxOf { it.course.time.endNode }
                    val titles = group.map { it.course.title }
                    val weekday = labels.weekdayName(day)
                    val nodes = nodeLabel(startNode, endNode)
                    footnoteSources.add(
                        if (allWeeks) {
                            labels.sharedCellFootnote(weekday, nodes, titles)
                        } else {
                            labels.conflictFootnote(weekday, nodes, titles)
                        },
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
        val weekEnd = displayWeekStart.plusDays((dayCount - 1).toLong())
        return ScheduleImageLayoutResult(
            width = ceil(gridRight + metrics.outerPadding).toInt(),
            height = ceil(contentBottom + metrics.outerPadding).toInt(),
            metrics = metrics,
            title = termName?.trim()?.takeIf { it.isNotEmpty() } ?: labels.defaultTitle,
            subtitle = if (allWeeks) {
                labels.allWeeksSubtitle
            } else {
                "${labels.weekLabel(safeWeek)} · ${labels.dateLabel(displayWeekStart)} - ${labels.dateLabel(weekEnd)}"
            },
            weekNumber = safeWeek,
            allWeeks = allWeeks,
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
            failureReason = when {
                courseCount > 0 || holidays.isNotEmpty() -> null
                allWeeks -> labels.emptyAllWeeksFailure
                else -> labels.emptyWeekFailure(safeWeek)
            },
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
        allWeeks: Boolean,
    ): List<PlacedCourse> {
        if (resolution.isHoliday) {
            return placeCourses(
                coursesMovedTo(
                    date = date,
                    overrides = overrides,
                    courseById = { id ->
                        (importedByDay.values.flatten() + visibleManual).firstOrNull { it.id == id }
                    },
                    isOriginallyActive = { course, from ->
                        isTermWeekNumberActive(resolveTermWeekNumber(termStartDate, from), course.weeks)
                    },
                ),
                slots,
            )
        }
        val sourceDate = resolution.sourceDate
        val sourceDay = sourceDate.dayOfWeek.value
        val candidates = if (allWeeks) {
            mergeAcrossWeeks(
                importedByDay[sourceDay].orEmpty() + visibleManual.filter { it.time.dayOfWeek == sourceDay },
            )
        } else {
            // Resolve the source day per course after partial swaps.
            val ownDay = date.dayOfWeek.value
            val pool = (
                importedByDay[sourceDay].orEmpty() + importedByDay[ownDay].orEmpty() +
                    visibleManual.filter { it.time.dayOfWeek == sourceDay || it.time.dayOfWeek == ownDay }
                ).distinct()
                .filterNot { isCourseMovedAwayFrom(date, it, overrides) }
            val movedIn = coursesMovedTo(
                date = date,
                overrides = overrides,
                courseById = { id ->
                    (importedByDay.values.flatten() + visibleManual).firstOrNull { it.id == id }
                },
                isOriginallyActive = { course, from ->
                    isTermWeekNumberActive(resolveTermWeekNumber(termStartDate, from), course.weeks)
                },
            )
            filterTemporaryCancelledCourses(
                date = date,
                courses = pool,
                overrides = overrides,
            ).filter { course ->
                val courseSource = temporaryScheduleCourseSourceDate(date, course, sourceDate, overrides)
                    ?: return@filter false
                isTermWeekNumberActive(resolveTermWeekNumber(termStartDate, courseSource), course.weeks)
            } + movedIn
        }

        return placeCourses(candidates, slots)
    }

    private fun placeCourses(courses: List<CourseItem>, slots: List<ClassSlotTime>): List<PlacedCourse> =
        courses
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

    /** Merge equivalent course fragments across weeks and union their week sets. */
    private fun mergeAcrossWeeks(courses: List<CourseItem>): List<CourseItem> = courses
        .groupBy { course ->
            listOf(
                course.title.trim(),
                course.teacher.trim(),
                course.location.trim(),
                course.category.name,
                course.time.startNode.toString(),
                course.time.endNode.toString(),
            )
        }
        .map { (_, group) ->
            val first = group.first()
            if (group.size == 1) return@map first
            val weeks = if (group.any { it.weeks.isEmpty() }) {
                emptyList()
            } else {
                group.flatMap { it.weeks }.distinct().sorted()
            }
            first.copy(weeks = weeks)
        }

    internal fun formatWeekRanges(weeks: List<Int>): String {
        val sorted = weeks.filter { it > 0 }.distinct().sorted()
        if (sorted.isEmpty()) return ""
        val parts = mutableListOf<String>()
        var start = sorted.first()
        var previous = start
        for (week in sorted.drop(1)) {
            if (week == previous + 1) {
                previous = week
                continue
            }
            parts.add(if (start == previous) "$start" else "$start-$previous")
            start = week
            previous = week
        }
        parts.add(if (start == previous) "$start" else "$start-$previous")
        return parts.joinToString(", ")
    }

    private fun rowIndexOf(slots: List<ClassSlotTime>, node: Int): Int {
        val covering = slots.indexOfFirst { node in it.startNode..it.endNode }
        if (covering >= 0) return covering
        if (node < slots.first().startNode) return 0
        val previous = slots.indexOfLast { it.endNode < node }
        return if (previous >= 0) previous else slots.lastIndex
    }

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
        labels: ScheduleImageLabels,
        showWeeks: Boolean,
        locationWeek: Int?,
    ): ScheduleImageBlock {
        val rect = laneRect(columnLeft, laneIndex, laneWidth, bodyTop, firstRow, item.rowStart, item.rowEnd, metrics)
        val content = rect.inset(metrics.blockPadding)
        val titleFontSize = metrics.titleFontSize * scale
        val detailFontSize = metrics.detailFontSize * scale
        val titleLineHeight = metrics.titleLineHeight * scale
        val detailLineHeight = metrics.detailLineHeight * scale
        val course = item.course
        val weeksNote = if (showWeeks) {
            formatWeekRanges(course.weeks).takeIf { it.isNotEmpty() }?.let(labels.weeksDetail)
        } else {
            null
        }
        val lines = composeBlockLines(
            title = course.title,
            details = listOfNotNull(weeksNote) + listOf(course.locationForWeek(locationWeek), course.teacher),
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
        labels: ScheduleImageLabels,
    ): ScheduleImageBlock {
        val rowStart = group.minOf { it.rowStart }
        val rowEnd = group.maxOf { it.rowEnd }
        val rect = laneRect(columnLeft, laneIndex, laneWidth, bodyTop, firstRow, rowStart, rowEnd, metrics)
        val content = rect.inset(metrics.blockPadding)
        val titleFontSize = metrics.titleFontSize * scale
        val detailFontSize = metrics.detailFontSize * scale
        val titleLineHeight = metrics.titleLineHeight * scale
        val detailLineHeight = metrics.detailLineHeight * scale
        val title = labels.overflowTitle(hiddenCount)
        val lines = composeBlockLines(
            title = title,
            details = listOf(labels.overflowMoreDetail),
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

    /** Prioritize titles, then location and teacher, wrapping within [content] bounds. */
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

    /** Reduce text size as side-by-side course columns become narrower. */
    private fun laneFontScale(laneCount: Int): Float = when {
        laneCount <= 1 -> 1f
        laneCount == 2 -> 0.84f
        else -> 0.72f
    }

    internal fun paletteIndexOf(title: String): Int {
        var hash = 0
        for (ch in title) {
            hash = (hash * 31 + ch.code) and 0x7FFFFFFF
        }
        return hash % PALETTE_SIZE
    }

    private fun nodeLabel(startNode: Int, endNode: Int): String =
        if (startNode == endNode) "$startNode" else "$startNode-$endNode"

    private fun emptyResult(
        metrics: ScheduleImageMetrics,
        weekNumber: Int,
        allWeeks: Boolean,
        termName: String?,
        labels: ScheduleImageLabels,
        reason: String,
    ): ScheduleImageLayoutResult {
        val right = metrics.outerPadding + metrics.nodeColumnWidth + DAYS_PER_WEEK * metrics.dayColumnWidth
        val bottom = metrics.outerPadding + metrics.headerHeight + metrics.dayHeaderHeight
        val empty = ScheduleImageRect(metrics.outerPadding, metrics.outerPadding, right, bottom)
        return ScheduleImageLayoutResult(
            width = ceil(right + metrics.outerPadding).toInt(),
            height = ceil(bottom + metrics.outerPadding).toInt(),
            metrics = metrics,
            title = termName?.trim()?.takeIf { it.isNotEmpty() } ?: labels.defaultTitle,
            subtitle = if (allWeeks) labels.allWeeksSubtitle else labels.weekLabel(weekNumber),
            weekNumber = weekNumber,
            allWeeks = allWeeks,
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

/** Wrap CJK by character; keep Latin word runs intact unless wider than the line. */
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
