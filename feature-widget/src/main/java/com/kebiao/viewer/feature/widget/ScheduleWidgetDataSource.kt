package com.kebiao.viewer.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import com.kebiao.viewer.core.data.DataStoreManualCourseRepository
import com.kebiao.viewer.core.data.DataStoreScheduleRepository
import com.kebiao.viewer.core.data.DataStoreUserPreferencesRepository
import com.kebiao.viewer.core.data.reminder.DataStoreReminderRepository
import com.kebiao.viewer.core.data.term.DataStoreTermProfileRepository
import com.kebiao.viewer.core.data.widget.DataStoreWidgetPreferencesRepository
import com.kebiao.viewer.core.kernel.model.CourseItem
import com.kebiao.viewer.core.kernel.model.TermTimingProfile
import com.kebiao.viewer.core.kernel.model.coursesOfDay
import com.kebiao.viewer.core.kernel.model.resolveTemporaryScheduleDayOfWeek
import com.kebiao.viewer.core.kernel.time.BeijingTime
import com.kebiao.viewer.core.reminder.model.ReminderRule
import com.kebiao.viewer.core.reminder.model.ReminderScopeType
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
    val targetDate: LocalDate,
    val weekdayLabel: String,
    val sourceDayOfWeek: Int,
    val rows: List<ScheduleWidgetCourseRow>,
)

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
        val zone = BeijingTime.resolveZone(userPrefs.timeZoneId)
        BeijingTime.setForcedNow(userPrefs.debugForcedDateTime)
        val today = BeijingTime.todayIn(zone)
        val offset = if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            widgetPreferencesRepository.widgetDayOffsetFlow.first()
        } else {
            widgetPreferencesRepository.widgetDayOffset(appWidgetId)
        }
        val targetDate = today.plusDays(offset.toLong())
        val termStart = activeTermStartDate(termProfileRepository, timingProfile)
            ?: userPrefs.termStartDate
        val weekIndex = resolveWeekIndex(targetDate, termStart)
        val dayOfWeek = resolveTemporaryScheduleDayOfWeek(
            date = targetDate,
            overrides = userPrefs.temporaryScheduleOverrides,
        )

        val importedCourses = scheduleRepository.scheduleFlow.first()
            ?.coursesOfDay(dayOfWeek)
            .orEmpty()
        val manualCourses = manualCourseRepository.manualCoursesFlow.first()
            .filter { it.time.dayOfWeek == dayOfWeek }
        val reminderRules = reminderRepository.reminderRulesFlow.first()
        val rows = (importedCourses + manualCourses)
            .filter { it.activeOnWeek(weekIndex) }
            .sortedBy { it.time.startNode }
            .map { it.toRow(timingProfile, reminderRules) }

        return ScheduleWidgetDayData(
            offset = offset,
            targetDate = targetDate,
            weekdayLabel = weekdayLabel(targetDate),
            sourceDayOfWeek = dayOfWeek,
            rows = rows,
        )
    }

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
            title = title,
            subtitle = subtitle,
            hasReminder = reminderRules.any { it.matches(this) },
        )
    }

    private fun ReminderRule.matches(course: CourseItem): Boolean = when (scopeType) {
        ReminderScopeType.SingleCourse -> courseId == course.id
        ReminderScopeType.TimeSlot ->
            startNode == course.time.startNode && endNode == course.time.endNode
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
