package com.x500x.cursimple.feature.widget

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import androidx.annotation.StringRes
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.holidayNameResOfName
import com.x500x.cursimple.core.kernel.mood.dayMood
import com.x500x.cursimple.core.kernel.mood.dayMoodLine
import com.x500x.cursimple.core.kernel.model.slotsCovering
import com.x500x.cursimple.core.kernel.model.weekdayNameRes
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

internal sealed interface WidgetHolidayLabel {
    data object Unnamed : WidgetHolidayLabel

    data class Named(val name: String) : WidgetHolidayLabel

    data class BuiltIn(val nameRes: Int) : WidgetHolidayLabel
}

internal fun widgetHolidayLabel(holidayName: String?, holidayNameRes: Int? = null): WidgetHolidayLabel = when {
    holidayNameRes != null -> WidgetHolidayLabel.BuiltIn(holidayNameRes)
    !holidayName.isNullOrBlank() -> WidgetHolidayLabel.Named(holidayName)
    else -> WidgetHolidayLabel.Unnamed
}

/**
 * Date-seeded text matches the app, excluding clock-sensitive greetings that widgets cannot
 * refresh reliably.
 */
internal fun Context.widgetTodayMood(
    today: LocalDate,
    now: LocalTime,
    holidayLabel: WidgetHolidayLabel?,
    tomorrowHoliday: Boolean,
    totalCourses: Int,
): String? {
    val mood = dayMood(
        now = LocalDateTime.of(today, now),
        isHoliday = holidayLabel != null,
        holidayNameRes = holidayLabel?.nameRes(),
        tomorrowHoliday = tomorrowHoliday,
        totalCourses = totalCourses,
        hasMoreToday = false,
        timeSensitive = false,
    ) ?: return null
    val line = dayMoodLine(today, mood) ?: return null
    return holidayLabel?.let { getString(R.string.widget_empty_holiday_mood, widgetHolidayText(it), line) } ?: line
}

private fun WidgetHolidayLabel.nameRes(): Int? = when (this) {
    WidgetHolidayLabel.Unnamed -> null
    is WidgetHolidayLabel.Named -> holidayNameResOfName(name)
    is WidgetHolidayLabel.BuiltIn -> nameRes
}

internal fun Context.widgetHolidayText(label: WidgetHolidayLabel): String = when (label) {
    WidgetHolidayLabel.Unnamed -> getString(R.string.widget_holiday_default)
    is WidgetHolidayLabel.Named -> label.name
    is WidgetHolidayLabel.BuiltIn -> getString(label.nameRes)
}

internal sealed interface WidgetDayTag {
    data object Yesterday : WidgetDayTag

    data object Today : WidgetDayTag

    data object Tomorrow : WidgetDayTag

    data class Ahead(val days: Int) : WidgetDayTag

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

internal fun Context.widgetDateWithWeekdayText(date: LocalDate): String = getString(
    R.string.widget_date_weekday,
    widgetMonthDayText(date),
    getString(weekdayNameRes(date.dayOfWeek.value)),
)

/** Holiday empty-state explanation takes priority over term configuration. */
internal sealed interface ScheduleWidgetEmptyLabel {
    data class Holiday(val label: WidgetHolidayLabel) : ScheduleWidgetEmptyLabel

    data object TermStartMissing : ScheduleWidgetEmptyLabel

    /** Pre-term state with optional [termStartDate]. */
    data class BeforeTermStart(val termStartDate: LocalDate?) : ScheduleWidgetEmptyLabel

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

internal sealed interface ScheduleWidgetSubtitle {
    val dayOfWeek: Int

    data class Holiday(
        override val dayOfWeek: Int,
        val label: WidgetHolidayLabel,
    ) : ScheduleWidgetSubtitle

    data class TermStartMissing(override val dayOfWeek: Int) : ScheduleWidgetSubtitle

    data class BeforeTermStart(override val dayOfWeek: Int) : ScheduleWidgetSubtitle

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

internal sealed interface NextCourseEmptyLabel {
    data class Holiday(val label: WidgetHolidayLabel) : NextCourseEmptyLabel

    data object TermStartMissing : NextCourseEmptyLabel

    data class BeforeTermStart(val termStartDate: LocalDate?) : NextCourseEmptyLabel

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

internal sealed interface NextCourseDayHeader {
    val tomorrow: Boolean

    data class Plain(override val tomorrow: Boolean) : NextCourseDayHeader

    data class Holiday(
        override val tomorrow: Boolean,
        val label: WidgetHolidayLabel,
    ) : NextCourseDayHeader

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

@StringRes
internal fun widgetCourseStatusRes(status: CourseStatus, exam: Boolean): Int = when (status) {
    CourseStatus.Live -> if (exam) R.string.widget_status_exam_live else R.string.widget_status_live
    CourseStatus.Soon -> R.string.widget_status_upcoming
    CourseStatus.Upcoming -> R.string.widget_status_not_started
    CourseStatus.Past -> R.string.widget_status_finished
}

internal fun Context.widgetNodeRangeText(startNode: Int, endNode: Int): String =
    getString(R.string.widget_node_range, startNode, endNode)

/** Numeric period range after a slot name; empty without a matching timing slot. */
internal fun widgetNodeNumbersText(profile: TermTimingProfile?, startNode: Int, endNode: Int): String =
    if (profile?.slotsCovering(startNode, endNode).isNullOrEmpty()) "" else "$startNode-$endNode"

/**
 * Prefer single-slot labels, otherwise ranges. Store plain text and apply styling during
 * binding for reliable equality and revisions.
 */
internal fun widgetSlotCellText(slotLabel: String?, nodeNumbers: String, fallback: String): CharSequence {
    if (nodeNumbers.isBlank()) return fallback
    if (slotLabel.isNullOrBlank()) return nodeNumbers
    val text = SpannableStringBuilder(slotLabel).append(' ')
    val start = text.length
    text.append("(").append(nodeNumbers).append(")")
    text.setSpan(RelativeSizeSpan(WIDGET_NODE_NUMBERS_SCALE), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    return text
}

private const val WIDGET_NODE_NUMBERS_SCALE = 0.78f

internal fun Context.widgetCourseTitleText(title: String, exam: Boolean): String =
    if (exam) getString(R.string.widget_exam_title, title) else title
