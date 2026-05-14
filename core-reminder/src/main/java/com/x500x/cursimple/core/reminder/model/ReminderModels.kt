package com.x500x.cursimple.core.reminder.model

import com.x500x.cursimple.core.kernel.model.CourseCategory
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
    @SerialName("mutedCourseIds") val mutedCourseIds: List<String> = emptyList(),
    @SerialName("displayName") val displayName: String? = null,
    @SerialName("firstCourseCandidate") val firstCourseCandidate: FirstCourseCandidateScope? = null,
    @SerialName("conditionMode") val conditionMode: ReminderConditionMode = ReminderConditionMode.All,
    @SerialName("conditions") val conditions: List<ReminderCondition> = emptyList(),
    @SerialName("actions") val actions: List<ReminderAction> = emptyList(),
    @SerialName("labelConditions") val labelConditions: List<ReminderLabelCondition> = emptyList(),
    @SerialName("labelActions") val labelActions: List<ReminderLabelAction> = emptyList(),
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

    @SerialName("exam")
    Exam,

    @SerialName("first_course_of_period")
    FirstCourseOfPeriod,

    @SerialName("label_rule")
    LabelRule,
}

@Serializable
enum class ReminderLabelPresence {
    @SerialName("exists")
    Exists,

    @SerialName("absent")
    Absent,
}

@Serializable
data class ReminderLabelCondition(
    @SerialName("slotLabel") val slotLabel: String,
    @SerialName("presence") val presence: ReminderLabelPresence,
)

@Serializable
enum class ReminderLabelActionType {
    @SerialName("remind")
    Remind,

    @SerialName("skip")
    Skip,
}

@Serializable
data class ReminderLabelAction(
    @SerialName("slotLabel") val slotLabel: String,
    @SerialName("action") val action: ReminderLabelActionType,
)

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
data class ReminderTimeRange(
    @SerialName("startTime") val startTime: String,
    @SerialName("endTime") val endTime: String,
)

@Serializable
data class FirstCourseCandidateScope(
    @SerialName("daysOfWeek") val daysOfWeek: List<Int> = emptyList(),
    @SerialName("weeks") val weeks: List<Int> = emptyList(),
    @SerialName("includeDates") val includeDates: List<String> = emptyList(),
    @SerialName("excludeDates") val excludeDates: List<String> = emptyList(),
    @SerialName("timeRange") val timeRange: ReminderTimeRange? = null,
    @SerialName("nodeRange") val nodeRange: ReminderNodeRange? = null,
    @SerialName("categories") val categories: List<CourseCategory> = listOf(CourseCategory.Course),
    @SerialName("titleContains") val titleContains: String? = null,
    @SerialName("teacherContains") val teacherContains: String? = null,
    @SerialName("locationContains") val locationContains: String? = null,
)

@Serializable
enum class ReminderConditionMode {
    @SerialName("all")
    All,

    @SerialName("any")
    Any,
}

@Serializable
enum class ReminderConditionType {
    @SerialName("course_exists_in_nodes")
    CourseExistsInNodes,

    @SerialName("course_absent_in_nodes")
    CourseAbsentInNodes,

    @SerialName("course_exists_in_time")
    CourseExistsInTime,

    @SerialName("course_absent_in_time")
    CourseAbsentInTime,

    @SerialName("occupancy_exists")
    OccupancyExists,

    @SerialName("occupancy_absent")
    OccupancyAbsent,

    @SerialName("occupancy_overlaps_course")
    OccupancyOverlapsCourse,

    @SerialName("occupancy_before_course")
    OccupancyBeforeCourse,

    @SerialName("weekday_matches")
    WeekdayMatches,

    @SerialName("week_matches")
    WeekMatches,

    @SerialName("date_matches")
    DateMatches,

    @SerialName("course_text_matches")
    CourseTextMatches,
}

