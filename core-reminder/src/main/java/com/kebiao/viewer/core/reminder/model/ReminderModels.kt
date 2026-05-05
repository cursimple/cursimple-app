package com.kebiao.viewer.core.reminder.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReminderRule(
    @SerialName("ruleId") val ruleId: String,
    @SerialName("pluginId") val pluginId: String,
    @SerialName("scopeType") val scopeType: ReminderScopeType,
    @SerialName("period") val period: ReminderDayPeriod? = null,
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
enum class ReminderDayPeriod {
    @SerialName("morning")
    Morning,

    @SerialName("afternoon")
    Afternoon,
}

@Serializable
enum class ReminderDispatchMode {
    @SerialName("system_first")
    SystemFirst,
}

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

enum class AlarmDispatchChannel {
    SystemClock,
    AppFallback,
}

data class AlarmDispatchResult(
    val channel: AlarmDispatchChannel,
    val succeeded: Boolean,
    val message: String,
)
