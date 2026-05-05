package com.kebiao.viewer.core.reminder

import android.content.Context
import com.kebiao.viewer.core.kernel.model.TermSchedule
import com.kebiao.viewer.core.kernel.model.TermTimingProfile
import com.kebiao.viewer.core.kernel.model.TemporaryScheduleOverride
import com.kebiao.viewer.core.reminder.dispatch.AlarmDispatcher
import com.kebiao.viewer.core.reminder.dispatch.AlarmDismisser
import com.kebiao.viewer.core.reminder.dispatch.SystemAlarmClockDispatcher
import com.kebiao.viewer.core.reminder.dispatch.SystemAlarmClockDismisser
import com.kebiao.viewer.core.reminder.logging.ReminderLogger
import com.kebiao.viewer.core.reminder.model.AlarmDispatchResult
import com.kebiao.viewer.core.reminder.model.AlarmDismissResult
import com.kebiao.viewer.core.reminder.model.ReminderPlan
import com.kebiao.viewer.core.reminder.model.ReminderDayPeriod
import com.kebiao.viewer.core.reminder.model.ReminderRule
import com.kebiao.viewer.core.reminder.model.ReminderSyncReason
import com.kebiao.viewer.core.reminder.model.ReminderSyncWindow
import com.kebiao.viewer.core.reminder.model.ReminderScopeType
import com.kebiao.viewer.core.reminder.model.SystemAlarmRecord
import com.kebiao.viewer.core.reminder.model.SystemAlarmSyncSummary
import com.kebiao.viewer.core.reminder.model.systemAlarmKey
import com.kebiao.viewer.core.reminder.model.systemAlarmLabel
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
    private val systemDispatcher: AlarmDispatcher = SystemAlarmClockDispatcher(context),
    private val systemDismisser: AlarmDismisser = SystemAlarmClockDismisser(context),
) {

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
        SYSTEM_ALARM_LOCK.withLock {
            val records = repository.getSystemAlarmRecords().filter { it.ruleId == ruleId }
            dismissRecords(records)
            repository.removeReminderRule(ruleId)
            repository.removeSystemAlarmRecordsForRule(ruleId)
        }
    }

    suspend fun clearSystemAlarmRecords() {
        SYSTEM_ALARM_LOCK.withLock {
            dismissRecords(repository.getSystemAlarmRecords())
            repository.clearSystemAlarmRecords()
        }
    }

    suspend fun syncSystemClockAlarmsForWindow(
        pluginId: String,
        schedule: TermSchedule,
        timingProfile: TermTimingProfile?,
        window: ReminderSyncWindow,
        reason: ReminderSyncReason,
        nowMillis: Long = System.currentTimeMillis(),
    ): SystemAlarmSyncSummary = SYSTEM_ALARM_LOCK.withLock {
        val expiredDismissal = dismissRecordsBefore(nowMillis)
        val profile = timingProfile ?: return@withLock emptySystemAlarmSyncSummary(
            dismissedCount = expiredDismissal.dismissedCount,
            dismissFailedCount = expiredDismissal.failedCount,
        )
        val zone = ZoneId.of(profile.timezone)
        val systemClockZone = ZoneId.systemDefault()
        val temporaryScheduleOverrides = temporaryScheduleOverridesProvider()
        val rules = repository.getReminderRules()
            .filter { it.enabled && it.pluginId == pluginId }
        val plans = rules.flatMap { rule ->
            planner.expandRule(
                rule = rule,
                schedule = schedule,
                timingProfile = profile,
                fromDate = Instant.ofEpochMilli(window.startMillis).atZone(zone).toLocalDate(),
                temporaryScheduleOverrides = temporaryScheduleOverrides,
            )
        }.asSequence()
            .filter { it.triggerAtMillis in window.startMillis..window.endMillis }
            .distinctBy { it.systemAlarmKey() }
            .sortedBy { it.triggerAtMillis }
            .toList()
        val plannedKeys = plans.mapTo(mutableSetOf()) { it.systemAlarmKey() }
        val staleDismissal = dismissStaleRecordsInWindow(
            pluginId = pluginId,
            plannedKeys = plannedKeys,
            window = window,
        )
        val existingKeys = runCatching {
            repository.getSystemAlarmRecords().mapTo(mutableSetOf()) { it.alarmKey }
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
                "windowStartMillis" to window.startMillis,
                "windowEndMillis" to window.endMillis,
            ),
        )
        var skippedExisting = 0
        var skippedUnrepresentable = 0
        val results = mutableListOf<AlarmDispatchResult>()
        plans.forEach { plan ->
            val key = plan.systemAlarmKey()
            if (key in existingKeys) {
                skippedExisting += 1
                return@forEach
            }
            if (!plan.canBeRepresentedBySystemClock(nowMillis = nowMillis, zone = systemClockZone)) {
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
            val result = systemDispatcher.dispatch(plan)
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
                            createdAtMillis = System.currentTimeMillis(),
                        ),
                    )
                }.onSuccess {
                    existingKeys += key
                }.onFailure { error ->
                    ReminderLogger.warn(
                        "reminder.system_clock.registry.write.failure",
                        mapOf("ruleId" to plan.ruleId, "planId" to plan.planId),
                        error,
                    )
                }
            }
        }
        val dismissedCount = expiredDismissal.dismissedCount + staleDismissal.dismissedCount
        val dismissFailedCount = expiredDismissal.failedCount + staleDismissal.failedCount
        val summary = SystemAlarmSyncSummary(
            submittedCount = results.size,
            createdCount = results.count { it.succeeded },
            skippedExistingCount = skippedExisting,
            skippedUnrepresentableCount = skippedUnrepresentable,
            results = results,
            dismissedCount = dismissedCount,
            dismissFailedCount = dismissFailedCount,
        )
        ReminderLogger.info(
            "reminder.system_clock.sync.finish",
            mapOf(
                "pluginId" to pluginId,
                "planCount" to plans.size,
                "submittedCount" to summary.submittedCount,
                "createdCount" to summary.createdCount,
                "skippedExistingCount" to summary.skippedExistingCount,
                "skippedUnrepresentableCount" to summary.skippedUnrepresentableCount,
                "dismissedCount" to summary.dismissedCount,
                "dismissFailedCount" to summary.dismissFailedCount,
                "failureCount" to summary.failedCount,
            ),
        )
        summary
    }

    private suspend fun dismissRecordsBefore(cutoffMillis: Long): DismissStats {
        val records = runCatching {
            repository.getSystemAlarmRecords()
                .filter { it.triggerAtMillis < cutoffMillis }
        }.getOrElse { error ->
            ReminderLogger.warn(
                "reminder.system_clock.registry.read_for_cleanup.failure",
                mapOf("cutoffMillis" to cutoffMillis),
                error,
            )
            emptyList()
        }
        return dismissRecords(records)
    }

    private suspend fun dismissStaleRecordsInWindow(
        pluginId: String,
        plannedKeys: Set<String>,
        window: ReminderSyncWindow,
    ): DismissStats {
        val records = runCatching {
            repository.getSystemAlarmRecords()
                .filter { record ->
                    record.pluginId == pluginId &&
                        record.triggerAtMillis in window.startMillis..window.endMillis &&
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
        records.distinctBy { it.alarmKey }.forEach { record ->
            val result = runCatching {
                systemDismisser.dismiss(record)
            }.getOrElse { error ->
                ReminderLogger.warn(
                    "reminder.system_clock.dismiss.unhandled_failure",
                    mapOf("alarmKey" to record.alarmKey),
                    error,
                )
                AlarmDismissResult(
                    alarmKey = record.alarmKey,
                    succeeded = false,
                    message = error.message ?: "删除系统闹钟失败",
                )
            }
            if (result.succeeded) {
                dismissed += 1
                runCatching {
                    repository.removeSystemAlarmRecord(record.alarmKey)
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
}

private val SYSTEM_ALARM_LOCK = Mutex()

private data class DismissStats(
    val dismissedCount: Int = 0,
    val failedCount: Int = 0,
)

private fun emptySystemAlarmSyncSummary(
    dismissedCount: Int = 0,
    dismissFailedCount: Int = 0,
): SystemAlarmSyncSummary = SystemAlarmSyncSummary(
    submittedCount = 0,
    createdCount = 0,
    skippedExistingCount = 0,
    skippedUnrepresentableCount = 0,
    results = emptyList(),
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
