package com.x500x.cursimple.feature.widget

import android.content.Context
import androidx.annotation.StringRes
import com.x500x.cursimple.core.kernel.model.weekdayNameRes
import java.time.LocalDate

/** 假日称呼；日历没给名字时由界面层补通用称呼。 */
internal sealed interface WidgetHolidayLabel {
    data object Unnamed : WidgetHolidayLabel

    data class Named(val name: String) : WidgetHolidayLabel

    /** 内置假日，名字随语言变化。 */
    data class BuiltIn(val nameRes: Int) : WidgetHolidayLabel
}

internal fun widgetHolidayLabel(holidayName: String?, holidayNameRes: Int? = null): WidgetHolidayLabel = when {
    holidayNameRes != null -> WidgetHolidayLabel.BuiltIn(holidayNameRes)
    !holidayName.isNullOrBlank() -> WidgetHolidayLabel.Named(holidayName)
    else -> WidgetHolidayLabel.Unnamed
}

internal fun Context.widgetHolidayText(label: WidgetHolidayLabel): String = when (label) {
    WidgetHolidayLabel.Unnamed -> getString(R.string.widget_holiday_default)
    is WidgetHolidayLabel.Named -> label.name
    is WidgetHolidayLabel.BuiltIn -> getString(label.nameRes)
}

/** 今日课表小组件标题里的日期偏移标签。 */
internal sealed interface WidgetDayTag {
    data object Yesterday : WidgetDayTag

    data object Today : WidgetDayTag

    data object Tomorrow : WidgetDayTag

    /** 今天之后第 [days] 天。 */
    data class Ahead(val days: Int) : WidgetDayTag

    /** 今天之前第 [days] 天。 */
    data class Behind(val days: Int) : WidgetDayTag
}

internal fun widgetDayTag(offset: Int): WidgetDayTag = when {
    offset == -1 -> WidgetDayTag.Yesterday
    offset == 0 -> WidgetDayTag.Today
    offset == 1 -> WidgetDayTag.Tomorrow
    offset > 0 -> WidgetDayTag.Ahead(offset)
    else -> WidgetDayTag.Behind(-offset)
}

internal fun Context.widgetDayTagText(tag: WidgetDayTag): String = when (tag) {
    WidgetDayTag.Yesterday -> getString(R.string.widget_day_tag_yesterday)
    WidgetDayTag.Today -> getString(R.string.widget_day_tag_today)
    WidgetDayTag.Tomorrow -> getString(R.string.widget_day_tag_tomorrow)
    is WidgetDayTag.Ahead -> getString(R.string.widget_day_tag_ahead, tag.days)
    is WidgetDayTag.Behind -> getString(R.string.widget_day_tag_behind, tag.days)
}

internal fun Context.widgetMonthDayText(date: LocalDate): String =
    getString(R.string.widget_month_day, date.monthValue, date.dayOfMonth)

/** 日期加星期几，用于临时调课的来源日期。 */
internal fun Context.widgetDateWithWeekdayText(date: LocalDate): String = getString(
    R.string.widget_date_weekday,
    widgetMonthDayText(date),
    getString(weekdayNameRes(date.dayOfWeek.value)),
)

/**
 * 今日课表小组件的空态。
 * 放假优先于学期状态：课表是否同步、是否已开学，都不如“这天不上课”贴近实际。
 */
internal sealed interface ScheduleWidgetEmptyLabel {
    data class Holiday(val label: WidgetHolidayLabel) : ScheduleWidgetEmptyLabel

    data object TermStartMissing : ScheduleWidgetEmptyLabel

    /** 已知开学日期但还没开学；[termStartDate] 为空时只说未开学。 */
    data class BeforeTermStart(val termStartDate: LocalDate?) : ScheduleWidgetEmptyLabel

    /** 正常上课日但当天没课，按日期偏移 [offset] 区分说法。 */
    data class NoCourses(val offset: Int) : ScheduleWidgetEmptyLabel
}

internal fun scheduleWidgetEmptyLabel(
    termStartMissing: Boolean,
    beforeTermStart: Boolean,
    termStartDate: LocalDate?,
    offset: Int,
    holidayLabel: WidgetHolidayLabel? = null,
): ScheduleWidgetEmptyLabel = when {
    holidayLabel != null -> ScheduleWidgetEmptyLabel.Holiday(holidayLabel)
    termStartMissing -> ScheduleWidgetEmptyLabel.TermStartMissing
    beforeTermStart -> ScheduleWidgetEmptyLabel.BeforeTermStart(termStartDate)
    else -> ScheduleWidgetEmptyLabel.NoCourses(offset)
}

