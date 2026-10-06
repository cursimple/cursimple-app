package com.x500x.cursimple.app.reminder

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.x500x.cursimple.R
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.reminder.DataStoreReminderRepository
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.reminder.dispatch.AppAlarmClockRegistrationVerifier
import com.x500x.cursimple.core.reminder.model.SystemAlarmRecord
import com.x500x.cursimple.core.reminder.permission.AlarmSettingsIntents
import com.x500x.cursimple.core.reminder.permission.canScheduleExactAlarms
import com.x500x.cursimple.core.reminder.permission.canUseFullScreenIntent
import com.x500x.cursimple.core.reminder.permission.hasNotificationPermission
import com.x500x.cursimple.core.reminder.permission.isIgnoringBatteryOptimizations
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Collect permission, channel, power, registration and arrival evidence for alarm diagnostics.
 */
data class AlarmDiagnosticsReport(
    val lines: List<Pair<String, String>>,
) {
    fun asText(): String = lines.joinToString("\n") { (label, value) -> "$label: $value" }
}

object AlarmDiagnostics {

    private val CLOCK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d HH:mm")

    suspend fun collect(context: Context): AlarmDiagnosticsReport {
        val appContext = context.applicationContext
        val records = runCatching {
            DataStoreReminderRepository(appContext).systemAlarmRecordsFlow.first()
        }.getOrDefault(emptyList())
        val preferences = runCatching {
            DataStoreUserPreferencesRepository(appContext).preferencesFlow.first()
        }.getOrNull()
        val silentGuard = preferences?.alarmKeepAliveEnabled ?: false

        val lines = buildList {
            add(appContext.getString(R.string.alarm_diag_device) to "${Build.MANUFACTURER} ${Build.MODEL}")
            add(
                appContext.getString(R.string.alarm_diag_system) to appContext.getString(
                    R.string.alarm_diag_system_value,
                    Build.VERSION.RELEASE,
                    Build.VERSION.SDK_INT,
                ),
            )

            add(
                appContext.getString(R.string.alarm_diag_notifications) to
                    appContext.granted(hasNotificationPermission(appContext)),
            )
            add(appContext.getString(R.string.alarm_diag_channel) to appContext.ringingChannelState())
            add(
                appContext.getString(R.string.alarm_diag_exact_alarm) to
                    appContext.granted(canScheduleExactAlarms(appContext)),
            )
            add(
                appContext.getString(R.string.alarm_diag_full_screen) to
                    appContext.granted(canUseFullScreenIntent(appContext)),
            )
            add(
                appContext.getString(R.string.alarm_diag_battery) to appContext.getString(
                    if (isIgnoringBatteryOptimizations(appContext)) {
                        R.string.alarm_diag_battery_unrestricted
                    } else {
                        R.string.alarm_diag_battery_restricted
                    },
                ),
            )
            add(
                appContext.getString(R.string.alarm_diag_power_save) to appContext.getString(
                    if (isPowerSaveMode(appContext)) {
                        R.string.alarm_diag_power_save_on
                    } else {
                        R.string.alarm_diag_power_save_off
                    },
                ),
            )
            add(appContext.getString(R.string.alarm_diag_dnd) to appContext.interruptionFilterState())
            add(
                appContext.getString(R.string.alarm_diag_vendor_autostart) to appContext.getString(
                    if (AlarmSettingsIntents.hasVendorAutoStartPage(appContext)) {
                        R.string.alarm_diag_vendor_autostart_present
                    } else {
                        R.string.alarm_diag_vendor_autostart_absent
                    },
                ),
            )

            add(
                appContext.getString(R.string.alarm_diag_keep_alive) to
                    appContext.silentGuardState(silentGuard),
            )
            add(
                appContext.getString(R.string.alarm_diag_registered) to
                    appContext.registeredSummary(records),
            )
            add(
                appContext.getString(R.string.alarm_diag_next) to
                    appContext.nextAlarmSummary(records),
            )
            add(
                appContext.getString(R.string.alarm_diag_recent_rings) to
                    appContext.recentRingSummary(),
            )
        }
        return AlarmDiagnosticsReport(lines)
    }

    private fun Context.granted(value: Boolean): String = getString(
        if (value) R.string.alarm_diag_granted else R.string.alarm_diag_not_granted,
    )

