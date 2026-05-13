package com.x500x.cursimple.core.reminder

import android.content.Context
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.reminder.dispatch.AlarmDispatcher
import com.x500x.cursimple.core.reminder.dispatch.AlarmDismisser
import com.x500x.cursimple.core.reminder.dispatch.AppAlarmClockDispatcher
import com.x500x.cursimple.core.reminder.dispatch.AppAlarmClockDismisser
import com.x500x.cursimple.core.reminder.dispatch.SystemAlarmClockDispatcher
import com.x500x.cursimple.core.reminder.dispatch.SystemAlarmClockDismisser
import com.x500x.cursimple.core.reminder.model.AlarmDispatchChannel
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import com.x500x.cursimple.core.reminder.model.AlarmDispatchResult
import com.x500x.cursimple.core.reminder.model.AlarmDismissResult
import com.x500x.cursimple.core.reminder.model.AppAlarmOperationMode
import com.x500x.cursimple.core.reminder.model.FirstCourseCandidateScope
import com.x500x.cursimple.core.reminder.model.ReminderAlarmBackend
import com.x500x.cursimple.core.reminder.model.ReminderAlarmSettings
import com.x500x.cursimple.core.reminder.model.ReminderAction
import com.x500x.cursimple.core.reminder.model.ReminderCondition
import com.x500x.cursimple.core.reminder.model.ReminderConditionMode
import com.x500x.cursimple.core.reminder.model.ReminderCustomOccupancy
import com.x500x.cursimple.core.reminder.model.ReminderPlan
import com.x500x.cursimple.core.reminder.model.ReminderDayPeriod
import com.x500x.cursimple.core.reminder.model.ReminderNodeRange
import com.x500x.cursimple.core.reminder.model.ReminderRule
import com.x500x.cursimple.core.reminder.model.ReminderSyncReason
import com.x500x.cursimple.core.reminder.model.ReminderSyncWindow
import com.x500x.cursimple.core.reminder.model.ReminderScopeType
import com.x500x.cursimple.core.reminder.model.SystemAlarmRecord
import com.x500x.cursimple.core.reminder.model.SystemAlarmSyncSummary
import com.x500x.cursimple.core.reminder.model.ReminderTimeRange
import com.x500x.cursimple.core.reminder.model.TriggeredAppAlarmFinishAction
import com.x500x.cursimple.core.reminder.model.TriggeredAppAlarmFinishResult
import com.x500x.cursimple.core.reminder.model.appAlarmRequestCode
import com.x500x.cursimple.core.reminder.model.systemAlarmKey
import com.x500x.cursimple.core.reminder.model.systemAlarmLabel
import com.x500x.cursimple.core.reminder.model.toAppAlarmRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

