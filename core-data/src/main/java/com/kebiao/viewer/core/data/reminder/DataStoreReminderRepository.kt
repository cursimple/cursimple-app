package com.kebiao.viewer.core.data.reminder

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit
import com.kebiao.viewer.core.reminder.ReminderRepository
import com.kebiao.viewer.core.reminder.model.ReminderAlarmBackend
import com.kebiao.viewer.core.reminder.model.ReminderRule
import com.kebiao.viewer.core.reminder.model.SystemAlarmRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.reminderStore by preferencesDataStore(name = "reminder_store")

class DataStoreReminderRepository(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : ReminderRepository {
    private val store = context.applicationContext.reminderStore

    override val reminderRulesFlow: Flow<List<ReminderRule>> = store.data.map { preferences ->
        preferences[KEY_REMINDER_RULES]?.let {
            json.decodeFromString(ListSerializer(ReminderRule.serializer()), it)
        }.orEmpty()
    }

    override val systemAlarmRecordsFlow: Flow<List<SystemAlarmRecord>> = store.data.map { preferences ->
        preferences[KEY_SYSTEM_ALARM_RECORDS]?.let {
            json.decodeFromString(ListSerializer(SystemAlarmRecord.serializer()), it)
        }.orEmpty()
    }

    override suspend fun getReminderRules(): List<ReminderRule> = reminderRulesFlow.first()

    override suspend fun saveReminderRule(rule: ReminderRule) {
        store.edit { preferences ->
            val current = preferences.decodeReminderRules()
            val next = current
                .filterNot { it.ruleId == rule.ruleId }
                .plus(rule)
                .sortedBy { it.createdAt }
            preferences[KEY_REMINDER_RULES] = json.encodeToString(
                ListSerializer(ReminderRule.serializer()),
                next,
            )
        }
    }

    override suspend fun removeReminderRule(ruleId: String) {
        store.edit { preferences ->
            val next = preferences.decodeReminderRules().filterNot { it.ruleId == ruleId }
            preferences[KEY_REMINDER_RULES] = json.encodeToString(
                ListSerializer(ReminderRule.serializer()),
                next,
            )
        }
    }

    override suspend fun getSystemAlarmRecords(): List<SystemAlarmRecord> = systemAlarmRecordsFlow.first()

    override suspend fun saveSystemAlarmRecord(record: SystemAlarmRecord) {
        store.edit { preferences ->
            val next = preferences.decodeSystemAlarmRecords()
                .filterNot { it.alarmKey == record.alarmKey && it.backend == record.backend }
                .plus(record)
                .sortedBy { it.triggerAtMillis }
            preferences[KEY_SYSTEM_ALARM_RECORDS] = json.encodeToString(
                ListSerializer(SystemAlarmRecord.serializer()),
                next,
            )
        }
    }

    override suspend fun removeSystemAlarmRecord(alarmKey: String, backend: ReminderAlarmBackend?) {
        store.edit { preferences ->
            val next = preferences.decodeSystemAlarmRecords().filterNot {
                it.alarmKey == alarmKey && (backend == null || it.backend == backend)
            }
            preferences[KEY_SYSTEM_ALARM_RECORDS] = json.encodeToString(
                ListSerializer(SystemAlarmRecord.serializer()),
                next,
            )
        }
    }

    override suspend fun removeSystemAlarmRecordsForRule(ruleId: String) {
        store.edit { preferences ->
            val next = preferences.decodeSystemAlarmRecords().filterNot { it.ruleId == ruleId }
            preferences[KEY_SYSTEM_ALARM_RECORDS] = json.encodeToString(
                ListSerializer(SystemAlarmRecord.serializer()),
                next,
            )
        }
    }

    override suspend fun clearSystemAlarmRecords() {
        store.edit { preferences ->
            preferences[KEY_SYSTEM_ALARM_RECORDS] = json.encodeToString(
                ListSerializer(SystemAlarmRecord.serializer()),
                emptyList(),
            )
        }
    }

    override suspend fun clearSystemAlarmRecordsBefore(cutoffMillis: Long) {
        store.edit { preferences ->
            val next = preferences.decodeSystemAlarmRecords()
                .filterNot { it.triggerAtMillis < cutoffMillis }
            preferences[KEY_SYSTEM_ALARM_RECORDS] = json.encodeToString(
                ListSerializer(SystemAlarmRecord.serializer()),
                next,
            )
        }
    }

    private companion object {
        val KEY_REMINDER_RULES = stringPreferencesKey("reminder_rules")
        val KEY_SYSTEM_ALARM_RECORDS = stringPreferencesKey("system_alarm_records")
    }

    private fun androidx.datastore.preferences.core.Preferences.decodeReminderRules(): List<ReminderRule> {
        return this[KEY_REMINDER_RULES]?.let {
            json.decodeFromString(ListSerializer(ReminderRule.serializer()), it)
        }.orEmpty()
    }

    private fun androidx.datastore.preferences.core.Preferences.decodeSystemAlarmRecords(): List<SystemAlarmRecord> {
        return this[KEY_SYSTEM_ALARM_RECORDS]?.let {
            json.decodeFromString(ListSerializer(SystemAlarmRecord.serializer()), it)
        }.orEmpty()
    }
}
