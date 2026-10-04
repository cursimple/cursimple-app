package com.x500x.cursimple.feature.schedule

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.x500x.cursimple.feature.schedule.R
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.data.ScheduleDisplayPreferences
import com.x500x.cursimple.core.kernel.model.ScheduledCourseOccurrence
import com.x500x.cursimple.core.kernel.model.scheduledCourseOccurrencesOn
import com.x500x.cursimple.core.kernel.model.nodeRange
import com.x500x.cursimple.core.kernel.model.resolveScheduleDay
import com.x500x.cursimple.core.kernel.model.holidayNameResOfName
import com.x500x.cursimple.core.kernel.mood.dayMood
import com.x500x.cursimple.core.kernel.mood.dayMoodLine
import androidx.compose.ui.platform.LocalContext
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

internal typealias TodayOverviewItem = ScheduledCourseOccurrence
internal data class TodayCourseConflict(val first: TodayOverviewItem, val second: TodayOverviewItem)

internal data class TodayOverviewState(
    val date: LocalDate,
    val current: TodayOverviewItem?,
    val next: TodayOverviewItem?,
    val remaining: Int,
    val total: Int,
    val holiday: Boolean,
    /** 认得出是哪个法定假日时的名称资源，用来挑那个节的问候。 */
    val holidayNameRes: Int? = null,
    val tomorrowHoliday: Boolean = false,
    val currentProgress: Float = 0f,
    val courses: List<TodayOverviewItem> = emptyList(),
    val conflicts: List<TodayCourseConflict> = emptyList(),
    val minutesUntilNext: Long? = null,
    val currentMinutesLeft: Long? = null,
    val weekKnown: Boolean = true,
    val now: LocalTime = LocalTime.MIDNIGHT,
) {
    val unknownTimeCount: Int get() = courses.count { !it.hasTime }
}

internal fun buildTodayOverview(
    now: LocalDateTime,
    courses: List<CourseItem>,
    timingProfile: TermTimingProfile?,
    termStartDate: LocalDate?,
    overrides: List<TemporaryScheduleOverride>,
    holidayCalendar: HolidayCalendarSettings,
): TodayOverviewState {
    val date = now.toLocalDate()
    val coursesToday = scheduledCourseOccurrencesOn(date, courses, timingProfile, termStartDate, overrides, holidayCalendar)
    val timed = coursesToday.filter { it.hasTime }
    val current = timed.firstOrNull { !now.toLocalTime().isBefore(it.start) && now.toLocalTime().isBefore(it.end) }
    val next = timed.firstOrNull { it.start!!.isAfter(now.toLocalTime()) }
    val progress = current?.let {
        val totalSeconds = java.time.Duration.between(it.start, it.end).seconds.coerceAtLeast(1L)
        val passedSeconds = java.time.Duration.between(it.start, now.toLocalTime()).seconds
        (passedSeconds.toFloat() / totalSeconds).coerceIn(0f, 1f)
    } ?: 0f
    val conflicts = buildList {
        // 已经应用当天的周次/调课规则，不再用原始星期与周次交集过滤移入课程。
        for (i in coursesToday.indices) for (j in i + 1 until coursesToday.size) {
            val a = coursesToday[i]
            val b = coursesToday[j]
            if (a.course.id == b.course.id) continue
            val overlaps = if (a.hasTime && b.hasTime) {
                a.start!!.isBefore(b.end) && b.start!!.isBefore(a.end)
            } else {
                val left = a.course.time.nodeRange()
                val right = b.course.time.nodeRange()
                left.first <= right.last && right.first <= left.last
            }
            if (overlaps) add(TodayCourseConflict(a, b))
        }
    }
    fun minutesLeft(until: LocalTime?) = until?.let {
        (Duration.between(now.toLocalTime(), it).seconds.coerceAtLeast(0L) + 59L) / 60L
    }
    val day = resolveScheduleDay(date, overrides, holidayCalendar)
    return TodayOverviewState(
        date = date,
        current = current,
        next = next,
        remaining = timed.count { it.start!!.isAfter(now.toLocalTime()) },
        total = coursesToday.size,
        holiday = day.isHoliday,
        holidayNameRes = day.holidayNameRes ?: day.holidayName?.let(::holidayNameResOfName),
        tomorrowHoliday = resolveScheduleDay(date.plusDays(1), overrides, holidayCalendar).isHoliday,
        currentProgress = progress,
        courses = coursesToday,
        conflicts = conflicts,
        minutesUntilNext = minutesLeft(next?.start),
        currentMinutesLeft = minutesLeft(current?.end),
        weekKnown = termStartDate != null,
        now = now.toLocalTime(),
    )
}

private val overviewClock = DateTimeFormatter.ofPattern("HH:mm")
internal fun clockText(clock: LocalTime?): String = clock?.format(overviewClock).orEmpty()

@Composable
internal fun occurrenceInfo(occurrence: ScheduledCourseOccurrence, display: ScheduleDisplayPreferences): String = listOfNotNull(
    if (occurrence.hasTime) "${clockText(occurrence.start)}–${clockText(occurrence.end)}" else stringResource(R.string.schedule_today_time_missing),
    occurrence.location.takeIf { display.locationVisible && it.isNotBlank() },
    occurrence.course.teacher.takeIf { display.teacherVisible && it.isNotBlank() },
).joinToString(" · ")

/**
 * 没课、放假或课都上完时卡片上的那句话；还有课、或者还不知道今天第几周（没导课）时不说。
 */
@Composable
internal fun todayMoodLine(state: TodayOverviewState): String? {
    if (!state.weekKnown) return null
    val mood = dayMood(
        now = LocalDateTime.of(state.date, state.now),
        isHoliday = state.holiday,
        holidayNameRes = state.holidayNameRes,
        tomorrowHoliday = state.tomorrowHoliday,
        totalCourses = state.total,
        hasMoreToday = state.current != null || state.next != null || state.unknownTimeCount > 0,
        timeSensitive = true,
    ) ?: return null
    return LocalContext.current.dayMoodLine(state.date, mood)
}