class ReminderCoordinator(
    context: Context,
    private val repository: ReminderRepository,
    private val planner: ReminderPlanner = ReminderPlanner(),
    private val temporaryScheduleOverridesProvider: suspend () -> List<TemporaryScheduleOverride> = { emptyList() },
    private val alarmSettingsProvider: suspend () -> ReminderAlarmSettings = { ReminderAlarmSettings() },
    private val appDispatcher: AlarmDispatcher = AppAlarmClockDispatcher(context),
    private val appDismisser: AlarmDismisser = AppAlarmClockDismisser(context),
    private val systemDispatcher: AlarmDispatcher = SystemAlarmClockDispatcher(context),
    private val systemDismisser: AlarmDismisser = SystemAlarmClockDismisser(context),
) {

    val reminderRulesFlow: Flow<List<ReminderRule>> = repository.reminderRulesFlow

    val customOccupanciesFlow: Flow<List<ReminderCustomOccupancy>> = repository.customOccupanciesFlow

    val systemAlarmRecordsFlow: Flow<List<SystemAlarmRecord>> = repository.systemAlarmRecordsFlow

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
        val existing = repository.getReminderRules().firstOrNull {
            it.hasSameDefinition(
                pluginId = pluginId,
                courseId = courseId,
                dayOfWeek = dayOfWeek,
                startNode = startNode,
                endNode = endNode,
                scopeType = scopeType,
                advanceMinutes = advanceMinutes,
                ringtoneUri = ringtoneUri,
            )
        }
        if (existing != null) {
            val rule = existing.copy(enabled = true, updatedAt = now)
            repository.saveReminderRule(rule)
            return rule
        }
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
        periodStartNode: Int? = null,
        periodEndNode: Int? = null,
        mutedNodeRanges: List<ReminderNodeRange> = emptyList(),
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
            periodStartNode = periodStartNode,
            periodEndNode = periodEndNode,
            mutedNodeRanges = mutedNodeRanges.map { it.normalized() },
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
            periodStartNode = periodStartNode,
            periodEndNode = periodEndNode,
            mutedNodeRanges = mutedNodeRanges.map { it.normalized() },
        )
        repository.saveReminderRule(rule)
        return rule
    }

    suspend fun upsertFlexibleFirstCourseReminder(
        pluginId: String,
        ruleId: String?,
        displayName: String,
        enabled: Boolean,
        advanceMinutes: Int,
        ringtoneUri: String?,
        candidate: FirstCourseCandidateScope,
        conditionMode: ReminderConditionMode,
        conditions: List<ReminderCondition>,
        actions: List<ReminderAction>,
    ): ReminderRule {
        val now = OffsetDateTime.now().toString()
        val existing = ruleId?.let { id ->
            repository.getReminderRules().firstOrNull {
                it.ruleId == id && it.pluginId == pluginId && it.scopeType == ReminderScopeType.FirstCourseOfPeriod
            }
        }
        val rule = (existing ?: ReminderRule(
            ruleId = UUID.randomUUID().toString(),
            pluginId = pluginId,
            scopeType = ReminderScopeType.FirstCourseOfPeriod,
            advanceMinutes = advanceMinutes,
            ringtoneUri = ringtoneUri,
            enabled = enabled,
            createdAt = now,
            updatedAt = now,
        )).copy(
            displayName = displayName.takeIf { it.isNotBlank() },
            advanceMinutes = advanceMinutes,
            ringtoneUri = ringtoneUri,
            enabled = enabled,
            updatedAt = now,
            firstCourseCandidate = candidate,
            conditionMode = conditionMode,
            conditions = conditions,
            actions = actions,
            periodStartNode = candidate.nodeRange?.startNode,
            periodEndNode = candidate.nodeRange?.endNode,
            mutedNodeRanges = emptyList(),
        )
        repository.saveReminderRule(rule)
        return rule
    }

    suspend fun upsertCustomOccupancy(
        pluginId: String,
        occupancyId: String?,
        name: String,
        timeRange: ReminderTimeRange,
        daysOfWeek: List<Int>,
        weeks: List<Int>,
        includeDates: List<String>,
        excludeDates: List<String>,
        linkedNodeRange: ReminderNodeRange?,
    ): ReminderCustomOccupancy {
        val now = OffsetDateTime.now().toString()
        val existing = occupancyId?.let { id ->
            repository.getCustomOccupancies(pluginId).firstOrNull { it.occupancyId == id }
        }
        val occupancy = (existing ?: ReminderCustomOccupancy(
            occupancyId = UUID.randomUUID().toString(),
            pluginId = pluginId,
            name = name,
            timeRange = timeRange,
            createdAt = now,
            updatedAt = now,
        )).copy(
            name = name,
            timeRange = timeRange,
            daysOfWeek = daysOfWeek.distinct().sorted(),
            weeks = weeks.distinct().sorted(),
            includeDates = includeDates.distinct().sorted(),
            excludeDates = excludeDates.distinct().sorted(),
            linkedNodeRange = linkedNodeRange?.normalized(),
            updatedAt = now,
        )
        repository.saveCustomOccupancy(occupancy)
        return occupancy
    }

    suspend fun removeCustomOccupancy(occupancyId: String) {
        repository.removeCustomOccupancy(occupancyId)
    }

    suspend fun upsertExamReminder(
        pluginId: String,
        enabled: Boolean,
        advanceMinutes: Int,
        ringtoneUri: String?,
    ): ReminderRule = SYSTEM_ALARM_LOCK.withLock {
        val now = OffsetDateTime.now().toString()
        val existing = repository.getReminderRules().firstOrNull {
            it.pluginId == pluginId && it.scopeType == ReminderScopeType.Exam
        }
        val rule = (existing ?: ReminderRule(
            ruleId = UUID.randomUUID().toString(),
            pluginId = pluginId,
            scopeType = ReminderScopeType.Exam,
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
        )
        repository.saveReminderRule(rule)
        if (!enabled) {
            val records = repository.getSystemAlarmRecords()
                .filter { it.ruleId == rule.ruleId && it.triggerAtMillis > System.currentTimeMillis() }
            dismissRecords(records)
        }
        rule
    }

    suspend fun setExamReminderMuted(
        pluginId: String,
        courseId: String,
        muted: Boolean,
    ): ReminderRule? = SYSTEM_ALARM_LOCK.withLock {
        val existing = repository.getReminderRules().firstOrNull {
            it.pluginId == pluginId && it.scopeType == ReminderScopeType.Exam
        } ?: return@withLock null
        val nextMutedCourseIds = if (muted) {
            (existing.mutedCourseIds + courseId).distinct()
        } else {
            existing.mutedCourseIds.filterNot { it == courseId }
        }
        val rule = existing.copy(
            mutedCourseIds = nextMutedCourseIds,
            updatedAt = OffsetDateTime.now().toString(),
        )
        repository.saveReminderRule(rule)
        if (muted) {
            val records = repository.getSystemAlarmRecords()
                .filter {
                    it.ruleId == existing.ruleId &&
                        it.pluginId == pluginId &&
                        it.courseId == courseId &&
                        it.triggerAtMillis > System.currentTimeMillis()
                }
            dismissRecords(records)
        }
        rule
    }

    suspend fun deleteRule(ruleId: String) {
        SYSTEM_ALARM_LOCK.withLock {
            val nowMillis = System.currentTimeMillis()
            val records = repository.getSystemAlarmRecords().filter { it.ruleId == ruleId }
            dismissRecords(records.filter { it.triggerAtMillis > nowMillis })
            repository.removeReminderRule(ruleId)
            repository.removeSystemAlarmRecordsForRule(ruleId)
        }
    }

    suspend fun clearSystemAlarmRecords() {
        SYSTEM_ALARM_LOCK.withLock {
            val nowMillis = System.currentTimeMillis()
            dismissRecords(
                repository.getSystemAlarmRecords().filter { it.triggerAtMillis > nowMillis },
            )
            repository.clearSystemAlarmRecords()
        }
    }

    suspend fun deleteAlarmRecord(
        alarmKey: String,
        backend: ReminderAlarmBackend,
    ): AlarmDismissResult = SYSTEM_ALARM_LOCK.withLock {
        val record = repository.getSystemAlarmRecords()
            .firstOrNull { it.alarmKey == alarmKey && it.backend == backend }
            ?: return@withLock AlarmDismissResult(
                alarmKey = alarmKey,
                succeeded = true,
                message = "闹钟登记已不存在",
            )
        if (record.triggerAtMillis <= System.currentTimeMillis()) {
            repository.removeSystemAlarmRecord(record.alarmKey, record.backend)
            return@withLock AlarmDismissResult(
                alarmKey = record.alarmKey,
                succeeded = true,
                message = "已移除过期闹钟登记",
            )
        }
        val dismisser = when (record.backend) {
            ReminderAlarmBackend.AppAlarmClock -> appDismisser
            ReminderAlarmBackend.SystemClockApp -> systemDismisser
        }
        val result = runCatching {
            dismisser.dismiss(record)
        }.getOrElse { error ->
            ReminderLogger.warn(
                "reminder.system_clock.dismiss.single_unhandled_failure",
                mapOf("alarmKey" to record.alarmKey, "backend" to record.backend.name),
                error,
            )
            AlarmDismissResult(
                alarmKey = record.alarmKey,
                succeeded = false,
                message = error.message ?: "取消闹钟失败",
            )
        }
        if (result.succeeded) {
            repository.removeSystemAlarmRecord(record.alarmKey, record.backend)
        }
        result
    }

    suspend fun consumeTriggeredAppAlarm(
        alarmKey: String,
        ruleId: String,
    ) {
        finishTriggeredAppAlarm(
            alarmKey = alarmKey,
            ruleId = ruleId,
            action = TriggeredAppAlarmFinishAction.Dismiss,
        )
    }

    suspend fun finishTriggeredAppAlarm(
        alarmKey: String,
        ruleId: String,
        action: TriggeredAppAlarmFinishAction,
    ): TriggeredAppAlarmFinishResult = SYSTEM_ALARM_LOCK.withLock {
        val rule = repository.getReminderRules().firstOrNull { it.ruleId == ruleId }
        if (rule == null) {
            repository.removeSystemAlarmRecord(alarmKey, ReminderAlarmBackend.AppAlarmClock)
            return@withLock finishTriggeredAction(action)
        }

        repository.removeSystemAlarmRecord(alarmKey, ReminderAlarmBackend.AppAlarmClock)
        if (rule.shouldDeleteAfterAppAlarmRing()) {
            val nowMillis = System.currentTimeMillis()
            val records = repository.getSystemAlarmRecords().filter { it.ruleId == ruleId }
            dismissRecords(records.filter { it.triggerAtMillis > nowMillis })
            repository.removeReminderRule(ruleId)
            repository.removeSystemAlarmRecordsForRule(ruleId)
        }
        finishTriggeredAction(action)
    }

    suspend fun syncSystemClockAlarmsForWindow(
        pluginId: String,
        schedule: TermSchedule,
        timingProfile: TermTimingProfile?,
        window: ReminderSyncWindow,
        reason: ReminderSyncReason,
        nowMillis: Long = System.currentTimeMillis(),
        clearExpiredRecords: Boolean = true,
    ): SystemAlarmSyncSummary = syncAlarmsForWindow(
        pluginId = pluginId,
        schedule = schedule,
        timingProfile = timingProfile,
        window = window,
        reason = reason,
        nowMillis = nowMillis,
        clearExpiredRecords = clearExpiredRecords,
    )

    suspend fun syncAlarmsForWindow(
        pluginId: String,
        schedule: TermSchedule,
        timingProfile: TermTimingProfile?,
        window: ReminderSyncWindow,
        reason: ReminderSyncReason,
        nowMillis: Long = System.currentTimeMillis(),
        clearExpiredRecords: Boolean = true,
    ): SystemAlarmSyncSummary = SYSTEM_ALARM_LOCK.withLock {
        val expiredAppDismissal = if (clearExpiredRecords && reason == ReminderSyncReason.AfterClassToday) {
            dismissExpiredAppAlarmRecords(nowMillis)
        } else {
            DismissStats()
        }
        val expiredRecordClearedCount = if (clearExpiredRecords) clearExpiredRecordsBefore(nowMillis) else 0
        val profile = timingProfile ?: return@withLock emptySystemAlarmSyncSummary(
            expiredRecordClearedCount = expiredRecordClearedCount,
            dismissedCount = expiredAppDismissal.dismissedCount,
            dismissFailedCount = expiredAppDismissal.failedCount,
        )
        val settings = alarmSettingsProvider()
        val zone = ZoneId.of(profile.timezone)
        val systemClockZone = ZoneId.systemDefault()
        val temporaryScheduleOverrides = temporaryScheduleOverridesProvider()
        val customOccupancies = repository.getCustomOccupancies(pluginId)
        cleanupExpiredExamMutes(
            pluginId = pluginId,
            schedule = schedule,
            timingProfile = profile,
            fromDate = Instant.ofEpochMilli(window.startMillis).atZone(zone).toLocalDate(),
            temporaryScheduleOverrides = temporaryScheduleOverrides,
        )
        val rules = repository.getReminderRules()
            .filter { it.enabled && it.pluginId == pluginId }
        val plans = rules.flatMap { rule ->
            planner.expandRule(
                rule = rule,
                schedule = schedule,
                timingProfile = profile,
                fromDate = Instant.ofEpochMilli(window.startMillis).atZone(zone).toLocalDate(),
                temporaryScheduleOverrides = temporaryScheduleOverrides,
                customOccupancies = customOccupancies,
            )
        }.asSequence()
            .filter { it.triggerAtMillis in window.startMillis..window.endMillis }
            .distinctBy { it.systemAlarmKey() }
            .sortedBy { it.triggerAtMillis }
            .toList()
        val plannedKeys = plans.mapTo(mutableSetOf()) { it.systemAlarmKey() }
        val outdatedAppOperationDismissal = if (settings.backend == ReminderAlarmBackend.AppAlarmClock) {
            dismissOutdatedAppAlarmOperationRecords(
                pluginId = pluginId,
                window = window,
            )
        } else {
            DismissStats()
        }
        val staleDismissal = dismissStaleRecordsInWindow(
            pluginId = pluginId,
            plannedKeys = plannedKeys,
            window = window,
            backend = settings.backend,
        )
        val existingKeys = runCatching {
            repository.getSystemAlarmRecords()
                .filter { it.backend == settings.backend }
                .filter { settings.backend != ReminderAlarmBackend.AppAlarmClock || it.operationMode == CURRENT_APP_ALARM_OPERATION_MODE }
                .mapTo(mutableSetOf()) { it.alarmKey }
        }.getOrElse { error ->
            ReminderLogger.warn(
                "reminder.system_clock.registry.read.failure",
                mapOf("pluginId" to pluginId),
                error,
            )
            mutableSetOf()
        }
        ReminderLogger.info(
            "reminder.system_clock.sync.start",
            mapOf(
                "pluginId" to pluginId,
                "ruleCount" to rules.size,
                "planCount" to plans.size,
                "reason" to reason.name,
                "backend" to settings.backend.name,
                "windowStartMillis" to window.startMillis,
                "windowEndMillis" to window.endMillis,
            ),
        )
        var skippedExisting = 0
        var skippedUnrepresentable = 0
        var registryWriteFailed = 0
        val results = mutableListOf<AlarmDispatchResult>()
        plans.forEach { plan ->
            val key = plan.systemAlarmKey()
            if (key in existingKeys) {
                skippedExisting += 1
                return@forEach
            }
            if (
                settings.backend == ReminderAlarmBackend.SystemClockApp &&
                !plan.canBeRepresentedBySystemClock(nowMillis = nowMillis, zone = systemClockZone)
            ) {
                skippedUnrepresentable += 1
                ReminderLogger.warn(
                    "reminder.system_clock.sync.unrepresentable",
                    mapOf(
                        "ruleId" to plan.ruleId,
                        "planId" to plan.planId,
                        "triggerAtMillis" to plan.triggerAtMillis,
                        "nowMillis" to nowMillis,
                    ),
                )
                return@forEach
            }
            val dispatcher = when (settings.backend) {
                ReminderAlarmBackend.AppAlarmClock -> appDispatcher
                ReminderAlarmBackend.SystemClockApp -> systemDispatcher
            }
            val result = dispatcher.dispatch(plan)
            results += result
            if (result.succeeded) {
                runCatching {
                    val label = plan.systemAlarmLabel()
                    repository.saveSystemAlarmRecord(
                        SystemAlarmRecord(
                            alarmKey = key,
                            ruleId = plan.ruleId,
                            pluginId = plan.pluginId,
                            planId = plan.planId,
                            courseId = plan.courseId,
                            triggerAtMillis = plan.triggerAtMillis,
                            message = label,
                            alarmLabel = label,
                            backend = settings.backend,
                            requestCode = if (settings.backend == ReminderAlarmBackend.AppAlarmClock) {
                                plan.appAlarmRequestCode()
                            } else {
                                null
                            },
                            operationMode = if (settings.backend == ReminderAlarmBackend.AppAlarmClock) {
                                CURRENT_APP_ALARM_OPERATION_MODE
                            } else {
                                AppAlarmOperationMode.LegacyBroadcast
                            },
                            displayTitle = plan.title,
                            displayMessage = plan.message,
                            createdAtMillis = System.currentTimeMillis(),
                        ),
                    )
                }.onSuccess {
                    existingKeys += key
                }.onFailure { error ->
                    registryWriteFailed += 1
                    ReminderLogger.warn(
                        "reminder.system_clock.registry.write.failure",
                        mapOf("ruleId" to plan.ruleId, "planId" to plan.planId),
                        error,
                    )
                }
            }
        }
        val summary = SystemAlarmSyncSummary(
            submittedCount = results.size,
            createdCount = results.count { it.succeeded },
            skippedExistingCount = skippedExisting,
            skippedUnrepresentableCount = skippedUnrepresentable,
            results = results,
            expiredRecordClearedCount = expiredRecordClearedCount,
            dismissedCount = expiredAppDismissal.dismissedCount +
                outdatedAppOperationDismissal.dismissedCount +
                staleDismissal.dismissedCount,
            dismissFailedCount = expiredAppDismissal.failedCount +
                outdatedAppOperationDismissal.failedCount +
                staleDismissal.failedCount,
            registryWriteFailedCount = registryWriteFailed,
        )
        ReminderLogger.info(
            "reminder.system_clock.sync.finish",
            mapOf(
                "pluginId" to pluginId,
                "planCount" to plans.size,
                "backend" to settings.backend.name,
                "submittedCount" to summary.submittedCount,
                "createdCount" to summary.createdCount,
                "skippedExistingCount" to summary.skippedExistingCount,
                "skippedUnrepresentableCount" to summary.skippedUnrepresentableCount,
                "expiredRecordClearedCount" to summary.expiredRecordClearedCount,
                "dismissedCount" to summary.dismissedCount,
                "dismissFailedCount" to summary.dismissFailedCount,
                "registryWriteFailedCount" to summary.registryWriteFailedCount,
                "failureCount" to summary.failedCount,
            ),
        )
        summary
    }

    private suspend fun dismissExpiredAppAlarmRecords(nowMillis: Long): DismissStats {
        val records = runCatching {
            repository.getSystemAlarmRecords()
                .filter { it.backend == ReminderAlarmBackend.AppAlarmClock && it.triggerAtMillis < nowMillis }
        }.getOrElse { error ->
            ReminderLogger.warn(
                "reminder.app_alarm_clock.registry.read_expired.failure",
                mapOf("nowMillis" to nowMillis),
                error,
            )
            emptyList()
        }
        return dismissRecords(records)
    }

    private suspend fun cleanupExpiredExamMutes(
        pluginId: String,
        schedule: TermSchedule,
        timingProfile: TermTimingProfile,
        fromDate: LocalDate,
        temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
    ) {
        repository.getReminderRules()
            .filter {
                it.pluginId == pluginId &&
                    it.scopeType == ReminderScopeType.Exam &&
                    it.mutedCourseIds.isNotEmpty()
            }
            .forEach { rule ->
                val activeMutedIds = rule.mutedCourseIds.filter { courseId ->
                    hasExamOccurrenceOnOrAfter(
                        courseId = courseId,
                        schedule = schedule,
                        timingProfile = timingProfile,
                        fromDate = fromDate,
                        temporaryScheduleOverrides = temporaryScheduleOverrides,
                    )
                }
                if (activeMutedIds != rule.mutedCourseIds) {
                    repository.saveReminderRule(
                        rule.copy(
                            mutedCourseIds = activeMutedIds,
                            updatedAt = OffsetDateTime.now().toString(),
                        ),
                    )
                }
            }
    }

    private fun hasExamOccurrenceOnOrAfter(
        courseId: String,
        schedule: TermSchedule,
        timingProfile: TermTimingProfile,
        fromDate: LocalDate,
        temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
    ): Boolean {
        val course = schedule.dailySchedules
            .flatMap { it.courses }
            .firstOrNull { it.id == courseId && it.category == CourseCategory.Exam }
            ?: return false
        val rule = ReminderRule(
            ruleId = "exam-mute-cleanup-$courseId",
            pluginId = "",
            scopeType = ReminderScopeType.SingleCourse,
            courseId = course.id,
            advanceMinutes = 0,
            createdAt = "",
            updatedAt = "",
        )
        return planner.expandRule(
            rule = rule,
            schedule = schedule,
            timingProfile = timingProfile,
            fromDate = fromDate,
            temporaryScheduleOverrides = temporaryScheduleOverrides,
        ).isNotEmpty()
    }

    private suspend fun dismissOutdatedAppAlarmOperationRecords(
        pluginId: String,
        window: ReminderSyncWindow,
    ): DismissStats {
        val records = runCatching {
            repository.getSystemAlarmRecords()
                .filter { record ->
                    record.pluginId == pluginId &&
                        record.backend == ReminderAlarmBackend.AppAlarmClock &&
                        record.operationMode != CURRENT_APP_ALARM_OPERATION_MODE &&
                        record.operationMode != AppAlarmOperationMode.SnoozeForegroundService &&
                        record.triggerAtMillis in window.startMillis..window.endMillis
                }
        }.getOrElse { error ->
            ReminderLogger.warn(
                "reminder.app_alarm_clock.registry.read_outdated_operation.failure",
                mapOf("pluginId" to pluginId),
                error,
            )
            emptyList()
        }
        return dismissRecords(records)
    }

    private suspend fun clearExpiredRecordsBefore(cutoffMillis: Long): Int {
        val records = runCatching {
            repository.getSystemAlarmRecords()
                .filter { it.triggerAtMillis < cutoffMillis }
        }.getOrElse { error ->
            ReminderLogger.warn(
                "reminder.system_clock.registry.read_for_cleanup.failure",
                mapOf("cutoffMillis" to cutoffMillis),
                error,
            )
            return 0
        }
        val clearedCount = records.distinctBy { it.alarmKey }.size
        if (clearedCount == 0) return 0
        return runCatching {
            repository.clearSystemAlarmRecordsBefore(cutoffMillis)
            ReminderLogger.info(
                "reminder.system_clock.registry.expired_cleanup.success",
                mapOf("cutoffMillis" to cutoffMillis, "clearedCount" to clearedCount),
            )
            clearedCount
        }.getOrElse { error ->
            ReminderLogger.warn(
                "reminder.system_clock.registry.expired_cleanup.failure",
                mapOf("cutoffMillis" to cutoffMillis, "clearedCount" to clearedCount),
                error,
            )
            0
        }
    }

    private suspend fun dismissStaleRecordsInWindow(
        pluginId: String,
        plannedKeys: Set<String>,
        window: ReminderSyncWindow,
        backend: ReminderAlarmBackend,
    ): DismissStats {
        val records = runCatching {
            repository.getSystemAlarmRecords()
                .filter { record ->
                    record.pluginId == pluginId &&
                        record.backend == backend &&
                        record.triggerAtMillis in window.startMillis..window.endMillis &&
                        (backend != ReminderAlarmBackend.AppAlarmClock ||
                            record.operationMode != AppAlarmOperationMode.SnoozeForegroundService) &&
                        record.alarmKey !in plannedKeys
                }
        }.getOrElse { error ->
            ReminderLogger.warn(
                "reminder.system_clock.registry.read_stale.failure",
                mapOf("pluginId" to pluginId),
                error,
            )
            emptyList()
        }
        return dismissRecords(records)
    }

    private suspend fun dismissRecords(records: List<SystemAlarmRecord>): DismissStats {
        if (records.isEmpty()) return DismissStats()
        var dismissed = 0
        var failed = 0
        records.distinctBy { it.backend to it.alarmKey }.forEach { record ->
            val dismisser = when (record.backend) {
                ReminderAlarmBackend.AppAlarmClock -> appDismisser
                ReminderAlarmBackend.SystemClockApp -> systemDismisser
            }
            val result = runCatching {
                dismisser.dismiss(record)
            }.getOrElse { error ->
                ReminderLogger.warn(
                    "reminder.system_clock.dismiss.unhandled_failure",
                    mapOf("alarmKey" to record.alarmKey),
                    error,
                )
                AlarmDismissResult(
                    alarmKey = record.alarmKey,
                    succeeded = false,
                    message = error.message ?: "删除闹钟失败",
                )
            }
            if (result.succeeded) {
                dismissed += 1
                runCatching {
                    repository.removeSystemAlarmRecord(record.alarmKey, record.backend)
                }.onFailure { error ->
                    ReminderLogger.warn(
                        "reminder.system_clock.registry.remove_after_dismiss.failure",
                        mapOf("alarmKey" to record.alarmKey),
                        error,
                    )
                }
            } else {
                failed += 1
            }
        }
        return DismissStats(dismissedCount = dismissed, failedCount = failed)
    }

    private suspend fun finishTriggeredAction(
        action: TriggeredAppAlarmFinishAction,
    ): TriggeredAppAlarmFinishResult = when (action) {
        TriggeredAppAlarmFinishAction.Dismiss -> TriggeredAppAlarmFinishResult(
            consumed = true,
            message = "闹钟已关闭",
        )

        is TriggeredAppAlarmFinishAction.Snooze -> {
            val result = runCatching {
                appDispatcher.dispatch(action.plan)
            }.getOrElse { error ->
                ReminderLogger.warn(
                    "reminder.app_alarm_clock.snooze.dispatch_unhandled_failure",
                    mapOf("ruleId" to action.plan.ruleId, "planId" to action.plan.planId),
                    error,
                )
                AlarmDispatchResult(
                    channel = AlarmDispatchChannel.AppAlarmClock,
                    succeeded = false,
                    message = error.message ?: "延后闹钟设置失败",
                )
            }
            if (result.succeeded) {
                repository.saveSystemAlarmRecord(
                    action.plan.toAppAlarmRecord(
                        operationMode = AppAlarmOperationMode.SnoozeForegroundService,
                    ),
                )
                TriggeredAppAlarmFinishResult(
                    consumed = true,
                    snoozeCreated = true,
                    message = "已延后 5 分钟",
                )
            } else {
                TriggeredAppAlarmFinishResult(
                    consumed = true,
                    snoozeCreated = false,
                    message = result.message,
                )
            }
        }
    }
}

