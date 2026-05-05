package com.kebiao.viewer.core.reminder

import android.content.Context
import com.kebiao.viewer.core.kernel.model.TermSchedule
import com.kebiao.viewer.core.kernel.model.TermTimingProfile
import com.kebiao.viewer.core.kernel.model.TemporaryScheduleOverride
import com.kebiao.viewer.core.reminder.dispatch.AppAlarmDispatcher
import com.kebiao.viewer.core.reminder.dispatch.HybridAlarmDispatcher
import com.kebiao.viewer.core.reminder.dispatch.SystemAlarmClockDispatcher
import com.kebiao.viewer.core.reminder.logging.ReminderLogger
import com.kebiao.viewer.core.reminder.model.AlarmDispatchResult
import com.kebiao.viewer.core.reminder.model.ReminderDayPeriod
import com.kebiao.viewer.core.reminder.model.ReminderRule
import com.kebiao.viewer.core.reminder.model.ReminderScopeType
import kotlinx.coroutines.flow.Flow
import java.time.OffsetDateTime
import java.util.UUID

class ReminderCoordinator(
    private val context: Context,
    private val repository: ReminderRepository,
    private val planner: ReminderPlanner = ReminderPlanner(),
    private val temporaryScheduleOverridesProvider: suspend () -> List<TemporaryScheduleOverride> = { emptyList() },
) {
    private val systemDispatcher = SystemAlarmClockDispatcher(context)
    private val appDispatcher = AppAlarmDispatcher(context)
    private val dispatcher = HybridAlarmDispatcher(
        systemDispatcher = systemDispatcher,
        appDispatcher = appDispatcher,
    )

    val reminderRulesFlow: Flow<List<ReminderRule>> = repository.reminderRulesFlow

    suspend fun getRules(): List<ReminderRule> = repository.getReminderRules()

    suspend fun saveRule(rule: ReminderRule) {
        repository.saveReminderRule(rule)
    }

    suspend fun createRule(
        pluginId: String,
        courseId: String?,
        dayOfWeek: Int?,
        startNode: Int?,
        endNode: Int?,
        scopeType: ReminderScopeType,
        advanceMinutes: Int,
        ringtoneUri: String?,
    ): ReminderRule {
        val now = OffsetDateTime.now().toString()
        val rule = ReminderRule(
            ruleId = UUID.randomUUID().toString(),
            pluginId = pluginId,
            scopeType = scopeType,
            period = null,
            courseId = courseId,
            dayOfWeek = dayOfWeek,
            startNode = startNode,
            endNode = endNode,
            advanceMinutes = advanceMinutes,
            ringtoneUri = ringtoneUri,
            createdAt = now,
            updatedAt = now,
        )
        repository.saveReminderRule(rule)
        return rule
    }

    suspend fun upsertFirstCourseReminder(
        pluginId: String,
        period: ReminderDayPeriod,
        enabled: Boolean,
        advanceMinutes: Int,
        ringtoneUri: String?,
    ): ReminderRule {
        val now = OffsetDateTime.now().toString()
        val existing = repository.getReminderRules().firstOrNull {
            it.pluginId == pluginId &&
                it.scopeType == ReminderScopeType.FirstCourseOfPeriod &&
                it.period == period
        }
        val rule = (existing ?: ReminderRule(
            ruleId = UUID.randomUUID().toString(),
            pluginId = pluginId,
            scopeType = ReminderScopeType.FirstCourseOfPeriod,
            period = period,
            advanceMinutes = advanceMinutes,
            ringtoneUri = ringtoneUri,
            enabled = enabled,
            createdAt = now,
            updatedAt = now,
        )).copy(
            advanceMinutes = advanceMinutes,
            ringtoneUri = ringtoneUri,
            enabled = enabled,
            updatedAt = now,
            period = period,
        )
        repository.saveReminderRule(rule)
        return rule
    }

    suspend fun deleteRule(ruleId: String) {
        repository.removeReminderRule(ruleId)
    }

    suspend fun syncRulesForSchedule(
        pluginId: String,
        schedule: TermSchedule,
        timingProfile: TermTimingProfile?,
        preferSystemClock: Boolean = false,
    ): List<AlarmDispatchResult> {
        val profile = timingProfile ?: return emptyList()
        val temporaryScheduleOverrides = temporaryScheduleOverridesProvider()
        val rules = repository.getReminderRules()
            .filter { it.enabled && it.pluginId == pluginId }
        val plans = rules.flatMap { rule ->
            planner.expandRule(
                rule = rule,
                schedule = schedule,
                timingProfile = profile,
                temporaryScheduleOverrides = temporaryScheduleOverrides,
            )
        }
        ReminderLogger.info(
            "reminder.sync.start",
            mapOf(
                "pluginId" to pluginId,
                "ruleCount" to rules.size,
                "planCount" to plans.size,
                "preferSystemClock" to preferSystemClock,
            ),
        )
        val results = plans.map { plan ->
            if (preferSystemClock) {
                dispatcher.dispatch(plan)
            } else {
                appDispatch(plan)
            }
        }
        ReminderLogger.info(
            "reminder.sync.finish",
            mapOf(
                "pluginId" to pluginId,
                "planCount" to plans.size,
                "successCount" to results.count { it.succeeded },
                "failureCount" to results.count { !it.succeeded },
            ),
        )
        return results
    }

    private suspend fun appDispatch(plan: com.kebiao.viewer.core.reminder.model.ReminderPlan): AlarmDispatchResult {
        return appDispatcher.dispatch(plan)
    }
}
