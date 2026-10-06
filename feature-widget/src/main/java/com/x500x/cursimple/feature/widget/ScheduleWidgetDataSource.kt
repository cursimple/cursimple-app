package com.x500x.cursimple.feature.widget

import com.x500x.cursimple.core.kernel.mood.dayMoodLine
import com.x500x.cursimple.core.kernel.mood.DayMood
import com.x500x.cursimple.core.kernel.model.resolveScheduleDay
import android.content.Context
import com.x500x.cursimple.core.data.DataStoreManualCourseRepository
import com.x500x.cursimple.core.data.DataStoreScheduleRepository
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.reminder.DataStoreReminderRepository
import com.x500x.cursimple.core.data.term.DataStoreTermProfileRepository
import com.x500x.cursimple.core.data.widget.DataStoreWidgetPreferencesRepository
import com.x500x.cursimple.core.data.widget.WidgetThemePreferences
import com.x500x.cursimple.core.data.widget.courseSlotLabelText
import com.x500x.cursimple.core.data.widget.resolveAccent
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.allCoursesWith
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.reminder.model.ReminderRule
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime

internal data class ScheduleWidgetCourseRow(
    val id: String,
    val nodeRange: String,
    val timeRange: String,
    val title: String,
    val subtitle: String,
    val hasReminder: Boolean,
    val onHoliday: Boolean = false,
    val status: CourseStatus? = null,
    val isExam: Boolean = false,
    val slotLabel: String? = null,
    /** Optional period range from a matching timing slot; otherwise use [nodeRange]. */
    val nodeNumbers: String = "",
) {
    val stableId: Long = id.hashCode().toLong()
}

internal data class ScheduleWidgetDayData(
    val offset: Int,
    val manualOffset: Int,
    val targetDate: LocalDate,
    val sourceDate: LocalDate,
    val rows: List<ScheduleWidgetCourseRow>,
    val widgetTheme: WidgetThemePreferences = WidgetThemePreferences(),
    val beforeTermStart: Boolean = false,
    val termStartMissing: Boolean = false,
    val termStartDate: LocalDate? = null,
    val holidayLabel: WidgetHolidayLabel? = null,
    val moodLine: String? = null,
) {
    val themeAccent: ThemeAccent = widgetTheme.themeAccent
}

internal object ScheduleWidgetDataSource {
    private val dayCache = WidgetDataCache<ScheduleWidgetDayData>()

    /** Last-read today anchor detects stale rendering after a missed midnight refresh. */
    @Volatile
    var lastRenderedTodayIso: String? = null
        private set

    /** [reuseRecent] shares one read between header and list. */
    suspend fun loadDay(
        context: Context,
        appWidgetId: Int,
        reuseRecent: Boolean = false,
    ): ScheduleWidgetDayData {
        if (reuseRecent) {
            dayCache.get(appWidgetId, System.nanoTime())?.let { return it }
        }
        return loadFreshDay(context, appWidgetId)
            .also { dayCache.put(appWidgetId, System.nanoTime(), it) }
    }

    /** Invalidate short-lived reads after preference or schedule changes. */
    fun invalidate() {
        dayCache.clear()
    }

