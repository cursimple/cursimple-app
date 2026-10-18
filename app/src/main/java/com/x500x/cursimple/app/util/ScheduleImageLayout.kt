package com.x500x.cursimple.app.util

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.ScheduleDayResolution
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.coursesOfDay
import com.x500x.cursimple.core.kernel.model.filterTemporaryCancelledCourses
import com.x500x.cursimple.core.kernel.model.coursesMovedTo
import com.x500x.cursimple.core.kernel.model.isCourseMovedAwayFrom
import com.x500x.cursimple.core.kernel.model.isTermWeekNumberActive
import com.x500x.cursimple.core.kernel.model.occurrencesOn
import com.x500x.cursimple.core.kernel.model.locationForWeek
import com.x500x.cursimple.core.kernel.model.teacherForWeek
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
    /** Shrink block text step by step until the whole title fits, instead of ellipsizing at full size. */
    val fitBlockText: Boolean = false,
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
    val isEvent: Boolean = false,
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
    /** Inserted for events outside class periods; labelled by time only. */
    val isGap: Boolean = false,
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
    val wallpaperMode: Boolean = false,
    val headerTop: Float = metrics.outerPadding,
)

/** User-chosen image contents; defaults keep personal details off the picture. */
data class ScheduleImageOptions(
    val showCourses: Boolean = true,
    val showExams: Boolean = true,
    val showEvents: Boolean = false,
    val showWeeks: Boolean = true,
    val showLocation: Boolean = false,
    val showTeacher: Boolean = false,
    val showPeriodTimes: Boolean = true,
    val showDates: Boolean = true,
    /** Weekdays to draw (1 = Monday). One week exports those dates; all-week exports repeat them. */
    val days: Set<Int> = ALL_DAYS,
) {
    companion object {
        val ALL_DAYS: Set<Int> = (1..7).toSet()
    }
}

/** Pure coordinate layout with externally supplied [ScheduleImageTextMeasurer]. */
object ScheduleImageLayout {

    /** Renderer palette size must match this color count. */
    const val PALETTE_SIZE = 8

    private const val DEFAULT_WEEK_COUNT = 20
    private const val DAYS_PER_WEEK = 7

    /** Lock-screen wallpaper: a true 9:16 page whose table stretches to fill it. */
    private const val WALLPAPER_WIDTH = 1440
    private const val WALLPAPER_HEIGHT = 2560

    /** Only the status bar and gesture bar stay clear; the table fills everything between. */
    private const val WALLPAPER_TOP_RESERVE = 0.045f
    private const val WALLPAPER_BOTTOM_RESERVE = 0.035f
    private const val WALLPAPER_MAX_FOOTNOTE_LINES = 4

    /** Bands inserted for events outside class periods are shorter than a period row. */
    private const val GAP_ROW_WEIGHT = 0.6f

    /** Node column width ratio when period times are hidden. */
    private const val COMPACT_NODE_COLUMN = 0.62f

    /** Portrait sizes for a 1440 px page; column width and row height are fitted per export. */
    private val WALLPAPER_METRICS = ScheduleImageMetrics(
        outerPadding = 28f,
        headerHeight = 172f,
        dayHeaderHeight = 120f,
        nodeColumnWidth = 112f,
        dayColumnWidth = 180f,
        rowHeight = 130f,
        blockGap = 5f,
        blockPadding = 10f,
        headerTitleFontSize = 62f,
        headerSubtitleFontSize = 36f,
        dayNameFontSize = 36f,
        dayDateFontSize = 26f,
        dayNoteFontSize = 22f,
        nodeIndexFontSize = 34f,
        nodeTimeFontSize = 24f,
        titleFontSize = 40f,
        detailFontSize = 29f,
        titleLineHeight = 48f,
        detailLineHeight = 36f,
        holidayFontSize = 34f,
        holidayLineHeight = 46f,
        footnoteFontSize = 27f,
        footnoteLineHeight = 37f,
        footnoteGap = 16f,
        maxTitleLines = 6,
        fitBlockText = true,
    )

    /** Steps tried, largest first, when [ScheduleImageMetrics.fitBlockText] is on. */
    private val FIT_SCALES = floatArrayOf(1f, 0.9f, 0.8f, 0.72f, 0.64f, 0.56f)
    private val LATIN_WORD = Regex("[A-Za-z0-9'&.-]+")