internal fun Context.scheduleWidgetEmptyText(label: ScheduleWidgetEmptyLabel): String = when (label) {
    is ScheduleWidgetEmptyLabel.Holiday ->
        getString(R.string.widget_empty_holiday, widgetHolidayText(label.label))

    ScheduleWidgetEmptyLabel.TermStartMissing -> getString(R.string.widget_empty_term_start_missing)
    is ScheduleWidgetEmptyLabel.BeforeTermStart -> beforeTermStartText(label.termStartDate)
    is ScheduleWidgetEmptyLabel.NoCourses -> getString(noCoursesRes(label.offset))
}

@StringRes
private fun noCoursesRes(offset: Int): Int = when (offset) {
    -1 -> R.string.widget_empty_yesterday
    0 -> R.string.widget_empty_today
    1 -> R.string.widget_empty_tomorrow
    else -> R.string.widget_empty_other_day
}

private fun Context.beforeTermStartText(termStartDate: LocalDate?): String = termStartDate
    ?.let {
        getString(R.string.widget_empty_before_term_start_date, it.monthValue, it.dayOfMonth)
    }
    ?: getString(R.string.widget_empty_before_term_start)

/** 今日课表小组件副标题：星期几，再补上假日、学期状态或临时调课来源。 */
internal sealed interface ScheduleWidgetSubtitle {
    val dayOfWeek: Int

    data class Holiday(
        override val dayOfWeek: Int,
        val label: WidgetHolidayLabel,
    ) : ScheduleWidgetSubtitle

    data class TermStartMissing(override val dayOfWeek: Int) : ScheduleWidgetSubtitle

    data class BeforeTermStart(override val dayOfWeek: Int) : ScheduleWidgetSubtitle

    /** 课程取自 [sourceDate] 的安排。 */
    data class TemporarySource(
        override val dayOfWeek: Int,
        val sourceDate: LocalDate,
    ) : ScheduleWidgetSubtitle

    data class Weekday(override val dayOfWeek: Int) : ScheduleWidgetSubtitle
}

internal fun scheduleWidgetSubtitle(
    dayOfWeek: Int,
    termStartMissing: Boolean,
    beforeTermStart: Boolean,
    sourceDate: LocalDate?,
    holidayLabel: WidgetHolidayLabel? = null,
): ScheduleWidgetSubtitle = when {
    holidayLabel != null -> ScheduleWidgetSubtitle.Holiday(dayOfWeek, holidayLabel)
    termStartMissing -> ScheduleWidgetSubtitle.TermStartMissing(dayOfWeek)
    beforeTermStart -> ScheduleWidgetSubtitle.BeforeTermStart(dayOfWeek)
    sourceDate != null -> ScheduleWidgetSubtitle.TemporarySource(dayOfWeek, sourceDate)
    else -> ScheduleWidgetSubtitle.Weekday(dayOfWeek)
}

internal fun Context.scheduleWidgetSubtitleText(subtitle: ScheduleWidgetSubtitle): String {
    val weekday = getString(weekdayNameRes(subtitle.dayOfWeek))
    return when (subtitle) {
        is ScheduleWidgetSubtitle.Holiday ->
            getString(R.string.widget_subtitle_holiday, weekday, widgetHolidayText(subtitle.label))

        is ScheduleWidgetSubtitle.TermStartMissing ->
            getString(R.string.widget_subtitle_term_start_missing, weekday)

        is ScheduleWidgetSubtitle.BeforeTermStart ->
            getString(R.string.widget_subtitle_before_term_start, weekday)

        is ScheduleWidgetSubtitle.TemporarySource -> getString(
            R.string.widget_subtitle_source,
            weekday,
            widgetDateWithWeekdayText(subtitle.sourceDate),
        )

        is ScheduleWidgetSubtitle.Weekday -> weekday
    }
}

/** 下一节课小组件的空态。 */
internal sealed interface NextCourseEmptyLabel {
    data class Holiday(val label: WidgetHolidayLabel) : NextCourseEmptyLabel

    data object TermStartMissing : NextCourseEmptyLabel

    data class BeforeTermStart(val termStartDate: LocalDate?) : NextCourseEmptyLabel

    /** 今天还上过课，但后面没有了。 */
    data object NoMoreToday : NextCourseEmptyLabel

    data object NoneToday : NextCourseEmptyLabel

    data object NoneTomorrow : NextCourseEmptyLabel

    data object NoneOnDay : NextCourseEmptyLabel
}

