package com.kebiao.viewer.core.reminder

import android.content.Context
import com.kebiao.viewer.core.kernel.model.TermSchedule
import com.kebiao.viewer.core.kernel.model.TermTimingProfile
import com.kebiao.viewer.core.reminder.dispatch.FallbackAlarmDispatcher
import com.kebiao.viewer.core.reminder.dispatch.HybridAlarmDispatcher
import com.kebiao.viewer.core.reminder.dispatch.SystemAlarmClockDispatcher
import com.kebiao.viewer.core.reminder.model.AlarmDispatchResult
import com.kebiao.viewer.core.reminder.model.ReminderRule
import kotlinx.coroutines.flow.Flow
import java.time.OffsetDateTime
import java.util.UUID

class ReminderCoordinator(
    private val context: Context,
    private val repository: ReminderRepository,
    private val planner: ReminderPlanner = ReminderPlanner(),
) {
    private val systemDispatcher = SystemAlarmClockDispatcher(context)
    private val fallbackDispatcher = FallbackAlarmDispatcher(context)
    private val dispatcher = HybridAlarmDispatcher(
        systemDispatcher = systemDispatcher,
        fallbackDispatcher = fallbackDispatcher,
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
        scopeType: com.kebiao.viewer.core.reminder.model.ReminderScopeType,
        advanceMinutes: Int,
        ringtoneUri: String?,
    ): ReminderRule {
        val now = OffsetDateTime.now().toString()
        val rule = ReminderRule(
            ruleId = UUID.randomUUID().toString(),
            pluginId = pluginId,
            scopeType = scopeType,
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
        return repository.getReminderRules()
            .filter { it.enabled && it.pluginId == pluginId }
            .flatMap { rule ->
                planner.expandRule(rule, schedule, profile).map { plan ->
                    if (preferSystemClock) {
                        dispatcher.dispatch(plan)
                    } else {
                        fallbackDispatch(plan)
                    }
                }
            }
    }

    private suspend fun fallbackDispatch(plan: com.kebiao.viewer.core.reminder.model.ReminderPlan): AlarmDispatchResult {
        return fallbackDispatcher.dispatch(plan)
    }
}
