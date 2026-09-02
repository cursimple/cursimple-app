package com.x500x.cursimple.core.reminder.model

import android.content.Context
import com.x500x.cursimple.core.kernel.model.weekdayNameRes
import com.x500x.cursimple.core.reminder.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 提醒通知标题的组成部分。
 * 逻辑层只给出字段，文字由界面层按当前语言渲染，纯逻辑因此不依赖 Context。
 */
@Serializable
data class ReminderNotificationTitle(
    @SerialName("dayOfWeek") val dayOfWeek: Int,
    @SerialName("startTime") val startTime: String,
    @SerialName("courseTitle") val courseTitle: String,
    @SerialName("exam") val exam: Boolean = false,
    @SerialName("firstCoursePeriod") val firstCoursePeriod: ReminderDayPeriod? = null,
    @SerialName("advanceMinutes") val advanceMinutes: Int = 0,
)

/** 提醒通知正文的组成部分，location 为空表示教室待定。 */
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
    getString(R.string.reminder_notification_nodes, message.startNode, message.endNode),
    message.location.ifBlank { getString(R.string.reminder_notification_location_tbd) },
)

/** 计划带类型文案时按当前语言渲染，否则用计划里已有的文本。 */
fun Context.reminderPlanTitleText(plan: ReminderPlan): String =
    plan.titleContent?.let { reminderNotificationTitleText(it) } ?: plan.title

fun Context.reminderPlanMessageText(plan: ReminderPlan): String =
    plan.messageContent?.let { reminderNotificationMessageText(it) } ?: plan.message

/**
 * 与界面语言无关的标题文本。
 * 闹钟登记按标题与正文去重，登记表与已下发的闹钟里保存的是这一份取值，切换语言不会改变它。
 */
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

/** 与界面语言无关的正文文本，用途同 [stableText]。 */
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