internal fun nextCourseEmptyLabel(
    weekIndex: Int?,
    termStartDate: LocalDate?,
    targetDate: LocalDate,
    today: LocalDate,
    hasCourses: Boolean,
    holidayLabel: WidgetHolidayLabel? = null,
): NextCourseEmptyLabel = when {
    holidayLabel != null -> NextCourseEmptyLabel.Holiday(holidayLabel)
    weekIndex == null -> NextCourseEmptyLabel.TermStartMissing
    isBeforeTermStart(weekIndex) -> NextCourseEmptyLabel.BeforeTermStart(termStartDate)
    targetDate == today && hasCourses -> NextCourseEmptyLabel.NoMoreToday
    targetDate == today -> NextCourseEmptyLabel.NoneToday
    targetDate == today.plusDays(1) -> NextCourseEmptyLabel.NoneTomorrow
    else -> NextCourseEmptyLabel.NoneOnDay
}

internal fun Context.nextCourseEmptyText(label: NextCourseEmptyLabel): String = when (label) {
    is NextCourseEmptyLabel.Holiday ->
        getString(R.string.widget_empty_holiday, widgetHolidayText(label.label))

    NextCourseEmptyLabel.TermStartMissing -> getString(R.string.widget_empty_term_start_missing)
    is NextCourseEmptyLabel.BeforeTermStart -> beforeTermStartText(label.termStartDate)
    NextCourseEmptyLabel.NoMoreToday -> getString(R.string.widget_next_empty_no_more_today)
    NextCourseEmptyLabel.NoneToday -> getString(R.string.widget_next_empty_today)
    NextCourseEmptyLabel.NoneTomorrow -> getString(R.string.widget_next_empty_tomorrow)
    NextCourseEmptyLabel.NoneOnDay -> getString(R.string.widget_next_empty_other_day)
}

/** 下一节课小组件表头前缀；[tomorrow] 为 true 表示展示的是次日安排。 */
internal sealed interface NextCourseDayHeader {
    val tomorrow: Boolean

    data class Plain(override val tomorrow: Boolean) : NextCourseDayHeader

    data class Holiday(
        override val tomorrow: Boolean,
        val label: WidgetHolidayLabel,
    ) : NextCourseDayHeader

    /** 课程取自 [sourceDate] 的安排。 */
    data class TemporarySource(
        override val tomorrow: Boolean,
        val sourceDate: LocalDate,
    ) : NextCourseDayHeader
}

internal fun nextCourseDayHeader(
    targetDate: LocalDate,
    sourceDate: LocalDate,
    today: LocalDate,
    holidayLabel: WidgetHolidayLabel? = null,
): NextCourseDayHeader {
    val tomorrow = targetDate != today
    return when {
        holidayLabel != null -> NextCourseDayHeader.Holiday(tomorrow, holidayLabel)
        sourceDate != targetDate -> NextCourseDayHeader.TemporarySource(tomorrow, sourceDate)
        else -> NextCourseDayHeader.Plain(tomorrow)
    }
}

internal fun Context.nextCourseDayHeaderText(header: NextCourseDayHeader): String {
    val dayLabel = getString(
        if (header.tomorrow) R.string.widget_next_header_tomorrow else R.string.widget_next_header_today,
    )
    return when (header) {
        is NextCourseDayHeader.Plain -> dayLabel
        is NextCourseDayHeader.Holiday ->
            getString(R.string.widget_next_header_holiday, dayLabel, widgetHolidayText(header.label))

        is NextCourseDayHeader.TemporarySource -> getString(
            R.string.widget_next_header_source,
            dayLabel,
            widgetDateWithWeekdayText(header.sourceDate),
        )
    }
}

/** 课程行的状态称呼；[exam] 为 true 时正在进行的是考试。 */
@StringRes
internal fun widgetCourseStatusRes(status: CourseStatus, exam: Boolean): Int = when (status) {
    CourseStatus.Live -> if (exam) R.string.widget_status_exam_live else R.string.widget_status_live
    CourseStatus.Upcoming -> R.string.widget_status_upcoming
    CourseStatus.Past -> R.string.widget_status_finished
}

internal fun Context.widgetNodeRangeText(startNode: Int, endNode: Int): String =
    getString(R.string.widget_node_range, startNode, endNode)

/** 考试课程在标题上带前缀，其余课程直接用标题。 */
internal fun Context.widgetCourseTitleText(title: String, exam: Boolean): String =
    if (exam) getString(R.string.widget_exam_title, title) else title
