package com.x500x.cursimple.core.reminder.model

import android.content.Context
import com.x500x.cursimple.core.reminder.R

/**
 * 提醒操作产生的用户可见提示。
 * 逻辑层只判定属于哪一种结果，文字由界面层按当前语言渲染，纯逻辑因此不依赖 Context。
 */
sealed interface ReminderMessage {
    /** 重建 App 自管闹钟失败，cause 为底层异常原文，缺失时用本地化兜底。 */
    data class RebuildAppAlarmFailed(val cause: String? = null) : ReminderMessage

    /** 取消闹钟失败，cause 为底层异常原文，缺失时用本地化兜底。 */
    data class CancelAlarmFailed(val cause: String? = null) : ReminderMessage

    /** 关闭闹钟失败，cause 为底层异常原文，缺失时用本地化兜底。 */
    data class DisableAlarmFailed(val cause: String? = null) : ReminderMessage

    /** 启用闹钟失败，cause 为底层异常原文，缺失时用本地化兜底。 */
    data class EnableAlarmFailed(val cause: String? = null) : ReminderMessage

    /** 删除闹钟失败，cause 为底层异常原文，缺失时用本地化兜底。 */
    data class DeleteAlarmFailed(val cause: String? = null) : ReminderMessage

    /** 延后闹钟设置失败，cause 为底层异常原文，缺失时用本地化兜底。 */
    data class SnoozeSetupFailed(val cause: String? = null) : ReminderMessage

    /** 闹钟登记已不存在。 */
    data object RegistrationMissing : ReminderMessage

    /** 已移除过期闹钟登记。 */
    data object ExpiredRegistrationRemoved : ReminderMessage

    /** 闹钟时间已过，无法重新启用。 */
    data object AlarmTimePassed : ReminderMessage

    /** 闹钟已关闭。 */
    data object AlarmDismissed : ReminderMessage

    /** 已延后 5 分钟。 */
    data object SnoozedFiveMinutes : ReminderMessage

    /** 应用不在前台，系统时钟无法创建闹钟。 */
    data object SystemClockDispatchRequiresForeground : ReminderMessage

    /** 应用不在前台，系统时钟无法删除闹钟。 */
    data object SystemClockDismissRequiresForeground : ReminderMessage
}

fun Context.reminderMessageText(message: ReminderMessage): String = when (message) {
    is ReminderMessage.RebuildAppAlarmFailed ->
        message.cause ?: getString(R.string.reminder_rebuild_app_alarm_failed)
    is ReminderMessage.CancelAlarmFailed ->
        message.cause ?: getString(R.string.reminder_cancel_alarm_failed)
    is ReminderMessage.DisableAlarmFailed ->
        message.cause ?: getString(R.string.reminder_disable_alarm_failed)
    is ReminderMessage.EnableAlarmFailed ->
        message.cause ?: getString(R.string.reminder_enable_alarm_failed)
    is ReminderMessage.DeleteAlarmFailed ->
        message.cause ?: getString(R.string.reminder_delete_alarm_failed)
    is ReminderMessage.SnoozeSetupFailed ->
        message.cause ?: getString(R.string.reminder_snooze_setup_failed)
    ReminderMessage.RegistrationMissing -> getString(R.string.reminder_registration_missing)
    ReminderMessage.ExpiredRegistrationRemoved -> getString(R.string.reminder_expired_registration_removed)
    ReminderMessage.AlarmTimePassed -> getString(R.string.reminder_alarm_time_passed)
    ReminderMessage.AlarmDismissed -> getString(R.string.reminder_alarm_dismissed)
    ReminderMessage.SnoozedFiveMinutes -> getString(R.string.reminder_snoozed_five_minutes)
    ReminderMessage.SystemClockDispatchRequiresForeground ->
        getString(R.string.reminder_system_clock_dispatch_requires_foreground)
    ReminderMessage.SystemClockDismissRequiresForeground ->
        getString(R.string.reminder_system_clock_dismiss_requires_foreground)
}