    private suspend fun loadFreshDay(context: Context, appWidgetId: Int): ScheduleWidgetDayData {
        val appContext = context.widgetLocaleContext()
        val termProfileRepository = DataStoreTermProfileRepository(appContext)
        val scheduleRepository = DataStoreScheduleRepository(appContext, termProfileRepository)
        val manualCourseRepository = DataStoreManualCourseRepository(appContext, termProfileRepository)
        val reminderRepository = DataStoreReminderRepository(appContext)
        val userPreferencesRepository = DataStoreUserPreferencesRepository(appContext)
        val widgetPreferencesRepository = DataStoreWidgetPreferencesRepository(appContext)

        val userPrefs = userPreferencesRepository.preferencesFlow.first()
        val timingProfile = widgetPreferencesRepository.timingProfileFlow.first()
        val widgetTheme = widgetPreferencesRepository.themePreferencesFlow.first()
            .resolveAccent(userPrefs.themeAccent, userPrefs.themeCustomColorArgb)
        val zone = BeijingTime.zone
        BeijingTime.setForcedNow(userPrefs.debugForcedDateTime)
        val today = BeijingTime.todayIn(zone)
        val now = BeijingTime.nowTimeIn(zone)
        lastRenderedTodayIso = today.toString()
        // Day-anchored offsets expire at midnight; zero identifies shared instance state.
        val manualOffset = widgetPreferencesRepository.effectiveWidgetDayOffset(
            appWidgetId = appWidgetId,
            todayIso = today.toString(),
        )
        val termStart = resolveWidgetTermStartDate(
            termProfileRepository = termProfileRepository,
            timingProfile = timingProfile,
            preferenceTermStartDate = userPrefs.termStartDate,
        )
        val sources = DaySources(
            schedule = scheduleRepository.scheduleFlow.first(),
            manualCourses = manualCourseRepository.manualCoursesFlow.first(),
            reminderRules = reminderRepository.reminderRulesFlow.first(),
            temporaryScheduleOverrides = userPrefs.temporaryScheduleOverrides,
            holidayCalendar = userPrefs.holidayCalendar,
        )

        fun dayAt(offset: Int) = loadDate(
            context = appContext,
            targetDate = today.plusDays(offset.toLong()),
            today = today,
            now = now,
            offset = offset,
            manualOffset = manualOffset,
            termStart = termStart,
            timingProfile = timingProfile,
            sources = sources,
            widgetTheme = widgetTheme,
        )

        if (manualOffset != 0) return dayAt(manualOffset).data

        val currentDay = dayAt(0)
        if (!shouldShowNextDayAtNight(now, currentDay.courses, timingProfile)) return currentDay.data
        val nextDay = (1..AUTO_ADVANCE_MAX_DAYS)
            .firstNotNullOfOrNull { offset ->
                dayAt(offset).takeIf { it.rows.isNotEmpty() && !it.onHoliday }
            }
        return (nextDay ?: dayAt(1)).data
    }

    private data class DaySources(
        val schedule: com.x500x.cursimple.core.kernel.model.TermSchedule?,
        val manualCourses: List<CourseItem>,
        val reminderRules: List<ReminderRule>,
        val temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
        val holidayCalendar: HolidayCalendarSettings,
    ) {
        val allCourses: List<CourseItem> = schedule.allCoursesWith(manualCourses)
    }

    private const val AUTO_ADVANCE_MAX_DAYS = 7

    private fun loadDate(
        context: Context,
        targetDate: LocalDate,
        today: LocalDate,
        now: LocalTime,
        offset: Int,
        manualOffset: Int,
        termStart: LocalDate?,
        timingProfile: TermTimingProfile?,
        sources: DaySources,
        widgetTheme: WidgetThemePreferences,
    ): LoadedDay {
        val day = resolveWidgetScheduleDay(
            targetDate = targetDate,
            termStart = termStart,
            temporaryScheduleOverrides = sources.temporaryScheduleOverrides,
            holidayCalendar = sources.holidayCalendar,
        ) { dayOfWeek -> sources.allCourses.filter { it.time.dayOfWeek == dayOfWeek } }
        val rows = day.courses.map {
            it.toRow(
                context = context,
                timingProfile = timingProfile,
                reminderRules = sources.reminderRules,
                onHoliday = day.onHoliday,
                status = widgetRowStatus(
                    course = it,
                    today = today,
                    targetDate = targetDate,
                    now = now,
                    timingProfile = timingProfile,
                    onHoliday = day.onHoliday,
                ),
            )
        }

        val weekKnown = day.weekIndex != null && !isBeforeTermStart(day.weekIndex)
        val moodLine = if (rows.isEmpty() && weekKnown && targetDate == today.plusDays(1) && day.holidayLabel == null) {
            context.dayMoodLine(today, DayMood.TomorrowFree)
        } else if (targetDate == today && rows.isEmpty() && weekKnown) {
            context.widgetTodayMood(
                today = today,
                now = now,
                holidayLabel = day.holidayLabel,
                tomorrowHoliday = resolveScheduleDay(
                    today.plusDays(1),
                    sources.temporaryScheduleOverrides,
                    sources.holidayCalendar,
                ).isHoliday,
                totalCourses = 0,
            )
        } else {
            null
        }
        return LoadedDay(
            data = ScheduleWidgetDayData(
                offset = offset,
                manualOffset = manualOffset,
                targetDate = targetDate,
                sourceDate = day.sourceDate,
                rows = rows,
                widgetTheme = widgetTheme,
                beforeTermStart = isBeforeTermStart(day.weekIndex),
                termStartMissing = day.weekIndex == null,
                termStartDate = termStart,
                holidayLabel = day.holidayLabel,
                moodLine = moodLine,
            ),
            courses = day.courses,
            onHoliday = day.onHoliday,
        )
    }

