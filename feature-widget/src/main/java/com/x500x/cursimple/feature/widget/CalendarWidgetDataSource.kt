package com.x500x.cursimple.feature.widget

import android.content.Context
import com.x500x.cursimple.core.data.DataStoreManualCourseRepository
import com.x500x.cursimple.core.data.DataStoreScheduleRepository
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.event.DataStoreScheduleEventRepository
import com.x500x.cursimple.core.data.term.DataStoreTermProfileRepository
import com.x500x.cursimple.core.data.widget.DataStoreWidgetPreferencesRepository
import com.x500x.cursimple.core.data.widget.WidgetThemePreferences
import com.x500x.cursimple.core.data.widget.classSlotLabelOfIndex
import com.x500x.cursimple.core.data.widget.classSlotLabelText
import com.x500x.cursimple.core.data.widget.resolveAccent
import com.x500x.cursimple.core.kernel.model.allCoursesWith
import com.x500x.cursimple.core.kernel.model.occurrencesOn
import com.x500x.cursimple.core.kernel.model.resolveScheduleDay
import com.x500x.cursimple.core.kernel.time.BeijingTime
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth

internal data class CalendarWidgetData(
    val mode: CalendarWidgetMode,
    val offset: Int,
    val today: LocalDate,
    val anchor: LocalDate,
    val weekIndex: Int?,
    val termStartMissing: Boolean,
    val week: CalendarWeekData?,
    val month: CalendarMonthData?,
    val widgetTheme: WidgetThemePreferences,
)

internal object CalendarWidgetDataSource {

    suspend fun load(context: Context, appWidgetId: Int): CalendarWidgetData {
        val appContext = context.applicationContext
        val termProfiles = DataStoreTermProfileRepository(appContext)
        val userPrefs = DataStoreUserPreferencesRepository(appContext).preferencesFlow.first()
        val widgetPrefs = DataStoreWidgetPreferencesRepository(appContext)
        val timingProfile = widgetPrefs.timingProfileFlow.first()
        val widgetTheme = widgetPrefs.themePreferencesFlow.first()
            .resolveAccent(userPrefs.themeAccent, userPrefs.themeCustomColorArgb)
        BeijingTime.setForcedNow(userPrefs.debugForcedDateTime)
        val zone = BeijingTime.zone
        val today = BeijingTime.todayIn(zone)
        val termStart = resolveWidgetTermStartDate(termProfiles, timingProfile, userPrefs.termStartDate)
        val schedule = DataStoreScheduleRepository(appContext, termProfiles).scheduleFlow.first()
        val manual = DataStoreManualCourseRepository(appContext, termProfiles).manualCoursesFlow.first()
        val allCourses = schedule.allCoursesWith(manual)
        val events = DataStoreScheduleEventRepository(appContext).eventsFlow.first()

        fun dayOf(date: LocalDate): CalendarDay {
            val resolved = resolveWidgetScheduleDay(
                targetDate = date,
                termStart = termStart,
                temporaryScheduleOverrides = userPrefs.temporaryScheduleOverrides,
                holidayCalendar = userPrefs.holidayCalendar,
            ) { dayOfWeek -> allCourses.filter { it.time.dayOfWeek == dayOfWeek } }
            val makeUp = resolveScheduleDay(date, userPrefs.temporaryScheduleOverrides, userPrefs.holidayCalendar).isMakeUpWorkday
            return CalendarDay(
                date = date,
                isToday = date == today,
                onHoliday = resolved.onHoliday,
                makeUpWorkday = makeUp,
                courses = resolved.courses,
                eventCount = events.occurrencesOn(date).size,
            )
        }

        val mode = CalendarWidgetState.mode(appContext, appWidgetId)
        val offset = CalendarWidgetState.offset(appContext, appWidgetId, today.toString())
        return when (mode) {
            CalendarWidgetMode.Week -> {
                val weekStart = weekStartOf(today).plusWeeks(offset.toLong())
                val allDays = (0L until 7L).map { dayOf(weekStart.plusDays(it)) }
                val days = visibleWeekDays(allDays, userPrefs.scheduleDisplay.weekendVisible)
                CalendarWidgetData(
                    mode = mode,
                    offset = offset,
                    today = today,
                    anchor = weekStart,
                    weekIndex = resolveWeekIndex(weekStart, termStart),
                    termStartMissing = termStart == null,
                    week = CalendarWeekData(
                        days = days,
                        blocks = days.map(::calendarBlocksOf),
                        // Use the locale-wrapped Context, not applicationContext.
                        rows = calendarRows(
                            profileSlots = timingProfile?.slotTimes.orEmpty(),
                            days = days,
                            labelOf = { slot, index -> context.classSlotLabelText(slot, index) },
                            fallbackLabel = { node -> context.classSlotLabelOfIndex(node) },
                        ),
                    ),
                    month = null,
                    widgetTheme = widgetTheme,
                )
            }
            CalendarWidgetMode.Month -> {
                val month = YearMonth.from(today).plusMonths(offset.toLong())
                CalendarWidgetData(
                    mode = mode,
                    offset = offset,
                    today = today,
                    anchor = month.atDay(1),
                    weekIndex = resolveWeekIndex(month.atDay(1), termStart),
                    termStartMissing = termStart == null,
                    week = null,
                    month = CalendarMonthData(month, monthGridDates(month).map(::dayOf)),
                    widgetTheme = widgetTheme,
                )
            }
        }
    }
}