private val SYSTEM_ALARM_LOCK = Mutex()

private val CURRENT_APP_ALARM_OPERATION_MODE = AppAlarmOperationMode.ForegroundService

private data class DismissStats(
    val dismissedCount: Int = 0,
    val failedCount: Int = 0,
)

private fun emptySystemAlarmSyncSummary(
    expiredRecordClearedCount: Int = 0,
    dismissedCount: Int = 0,
    dismissFailedCount: Int = 0,
): SystemAlarmSyncSummary = SystemAlarmSyncSummary(
    submittedCount = 0,
    createdCount = 0,
    skippedExistingCount = 0,
    skippedUnrepresentableCount = 0,
    results = emptyList(),
    expiredRecordClearedCount = expiredRecordClearedCount,
    dismissedCount = dismissedCount,
    dismissFailedCount = dismissFailedCount,
)

object ReminderSyncWindows {
    fun todayFromNow(
        timingProfile: TermTimingProfile,
        nowMillis: Long = System.currentTimeMillis(),
    ): ReminderSyncWindow {
        val zone = ZoneId.of(timingProfile.timezone)
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        return ReminderSyncWindow(
            startMillis = nowMillis,
            endMillis = endOfDayMillis(now.toLocalDate(), zone),
        )
    }

    fun nextDay(
        timingProfile: TermTimingProfile,
        nowMillis: Long = System.currentTimeMillis(),
    ): ReminderSyncWindow {
        val zone = ZoneId.of(timingProfile.timezone)
        val nextDay = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().plusDays(1)
        return ReminderSyncWindow(
            startMillis = nextDay.atStartOfDay(zone).toInstant().toEpochMilli(),
            endMillis = endOfDayMillis(nextDay, zone),
        )
    }

