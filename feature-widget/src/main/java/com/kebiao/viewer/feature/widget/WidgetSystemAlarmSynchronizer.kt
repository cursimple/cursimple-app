package com.kebiao.viewer.feature.widget

import android.content.Context
import com.kebiao.viewer.core.data.DataStoreManualCourseRepository
import com.kebiao.viewer.core.data.DataStoreScheduleRepository
import com.kebiao.viewer.core.data.DataStoreUserPreferencesRepository
import com.kebiao.viewer.core.data.reminder.DataStoreReminderRepository
import com.kebiao.viewer.core.data.term.DataStoreTermProfileRepository
import com.kebiao.viewer.core.data.widget.DataStoreWidgetPreferencesRepository
import com.kebiao.viewer.core.kernel.model.CourseItem
import com.kebiao.viewer.core.kernel.model.DailySchedule
import com.kebiao.viewer.core.kernel.model.TermSchedule
import com.kebiao.viewer.core.reminder.ReminderCoordinator
import com.kebiao.viewer.core.reminder.ReminderSyncWindows
import com.kebiao.viewer.core.reminder.model.ReminderSyncReason
import com.kebiao.viewer.core.reminder.model.SystemAlarmSyncSummary
import kotlinx.coroutines.flow.first
import java.time.OffsetDateTime

internal object WidgetSystemAlarmSynchronizer {
    suspend fun reconcileToday(context: Context): SystemAlarmSyncSummary {
        val appContext = context.applicationContext
        val termProfileRepository = DataStoreTermProfileRepository(appContext)
        val scheduleRepository = DataStoreScheduleRepository(appContext, termProfileRepository)
        val manualCourseRepository = DataStoreManualCourseRepository(appContext, termProfileRepository)
        val userPreferencesRepository = DataStoreUserPreferencesRepository(appContext)
        val reminderRepository = DataStoreReminderRepository(appContext)
        val widgetPreferencesRepository = DataStoreWidgetPreferencesRepository(appContext)

        val timingProfile = widgetPreferencesRepository.timingProfileFlow.first()
            ?: return emptySystemAlarmSyncSummary()
        val pluginId = scheduleRepository.lastPluginIdFlow.first()

        val schedule = mergeManualCoursesForReminders(
            schedule = scheduleRepository.scheduleFlow.first(),
            manualCourses = manualCourseRepository.manualCoursesFlow.first(),
        )
        val coordinator = ReminderCoordinator(
            context = appContext,
            repository = reminderRepository,
            temporaryScheduleOverridesProvider = {
                userPreferencesRepository.preferencesFlow.first().temporaryScheduleOverrides
            },
        )
        if (schedule == null) {
            coordinator.clearSystemAlarmRecords()
            return emptySystemAlarmSyncSummary()
        }
        return coordinator.syncSystemClockAlarmsForWindow(
            pluginId = pluginId,
            schedule = schedule,
            timingProfile = timingProfile,
            window = ReminderSyncWindows.todayFromNow(timingProfile),
            reason = ReminderSyncReason.WidgetRefresh,
        )
    }

    private fun mergeManualCoursesForReminders(
        schedule: TermSchedule?,
        manualCourses: List<CourseItem>,
    ): TermSchedule? {
        if (schedule == null && manualCourses.isEmpty()) return null
        val allCourses = schedule?.dailySchedules.orEmpty().flatMap { it.courses } + manualCourses
        val dailySchedules = allCourses
            .groupBy { it.time.dayOfWeek }
            .toSortedMap()
            .map { (day, courses) ->
                DailySchedule(
                    dayOfWeek = day,
                    courses = courses.sortedWith(
                        compareBy<CourseItem> { it.time.startNode }
                            .thenBy { it.time.endNode }
                            .thenBy { it.title },
                    ),
                )
            }
        return TermSchedule(
            termId = schedule?.termId ?: "manual",
            updatedAt = schedule?.updatedAt ?: OffsetDateTime.now().toString(),
            dailySchedules = dailySchedules,
        )
    }

    private fun emptySystemAlarmSyncSummary(): SystemAlarmSyncSummary = SystemAlarmSyncSummary(
        submittedCount = 0,
        createdCount = 0,
        skippedExistingCount = 0,
        skippedUnrepresentableCount = 0,
        results = emptyList(),
    )
}