    /**
     * Report disabled ringing channels independently of runtime permissions; an uncreated
     * channel is normal.
     */
    private fun Context.ringingChannelState(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return getString(R.string.alarm_diag_channel_not_applicable)
        }
        val manager = getSystemService(NotificationManager::class.java)
            ?: return getString(R.string.alarm_diag_unreadable)
        val channel = manager.getNotificationChannel(AlarmRingingService.CHANNEL_ID)
            ?: return getString(R.string.alarm_diag_channel_absent)
        return getString(
            if (channel.importance == NotificationManager.IMPORTANCE_NONE) {
                R.string.alarm_diag_channel_blocked
            } else {
                R.string.alarm_diag_channel_ok
            },
        )
    }

    private fun isPowerSaveMode(context: Context): Boolean =
        runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode
        }.getOrNull() ?: false

    /** DND state is unknown without policy access. */
    private fun Context.interruptionFilterState(): String {
        val manager = getSystemService(NotificationManager::class.java)
            ?: return getString(R.string.alarm_diag_unreadable)
        return getString(
            when (runCatching { manager.currentInterruptionFilter }.getOrNull()) {
                NotificationManager.INTERRUPTION_FILTER_ALL -> R.string.alarm_diag_dnd_off
                NotificationManager.INTERRUPTION_FILTER_PRIORITY -> R.string.alarm_diag_dnd_priority
                NotificationManager.INTERRUPTION_FILTER_ALARMS -> R.string.alarm_diag_dnd_alarms
                NotificationManager.INTERRUPTION_FILTER_NONE -> R.string.alarm_diag_dnd_none
                else -> R.string.alarm_diag_dnd_unknown
            },
        )
    }

    private fun Context.silentGuardState(enabled: Boolean): String = getString(
        when {
            !enabled -> R.string.alarm_diag_keep_alive_off
            ReminderGuardJobService.isScheduled(this) -> R.string.alarm_diag_keep_alive_silent
            else -> R.string.alarm_diag_keep_alive_silent_missing
        },
    )

    /** Compare stored alarms with registrations to detect lost scheduling. */
    private fun Context.registeredSummary(records: List<SystemAlarmRecord>): String {
        val upcoming = records.filter {
            it.enabled && it.triggerAtMillis > BeijingTime.nowMillis(BeijingTime.zone)
        }
        if (upcoming.isEmpty()) return getString(R.string.alarm_diag_registered_none)
        val verifier = AppAlarmClockRegistrationVerifier(this)
        val stillRegistered = runCatching {
            upcoming.count { verifier.isRegistered(it) }
        }.getOrDefault(-1)
        return if (stillRegistered < 0) {
            getString(R.string.alarm_diag_registered_unverifiable, upcoming.size)
        } else {
            getString(R.string.alarm_diag_registered_summary, upcoming.size, stillRegistered)
        }
    }

    private fun Context.nextAlarmSummary(records: List<SystemAlarmRecord>): String {
        val now = BeijingTime.nowMillis(BeijingTime.zone)
        val next = records.filter { it.enabled && it.triggerAtMillis > now }
            .minByOrNull { it.triggerAtMillis }
            ?: return getString(R.string.alarm_diag_none)
        return getString(
            R.string.alarm_diag_next_value,
            formatClock(next.triggerAtMillis),
            next.displayTitle ?: next.message,
        )
    }

    private fun Context.recentRingSummary(): String {
        val events = AlarmRingHistory.recent(this)
        if (events.isEmpty()) return getString(R.string.alarm_diag_recent_rings_none)
        return events.joinToString(" / ") { event ->
            val outcome = getString(
                when (event.outcome) {
                    AlarmRingOutcome.Rang -> R.string.alarm_diag_ring_outcome_rang
                    AlarmRingOutcome.Missed -> R.string.alarm_diag_ring_outcome_missed
                    AlarmRingOutcome.Skipped -> R.string.alarm_diag_ring_outcome_skipped
                },
            )
            getString(
                R.string.alarm_diag_ring_entry,
                formatClock(event.atMillis),
                outcome,
                event.label,
            ).trim()
        }
    }

    private fun formatClock(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(BeijingTime.zone).format(CLOCK_FORMAT)
}
