package com.kebiao.viewer.core.reminder

import com.kebiao.viewer.core.reminder.model.ReminderRule
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {
    val reminderRulesFlow: Flow<List<ReminderRule>>

    suspend fun getReminderRules(): List<ReminderRule>

    suspend fun saveReminderRule(rule: ReminderRule)

    suspend fun removeReminderRule(ruleId: String)
}
