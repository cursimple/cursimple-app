package com.x500x.cursimple.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import com.x500x.cursimple.core.data.DataStoreManualCourseRepository
import com.x500x.cursimple.core.data.DataStoreScheduleRepository
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.reminder.DataStoreReminderRepository
import com.x500x.cursimple.core.data.term.DataStoreTermProfileRepository
import com.x500x.cursimple.core.data.widget.DataStoreWidgetPreferencesRepository
import com.x500x.cursimple.core.data.widget.WidgetThemePreferences
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.coursesOfDay
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.reminder.model.ReminderRule
import kotlinx.coroutines.flow.first
import java.time.LocalDate

internal data class ScheduleWidgetCourseRow(
    val id: String,
    val nodeRange: String,
    val timeRange: String,
    val title: String,
    val subtitle: String,
    val hasReminder: Boolean,
) {
    val stableId: Long = id.hashCode().toLong()
}

internal data class ScheduleWidgetDayData(
    val offset: Int,
    val manualOffset: Int,
    val targetDate: LocalDate,
    val weekdayLabel: String,
    val sourceDate: LocalDate,
    val rows: List<ScheduleWidgetCourseRow>,
    val widgetTheme: WidgetThemePreferences = WidgetThemePreferences(),
    val beforeTermStart: Boolean = false,
    val termStartMissing: Boolean = false,
    val termStartDate: LocalDate? = null,
    val holidayLabel: String? = null,
) {
    val themeAccent: ThemeAccent = widgetTheme.themeAccent
}

internal object ScheduleWidgetDataSource {
    private val dayCache = WidgetDataCache<ScheduleWidgetDayData>()

    /** [reuseRecent] 为 true 时优先复用刚读出的当次结果，让列表跟着头部走同一份数据。 */
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

    private suspend fun loadFreshDay(context: Context, appWidgetId: Int): ScheduleWidgetDayData {
        val appContext = context.applicationContext
        val termProfileRepository = DataStoreTermProfileRepository(appContext)
        val scheduleRepository = DataStoreScheduleRepository(appContext, termProfileRepository)
        val manualCourseRepository = DataStoreManualCourseRepository(appContext, termProfileRepository)
        val reminderRepository = DataStoreReminderRepository(appContext)
        val userPreferencesRepository = DataStoreUserPreferencesRepository(appContext)
        val widgetPreferencesRepository = DataStoreWidgetPreferencesRepository(appContext)

        val userPrefs = userPreferencesRepository.preferencesFlow.first()
        val timingProfile = widgetPreferencesRepository.timingProfileFlow.first()
        val widgetTheme = widgetPreferencesRepository.themePreferencesFlow.first()
        val zone = BeijingTime.zone
        BeijingTime.setForcedNow(userPrefs.debugForcedDateTime)
        val today = BeijingTime.todayIn(zone)
        val manualOffset = if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            widgetPreferencesRepository.widgetDayOffsetFlow.first()
        } else {
            widgetPreferencesRepository.widgetDayOffset(appWidgetId)
        }
        val termStart = resolveWidgetTermStartDate(
            termProfileRepository = termProfileRepository,
            timingProfile = timingProfile,
            preferenceTermStartDate = userPrefs.termStartDate,
        )

        val currentDay = loadDate(
            targetDate = today,
            offset = 0,
            manualOffset = manualOffset,
            termStart = termStart,
            timingProfile = timingProfile,
            scheduleRepository = scheduleRepository,
            manualCourseRepository = manualCourseRepository,
            reminderRepository = reminderRepository,
            temporaryScheduleOverrides = userPrefs.temporaryScheduleOverrides,
            holidayCalendar = userPrefs.holidayCalendar,
            widgetTheme = widgetTheme,
        )
        if (manualOffset == 0 && shouldShowNextDayAtNight(BeijingTime.nowTimeIn(zone), currentDay.courses, timingProfile)) {
            return loadDate(
                targetDate = today.plusDays(1),
                offset = 1,
                manualOffset = manualOffset,
                termStart = termStart,
                timingProfile = timingProfile,
                scheduleRepository = scheduleRepository,
                manualCourseRepository = manualCourseRepository,
                reminderRepository = reminderRepository,
                temporaryScheduleOverrides = userPrefs.temporaryScheduleOverrides,
                holidayCalendar = userPrefs.holidayCalendar,
                widgetTheme = widgetTheme,
            ).data
        }
        if (manualOffset == 0) return currentDay.data

