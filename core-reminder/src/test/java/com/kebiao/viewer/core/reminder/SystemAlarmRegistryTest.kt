package com.kebiao.viewer.core.reminder

import android.content.ContextWrapper
import com.kebiao.viewer.core.kernel.model.ClassSlotTime
import com.kebiao.viewer.core.kernel.model.CourseItem
import com.kebiao.viewer.core.kernel.model.CourseTimeSlot
import com.kebiao.viewer.core.kernel.model.DailySchedule
import com.kebiao.viewer.core.kernel.model.TermSchedule
import com.kebiao.viewer.core.kernel.model.TermTimingProfile
import com.kebiao.viewer.core.reminder.dispatch.AlarmDispatcher
import com.kebiao.viewer.core.reminder.model.AlarmDispatchChannel
import com.kebiao.viewer.core.reminder.model.AlarmDispatchResult
import com.kebiao.viewer.core.reminder.model.ReminderPlan
import com.kebiao.viewer.core.reminder.model.ReminderRule
import com.kebiao.viewer.core.reminder.model.ReminderScopeType
import com.kebiao.viewer.core.reminder.model.ReminderSyncReason
import com.kebiao.viewer.core.reminder.model.SystemAlarmRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class SystemAlarmRegistryTest {
    @Test
    fun `successful system clock dispatch is recorded and skipped next time`() = runBlocking {
        val repository = FakeReminderRepository(
            rules = listOf(sampleRule()),
        )
        val dispatcher = FakeAlarmDispatcher(succeeded = true)
        val coordinator = ReminderCoordinator(
            context = ContextWrapper(null),
            repository = repository,
            systemDispatcher = dispatcher,
        )
        val profile = sampleProfile()
        val nowMillis = sampleNowMillis(hour = 7, minute = 0)
        val window = ReminderSyncWindows.todayFromNow(profile, nowMillis)

        val first = coordinator.syncSystemClockAlarmsForWindow(
            pluginId = "demo",
            schedule = sampleSchedule(),
            timingProfile = profile,
            window = window,
            reason = ReminderSyncReason.RuleCreatedToday,
            nowMillis = nowMillis,
        )
        val second = coordinator.syncSystemClockAlarmsForWindow(
            pluginId = "demo",
            schedule = sampleSchedule(),
            timingProfile = profile,
            window = window,
            reason = ReminderSyncReason.RuleCreatedToday,
            nowMillis = nowMillis,
        )

        assertEquals(1, first.createdCount)
        assertEquals(1, repository.records.value.size)
        assertEquals(1, dispatcher.dispatchCount)
        assertEquals(1, second.skippedExistingCount)
        assertEquals(0, second.createdCount)
    }

    @Test
    fun `failed system clock dispatch is not recorded and can retry`() = runBlocking {
        val repository = FakeReminderRepository(
            rules = listOf(sampleRule()),
        )
        val dispatcher = FakeAlarmDispatcher(succeeded = false)
        val coordinator = ReminderCoordinator(
            context = ContextWrapper(null),
            repository = repository,
            systemDispatcher = dispatcher,
        )
        val profile = sampleProfile()
        val nowMillis = sampleNowMillis(hour = 7, minute = 0)
        val window = ReminderSyncWindows.todayFromNow(profile, nowMillis)

        val first = coordinator.syncSystemClockAlarmsForWindow(
            pluginId = "demo",
            schedule = sampleSchedule(),
            timingProfile = profile,
            window = window,
            reason = ReminderSyncReason.RuleCreatedToday,
            nowMillis = nowMillis,
        )
        val second = coordinator.syncSystemClockAlarmsForWindow(
            pluginId = "demo",
            schedule = sampleSchedule(),
            timingProfile = profile,
            window = window,
            reason = ReminderSyncReason.RuleCreatedToday,
            nowMillis = nowMillis,
        )

        assertEquals(1, first.failedCount)
        assertEquals(0, repository.records.value.size)
        assertEquals(2, dispatcher.dispatchCount)
        assertEquals(1, second.failedCount)
    }

    private fun sampleRule(): ReminderRule = ReminderRule(
        ruleId = "rule",
        pluginId = "demo",
        scopeType = ReminderScopeType.SingleCourse,
        courseId = "math",
        advanceMinutes = 15,
        createdAt = "2026-02-23T00:00:00+08:00",
        updatedAt = "2026-02-23T00:00:00+08:00",
    )

    private fun sampleSchedule(): TermSchedule = TermSchedule(
        termId = "2026-spring",
        updatedAt = "2026-02-23T00:00:00+08:00",
        dailySchedules = listOf(
            DailySchedule(
                dayOfWeek = 1,
                courses = listOf(
                    CourseItem(
                        id = "math",
                        title = "高等数学",
                        weeks = listOf(1),
                        time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
                    ),
                ),
            ),
        ),
    )

    private fun sampleProfile(): TermTimingProfile = TermTimingProfile(
        termStartDate = "2026-02-23",
        timezone = ZoneId.systemDefault().id,
        slotTimes = listOf(
            ClassSlotTime(1, 2, "08:00", "09:35"),
        ),
    )

    private fun sampleNowMillis(hour: Int, minute: Int): Long =
        LocalDateTime.of(2026, 2, 23, hour, minute)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private class FakeAlarmDispatcher(
        private val succeeded: Boolean,
    ) : AlarmDispatcher {
        var dispatchCount: Int = 0

        override suspend fun dispatch(plan: ReminderPlan): AlarmDispatchResult {
            dispatchCount += 1
            return AlarmDispatchResult(
                channel = AlarmDispatchChannel.SystemClock,
                succeeded = succeeded,
                message = if (succeeded) "ok" else "failed",
            )
        }
    }

    private class FakeReminderRepository(
        rules: List<ReminderRule>,
    ) : ReminderRepository {
        private val rulesState = MutableStateFlow(rules)
        val records = MutableStateFlow<List<SystemAlarmRecord>>(emptyList())

        override val reminderRulesFlow: Flow<List<ReminderRule>> = rulesState
        override val systemAlarmRecordsFlow: Flow<List<SystemAlarmRecord>> = records

        override suspend fun getReminderRules(): List<ReminderRule> = rulesState.value

        override suspend fun saveReminderRule(rule: ReminderRule) {
            rulesState.value = rulesState.value.filterNot { it.ruleId == rule.ruleId } + rule
        }

        override suspend fun removeReminderRule(ruleId: String) {
            rulesState.value = rulesState.value.filterNot { it.ruleId == ruleId }
        }

        override suspend fun getSystemAlarmRecords(): List<SystemAlarmRecord> = records.value

        override suspend fun saveSystemAlarmRecord(record: SystemAlarmRecord) {
            records.value = records.value.filterNot { it.alarmKey == record.alarmKey } + record
        }

        override suspend fun removeSystemAlarmRecordsForRule(ruleId: String) {
            records.value = records.value.filterNot { it.ruleId == ruleId }
        }

        override suspend fun clearSystemAlarmRecords() {
            records.value = emptyList()
        }

        override suspend fun clearSystemAlarmRecordsBefore(cutoffMillis: Long) {
            records.value = records.value.filterNot { it.triggerAtMillis < cutoffMillis }
        }
    }
}
