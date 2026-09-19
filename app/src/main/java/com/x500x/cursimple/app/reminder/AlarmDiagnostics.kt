package com.x500x.cursimple.app.reminder

import android.app.ActivityManager
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
 * 闹钟自检。
 *
 * 「闹钟没响」在用户那头是一个现象，在代码这头至少有六种原因：权限被收走、渠道被关掉、
 * 系统进了省电、排程根本没登记上、送到了但被去重吃掉、或者进程早就被厂商清理器杀干净了。
 * 隔着屏幕一个个问要来回好几轮，这里把判断依据一次性列出来，用户复制一段发过来就能定位。
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
        val keepAliveEnabled = runCatching {
            DataStoreUserPreferencesRepository(appContext).preferencesFlow.first().alarmKeepAliveEnabled
        }.getOrDefault(false)

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
                    appContext.keepAliveState(keepAliveEnabled),
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
     * 响铃渠道的状态。
     *
     * 渠道被用户单独关掉时，权限页上每一项都是绿的，闹钟却一声不出——这是最难猜到的一种，
     * 所以单列一行。渠道是响第一次的时候才建的，没建过属于正常。
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

    /** 读勿扰状态要通知策略访问权，没有就是 UNKNOWN，不能把它说成「关」。 */
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

    private fun Context.keepAliveState(enabled: Boolean): String {
        if (!enabled) return getString(R.string.alarm_diag_keep_alive_off)
        return getString(
            if (isKeepAliveRunning()) {
                R.string.alarm_diag_keep_alive_running
            } else {
                R.string.alarm_diag_keep_alive_dead
            },
        )
    }

    /** getRunningServices 从 Android 8 起只返回自己的服务，这里查的正是自己的，够用。 */
    private fun Context.isKeepAliveRunning(): Boolean {
        val manager = getSystemService(ActivityManager::class.java) ?: return false
        val className = AlarmKeepAliveService::class.java.name
        return runCatching {
            @Suppress("DEPRECATION")
            manager.getRunningServices(Int.MAX_VALUE).any { it.service.className == className }
        }.getOrDefault(false)
    }

    /**
     * 记录里有几条、系统里还剩几条。
     *
     * 两个数字对不上就是排程掉了——应用被强停过、或者精确闹钟权限中途被收走，
     * 这时候用户看到的现象就是「本来好好的，某天开始不响了」。
     */
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