    private data class LoadedDay(
        val data: ScheduleWidgetDayData,
        val courses: List<CourseItem>,
        val onHoliday: Boolean,
    ) {
        val rows: List<ScheduleWidgetCourseRow> get() = data.rows
    }

    private fun CourseItem.toRow(
        context: Context,
        timingProfile: TermTimingProfile?,
        reminderRules: List<ReminderRule>,
        onHoliday: Boolean,
        status: CourseStatus?,
    ): ScheduleWidgetCourseRow {
        val nodeRange = context.widgetNodeRangeText(time.startNode, time.endNode)
        val timeRange = timingProfile?.courseClockRange(this) ?: nodeRange
        val subtitle = listOf(location, teacher)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
            .ifBlank { context.getString(R.string.widget_course_subtitle_placeholder) }
        return ScheduleWidgetCourseRow(
            id = id,
            nodeRange = nodeRange,
            timeRange = timeRange,
            title = context.widgetCourseTitleText(title, category == CourseCategory.Exam),
            subtitle = subtitle,
            hasReminder = reminderRules.any { it.matchesWidgetCourse(this, timingProfile) },
            onHoliday = onHoliday,
            status = status,
            isExam = category == CourseCategory.Exam,
            slotLabel = context.courseSlotLabelText(timingProfile, time.startNode, time.endNode),
            nodeNumbers = widgetNodeNumbersText(timingProfile, time.startNode, time.endNode),
        )
    }
}

/** Widget today honors the debug clock so offset anchors and displayed dates agree. */
internal suspend fun widgetTodayIso(context: Context): String {
    val prefs = DataStoreUserPreferencesRepository(context.applicationContext).preferencesFlow.first()
    BeijingTime.setForcedNow(prefs.debugForcedDateTime)
    return BeijingTime.todayIn(BeijingTime.zone).toString()
}

/** Class status requires today, a working date and known timing boundaries. */
internal fun widgetRowStatus(
    course: CourseItem,
    today: LocalDate,
    targetDate: LocalDate,
    now: LocalTime,
    timingProfile: TermTimingProfile?,
    onHoliday: Boolean,
): CourseStatus? {
    if (onHoliday || timingProfile == null || targetDate != today) return null
    return resolveCourseStatus(
        course = course,
        today = today,
        targetDate = targetDate,
        now = now,
        timingProfile = timingProfile,
    )
}

/** Shared term-date resolution keeps widget teaching weeks consistent. */
internal suspend fun resolveWidgetTermStartDate(
    termProfileRepository: DataStoreTermProfileRepository,
    timingProfile: TermTimingProfile?,
    preferenceTermStartDate: LocalDate?,
): LocalDate? {
    val activeTermId = termProfileRepository.activeTermId()
    val activeTermStartIso = termProfileRepository.termsFlow.first()
        .firstOrNull { it.id == activeTermId }
        ?.termStartDate
    return selectTermStartDate(
        activeTermStartIso = activeTermStartIso,
        timingProfileTermStartIso = timingProfile?.termStartDate,
        preferenceTermStartDate = preferenceTermStartDate,
    )
}

internal fun TermTimingProfile.withTermStartDate(termStartDate: LocalDate?): TermTimingProfile {
    val iso = termStartDate?.toString() ?: return this
    return if (iso == this.termStartDate) this else copy(termStartDate = iso)
}

internal fun selectTermStartDate(
    activeTermStartIso: String?,
    timingProfileTermStartIso: String?,
    preferenceTermStartDate: LocalDate?,
): LocalDate? =
    activeTermStartIso?.let(::parseIsoDate)
        ?: timingProfileTermStartIso?.let(::parseIsoDate)
        ?: preferenceTermStartDate

private fun parseIsoDate(value: String): LocalDate? =
    runCatching { LocalDate.parse(value) }.getOrNull()
