package com.kebiao.viewer.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kebiao.viewer.app.ClassScheduleApplication
import com.kebiao.viewer.core.kernel.model.TermTimingProfile
import com.kebiao.viewer.core.kernel.model.endLocalTime
import com.kebiao.viewer.core.reminder.logging.ReminderLogger
import com.kebiao.viewer.core.reminder.model.ReminderSyncReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class SystemAlarmCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? ClassScheduleApplication
                if (app == null) {
                    ReminderLogger.warn("reminder.system_clock.check.no_application", emptyMap())
                    return@launch
                }
                when (intent.action) {
                    ACTION_DAILY_NEXT_DAY_CHECK -> app.appContainer.runSystemAlarmCheck(ReminderSyncReason.DailyNextDay)
                    ACTION_AFTER_CLASS_CHECK -> app.appContainer.runSystemAlarmCheck(ReminderSyncReason.AfterClassToday)
                    Intent.ACTION_BOOT_COMPLETED -> app.appContainer.scheduleSystemAlarmChecks()
                    Intent.ACTION_MY_PACKAGE_REPLACED,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED,
                    Intent.ACTION_DATE_CHANGED -> app.appContainer.scheduleSystemAlarmChecks()
                    else -> ReminderLogger.warn(
                        "reminder.system_clock.check.unknown_action",
                        mapOf("action" to intent.action.orEmpty()),
                    )
                }
            } catch (error: Throwable) {
                ReminderLogger.warn(
                    "reminder.system_clock.check.failure",
                    mapOf("action" to intent.action.orEmpty()),
                    error,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DAILY_NEXT_DAY_CHECK = "com.kebiao.viewer.action.DAILY_NEXT_DAY_SYSTEM_ALARM_CHECK"
        const val ACTION_AFTER_CLASS_CHECK = "com.kebiao.viewer.action.AFTER_CLASS_SYSTEM_ALARM_CHECK"
    }
}

object SystemAlarmCheckScheduler {
    fun scheduleDailyNextDayCheck(
        context: Context,
        timingProfile: TermTimingProfile,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val zone = ZoneId.of(timingProfile.timezone)
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDateTime()
        var target = LocalDateTime.of(now.toLocalDate(), DAILY_CHECK_TIME)
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }
        scheduleExact(
            context = context,
            action = SystemAlarmCheckReceiver.ACTION_DAILY_NEXT_DAY_CHECK,
            requestCode = REQUEST_DAILY_NEXT_DAY_CHECK,
            triggerAtMillis = target.atZone(zone).toInstant().toEpochMilli(),
            eventName = "reminder.system_clock.check.schedule_daily",
        )
    }

    fun scheduleNextAfterClassCheck(
        context: Context,
        timingProfile: TermTimingProfile,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val zone = ZoneId.of(timingProfile.timezone)
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val slotEndTimes = timingProfile.slotTimes
            .mapNotNull { runCatching { it.endLocalTime() }.getOrNull() }
            .distinct()
            .sorted()
        if (slotEndTimes.isEmpty()) return
        val target = (0..1).asSequence()
            .flatMap { dayOffset ->
                slotEndTimes.asSequence().map { endTime ->
                    LocalDateTime.of(now.toLocalDate().plusDays(dayOffset.toLong()), endTime)
                }
            }
            .firstOrNull { it.atZone(zone).toInstant().toEpochMilli() > nowMillis + MIN_FUTURE_DELAY_MILLIS }
            ?: return
        scheduleExact(
            context = context,
            action = SystemAlarmCheckReceiver.ACTION_AFTER_CLASS_CHECK,
            requestCode = REQUEST_AFTER_CLASS_CHECK,
            triggerAtMillis = target.atZone(zone).toInstant().toEpochMilli(),
            eventName = "reminder.system_clock.check.schedule_after_class",
        )
    }

    private fun scheduleExact(
        context: Context,
        action: String,
        requestCode: Int,
        triggerAtMillis: Long,
        eventName: String,
    ) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!alarmManager.canScheduleExactAlarmCompat()) {
            ReminderLogger.warn(
                "$eventName.permission_missing",
                mapOf("sdk" to Build.VERSION.SDK_INT, "triggerAtMillis" to triggerAtMillis),
            )
            return
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            requestCode,
            Intent(appContext, SystemAlarmCheckReceiver::class.java).apply {
                this.action = action
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
            ReminderLogger.info(eventName, mapOf("triggerAtMillis" to triggerAtMillis))
        }.onFailure { error ->
            ReminderLogger.warn(eventName + ".failure", mapOf("triggerAtMillis" to triggerAtMillis), error)
        }
    }

    private fun AlarmManager.canScheduleExactAlarmCompat(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || runCatching {
            canScheduleExactAlarms()
        }.getOrDefault(false)

    private val DAILY_CHECK_TIME: LocalTime = LocalTime.of(22, 0)
    private const val REQUEST_DAILY_NEXT_DAY_CHECK = 74_220
    private const val REQUEST_AFTER_CLASS_CHECK = 74_221
    private const val MIN_FUTURE_DELAY_MILLIS = 5_000L
}
