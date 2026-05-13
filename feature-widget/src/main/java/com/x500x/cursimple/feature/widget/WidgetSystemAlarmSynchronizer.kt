package com.x500x.cursimple.feature.widget

import android.content.Context
import com.x500x.cursimple.core.data.DataStoreManualCourseRepository
import com.x500x.cursimple.core.data.DataStoreScheduleRepository
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.UserPreferences
import com.x500x.cursimple.core.data.reminder.DataStoreReminderRepository
import com.x500x.cursimple.core.data.term.DataStoreTermProfileRepository
import com.x500x.cursimple.core.data.widget.DataStoreWidgetPreferencesRepository
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.DailySchedule
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.reminder.ReminderCoordinator
import com.x500x.cursimple.core.reminder.ReminderSyncWindows
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import com.x500x.cursimple.core.reminder.model.ReminderAlarmBackend
import com.x500x.cursimple.core.reminder.model.ReminderAlarmSettings
import com.x500x.cursimple.core.reminder.model.ReminderSyncReason
import com.x500x.cursimple.core.reminder.model.SystemAlarmSyncSummary
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
        val nowMillis = System.currentTimeMillis()
        val preferences = userPreferencesRepository.preferencesFlow.first()
        val alarmSettings = preferences.toReminderAlarmSettings()

        val timingProfile = widgetPreferencesRepository.timingProfileFlow.first()
            ?: return emptySystemAlarmSyncSummary().also { summary ->
                logAlarmRegistrationReadiness(summary, alarmSettings.backend)
            }
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
            alarmSettingsProvider = {
                alarmSettings
            },
        )
        if (schedule == null) {
            coordinator.clearSystemAlarmRecords()
            return emptySystemAlarmSyncSummary().also { summary ->
                logAlarmRegistrationReadiness(summary, alarmSettings.backend)
            }
        }
        val summary = coordinator.syncAlarmsForWindow(
            pluginId = pluginId,
            schedule = schedule,
            timingProfile = timingProfile,
            window = ReminderSyncWindows.todayFromNow(timingProfile, nowMillis),
            reason = ReminderSyncReason.WidgetRefresh,
            nowMillis = nowMillis,
        )
        logAlarmRegistrationReadiness(summary, alarmSettings.backend)
        return summary
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

    private fun UserPreferences.toReminderAlarmSettings(): ReminderAlarmSettings = ReminderAlarmSettings(
        backend = alarmBackend,
        ringDurationSeconds = alarmRingDurationSeconds,
        repeatIntervalSeconds = alarmRepeatIntervalSeconds,
        repeatCount = alarmRepeatCount,
    )

    private fun logAlarmRegistrationReadiness(
        summary: SystemAlarmSyncSummary,
        backend: ReminderAlarmBackend,
    ) {
        val readiness = summarizeWidgetAlarmRegistration(summary, backend)
        ReminderLogger.info(
            "widget.alarm_registration.readiness",
            mapOf(
                "backend" to readiness.backendLabel,
                "status" to readiness.status.name,
                "message" to readiness.message,
                "submittedCount" to summary.submittedCount,
                "createdCount" to summary.createdCount,
                "skippedExistingCount" to summary.skippedExistingCount,
                "skippedUnrepresentableCount" to summary.skippedUnrepresentableCount,
                "registryWriteFailedCount" to summary.registryWriteFailedCount,
                "failureCount" to summary.failedCount,
            ),
        )
    }
}

internal enum class WidgetAlarmRegistrationStatus {
    Ready,
    Repaired,
    Failed,
    NotNeeded,
}

internal data class WidgetAlarmRegistrationReadiness(
    val status: WidgetAlarmRegistrationStatus,
    val backendLabel: String,
    val message: String,
)

internal fun summarizeWidgetAlarmRegistration(
    summary: SystemAlarmSyncSummary,
    backend: ReminderAlarmBackend,
): WidgetAlarmRegistrationReadiness {
    val backendLabel = when (backend) {
        ReminderAlarmBackend.AppAlarmClock -> "App 自管闹钟"
        ReminderAlarmBackend.SystemClockApp -> "系统时钟 App 闹钟"
    }
    val hasFailure = summary.failedCount > 0 ||
        summary.skippedUnrepresentableCount > 0 ||
        summary.dismissFailedCount > 0
    val status = when {
        hasFailure -> WidgetAlarmRegistrationStatus.Failed
        summary.createdCount > 0 -> WidgetAlarmRegistrationStatus.Repaired
        summary.skippedExistingCount > 0 -> WidgetAlarmRegistrationStatus.Ready
        else -> WidgetAlarmRegistrationStatus.NotNeeded
    }
    val message = when (status) {
        WidgetAlarmRegistrationStatus.Ready -> "提醒已通过$backendLabel 注册就绪"
        WidgetAlarmRegistrationStatus.Repaired -> "已通过$backendLabel 补注册 ${summary.createdCount} 个提醒"
        WidgetAlarmRegistrationStatus.Failed -> "提醒通过$backendLabel 注册失败，请检查权限和闹钟通道"
        WidgetAlarmRegistrationStatus.NotNeeded -> "当前窗口没有需要注册的提醒"
    }
    return WidgetAlarmRegistrationReadiness(
        status = status,
        backendLabel = backendLabel,
        message = message,
    )
}
