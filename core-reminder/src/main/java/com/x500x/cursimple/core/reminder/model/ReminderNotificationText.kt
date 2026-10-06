package com.x500x.cursimple.core.reminder.model

import android.content.Context
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.weekdayNameRes
import com.x500x.cursimple.core.reminder.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Pure title fields for UI-localized reminder notifications. */
@Serializable
data class ReminderNotificationTitle(
    @SerialName("dayOfWeek") val dayOfWeek: Int,
    @SerialName("startTime") val startTime: String,
    @SerialName("courseTitle") val courseTitle: String,
    @SerialName("exam") val exam: Boolean = false,
    @SerialName("firstCoursePeriod") val firstCoursePeriod: ReminderDayPeriod? = null,
    @SerialName("advanceMinutes") val advanceMinutes: Int = 0,
)

@Serializable
data class ReminderNotificationMessage(
    @SerialName("month") val month: Int,
    @SerialName("dayOfMonth") val dayOfMonth: Int,
    @SerialName("dayOfWeek") val dayOfWeek: Int,
    @SerialName("startTime") val startTime: String,
    @SerialName("endTime") val endTime: String,
    @SerialName("startNode") val startNode: Int,
    @SerialName("endNode") val endNode: Int,
    @SerialName("location") val location: String,
    /** Optional single-slot name; multi-slot and legacy plans use period numbers. */
    @SerialName("slotLabel") val slotLabel: String = "",
)

fun Context.reminderNotificationTitleText(title: ReminderNotificationTitle): String {
    val course = if (title.exam) {
        getString(R.string.reminder_notification_title_exam, title.courseTitle)
    } else {
        title.courseTitle
    }
    val body = when (title.firstCoursePeriod) {
        ReminderDayPeriod.Morning ->
            getString(R.string.reminder_notification_title_first_course_morning, course)
        ReminderDayPeriod.Afternoon ->
            getString(R.string.reminder_notification_title_first_course_afternoon, course)
        ReminderDayPeriod.Evening ->
            getString(R.string.reminder_notification_title_first_course_evening, course)
        null -> course
    }
    val withAdvance = if (title.advanceMinutes > 0) {
        getString(R.string.reminder_notification_title_advance, body, title.advanceMinutes)
    } else {
        body
    }
    return getString(
        R.string.reminder_notification_title,
        weekdayText(title.dayOfWeek),
        title.startTime,
        withAdvance,
    )
}

fun Context.reminderNotificationMessageText(message: ReminderNotificationMessage): String = getString(
    R.string.reminder_notification_message,
    getString(R.string.reminder_notification_date, message.month, message.dayOfMonth),
    weekdayText(message.dayOfWeek),
    message.startTime,
    message.endTime,
    reminderNotificationNodesText(message),
    message.location.ifBlank { getString(R.string.reminder_notification_location_tbd) },
)

/** Prefer the slot label, followed by its period range. */
private fun Context.reminderNotificationNodesText(message: ReminderNotificationMessage): String {
    val label = message.slotLabel.trim()
    if (label.isEmpty()) {
        return getString(R.string.reminder_notification_nodes, message.startNode, message.endNode)
    }
    return getString(
        R.string.reminder_notification_nodes_labeled,
        label,
        "${message.startNode}-${message.endNode}",
    )
}

/** Localize typed plan text; otherwise preserve stored text. */
fun Context.reminderPlanTitleText(plan: ReminderPlan): String =
    plan.titleContent?.let { reminderNotificationTitleText(it) } ?: plan.title

fun Context.reminderPlanMessageText(plan: ReminderPlan): String =
    plan.messageContent?.let { reminderNotificationMessageText(it) } ?: plan.message

/** Stable title for alarm registration and deduplication, independent of display locale. */
fun ReminderNotificationTitle.stableText(): String {
    val course = if (exam) "考试：$courseTitle" else courseTitle
    val prefix = when (firstCoursePeriod) {
        ReminderDayPeriod.Morning -> "上午首次课："
        ReminderDayPeriod.Afternoon -> "下午首次课："
        ReminderDayPeriod.Evening -> "晚上首次课："
        null -> ""
    }
    val advance = if (advanceMinutes > 0) "（提前${advanceMinutes}分钟）" else ""
    return "${stableWeekdayName(dayOfWeek)} $startTime $prefix$course$advance"
}

fun ReminderNotificationMessage.stableText(): String {
    val date = "${month}月${dayOfMonth}日"
    val weekday = stableWeekdayName(dayOfWeek)
    val timeRange = "$startTime-$endTime"
    val nodes = "第$startNode-${endNode}节"
    return "$date $weekday $timeRange · $nodes · ${location.ifBlank { "待定教室" }}"
}

private fun Context.weekdayText(dayOfWeek: Int): String =
    if (dayOfWeek in 1..7) {
        getString(weekdayNameRes(dayOfWeek))
    } else {
        getString(R.string.reminder_weekday_other, dayOfWeek)
    }

private fun stableWeekdayName(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    7 -> "周日"
    else -> "周$dayOfWeek"
}

/** Use a slot name only when it fully contains the course. */
fun ClassSlotTime.notificationLabelFor(startNode: Int, endNode: Int): String =
    label.trim().takeIf { this.startNode <= startNode && this.endNode >= endNode }.orEmpty()
