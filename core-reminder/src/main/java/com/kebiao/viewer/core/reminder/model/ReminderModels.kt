package com.kebiao.viewer.core.reminder.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId

@Serializable
data class ReminderRule(
    @SerialName("ruleId") val ruleId: String,
    @SerialName("pluginId") val pluginId: String,
    @SerialName("scopeType") val scopeType: ReminderScopeType,
    @SerialName("period") val period: ReminderDayPeriod? = null,
    @SerialName("periodStartNode") val periodStartNode: Int? = null,
    @SerialName("periodEndNode") val periodEndNode: Int? = null,
    @SerialName("mutedNodeRanges") val mutedNodeRanges: List<ReminderNodeRange> = emptyList(),
    @SerialName("courseId") val courseId: String? = null,
    @SerialName("dayOfWeek") val dayOfWeek: Int? = null,
    @SerialName("startNode") val startNode: Int? = null,
    @SerialName("endNode") val endNode: Int? = null,
    @SerialName("advanceMinutes") val advanceMinutes: Int,
    @SerialName("ringtoneUri") val ringtoneUri: String? = null,
    @SerialName("dispatchMode") val dispatchMode: ReminderDispatchMode = ReminderDispatchMode.SystemFirst,
    @SerialName("enabled") val enabled: Boolean = true,
    @SerialName("createdAt") val createdAt: String,
    @SerialName("updatedAt") val updatedAt: String,
)

@Serializable
enum class ReminderScopeType {
    @SerialName("single_course")
    SingleCourse,

    @SerialName("time_slot")
    TimeSlot,

    @SerialName("first_course_of_period")
    FirstCourseOfPeriod,
}

@Serializable
data class ReminderNodeRange(
    @SerialName("startNode") val startNode: Int,
    @SerialName("endNode") val endNode: Int = startNode,
) {
    fun overlaps(start: Int, end: Int): Boolean {
        val normalizedStart = minOf(startNode, endNode)
        val normalizedEnd = maxOf(startNode, endNode)
        return start <= normalizedEnd && end >= normalizedStart
    }

    fun normalized(): ReminderNodeRange = ReminderNodeRange(
        startNode = minOf(startNode, endNode),
        endNode = maxOf(startNode, endNode),
    )
}

@Serializable
enum class ReminderDayPeriod {
    @SerialName("morning")
    Morning,

    @SerialName("afternoon")
    Afternoon,

    @SerialName("evening")
    Evening,
}

@Serializable
enum class ReminderDispatchMode {
    @SerialName("system_first")
    SystemFirst,
}

@Serializable
enum class ReminderAlarmBackend {
    @SerialName("app_alarm_clock")
    AppAlarmClock,

    @SerialName("system_clock_app")
    SystemClockApp,
}

@Serializable
enum class AppAlarmOperationMode {
    @SerialName("legacy_broadcast")
    LegacyBroadcast,

    @SerialName("foreground_service")
    ForegroundService,

    @SerialName("snooze_foreground_service")
    SnoozeForegroundService,
}

data class ReminderAlarmSettings(
    val backend: ReminderAlarmBackend = ReminderAlarmBackend.AppAlarmClock,
    val ringDurationSeconds: Int = 60,
    val repeatIntervalSeconds: Int = 120,
    val repeatCount: Int = 1,
)

data class ReminderPlan(
    val planId: String,
    val ruleId: String,
    val pluginId: String,
    val title: String,
    val message: String,
    val triggerAtMillis: Long,
    val ringtoneUri: String?,
    val courseId: String?,
)

sealed interface TriggeredAppAlarmFinishAction {
    data object Dismiss : TriggeredAppAlarmFinishAction

    data class Snooze(val plan: ReminderPlan) : TriggeredAppAlarmFinishAction
}

data class TriggeredAppAlarmFinishResult(
    val consumed: Boolean,
    val snoozeCreated: Boolean = false,
    val message: String = "",
)

data class ReminderSyncWindow(
    val startMillis: Long,
    val endMillis: Long,
)

enum class ReminderSyncReason {
    RuleCreatedToday,
    DailyNextDay,
    AfterClassToday,
    ScheduleChanged,
    WidgetRefresh,
}

@Serializable
data class SystemAlarmRecord(
    @SerialName("alarmKey") val alarmKey: String,
    @SerialName("ruleId") val ruleId: String,
    @SerialName("pluginId") val pluginId: String,
    @SerialName("planId") val planId: String,
    @SerialName("courseId") val courseId: String? = null,
    @SerialName("triggerAtMillis") val triggerAtMillis: Long,
    @SerialName("message") val message: String,
    @SerialName("alarmLabel") val alarmLabel: String? = null,
    @SerialName("backend") val backend: ReminderAlarmBackend = ReminderAlarmBackend.SystemClockApp,
    @SerialName("requestCode") val requestCode: Int? = null,
    @SerialName("operationMode") val operationMode: AppAlarmOperationMode = AppAlarmOperationMode.LegacyBroadcast,
    @SerialName("displayTitle") val displayTitle: String? = null,
    @SerialName("displayMessage") val displayMessage: String? = null,
    @SerialName("createdAtMillis") val createdAtMillis: Long,
)

data class SystemAlarmSyncSummary(
    val submittedCount: Int,
    val createdCount: Int,
    val skippedExistingCount: Int,
    val skippedUnrepresentableCount: Int,
    val results: List<AlarmDispatchResult>,
    val expiredRecordClearedCount: Int = 0,
    val dismissedCount: Int = 0,
    val dismissFailedCount: Int = 0,
) {
    val failedCount: Int = results.count { !it.succeeded }
}

fun ReminderPlan.systemAlarmKey(): String =
    listOf(pluginId, triggerAtMillis.toString(), title, message, ringtoneUri.orEmpty()).joinToString("|")

fun ReminderPlan.appAlarmRequestCode(): Int = systemAlarmKey().hashCode() and Int.MAX_VALUE

fun ReminderPlan.toAppAlarmRecord(
    operationMode: AppAlarmOperationMode,
    createdAtMillis: Long = System.currentTimeMillis(),
): SystemAlarmRecord {
    val label = systemAlarmLabel()
    return SystemAlarmRecord(
        alarmKey = systemAlarmKey(),
        ruleId = ruleId,
        pluginId = pluginId,
        planId = planId,
        courseId = courseId,
        triggerAtMillis = triggerAtMillis,
        message = label,
        alarmLabel = label,
        backend = ReminderAlarmBackend.AppAlarmClock,
        requestCode = appAlarmRequestCode(),
        operationMode = operationMode,
        displayTitle = title,
        displayMessage = message,
        createdAtMillis = createdAtMillis,
    )
}

fun ReminderPlan.systemAlarmLabel(): String {
    val trigger = Instant.ofEpochMilli(triggerAtMillis).atZone(ZoneId.systemDefault())
    val time = "${trigger.hour.toString().padStart(2, '0')}:${trigger.minute.toString().padStart(2, '0')}"
    return "课表提醒 · $title · $time"
}

enum class AlarmDispatchChannel {
    AppAlarmClock,
    SystemClockApp,
}

data class AlarmDispatchResult(
    val channel: AlarmDispatchChannel,
    val succeeded: Boolean,
    val message: String,
)

data class AlarmDismissResult(
    val alarmKey: String,
    val succeeded: Boolean,
    val message: String,
)