    private fun endOfDayMillis(date: LocalDate, zone: ZoneId): Long =
        date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
}

private fun ReminderPlan.canBeRepresentedBySystemClock(
    nowMillis: Long,
    zone: ZoneId,
): Boolean {
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDateTime()
    val trigger = Instant.ofEpochMilli(triggerAtMillis).atZone(zone).toLocalDateTime()
    if (!trigger.isAfter(now)) return false
    val today = now.toLocalDate()
    return when (trigger.toLocalDate()) {
        today -> true
        today.plusDays(1) -> trigger.toLocalTime().isBefore(now.toLocalTime())
        else -> false
    }
}

private fun ReminderRule.hasSameDefinition(
    pluginId: String,
    courseId: String?,
    dayOfWeek: Int?,
    startNode: Int?,
    endNode: Int?,
    scopeType: ReminderScopeType,
    advanceMinutes: Int,
    ringtoneUri: String?,
): Boolean =
    this.pluginId == pluginId &&
        this.courseId == courseId &&
        this.dayOfWeek == dayOfWeek &&
        this.startNode == startNode &&
        this.endNode == endNode &&
        this.scopeType == scopeType &&
        this.advanceMinutes == advanceMinutes &&
        this.ringtoneUri.normalizeRingtoneUri() == ringtoneUri.normalizeRingtoneUri()

private fun String?.normalizeRingtoneUri(): String? = takeUnless { it.isNullOrBlank() }

private fun ReminderRule.shouldDeleteAfterAppAlarmRing(): Boolean =
    scopeType != ReminderScopeType.FirstCourseOfPeriod && scopeType != ReminderScopeType.Exam