        return loadDate(
            targetDate = today.plusDays(manualOffset.toLong()),
            offset = manualOffset,
            manualOffset = manualOffset,
            termStart = termStart,
            timingProfile = timingProfile,
            scheduleRepository = scheduleRepository,
            manualCourseRepository = manualCourseRepository,
            reminderRepository = reminderRepository,
            temporaryScheduleOverrides = userPrefs.temporaryScheduleOverrides,
            holidayCalendar = userPrefs.holidayCalendar,
            widgetTheme = widgetTheme,
        ).data
    }

    private suspend fun loadDate(
        targetDate: LocalDate,
        offset: Int,
        manualOffset: Int,
        termStart: LocalDate?,
        timingProfile: TermTimingProfile?,
        scheduleRepository: DataStoreScheduleRepository,
        manualCourseRepository: DataStoreManualCourseRepository,
        reminderRepository: DataStoreReminderRepository,
        temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
        holidayCalendar: HolidayCalendarSettings,
        widgetTheme: WidgetThemePreferences,
    ): LoadedDay {
        val schedule = scheduleRepository.scheduleFlow.first()
        val manualCourses = manualCourseRepository.manualCoursesFlow.first()
        val reminderRules = reminderRepository.reminderRulesFlow.first()

        val day = resolveWidgetScheduleDay(
            targetDate = targetDate,
            termStart = termStart,
            temporaryScheduleOverrides = temporaryScheduleOverrides,
            holidayCalendar = holidayCalendar,
        ) { dayOfWeek ->
            schedule?.coursesOfDay(dayOfWeek).orEmpty() +
                manualCourses.filter { it.time.dayOfWeek == dayOfWeek }
        }
        val rows = day.courses.map { it.toRow(timingProfile, reminderRules) }

        return LoadedDay(
            data = ScheduleWidgetDayData(
                offset = offset,
                manualOffset = manualOffset,
                targetDate = targetDate,
                weekdayLabel = weekdayLabel(targetDate),
                sourceDate = day.sourceDate,
                rows = rows,
                widgetTheme = widgetTheme,
                beforeTermStart = isBeforeTermStart(day.weekIndex),
                termStartMissing = day.weekIndex == null,
                termStartDate = termStart,
                holidayLabel = day.holidayLabel,
            ),
            courses = day.courses,
        )
    }

    private data class LoadedDay(
        val data: ScheduleWidgetDayData,
        val courses: List<CourseItem>,
    )

    private fun CourseItem.toRow(
        timingProfile: TermTimingProfile?,
        reminderRules: List<ReminderRule>,
    ): ScheduleWidgetCourseRow {
        val nodeRange = "${time.startNode}-${time.endNode}节"
        val timeRange = timingProfile?.courseClockRange(this) ?: nodeRange
        val subtitle = listOf(location, teacher)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
            .ifBlank { "待定" }
        return ScheduleWidgetCourseRow(
            id = id,
            nodeRange = nodeRange,
            timeRange = timeRange,
            title = if (category == CourseCategory.Exam) "考试 · $title" else title,
            subtitle = subtitle,
            hasReminder = reminderRules.any { it.matchesWidgetCourse(this, timingProfile) },
        )
    }

    private fun weekdayLabel(date: LocalDate): String = when (date.dayOfWeek.value) {
        1 -> "星期一"
        2 -> "星期二"
        3 -> "星期三"
        4 -> "星期四"
        5 -> "星期五"
        6 -> "星期六"
        7 -> "星期日"
        else -> ""
    }
}

/** 所有小组件共用的开学日期来源，保证不同小组件算出同一个教学周。 */
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

/** 把计时档案的开学日期换成统一解析出的日期；日期为空或本就一致时返回原档案。 */
internal fun TermTimingProfile.withTermStartDate(termStartDate: LocalDate?): TermTimingProfile {
    val iso = termStartDate?.toString() ?: return this
    return if (iso == this.termStartDate) this else copy(termStartDate = iso)
}

/** 当前学期档案 → 小组件计时档案 → 用户偏好，取第一个能解析出日期的来源。 */
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
