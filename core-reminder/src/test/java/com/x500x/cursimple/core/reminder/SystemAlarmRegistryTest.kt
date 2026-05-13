package com.x500x.cursimple.core.reminder

import android.content.ContextWrapper
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.DailySchedule
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.reminder.dispatch.AlarmDispatcher
import com.x500x.cursimple.core.reminder.dispatch.AlarmDismisser
import com.x500x.cursimple.core.reminder.model.AlarmDispatchChannel
import com.x500x.cursimple.core.reminder.model.AlarmDispatchResult
import com.x500x.cursimple.core.reminder.model.AlarmDismissResult
import com.x500x.cursimple.core.reminder.model.AppAlarmOperationMode
import com.x500x.cursimple.core.reminder.model.ReminderAlarmBackend
import com.x500x.cursimple.core.reminder.model.ReminderAlarmSettings
import com.x500x.cursimple.core.reminder.model.ReminderCustomOccupancy
import com.x500x.cursimple.core.reminder.model.ReminderPlan
import com.x500x.cursimple.core.reminder.model.ReminderRule
import com.x500x.cursimple.core.reminder.model.ReminderScopeType
import com.x500x.cursimple.core.reminder.model.ReminderSyncReason
import com.x500x.cursimple.core.reminder.model.SystemAlarmRecord
import com.x500x.cursimple.core.reminder.model.TriggeredAppAlarmFinishAction
import com.x500x.cursimple.core.reminder.model.systemAlarmKey
import com.x500x.cursimple.core.reminder.model.systemAlarmLabel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        assertEquals(AppAlarmOperationMode.ForegroundService, record.operationMode)
        assertTrue(record.displayTitle.orEmpty().contains("高等数学"))
        assertTrue(record.displayMessage.orEmpty().contains("08:00-09:35"))
    }

    @Test
    fun `app managed sync migrates legacy broadcast operation before recreating alarm`() = runBlocking {
        val repository = FakeReminderRepository(
            rules = listOf(sampleRule()),
        )
        val dispatcher = FakeAlarmDispatcher(
            succeeded = true,
            channel = AlarmDispatchChannel.AppAlarmClock,
        )
        val appDismisser = FakeAlarmDismisser(succeeded = true)
        val coordinator = ReminderCoordinator(
            context = ContextWrapper(null),
            repository = repository,
            alarmSettingsProvider = { ReminderAlarmSettings(backend = ReminderAlarmBackend.AppAlarmClock) },
            appDispatcher = dispatcher,
            appDismisser = appDismisser,
        )
        val profile = sampleProfile()
        val nowMillis = sampleNowMillis(hour = 7, minute = 0)
        val window = ReminderSyncWindows.todayFromNow(profile, nowMillis)
        coordinator.syncAlarmsForWindow(
            pluginId = "demo",
            schedule = sampleSchedule(),
            timingProfile = profile,
            window = window,
            reason = ReminderSyncReason.RuleCreatedToday,
            nowMillis = nowMillis,
        )
        repository.records.value = repository.records.value.map {
            it.copy(operationMode = AppAlarmOperationMode.LegacyBroadcast)
        }

        val summary = coordinator.syncAlarmsForWindow(
            pluginId = "demo",
            schedule = sampleSchedule(),
            timingProfile = profile,
            window = window,
            reason = ReminderSyncReason.ScheduleChanged,
            nowMillis = nowMillis,
        )

        assertEquals(1, summary.dismissedCount)
        assertEquals(1, summary.createdCount)
        assertEquals(1, appDismisser.dismissCount)
        assertEquals(2, dispatcher.dispatchCount)
        assertEquals(AppAlarmOperationMode.ForegroundService, repository.records.value.single().operationMode)
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

    @Test
    fun `deleting one app managed alarm record dismisses and removes only that record`() = runBlocking {
        val target = sampleRecord(triggerAtMillis = futureMillis())
            .copy(alarmKey = "target", backend = ReminderAlarmBackend.AppAlarmClock, requestCode = 1001)
        val other = sampleRecord(triggerAtMillis = futureMillis() + 60_000)
            .copy(alarmKey = "other", backend = ReminderAlarmBackend.AppAlarmClock, requestCode = 1002)
        val repository = FakeReminderRepository(rules = listOf(sampleRule())).apply {
            records.value = listOf(target, other)
        }
        val appDismisser = FakeAlarmDismisser(succeeded = true)
        val coordinator = ReminderCoordinator(
            context = ContextWrapper(null),
            repository = repository,
            alarmSettingsProvider = { ReminderAlarmSettings(backend = ReminderAlarmBackend.AppAlarmClock) },
            appDismisser = appDismisser,
        )

        val result = coordinator.deleteAlarmRecord("target", ReminderAlarmBackend.AppAlarmClock)

        assertEquals(true, result.succeeded)
        assertEquals(1, appDismisser.dismissCount)
        assertEquals(listOf(other), repository.records.value)
    }

    @Test
    fun `consuming triggered app managed course alarm deletes one shot rule and its alarms`() = runBlocking {
        val fired = sampleRecord(triggerAtMillis = sampleNowMillis(hour = 7, minute = 45))
            .copy(alarmKey = "fired", backend = ReminderAlarmBackend.AppAlarmClock, requestCode = 1001)
        val future = sampleRecord(triggerAtMillis = futureMillis())
            .copy(alarmKey = "future", backend = ReminderAlarmBackend.AppAlarmClock, requestCode = 1002)
        val repository = FakeReminderRepository(rules = listOf(sampleRule())).apply {
            records.value = listOf(fired, future)
        }
        val appDismisser = FakeAlarmDismisser(succeeded = true)
        val coordinator = ReminderCoordinator(
            context = ContextWrapper(null),
            repository = repository,
            appDismisser = appDismisser,
        )

        coordinator.consumeTriggeredAppAlarm("fired", "rule")

        assertEquals(emptyList<ReminderRule>(), repository.getReminderRules())
        assertEquals(emptyList<SystemAlarmRecord>(), repository.records.value)
        assertEquals(1, appDismisser.dismissCount)
    }

    @Test
    fun `consuming first course app alarm keeps recurring rule`() = runBlocking {
        val rule = sampleRule().copy(scopeType = ReminderScopeType.FirstCourseOfPeriod)
        val fired = sampleRecord(triggerAtMillis = sampleNowMillis(hour = 7, minute = 45))
            .copy(alarmKey = "fired", backend = ReminderAlarmBackend.AppAlarmClock, requestCode = 1001)
        val future = sampleRecord(triggerAtMillis = futureMillis())
            .copy(alarmKey = "future", backend = ReminderAlarmBackend.AppAlarmClock, requestCode = 1002)
        val repository = FakeReminderRepository(rules = listOf(rule)).apply {
            records.value = listOf(fired, future)
        }
        val appDismisser = FakeAlarmDismisser(succeeded = true)
        val coordinator = ReminderCoordinator(
            context = ContextWrapper(null),
            repository = repository,
            appDismisser = appDismisser,
        )

        coordinator.consumeTriggeredAppAlarm("fired", "rule")

        assertEquals(listOf(rule), repository.getReminderRules())
        assertEquals(listOf(future), repository.records.value)
        assertEquals(0, appDismisser.dismissCount)
    }

    @Test
    fun `finishing triggered app alarm with snooze records snoozed alarm`() = runBlocking {
        val fired = sampleRecord(triggerAtMillis = sampleNowMillis(hour = 7, minute = 45))
            .copy(alarmKey = "fired", backend = ReminderAlarmBackend.AppAlarmClock, requestCode = 1001)
        val future = sampleRecord(triggerAtMillis = futureMillis())
            .copy(alarmKey = "future", backend = ReminderAlarmBackend.AppAlarmClock, requestCode = 1002)
        val repository = FakeReminderRepository(rules = listOf(sampleRule())).apply {
            records.value = listOf(fired, future)
        }
        val appDispatcher = FakeAlarmDispatcher(
            succeeded = true,
            channel = AlarmDispatchChannel.AppAlarmClock,
        )
        val appDismisser = FakeAlarmDismisser(succeeded = true)
        val coordinator = ReminderCoordinator(
            context = ContextWrapper(null),
            repository = repository,
            appDispatcher = appDispatcher,
            appDismisser = appDismisser,
        )
        val snoozePlan = sampleSnoozePlan()

        val result = coordinator.finishTriggeredAppAlarm(
            alarmKey = "fired",
            ruleId = "rule",
            action = TriggeredAppAlarmFinishAction.Snooze(snoozePlan),
        )

        val record = repository.records.value.single()
        assertEquals(true, result.consumed)
        assertEquals(true, result.snoozeCreated)
        assertEquals(emptyList<ReminderRule>(), repository.getReminderRules())
        assertEquals(snoozePlan.systemAlarmKey(), record.alarmKey)
        assertEquals(AppAlarmOperationMode.SnoozeForegroundService, record.operationMode)
        assertEquals("高等数学", record.displayTitle)
        assertEquals("已延后 5 分钟", record.displayMessage)
        assertEquals(1, appDispatcher.dispatchCount)
        assertEquals(1, appDismisser.dismissCount)
    }

    @Test
    fun `failed snooze dispatch is not recorded`() = runBlocking {
        val fired = sampleRecord(triggerAtMillis = sampleNowMillis(hour = 7, minute = 45))
            .copy(alarmKey = "fired", backend = ReminderAlarmBackend.AppAlarmClock, requestCode = 1001)
        val repository = FakeReminderRepository(rules = listOf(sampleRule())).apply {
            records.value = listOf(fired)
        }
        val appDispatcher = FakeAlarmDispatcher(
            succeeded = false,
            channel = AlarmDispatchChannel.AppAlarmClock,
        )
        val coordinator = ReminderCoordinator(
            context = ContextWrapper(null),
            repository = repository,
            appDispatcher = appDispatcher,
        )

        val result = coordinator.finishTriggeredAppAlarm(
            alarmKey = "fired",
            ruleId = "rule",
            action = TriggeredAppAlarmFinishAction.Snooze(sampleSnoozePlan()),
        )

        assertEquals(true, result.consumed)
        assertEquals(false, result.snoozeCreated)
        assertEquals(emptyList<ReminderRule>(), repository.getReminderRules())
        assertEquals(emptyList<SystemAlarmRecord>(), repository.records.value)
        assertEquals(1, appDispatcher.dispatchCount)
    }

    @Test
    fun `app managed sync keeps active snooze records outside stale cleanup`() = runBlocking {
        val profile = sampleProfile()
        val nowMillis = sampleNowMillis(hour = 7, minute = 0)
        val snoozeRecord = sampleSnoozePlan(triggerAtMillis = sampleNowMillis(hour = 7, minute = 50))
            .let { plan ->
                SystemAlarmRecord(
                    alarmKey = plan.systemAlarmKey(),
                    ruleId = plan.ruleId,
                    pluginId = plan.pluginId,
                    planId = plan.planId,
                    courseId = plan.courseId,
                    triggerAtMillis = plan.triggerAtMillis,
                    message = "课表提醒 · 高等数学 · 07:50",
                    alarmLabel = "课表提醒 · 高等数学 · 07:50",
                    backend = ReminderAlarmBackend.AppAlarmClock,
                    requestCode = 1003,
                    operationMode = AppAlarmOperationMode.SnoozeForegroundService,
                    displayTitle = plan.title,
                    displayMessage = plan.message,
                    createdAtMillis = nowMillis,
                )
            }
        val repository = FakeReminderRepository(rules = emptyList()).apply {
            records.value = listOf(snoozeRecord)
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
            window = ReminderSyncWindows.todayFromNow(profile, nowMillis),
            reason = ReminderSyncReason.ScheduleChanged,
            nowMillis = nowMillis,
        )

        assertEquals(0, summary.dismissedCount)
        assertEquals(0, appDismisser.dismissCount)
        assertEquals(listOf(snoozeRecord), repository.records.value)
    }

    @Test
    fun `exam reminder sync clears expired muted exam ids and keeps future ones`() = runBlocking {
        val rule = ReminderRule(
            ruleId = "exam-rule",
            pluginId = "demo",
            scopeType = ReminderScopeType.Exam,
            mutedCourseIds = listOf("past-exam", "future-exam"),
            advanceMinutes = 40,
            createdAt = "2026-02-23T00:00:00+08:00",
            updatedAt = "2026-02-23T00:00:00+08:00",
        )
        val repository = FakeReminderRepository(rules = listOf(rule))
        val coordinator = ReminderCoordinator(
            context = ContextWrapper(null),
            repository = repository,
            alarmSettingsProvider = { ReminderAlarmSettings(backend = ReminderAlarmBackend.SystemClockApp) },
            systemDispatcher = FakeAlarmDispatcher(succeeded = true),
        )
        val profile = sampleProfile()
        val nowMillis = LocalDateTime.of(2026, 3, 2, 7, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        coordinator.syncSystemClockAlarmsForWindow(
            pluginId = "demo",
            schedule = sampleExamSchedule(),
            timingProfile = profile,
            window = ReminderSyncWindows.todayFromNow(profile, nowMillis),
            reason = ReminderSyncReason.ScheduleChanged,
            nowMillis = nowMillis,
        )

        val updatedRule = repository.getReminderRules().single()
        assertEquals(listOf("future-exam"), updatedRule.mutedCourseIds)
    }

    @Test
    fun `muting exam reminder does not dismiss independent single exam reminder`() = runBlocking {
        val examRule = ReminderRule(
            ruleId = "exam-rule",
            pluginId = "demo",
            scopeType = ReminderScopeType.Exam,
            advanceMinutes = 40,
            createdAt = "2026-02-23T00:00:00+08:00",
            updatedAt = "2026-02-23T00:00:00+08:00",
        )
        val singleRule = sampleRule().copy(ruleId = "single-rule", courseId = "future-exam")
        val examRecord = sampleRecord(triggerAtMillis = futureMillis()).copy(
            alarmKey = "exam-alarm",
            ruleId = "exam-rule",
            courseId = "future-exam",
        )
        val singleRecord = sampleRecord(triggerAtMillis = futureMillis() + 60_000).copy(
            alarmKey = "single-alarm",
            ruleId = "single-rule",
            courseId = "future-exam",
        )
        val repository = FakeReminderRepository(rules = listOf(examRule, singleRule)).apply {
            records.value = listOf(examRecord, singleRecord)
        }
        val dismisser = FakeAlarmDismisser(succeeded = true)
        val coordinator = ReminderCoordinator(
            context = ContextWrapper(null),
            repository = repository,
            systemDismisser = dismisser,
        )

        coordinator.setExamReminderMuted(pluginId = "demo", courseId = "future-exam", muted = true)

        val updatedExamRule = repository.getReminderRules().first { it.ruleId == "exam-rule" }
        assertEquals(listOf("future-exam"), updatedExamRule.mutedCourseIds)
        assertEquals(listOf(singleRecord), repository.records.value)
        assertEquals(1, dismisser.dismissCount)
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

    private fun sampleExamSchedule(): TermSchedule = TermSchedule(
        termId = "2026-spring",
        updatedAt = "2026-02-23T00:00:00+08:00",
        dailySchedules = listOf(
            DailySchedule(
                dayOfWeek = 1,
                courses = listOf(
                    CourseItem(
                        id = "past-exam",
                        title = "高等数学期中",
                        weeks = listOf(1),
                        category = CourseCategory.Exam,
                        time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
                    ),
                    CourseItem(
                        id = "future-exam",
                        title = "高等数学期末",
                        weeks = listOf(2),
                        category = CourseCategory.Exam,
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

    private fun sampleSnoozePlan(triggerAtMillis: Long = futureMillis()): ReminderPlan = ReminderPlan(
        planId = "plan-snooze-$triggerAtMillis",
        ruleId = "rule",
        pluginId = "demo",
        title = "高等数学",
        message = "已延后 5 分钟",
        triggerAtMillis = triggerAtMillis,
        ringtoneUri = null,
        courseId = "math",
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
        private val occupanciesState = MutableStateFlow<List<ReminderCustomOccupancy>>(emptyList())
        val records = MutableStateFlow<List<SystemAlarmRecord>>(emptyList())

        override val reminderRulesFlow: Flow<List<ReminderRule>> = rulesState
        override val customOccupanciesFlow: Flow<List<ReminderCustomOccupancy>> = occupanciesState
        override val systemAlarmRecordsFlow: Flow<List<SystemAlarmRecord>> = records

        override suspend fun getReminderRules(): List<ReminderRule> = rulesState.value

        override suspend fun saveReminderRule(rule: ReminderRule) {
            rulesState.value = rulesState.value.filterNot { it.ruleId == rule.ruleId } + rule
        }

        override suspend fun removeReminderRule(ruleId: String) {
            rulesState.value = rulesState.value.filterNot { it.ruleId == ruleId }
        }

        override suspend fun getCustomOccupancies(pluginId: String?): List<ReminderCustomOccupancy> {
            return pluginId?.let { id ->
                occupanciesState.value.filter { it.pluginId == id }
            } ?: occupanciesState.value
        }

        override suspend fun saveCustomOccupancy(occupancy: ReminderCustomOccupancy) {
            occupanciesState.value = occupanciesState.value.filterNot {
                it.occupancyId == occupancy.occupancyId
            } + occupancy
        }

        override suspend fun removeCustomOccupancy(occupancyId: String) {
            occupanciesState.value = occupanciesState.value.filterNot { it.occupancyId == occupancyId }
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
