package com.x500x.cursimple.core.reminder.model

import android.content.Context
import com.x500x.cursimple.core.reminder.R

/** Typed reminder feedback localized by the UI. */
sealed interface ReminderMessage {
    /** Rebuild failure with optional underlying cause. */
    data class RebuildAppAlarmFailed(val cause: String? = null) : ReminderMessage

    /** Cancellation failure with optional underlying cause. */
    data class CancelAlarmFailed(val cause: String? = null) : ReminderMessage

    /** Dismissal failure with optional underlying cause. */
    data class DisableAlarmFailed(val cause: String? = null) : ReminderMessage

    /** Enablement failure with optional underlying cause. */
    data class EnableAlarmFailed(val cause: String? = null) : ReminderMessage

    /** Deletion failure with optional underlying cause. */
    data class DeleteAlarmFailed(val cause: String? = null) : ReminderMessage

    /** Snooze scheduling failure with optional underlying cause. */
    data class SnoozeSetupFailed(val cause: String? = null) : ReminderMessage

    data object RegistrationMissing : ReminderMessage

    data object ExpiredRegistrationRemoved : ReminderMessage

    data object AlarmTimePassed : ReminderMessage

    data object AlarmDismissed : ReminderMessage

    data object SnoozedFiveMinutes : ReminderMessage

    data object SystemClockDispatchRequiresForeground : ReminderMessage

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