@Serializable
data class ReminderCondition(
    @SerialName("type") val type: ReminderConditionType,
    @SerialName("nodeRange") val nodeRange: ReminderNodeRange? = null,
    @SerialName("timeRange") val timeRange: ReminderTimeRange? = null,
    @SerialName("occupancyId") val occupancyId: String? = null,
    @SerialName("daysOfWeek") val daysOfWeek: List<Int> = emptyList(),
    @SerialName("weeks") val weeks: List<Int> = emptyList(),
    @SerialName("dates") val dates: List<String> = emptyList(),
    @SerialName("text") val text: String? = null,
)

@Serializable
enum class ReminderActionType {
    @SerialName("remind_first_candidate")
    RemindFirstCandidate,

    @SerialName("skip")
    Skip,

    @SerialName("continue_after_node")
    ContinueAfterNode,

    @SerialName("continue_after_time")
    ContinueAfterTime,

    @SerialName("use_candidate_scope")
    UseCandidateScope,
}

@Serializable
data class ReminderAction(
    @SerialName("type") val type: ReminderActionType,
    @SerialName("afterNode") val afterNode: Int? = null,
    @SerialName("afterTime") val afterTime: String? = null,
    @SerialName("candidateScope") val candidateScope: FirstCourseCandidateScope? = null,
)

@Serializable
data class ReminderCustomOccupancy(
    @SerialName("occupancyId") val occupancyId: String,
    @SerialName("pluginId") val pluginId: String,
    @SerialName("name") val name: String,
    @SerialName("timeRange") val timeRange: ReminderTimeRange,
    @SerialName("daysOfWeek") val daysOfWeek: List<Int> = emptyList(),
    @SerialName("weeks") val weeks: List<Int> = emptyList(),
    @SerialName("includeDates") val includeDates: List<String> = emptyList(),
    @SerialName("excludeDates") val excludeDates: List<String> = emptyList(),
    @SerialName("linkedNodeRange") val linkedNodeRange: ReminderNodeRange? = null,
    @SerialName("createdAt") val createdAt: String,
    @SerialName("updatedAt") val updatedAt: String,
)

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

const val DEFAULT_APP_ALARM_RING_DURATION_SECONDS = 2 * 60
const val DEFAULT_APP_ALARM_REPEAT_INTERVAL_SECONDS = 60 * 5
const val DEFAULT_APP_ALARM_REPEAT_COUNT = 5

data class ReminderAlarmSettings(
    val backend: ReminderAlarmBackend = ReminderAlarmBackend.AppAlarmClock,
    val ringDurationSeconds: Int = DEFAULT_APP_ALARM_RING_DURATION_SECONDS,
    val repeatIntervalSeconds: Int = DEFAULT_APP_ALARM_REPEAT_INTERVAL_SECONDS,
    val repeatCount: Int = DEFAULT_APP_ALARM_REPEAT_COUNT,
)

data class EditableAppAlarmSettings(
    val ringtoneUriOverride: String? = null,
    val ringDurationSeconds: Int? = null,
    val repeatIntervalSeconds: Int? = null,
    val repeatCount: Int? = null,
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
    val ringDurationSeconds: Int? = null,
    val repeatIntervalSeconds: Int? = null,
    val repeatCount: Int? = null,
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
    AlarmRuntime,
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
    @SerialName("enabled") val enabled: Boolean = true,
    @SerialName("ringDurationSeconds") val ringDurationSeconds: Int? = null,
    @SerialName("repeatIntervalSeconds") val repeatIntervalSeconds: Int? = null,
    @SerialName("repeatCount") val repeatCount: Int? = null,
    @SerialName("ringtoneUriOverride") val ringtoneUriOverride: String? = null,
    @SerialName("manualAlarm") val manualAlarm: Boolean = false,
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
    val registryWriteFailedCount: Int = 0,
) {
    val failedCount: Int = results.count { !it.succeeded } + registryWriteFailedCount
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
        ringDurationSeconds = ringDurationSeconds,
        repeatIntervalSeconds = repeatIntervalSeconds,
        repeatCount = repeatCount,
        ringtoneUriOverride = ringtoneUri,
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
