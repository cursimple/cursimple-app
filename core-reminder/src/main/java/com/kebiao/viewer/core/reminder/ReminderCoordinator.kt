package com.kebiao.viewer.core.reminder

import android.content.Context
import com.kebiao.viewer.core.kernel.model.TermSchedule
import com.kebiao.viewer.core.kernel.model.TermTimingProfile
import com.kebiao.viewer.core.kernel.model.TemporaryScheduleOverride
import com.kebiao.viewer.core.reminder.dispatch.AlarmDispatcher
import com.kebiao.viewer.core.reminder.dispatch.SystemAlarmClockDispatcher
import com.kebiao.viewer.core.reminder.logging.ReminderLogger
import com.kebiao.viewer.core.reminder.model.AlarmDispatchResult
import com.kebiao.viewer.core.reminder.model.ReminderPlan
import com.kebiao.viewer.core.reminder.model.ReminderDayPeriod
import com.kebiao.viewer.core.reminder.model.ReminderRule
import com.kebiao.viewer.core.reminder.model.ReminderSyncReason
import com.kebiao.viewer.core.reminder.model.ReminderSyncWindow
import com.kebiao.viewer.core.reminder.model.ReminderScopeType
import com.kebiao.viewer.core.reminder.model.SystemAlarmRecord
import com.kebiao.viewer.core.reminder.model.SystemAlarmSyncSummary
import com.kebiao.viewer.core.reminder.model.systemAlarmKey
import kotlinx.coroutines.flow.Flow
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
        repository.removeReminderRule(ruleId)
        repository.removeSystemAlarmRecordsForRule(ruleId)
    }

    suspend fun clearSystemAlarmRecords() {
        repository.clearSystemAlarmRecords()
    }

    suspend fun syncSystemClockAlarmsForWindow(
        pluginId: String,
        schedule: TermSchedule,
        timingProfile: TermTimingProfile?,
        window: ReminderSyncWindow,
        reason: ReminderSyncReason,
        nowMillis: Long = System.currentTimeMillis(),
    ): SystemAlarmSyncSummary {
        val profile = timingProfile ?: return SystemAlarmSyncSummary(
            submittedCount = 0,
            createdCount = 0,
            skippedExistingCount = 0,
            skippedUnrepresentableCount = 0,
            results = emptyList(),
        )
        runCatching {
            repository.clearSystemAlarmRecordsBefore(window.startMillis)
        }.onFailure { error ->
            ReminderLogger.warn(
                "reminder.system_clock.registry.cleanup.failure",
                mapOf("cutoffMillis" to window.startMillis),
                error,
            )
        }
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
                    repository.saveSystemAlarmRecord(
                        SystemAlarmRecord(
                            alarmKey = key,
                            ruleId = plan.ruleId,
                            pluginId = plan.pluginId,
                            planId = plan.planId,
                            triggerAtMillis = plan.triggerAtMillis,
                            message = plan.title,
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
        val summary = SystemAlarmSyncSummary(
            submittedCount = results.size,
            createdCount = results.count { it.succeeded },
            skippedExistingCount = skippedExisting,
            skippedUnrepresentableCount = skippedUnrepresentable,
            results = results,
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
                "failureCount" to summary.failedCount,
            ),
        )
        return summary
    }
}

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
