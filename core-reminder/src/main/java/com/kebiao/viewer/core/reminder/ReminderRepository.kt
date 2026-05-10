package com.kebiao.viewer.core.reminder

import com.kebiao.viewer.core.reminder.model.ReminderRule
import com.kebiao.viewer.core.reminder.model.ReminderAlarmBackend
import com.kebiao.viewer.core.reminder.model.ReminderCustomOccupancy
import com.kebiao.viewer.core.reminder.model.SystemAlarmRecord
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {
    val reminderRulesFlow: Flow<List<ReminderRule>>

    val customOccupanciesFlow: Flow<List<ReminderCustomOccupancy>>

    val systemAlarmRecordsFlow: Flow<List<SystemAlarmRecord>>

    suspend fun getReminderRules(): List<ReminderRule>

    suspend fun saveReminderRule(rule: ReminderRule)

    suspend fun removeReminderRule(ruleId: String)

    suspend fun getCustomOccupancies(pluginId: String? = null): List<ReminderCustomOccupancy>

    suspend fun saveCustomOccupancy(occupancy: ReminderCustomOccupancy)

    suspend fun removeCustomOccupancy(occupancyId: String)

    suspend fun getSystemAlarmRecords(): List<SystemAlarmRecord>

    suspend fun saveSystemAlarmRecord(record: SystemAlarmRecord)

    suspend fun removeSystemAlarmRecord(alarmKey: String, backend: ReminderAlarmBackend? = null)

    suspend fun removeSystemAlarmRecordsForRule(ruleId: String)

    suspend fun clearSystemAlarmRecords()

    suspend fun clearSystemAlarmRecordsBefore(cutoffMillis: Long)
}
