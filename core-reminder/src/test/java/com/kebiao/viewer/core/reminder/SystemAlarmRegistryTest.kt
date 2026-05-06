package com.kebiao.viewer.core.reminder

import android.content.ContextWrapper
import com.kebiao.viewer.core.kernel.model.ClassSlotTime
import com.kebiao.viewer.core.kernel.model.CourseItem
import com.kebiao.viewer.core.kernel.model.CourseTimeSlot
import com.kebiao.viewer.core.kernel.model.DailySchedule
import com.kebiao.viewer.core.kernel.model.TermSchedule
import com.kebiao.viewer.core.kernel.model.TermTimingProfile
import com.kebiao.viewer.core.reminder.dispatch.AlarmDispatcher
import com.kebiao.viewer.core.reminder.dispatch.AlarmDismisser
import com.kebiao.viewer.core.reminder.model.AlarmDispatchChannel
import com.kebiao.viewer.core.reminder.model.AlarmDispatchResult
import com.kebiao.viewer.core.reminder.model.AlarmDismissResult
import com.kebiao.viewer.core.reminder.model.ReminderAlarmBackend
import com.kebiao.viewer.core.reminder.model.ReminderAlarmSettings
import com.kebiao.viewer.core.reminder.model.ReminderPlan
import com.kebiao.viewer.core.reminder.model.ReminderRule
import com.kebiao.viewer.core.reminder.model.ReminderScopeType
import com.kebiao.viewer.core.reminder.model.ReminderSyncReason
import com.kebiao.viewer.core.reminder.model.SystemAlarmRecord
import com.kebiao.viewer.core.reminder.model.systemAlarmLabel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class SystemAlarmRegistryTest {
    @Test
    fun `system clock alarm label does not include hash token`() {
        val plan = ReminderPlan(
            planId = "plan",
            ruleId = "rule",
            pluginId = "demo",
            title = "高等数学",
            message = "高等数学即将开始",
            triggerAtMillis = sampleNowMillis(hour = 7, minute = 45),
            ringtoneUri = null,
            courseId = "math",
        )

        val label = plan.systemAlarmLabel()

        assertEquals("课表提醒 · 高等数学 · 07:45", label)
        assertFalse(label.contains("#"))
    }

    @Test
    fun `successful system clock dispatch is recorded and skipped next time`() = runBlocking {
        val repository = FakeReminderRepository(
            rules = listOf(sampleRule()),
        )
        val dispatcher = FakeAlarmDispatcher(succeeded = true)
        val coordinator = ReminderCoordinator(
            context = ContextWrapper(null),
            repository = repository,
            alarmSettingsProvider = { ReminderAlarmSettings(backend = ReminderAlarmBackend.SystemClockApp) },
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
            alarmSettingsProvider = { ReminderAlarmSettings(backend = ReminderAlarmBackend.SystemClockApp) },
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

    @Test
    fun `app managed alarm dispatch records backend and request code`() = runBlocking {
        val repository = FakeReminderRepository(
            rules = listOf(sampleRule()),
        )
        val dispatcher = FakeAlarmDispatcher(
            succeeded = true,
            channel = AlarmDispatchChannel.AppAlarmClock,
        )
        val coordinator = ReminderCoordinator(
            context = ContextWrapper(null),
            repository = repository,
            alarmSettingsProvider = { ReminderAlarmSettings(backend = ReminderAlarmBackend.AppAlarmClock) },
            appDispatcher = dispatcher,
        )
        val profile = sampleProfile()
        val nowMillis = sampleNowMillis(hour = 7, minute = 0)

        val summary = coordinator.syncAlarmsForWindow(
            pluginId = "demo",
            schedule = sampleSchedule(),
            timingProfile = profile,
            window = ReminderSyncWindows.todayFromNow(profile, nowMillis),
            reason = ReminderSyncReason.RuleCreatedToday,
            nowMillis = nowMillis,
        )

        val record = repository.records.value.single()
        assertEquals(1, summary.createdCount)
        assertEquals(ReminderAlarmBackend.AppAlarmClock, record.backend)
        assertEquals(record.alarmKey.hashCode() and Int.MAX_VALUE, record.requestCode)
    }

    @Test
    fun `after class check dismisses expired app managed alarm`() = runBlocking {
        val profile = sampleProfile()
        val expiredRecord = sampleRecord(triggerAtMillis = sampleNowMillis(hour = 7, minute = 45))
            .copy(backend = ReminderAlarmBackend.AppAlarmClock, requestCode = 1234)
        val repository = FakeReminderRepository(rules = emptyList()).apply {
            records.value = listOf(expiredRecord)
        }
        val appDismisser = FakeAlarmDismisser(succeeded = true)
        val coordinator = ReminderCoordinator(
            context = ContextWrapper(null),
            repository = repository,
            alarmSettingsProvider = { ReminderAlarmSettings(backend = ReminderAlarmBackend.AppAlarmClock) },
            appDispatcher = FakeAlarmDispatcher(succeeded = true, channel = AlarmDispatchChannel.AppAlarmClock),
            appDismisser = appDismisser,
        )

        val summary = coordinator.syncAlarmsForWindow(
            pluginId = "demo",
            schedule = sampleSchedule(),
            timingProfile = profile,
            window = ReminderSyncWindows.todayFromNow(profile, sampleNowMillis(hour = 9, minute = 40)),
            reason = ReminderSyncReason.AfterClassToday,
            nowMillis = sampleNowMillis(hour = 9, minute = 40),
        )

        assertEquals(1, summary.dismissedCount)
        assertEquals(1, appDismisser.dismissCount)
        assertEquals(emptyList<SystemAlarmRecord>(), repository.records.value)
    }

    @Test
    fun `expired records are cleared locally before the next sync`() = runBlocking {
        val profile = sampleProfile()
        val expiredRecord = sampleRecord(triggerAtMillis = sampleNowMillis(hour = 7, minute = 45))
        val repository = FakeReminderRepository(rules = emptyList()).apply {
            records.value = listOf(expiredRecord)
        }
        val dismisser = FakeAlarmDismisser(succeeded = true)
        val coordinator = ReminderCoordinator(
            context = ContextWrapper(null),
            repository = repository,
            alarmSettingsProvider = { ReminderAlarmSettings(backend = ReminderAlarmBackend.SystemClockApp) },
            systemDispatcher = FakeAlarmDispatcher(succeeded = true),
            systemDismisser = dismisser,
        )
        val nowMillis = sampleNowMillis(hour = 9, minute = 40)

        val summary = coordinator.syncSystemClockAlarmsForWindow(
            pluginId = "demo",
            schedule = sampleSchedule(),
            timingProfile = profile,
            window = ReminderSyncWindows.todayFromNow(profile, nowMillis),
            reason = ReminderSyncReason.AfterClassToday,
            nowMillis = nowMillis,
        )

        assertEquals(1, summary.expiredRecordClearedCount)
        assertEquals(0, summary.dismissedCount)
        assertEquals(0, summary.dismissFailedCount)
        assertEquals(0, dismisser.dismissCount)
        assertEquals(emptyList<SystemAlarmRecord>(), repository.records.value)
    }

    @Test
    fun `daily next day sync does not dismiss future alarms that are still today`() = runBlocking {
        val profile = sampleProfile()
        val futureTodayRecord = sampleRecord(triggerAtMillis = sampleNowMillis(hour = 23, minute = 0))
        val repository = FakeReminderRepository(rules = emptyList()).apply {
            records.value = listOf(futureTodayRecord)
        }
        val dismisser = FakeAlarmDismisser(succeeded = true)
        val coordinator = ReminderCoordinator(
            context = ContextWrapper(null),
            repository = repository,
            alarmSettingsProvider = { ReminderAlarmSettings(backend = ReminderAlarmBackend.SystemClockApp) },
            systemDispatcher = FakeAlarmDispatcher(succeeded = true),
            systemDismisser = dismisser,
        )
        val nowMillis = sampleNowMillis(hour = 22, minute = 0)

        val summary = coordinator.syncSystemClockAlarmsForWindow(
            pluginId = "demo",
            schedule = sampleSchedule(),
            timingProfile = profile,
            window = ReminderSyncWindows.nextDay(profile, nowMillis),
            reason = ReminderSyncReason.DailyNextDay,
            nowMillis = nowMillis,
        )

        assertEquals(0, summary.dismissedCount)
        assertEquals(0, dismisser.dismissCount)
        assertEquals(listOf(futureTodayRecord), repository.records.value)
    }

    @Test
    fun `stale records in the active window are dismissed`() = runBlocking {
        val profile = sampleProfile()
        val staleRecord = sampleRecord(triggerAtMillis = sampleNowMillis(hour = 10, minute = 0))
        val repository = FakeReminderRepository(rules = emptyList()).apply {
            records.value = listOf(staleRecord)
        }
        val dismisser = FakeAlarmDismisser(succeeded = true)
        val coordinator = ReminderCoordinator(
            context = ContextWrapper(null),
            repository = repository,
            alarmSettingsProvider = { ReminderAlarmSettings(backend = ReminderAlarmBackend.SystemClockApp) },
            systemDispatcher = FakeAlarmDispatcher(succeeded = true),
            systemDismisser = dismisser,
        )
        val nowMillis = sampleNowMillis(hour = 7, minute = 0)

        val summary = coordinator.syncSystemClockAlarmsForWindow(
            pluginId = "demo",
            schedule = sampleSchedule(),
            timingProfile = profile,
            window = ReminderSyncWindows.todayFromNow(profile, nowMillis),
            reason = ReminderSyncReason.ScheduleChanged,
            nowMillis = nowMillis,
        )

        assertEquals(1, summary.dismissedCount)
        assertEquals(0, summary.dismissFailedCount)
        assertEquals(1, dismisser.dismissCount)
        assertEquals(emptyList<SystemAlarmRecord>(), repository.records.value)
    }

    @Test
    fun `deleting a reminder rule dismisses its registered future system alarm`() = runBlocking {
        val record = sampleRecord(triggerAtMillis = futureMillis())
        val repository = FakeReminderRepository(rules = listOf(sampleRule())).apply {
            records.value = listOf(record)
        }
        val dismisser = FakeAlarmDismisser(succeeded = true)
        val coordinator = ReminderCoordinator(
            context = ContextWrapper(null),
            repository = repository,
            alarmSettingsProvider = { ReminderAlarmSettings(backend = ReminderAlarmBackend.SystemClockApp) },
            systemDispatcher = FakeAlarmDispatcher(succeeded = true),
            systemDismisser = dismisser,
        )

        coordinator.deleteRule("rule")

        assertEquals(1, dismisser.dismissCount)
        assertEquals(emptyList<ReminderRule>(), repository.getReminderRules())
        assertEquals(emptyList<SystemAlarmRecord>(), repository.records.value)
    }

    @Test
    fun `deleting a reminder rule clears expired records without dismissing system alarm`() = runBlocking {
        val record = sampleRecord(triggerAtMillis = sampleNowMillis(hour = 7, minute = 45))
        val repository = FakeReminderRepository(rules = listOf(sampleRule())).apply {
            records.value = listOf(record)
        }
        val dismisser = FakeAlarmDismisser(succeeded = true)
        val coordinator = ReminderCoordinator(
            context = ContextWrapper(null),
            repository = repository,
            alarmSettingsProvider = { ReminderAlarmSettings(backend = ReminderAlarmBackend.SystemClockApp) },
            systemDispatcher = FakeAlarmDispatcher(succeeded = true),
            systemDismisser = dismisser,
        )

        coordinator.deleteRule("rule")

        assertEquals(0, dismisser.dismissCount)
        assertEquals(emptyList<ReminderRule>(), repository.getReminderRules())
        assertEquals(emptyList<SystemAlarmRecord>(), repository.records.value)
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

    private fun futureMillis(): Long = System.currentTimeMillis() + 60 * 60 * 1000

    private fun sampleRecord(triggerAtMillis: Long): SystemAlarmRecord = SystemAlarmRecord(
        alarmKey = "alarm-$triggerAtMillis",
        ruleId = "rule",
        pluginId = "demo",
        planId = "plan-$triggerAtMillis",
        courseId = "math",
        triggerAtMillis = triggerAtMillis,
        message = "课表提醒 · 高等数学 · 07:45",
        alarmLabel = "课表提醒 · 高等数学 · 07:45",
        createdAtMillis = triggerAtMillis - 60_000,
    )

    private class FakeAlarmDispatcher(
        private val succeeded: Boolean,
        private val channel: AlarmDispatchChannel = AlarmDispatchChannel.SystemClockApp,
    ) : AlarmDispatcher {
        var dispatchCount: Int = 0

        override suspend fun dispatch(plan: ReminderPlan): AlarmDispatchResult {
            dispatchCount += 1
            return AlarmDispatchResult(
                channel = channel,
                succeeded = succeeded,
                message = if (succeeded) "ok" else "failed",
            )
        }
    }

    private class FakeAlarmDismisser(
        private val succeeded: Boolean,
    ) : AlarmDismisser {
        var dismissCount: Int = 0

        override suspend fun dismiss(record: SystemAlarmRecord): AlarmDismissResult {
            dismissCount += 1
            return AlarmDismissResult(
                alarmKey = record.alarmKey,
                succeeded = succeeded,
                message = if (succeeded) "dismissed" else "failed",
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
            records.value = records.value.filterNot {
                it.alarmKey == record.alarmKey && it.backend == record.backend
            } + record
        }

        override suspend fun removeSystemAlarmRecord(alarmKey: String, backend: ReminderAlarmBackend?) {
            records.value = records.value.filterNot {
                it.alarmKey == alarmKey && (backend == null || it.backend == backend)
            }
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