    private data class PlacedCourse(
        val course: CourseItem,
        val rowStart: Int,
        val rowEnd: Int,
    )

    /** A course or event resolved to display rows and its visible detail lines. */
    private data class PlacedItem(
        val id: String,
        val title: String,
        val details: List<String>,
        val rowStart: Int,
        val rowEnd: Int,
        val startNode: Int,
        val endNode: Int,
        val isExam: Boolean,
        val isEvent: Boolean,
    )

    /** A period row, or a time band ([slotIndex] < 0) inserted for events outside periods. */
    private data class RowSpec(
        val slotIndex: Int,
        val nodeLabel: String,
        val startLabel: String,
        val endLabel: String,
        val startMinute: Int?,
        val endMinute: Int?,
        val weight: Float,
    ) {
        val isGap: Boolean get() = slotIndex < 0
    }

    private class RowGeometry(val tops: FloatArray, val bottoms: FloatArray)

    private data class EventEntry(val event: ScheduleEvent, val weeks: List<Int>)

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
        wallpaperMode: Boolean = false,
        events: List<ScheduleEvent> = emptyList(),
        options: ScheduleImageOptions = ScheduleImageOptions(),
    ): ScheduleImageLayoutResult {
        val base = if (wallpaperMode) WALLPAPER_METRICS else metrics
        val slots = timingProfile.slotTimes
            .filter { it.endNode >= it.startNode }
            .sortedWith(compareBy({ it.startNode }, { it.endNode }))
        val safeWeek = weekNumber.coerceAtLeast(1)
        if (slots.isEmpty()) {
            return emptyResult(base, safeWeek, allWeeks, termName, labels, labels.noTimingFailure, wallpaperMode)
        }

        val weekMonday = weekStartDate(termStartDate, safeWeek)
        val displayWeekStart = if (weekStartDay == WeekStartDay.Sunday) weekMonday.minusDays(1) else weekMonday
        val chosenDays = options.days.ifEmpty { ScheduleImageOptions.ALL_DAYS }
        val columns = columnDayOfWeeks(weekStart = weekStartDay, weekendVisible = true, saturdayVisible = true)
            .filter { it in chosenDays }
        val dayDates = columns.associateWith { columnDate(displayWeekStart, it) }
        val resolutions = dayDates.mapValues { (_, date) ->
            if (allWeeks) {
                ScheduleDayResolution(date = date, sourceDate = date, isHoliday = false, holidayName = null)
            } else {
                resolveScheduleDay(date, overrides, holidayCalendar)
            }
        }

        fun CourseItem.included(): Boolean =
            if (category == CourseCategory.Exam) options.showExams else options.showCourses
        // Hidden weekend columns can still lend their courses to a swapped weekday.
        val importedByDay = (1..DAYS_PER_WEEK).associateWith { day ->
            schedule?.coursesOfDay(day).orEmpty().visibleScheduleCourses().filter { it.included() }
        }
        val visibleManual = manualCourses.visibleScheduleCourses().filter { it.included() }

        val coursesByDay = columns.associateWith { day ->
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
        val eventsByDay = if (options.showEvents) {
            collectEvents(events, columns, dayDates, termStartDate, allWeeks, maxWeekNumber(schedule, manualCourses))
        } else {
            emptyMap()
        }

        // Configured periods plus bands for events outside them; empty periods stay visible.
        val rowSpecs = buildRows(
            slots,
            eventsByDay.values.flatten().mapNotNull { entry ->
                val start = entry.event.startMinute ?: return@mapNotNull null
                val end = entry.event.endMinute ?: return@mapNotNull null
                start to end
            },
        )
        val slotRow = IntArray(slots.size)
        rowSpecs.forEachIndexed { index, row -> if (!row.isGap) slotRow[row.slotIndex] = index }
        val detailWeek = if (allWeeks) null else safeWeek
        val placedByDay = columns.associateWith { day ->
            val courses = coursesByDay.getValue(day).map { placed ->
                val course = placed.course
                PlacedItem(
                    id = course.id,
                    title = course.title,
                    details = courseDetails(course, options, labels, detailWeek),
                    rowStart = slotRow[placed.rowStart],
                    rowEnd = slotRow[placed.rowEnd],
                    startNode = course.time.startNode,
                    endNode = course.time.endNode,
                    isExam = course.category == CourseCategory.Exam,
                    isEvent = false,
                )
            }
            val dayEvents = eventsByDay[day].orEmpty().mapNotNull { placeEvent(it, rowSpecs, options, labels, allWeeks) }
            (courses + dayEvents).sortedWith(
                compareBy({ it.rowStart }, { it.rowEnd }, { it.isEvent }, { it.startNode }, { it.title }, { it.id }),
            )
        }
        val shown = columns.flatMap { placedByDay.getValue(it) }
        val dayCount = columns.size

        val nodeColumnWidth = base.nodeColumnWidth * if (options.showPeriodTimes) 1f else COMPACT_NODE_COLUMN
        val dayColumnWidth = if (wallpaperMode) {
            (WALLPAPER_WIDTH - 2 * base.outerPadding - nodeColumnWidth) / dayCount
        } else {
            base.dayColumnWidth
        }
        val gridLeft = base.outerPadding
        val gridRight = gridLeft + nodeColumnWidth + dayCount * dayColumnWidth

        // Footnotes depend only on placement, so the wallpaper can reserve their space first.
        val footnoteSources = mutableListOf<String>()
        columns.forEach { day ->
            for (group in overlapGroups(placedByDay.getValue(day))) {
                if (group.size < base.footnoteLaneThreshold) continue
                val titles = group.map { it.title }
                val weekday = labels.weekdayName(day)
                val span = spanLabel(group, rowSpecs)
                footnoteSources.add(
                    if (allWeeks) {
                        labels.sharedCellFootnote(weekday, span, titles)
                    } else {
                        labels.conflictFootnote(weekday, span, titles)
                    },
                )
            }
        }
        val wrappedFootnotes = footnoteSources.flatMap { note ->
            ScheduleImageText.wrap(
                text = note,
                maxWidth = gridRight - gridLeft,
                maxLines = 2,
                fontSize = base.footnoteFontSize,
                bold = false,
                measurer = measurer,
            )
        }
        val footnotes = if (wallpaperMode) wrappedFootnotes.take(WALLPAPER_MAX_FOOTNOTE_LINES) else wrappedFootnotes
        val footnoteBlock = if (footnotes.isEmpty()) 0f else base.footnoteGap + footnotes.size * base.footnoteLineHeight

        val headerTop = if (wallpaperMode) WALLPAPER_HEIGHT * WALLPAPER_TOP_RESERVE else base.outerPadding
        val gridTop = headerTop + base.headerHeight
        val bodyTop = gridTop + base.dayHeaderHeight
        val totalWeight = rowSpecs.sumOf { it.weight.toDouble() }.toFloat()
        val m = if (wallpaperMode) {
            val bodyLimit = WALLPAPER_HEIGHT * (1f - WALLPAPER_BOTTOM_RESERVE) - footnoteBlock
            val unit = (bodyLimit - bodyTop) / totalWeight
            // Fewer columns leave wider blocks, so the text grows with them.
            val text = minOf(dayColumnWidth / 180f, unit / 110f).coerceIn(0.7f, 1.8f)
            val node = (unit / 120f).coerceIn(0.75f, 1f)
            base.copy(
                nodeColumnWidth = nodeColumnWidth,
                dayColumnWidth = dayColumnWidth,
                rowHeight = unit,
                titleFontSize = base.titleFontSize * text,
                detailFontSize = base.detailFontSize * text,
                titleLineHeight = base.titleLineHeight * text,
                detailLineHeight = base.detailLineHeight * text,
                holidayFontSize = base.holidayFontSize * text,
                holidayLineHeight = base.holidayLineHeight * text,
                nodeIndexFontSize = base.nodeIndexFontSize * node,
                nodeTimeFontSize = base.nodeTimeFontSize * node,
            )
        } else {
            base.copy(nodeColumnWidth = nodeColumnWidth)
        }

        val tops = FloatArray(rowSpecs.size)
        val bottoms = FloatArray(rowSpecs.size)
        var cursor = bodyTop
        rowSpecs.forEachIndexed { index, row ->
            tops[index] = cursor
            cursor += row.weight * m.rowHeight
            bottoms[index] = cursor
        }
        val geometry = RowGeometry(tops, bottoms)
        val bodyBottom = cursor

        fun columnLeft(columnIndex: Int): Float =
            gridLeft + m.nodeColumnWidth + columnIndex * m.dayColumnWidth

        val dayHeaders = columns.mapIndexed { columnIndex, day ->
            val date = dayDates.getValue(day)
            val resolution = resolutions.getValue(day)
            val left = columnLeft(columnIndex)
            ScheduleImageDayHeader(
                dayOfWeek = day,
                rect = ScheduleImageRect(left, gridTop, left + m.dayColumnWidth, bodyTop),
                weekdayLabel = labels.weekdayName(day),
                dateLabel = if (allWeeks || !options.showDates) "" else labels.dateLabel(date),
                noteLabel = when {
                    resolution.isHoliday -> null
                    resolution.sourceDate != date ->
                        labels.makeUpNote(labels.weekdayName(resolution.sourceDate.dayOfWeek.value))
                    else -> null
                },
                isWeekend = day >= DayOfWeek.SATURDAY.value,
            )
        }

        val rows = rowSpecs.mapIndexed { index, row ->
            // Bands have no period number, so they always keep their times.
            val showTimes = row.isGap || options.showPeriodTimes
            ScheduleImageRow(
                rect = ScheduleImageRect(gridLeft, tops[index], gridLeft + m.nodeColumnWidth, bottoms[index]),
                slotIndex = row.slotIndex,
                nodeLabel = row.nodeLabel,
                startTimeLabel = if (showTimes) row.startLabel else "",
                endTimeLabel = if (showTimes) row.endLabel else "",
                isGap = row.isGap,
            )
        }

        val holidays = columns.mapIndexedNotNull { columnIndex, day ->
            val resolution = resolutions.getValue(day)
            if (!resolution.isHoliday) return@mapIndexedNotNull null
            if (placedByDay.getValue(day).isNotEmpty()) return@mapIndexedNotNull null
            val left = columnLeft(columnIndex)
            val rect = ScheduleImageRect(left, bodyTop, left + m.dayColumnWidth, bodyBottom)
            val content = rect.inset(m.blockPadding + m.blockGap)
            val name = resolution.holidayNameRes?.let(labels.holidayNameOfRes)
                ?: resolution.holidayName?.takeIf { it.isNotBlank() }
                ?: labels.holidayFallbackName
            val nameLines = ScheduleImageText.wrap(
                text = name,
                maxWidth = content.width,
                maxLines = 3,
                fontSize = m.holidayFontSize,
                bold = true,
                measurer = measurer,
            )
            ScheduleImageHoliday(
                dayOfWeek = day,
                rect = rect,
                contentRect = content,
                lines = nameLines + labels.holidayAllDayOff,
                fontSize = m.holidayFontSize,
                lineHeight = m.holidayLineHeight,
            )
        }

        val blocks = mutableListOf<ScheduleImageBlock>()
        columns.forEachIndexed { columnIndex, day ->
            val placed = placedByDay.getValue(day)
            if (placed.isEmpty()) return@forEachIndexed
            val left = columnLeft(columnIndex)
            for (group in overlapGroups(placed)) {
                val overflow = group.size > m.maxLanesPerCell
                val laneCount = if (overflow) m.maxLanesPerCell else group.size
                val drawn = if (overflow) group.take(m.maxLanesPerCell - 1) else group
                val laneWidth = (m.dayColumnWidth - (laneCount + 1) * m.blockGap) / laneCount
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
                            geometry = geometry,
                            scale = scale,
                            metrics = m,
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
                            geometry = geometry,
                            scale = scale,
                            metrics = m,
                            measurer = measurer,
                            labels = labels,
                        ),
                    )
                }
            }
        }

        val footnoteTop = bodyBottom + m.footnoteGap
        val contentBottom = if (footnotes.isEmpty()) {
            bodyBottom
        } else {
            footnoteTop + footnotes.size * m.footnoteLineHeight
        }

        val courseCount = shown.size
        val weekBegin = dayDates.getValue(columns.first())
        val weekEnd = dayDates.getValue(columns.last())
        val width = if (wallpaperMode) WALLPAPER_WIDTH else ceil(gridRight + m.outerPadding).toInt()
        val height = if (wallpaperMode) WALLPAPER_HEIGHT else ceil(contentBottom + m.outerPadding).toInt()
        return ScheduleImageLayoutResult(
            width = width,
            height = height,
            metrics = m,
            title = termName?.trim()?.takeIf { it.isNotEmpty() } ?: labels.defaultTitle,
            subtitle = when {
                allWeeks -> labels.allWeeksSubtitle
                !options.showDates -> labels.weekLabel(safeWeek)
                weekBegin == weekEnd -> "${labels.weekLabel(safeWeek)} · ${labels.dateLabel(weekBegin)}"
                else -> "${labels.weekLabel(safeWeek)} · ${labels.dateLabel(weekBegin)} - ${labels.dateLabel(weekEnd)}"
            },
            weekNumber = safeWeek,
            allWeeks = allWeeks,
            gridRect = ScheduleImageRect(gridLeft, gridTop, gridRight, bodyBottom),
            bodyRect = ScheduleImageRect(gridLeft, bodyTop, gridRight, bodyBottom),
            nodeColumnRect = ScheduleImageRect(gridLeft, gridTop, gridLeft + m.nodeColumnWidth, bodyBottom),
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
            wallpaperMode = wallpaperMode,
            headerTop = headerTop,
        )
    }

    private fun courseDetails(
        course: CourseItem,
        options: ScheduleImageOptions,
        labels: ScheduleImageLabels,
        week: Int?,
    ): List<String> = buildList {
        if (options.showWeeks) formatWeekRanges(course.weeks).takeIf { it.isNotEmpty() }?.let { add(labels.weeksDetail(it)) }
        if (options.showLocation) add(course.locationForWeek(week))
        if (options.showTeacher) add(course.teacherForWeek(week))
    }

    /** Single weeks use real dates; all weeks group each event by weekday with the weeks it occurs in. */
    private fun collectEvents(
        events: List<ScheduleEvent>,
        columns: List<Int>,
        dayDates: Map<Int, LocalDate>,
        termStartDate: LocalDate,
        allWeeks: Boolean,
        maxWeek: Int,
    ): Map<Int, List<EventEntry>> {
        if (events.isEmpty()) return emptyMap()
        if (!allWeeks) {
            return columns.associateWith { day ->
                events.occurrencesOn(dayDates.getValue(day)).map { EventEntry(it, emptyList()) }
            }
        }
        val firstMonday = weekStartDate(termStartDate, 1)
        return columns.associateWith { day ->
            events.mapNotNull { event ->
                val weeks = (1..maxWeek).filter { week ->
                    event.occursOn(firstMonday.plusWeeks(week - 1L).plusDays(day - 1L))
                }
                weeks.takeIf { it.isNotEmpty() }?.let { EventEntry(event, it) }
            }.sortedWith(compareBy({ it.event.startMinute }, { it.event.title }))
        }
    }

    /** Events overlapping no period get a band between the neighbouring periods, as on the timetable. */
    private fun buildRows(slots: List<ClassSlotTime>, eventRanges: List<Pair<Int, Int>>): List<RowSpec> {
        val clocks = slots.map { slot ->
            val start = clockMinute(slot.startTime)
            val end = clockMinute(slot.endTime)
            if (start != null && end != null && end > start) start to end else null
        }
        val timed = clocks.indices.filter { clocks[it] != null }
        val inserts = sortedMapOf<Int, Pair<Int, Int>>()
        for ((start, end) in eventRanges) {
            val overlaps = timed.any { index ->
                val (slotStart, slotEnd) = clocks[index]!!
                minOf(end, slotEnd) > maxOf(start, slotStart)
            }
            if (overlaps) continue
            val key = timed.firstOrNull { clocks[it]!!.first >= end } ?: slots.size
            inserts[key] = inserts[key]?.let { minOf(it.first, start) to maxOf(it.second, end) } ?: (start to end)
        }
        fun band(range: Pair<Int, Int>) = RowSpec(
            slotIndex = -1,
            nodeLabel = "",
            startLabel = formatClock(range.first),
            endLabel = formatClock(range.second),
            startMinute = range.first,
            endMinute = range.second,
            weight = GAP_ROW_WEIGHT,
        )
        val rows = mutableListOf<RowSpec>()
        slots.forEachIndexed { index, slot ->
            inserts[index]?.let { rows += band(it) }
            rows += RowSpec(
                slotIndex = index,
                nodeLabel = nodeLabel(slot.startNode, slot.endNode),
                startLabel = slot.startTime,
                endLabel = slot.endTime,
                startMinute = clocks[index]?.first,
                endMinute = clocks[index]?.second,
                weight = 1f,
            )
        }
        inserts[slots.size]?.let { rows += band(it) }
        return rows
    }

    private fun placeEvent(
        entry: EventEntry,
        rows: List<RowSpec>,
        options: ScheduleImageOptions,
        labels: ScheduleImageLabels,
        allWeeks: Boolean,
    ): PlacedItem? {
        val event = entry.event
        val start = event.startMinute ?: return null
        val end = event.endMinute ?: return null
        val hits = rows.indices.filter { index ->
            val rowStart = rows[index].startMinute ?: return@filter false
            val rowEnd = rows[index].endMinute ?: return@filter false
            minOf(end, rowEnd) > maxOf(start, rowStart)
        }
        val first = hits.firstOrNull() ?: return null
        val details = buildList {
            add("${event.startTime.trim()}-${event.endTime.trim()}")
            if (options.showWeeks && allWeeks) {
                formatWeekRanges(entry.weeks).takeIf { it.isNotEmpty() }?.let { add(labels.weeksDetail(it)) }
            }
            if (options.showLocation) add(event.location)
        }
        return PlacedItem(
            id = "event:${event.id}",
            title = event.title,
            details = details,
            rowStart = first,
            rowEnd = hits.last(),
            startNode = 0,
            endNode = 0,
            isExam = false,
            isEvent = true,
        )
    }

    /** Period numbers when courses are involved, otherwise the band times. */
    private fun spanLabel(group: List<PlacedItem>, rows: List<RowSpec>): String {
        val courses = group.filterNot { it.isEvent }
        if (courses.isNotEmpty()) return nodeLabel(courses.minOf { it.startNode }, courses.maxOf { it.endNode })
        return "${rows[group.minOf { it.rowStart }].startLabel}-${rows[group.maxOf { it.rowEnd }].endLabel}"
    }

    private fun clockMinute(raw: String): Int? {
        val parts = raw.trim().split(':')
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    private fun formatClock(minute: Int): String =
        "${(minute / 60).toString().padStart(2, '0')}:${(minute % 60).toString().padStart(2, '0')}"

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

    private fun overlapGroups(placed: List<PlacedItem>): List<List<PlacedItem>> {
        val groups = mutableListOf<MutableList<PlacedItem>>()
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
        item: PlacedItem,
        columnLeft: Float,
        laneIndex: Int,
        laneCount: Int,
        laneWidth: Float,
        geometry: RowGeometry,
        scale: Float,
        metrics: ScheduleImageMetrics,
        measurer: ScheduleImageTextMeasurer,
    ): ScheduleImageBlock {
        val rect = laneRect(columnLeft, laneIndex, laneWidth, geometry, item.rowStart, item.rowEnd, metrics)
        val content = rect.inset(metrics.blockPadding)
        @Suppress("NAME_SHADOWING")
        val scale = fittedScale(item.title, item.details, content, scale, metrics, measurer)
        val titleFontSize = metrics.titleFontSize * scale
        val detailFontSize = metrics.detailFontSize * scale
        val titleLineHeight = metrics.titleLineHeight * scale
        val detailLineHeight = metrics.detailLineHeight * scale
        val lines = composeBlockLines(
            title = item.title,
            details = item.details,
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
            startNode = item.startNode,
            endNode = item.endNode,
            laneIndex = laneIndex,
            laneCount = laneCount,
            title = item.title,
            lines = lines,
            titleFontSize = titleFontSize,
            detailFontSize = detailFontSize,
            titleLineHeight = titleLineHeight,
            detailLineHeight = detailLineHeight,
            colorIndex = paletteIndexOf(item.title),
            isExam = item.isExam,
            isOverflow = false,
            isEvent = item.isEvent,
        )
    }

    private fun buildOverflowBlock(
        day: Int,
        group: List<PlacedItem>,
        hiddenCount: Int,
        columnLeft: Float,
        laneIndex: Int,
        laneCount: Int,
        laneWidth: Float,
        geometry: RowGeometry,
        scale: Float,
        metrics: ScheduleImageMetrics,
        measurer: ScheduleImageTextMeasurer,
        labels: ScheduleImageLabels,
    ): ScheduleImageBlock {
        val rowStart = group.minOf { it.rowStart }
        val rowEnd = group.maxOf { it.rowEnd }
        val rect = laneRect(columnLeft, laneIndex, laneWidth, geometry, rowStart, rowEnd, metrics)
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
            startNode = group.minOf { it.startNode },
            endNode = group.maxOf { it.endNode },
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

    /** With [ScheduleImageMetrics.fitBlockText], the largest step at which the title and details all fit. */
    private fun fittedScale(
        title: String,
        details: List<String>,
        content: ScheduleImageRect,
        laneScale: Float,
        metrics: ScheduleImageMetrics,
        measurer: ScheduleImageTextMeasurer,
    ): Float {
        if (!metrics.fitBlockText || content.width <= 0f || content.height <= 0f) return laneScale
        val usable = details.map { it.trim() }.filter { it.isNotEmpty() }
        val words = LATIN_WORD.findAll(title).map { it.value }.toList()
        // Prefer a size where no Latin word is split mid-word; drop that wish if nothing fits.
        for (keepWords in booleanArrayOf(true, false)) for (step in FIT_SCALES) {
            val scale = laneScale * step
            if (keepWords && words.any { measurer.measure(it, metrics.titleFontSize * scale, true) > content.width }) continue
            val titleLines = ScheduleImageText.wrap(
                text = title,
                maxWidth = content.width,
                maxLines = Int.MAX_VALUE,
                fontSize = metrics.titleFontSize * scale,
                bold = true,
                measurer = measurer,
            ).size
            val detailsFit = usable.all { measurer.measure(it, metrics.detailFontSize * scale, false) <= content.width }
            val height = titleLines * metrics.titleLineHeight * scale + usable.size * metrics.detailLineHeight * scale
            if (titleLines <= metrics.maxTitleLines && detailsFit && height <= content.height) return scale
        }
        return laneScale * FIT_SCALES.last()
    }

    private fun laneRect(
        columnLeft: Float,
        laneIndex: Int,
        laneWidth: Float,
        geometry: RowGeometry,
        rowStart: Int,
        rowEnd: Int,
        metrics: ScheduleImageMetrics,
    ): ScheduleImageRect {
        val left = columnLeft + metrics.blockGap + laneIndex * (laneWidth + metrics.blockGap)
        val top = geometry.tops[rowStart] + metrics.blockGap
        val bottom = geometry.bottoms[rowEnd] - metrics.blockGap
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
        wallpaperMode: Boolean = false,
    ): ScheduleImageLayoutResult {
        val headerTop = if (wallpaperMode) WALLPAPER_HEIGHT * WALLPAPER_TOP_RESERVE else metrics.outerPadding
        val right = if (wallpaperMode) {
            WALLPAPER_WIDTH - metrics.outerPadding
        } else {
            metrics.outerPadding + metrics.nodeColumnWidth + DAYS_PER_WEEK * metrics.dayColumnWidth
        }
        val bottom = headerTop + metrics.headerHeight + metrics.dayHeaderHeight
        val empty = ScheduleImageRect(metrics.outerPadding, headerTop, right, bottom)
        return ScheduleImageLayoutResult(
            width = if (wallpaperMode) WALLPAPER_WIDTH else ceil(right + metrics.outerPadding).toInt(),
            height = if (wallpaperMode) WALLPAPER_HEIGHT else ceil(bottom + metrics.outerPadding).toInt(),
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
            wallpaperMode = wallpaperMode,
            headerTop = headerTop,
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
