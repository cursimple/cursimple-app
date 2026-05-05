package com.kebiao.viewer.core.reminder

import com.kebiao.viewer.core.reminder.model.ReminderRule
import com.kebiao.viewer.core.reminder.model.SystemAlarmRecord
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {
    val reminderRulesFlow: Flow<List<ReminderRule>>

    val systemAlarmRecordsFlow: Flow<List<SystemAlarmRecord>>

    suspend fun getReminderRules(): List<ReminderRule>

    suspend fun saveReminderRule(rule: ReminderRule)

    suspend fun removeReminderRule(ruleId: String)

    suspend fun getSystemAlarmRecords(): List<SystemAlarmRecord>

    suspend fun saveSystemAlarmRecord(record: SystemAlarmRecord)

    suspend fun removeSystemAlarmRecordsForRule(ruleId: String)

    suspend fun clearSystemAlarmRecords()

    suspend fun clearSystemAlarmRecordsBefore(cutoffMillis: Long)
}
