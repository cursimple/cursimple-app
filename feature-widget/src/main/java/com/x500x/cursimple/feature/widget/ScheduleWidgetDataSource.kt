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
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.coursesOfDay
import com.x500x.cursimple.core.kernel.model.filterTemporaryCancelledCourses
import com.x500x.cursimple.core.kernel.model.visibleScheduleCourses
import com.x500x.cursimple.core.kernel.model.resolveTemporaryScheduleSourceDate
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.reminder.model.ReminderRule
import com.x500x.cursimple.core.reminder.model.ReminderScopeType
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
) {
    val themeAccent: ThemeAccent = widgetTheme.themeAccent
}

internal object ScheduleWidgetDataSource {
    suspend fun loadDay(context: Context, appWidgetId: Int): ScheduleWidgetDayData {
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
        val termStart = activeTermStartDate(termProfileRepository, timingProfile)
            ?: userPrefs.termStartDate

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
        temporaryScheduleOverrides: List<com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride>,
        widgetTheme: WidgetThemePreferences,
    ): LoadedDay {
        val sourceDate = resolveTemporaryScheduleSourceDate(
            date = targetDate,
            overrides = temporaryScheduleOverrides,
        )
        val weekIndex = resolveWeekIndex(sourceDate, termStart)
        val dayOfWeek = sourceDate.dayOfWeek.value

        val importedCourses = scheduleRepository.scheduleFlow.first()
            ?.coursesOfDay(dayOfWeek)
            .orEmpty()
        val manualCourses = manualCourseRepository.manualCoursesFlow.first()
            .filter { it.time.dayOfWeek == dayOfWeek }
        val reminderRules = reminderRepository.reminderRulesFlow.first()
        val courses = filterTemporaryCancelledCourses(
            date = targetDate,
            courses = importedCourses + manualCourses,
            overrides = temporaryScheduleOverrides,
        )
            .visibleScheduleCourses()
            .filter { it.activeOnWeek(weekIndex) }
            .sortedBy { it.time.startNode }
        val rows = courses.map { it.toRow(timingProfile, reminderRules) }

        return LoadedDay(
            data = ScheduleWidgetDayData(
                offset = offset,
                manualOffset = manualOffset,
                targetDate = targetDate,
                weekdayLabel = weekdayLabel(targetDate),
                sourceDate = sourceDate,
                rows = rows,
                widgetTheme = widgetTheme,
            ),
            courses = courses,
        )
    }

    private data class LoadedDay(
        val data: ScheduleWidgetDayData,
        val courses: List<CourseItem>,
    )

    private suspend fun activeTermStartDate(
        termProfileRepository: DataStoreTermProfileRepository,
        timingProfile: TermTimingProfile?,
    ): LocalDate? {
        val activeTermId = termProfileRepository.activeTermId()
        val activeTermStart = termProfileRepository.termsFlow.first()
            .firstOrNull { it.id == activeTermId }
            ?.termStartDate
            ?.let(::parseIsoDate)
        return activeTermStart ?: timingProfile?.termStartDate?.let(::parseIsoDate)
    }

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
            hasReminder = reminderRules.any { it.matches(this) },
        )
    }

    private fun ReminderRule.matches(course: CourseItem): Boolean = enabled && when (scopeType) {
        ReminderScopeType.SingleCourse -> courseId == course.id
        ReminderScopeType.TimeSlot ->
            startNode == course.time.startNode && endNode == course.time.endNode
        ReminderScopeType.Exam ->
            course.category == CourseCategory.Exam && course.id !in mutedCourseIds
        ReminderScopeType.FirstCourseOfPeriod -> false
        ReminderScopeType.LabelRule -> labelActions.any {
            it.action == com.x500x.cursimple.core.reminder.model.ReminderLabelActionType.Remind &&
                it.slotLabel == course.slotLabelOverride
        }
    }

    private fun parseIsoDate(value: String): LocalDate? =
        runCatching { LocalDate.parse(value) }.getOrNull()

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
