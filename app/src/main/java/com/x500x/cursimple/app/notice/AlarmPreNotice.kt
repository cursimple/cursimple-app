package com.x500x.cursimple.app.notice

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.x500x.cursimple.R
import com.x500x.cursimple.app.ClassScheduleApplication
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.reminderDayPolicy
import com.x500x.cursimple.core.reminder.alarmDaySuppression
import com.x500x.cursimple.core.data.reminder.DataStoreReminderRepository
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.reminder.dispatch.AppAlarmClockRegistrationVerifier
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import com.x500x.cursimple.core.reminder.model.ReminderAlarmBackend
import com.x500x.cursimple.core.reminder.model.SystemAlarmRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/**
 * Schedule only the next pre-alarm notice and inspect missing registrations before it rings.
 */
object AlarmPreNoticeScheduler {
    private const val ACTION_PRE_NOTICE = "com.x500x.cursimple.action.ALARM_PRE_NOTICE"
    private const val REQUEST_CODE = 0x0C21
    private const val EXTRA_ALARM_KEY = "alarmKey"
    private const val EXTRA_TRIGGER_AT = "triggerAt"

    private val lock = Mutex()

    /** Cancel when disabled or empty; otherwise schedule from current alarms and settings. */
    suspend fun reschedule(context: Context) = lock.withLock {
        val app = context.applicationContext
        cancel(app)
        val preferences = DataStoreUserPreferencesRepository(app).preferencesFlow.first()
        val settings = preferences.alarmPreNotice
        if (!settings.enabled) return@withLock
        val leadMillis = settings.advanceMinutes * 60_000L
        val now = System.currentTimeMillis()
        val next = DataStoreReminderRepository(app).systemAlarmRecordsFlow.first()
            .filter { it.enabled && it.triggerAtMillis - leadMillis > now }
            .filter { alarmDaySuppression(it.triggerAtMillis, it.allowOnHoliday, BeijingTime.zone, preferences.reminderDayPolicy(), preferences.holidayCalendar, preferences.temporaryScheduleOverrides) == null }
            .minByOrNull { it.triggerAtMillis }
            ?: return@withLock
        val intent = Intent(app, AlarmPreNoticeReceiver::class.java).apply {
            action = ACTION_PRE_NOTICE
            setPackage(app.packageName)
            putExtra(EXTRA_ALARM_KEY, next.alarmKey)
            putExtra(EXTRA_TRIGGER_AT, next.triggerAtMillis)
        }
        val operation = PendingIntent.getBroadcast(
            app,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val fireAt = next.triggerAtMillis - leadMillis
        val alarmManager = app.getSystemService(AlarmManager::class.java) ?: return@withLock
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, operation)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, operation)
            }
        }.onFailure { ReminderLogger.warn("alarm_pre_notice.schedule.failure", emptyMap(), it) }
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        val operation = PendingIntent.getBroadcast(
            app,
            REQUEST_CODE,
            Intent(app, AlarmPreNoticeReceiver::class.java).apply {
                action = ACTION_PRE_NOTICE
                setPackage(app.packageName)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        ) ?: return
        runCatching {
            app.getSystemService(AlarmManager::class.java)?.cancel(operation)
            operation.cancel()
        }
    }

    internal fun alarmKeyOf(intent: Intent): String? = intent.getStringExtra(EXTRA_ALARM_KEY)
    internal fun triggerAtOf(intent: Intent): Long = intent.getLongExtra(EXTRA_TRIGGER_AT, 0L)
}

/** Rebuild registrations only when inspection finds missing alarms. */
object AlarmRegistrationRepair {
    suspend fun repairIfMissing(context: Context, reason: String): Boolean {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val verifier = AppAlarmClockRegistrationVerifier(app)
        val upcoming = DataStoreReminderRepository(app).systemAlarmRecordsFlow.first()
            .filter { it.backend == ReminderAlarmBackend.AppAlarmClock && it.enabled && it.triggerAtMillis > now }
        if (upcoming.isEmpty()) return false
        var missing = upcoming.count { !verifier.isRegistered(it) }
        // PendingIntent existence is insufficient; compare the earliest own alarm with the system's next alarm as well.
        if (missing == 0 && systemLostEarliest(app, upcoming.minOf { it.triggerAtMillis })) missing = 1
        if (missing == 0) return false
        ReminderLogger.warn(
            "reminder.registration_repair.missing",
            mapOf("reason" to reason, "missing" to missing),
        )
        val container = (app as? ClassScheduleApplication)?.appContainer ?: return false
        runCatching { container.ensureAlarmRuntimeHealth() }
            .onFailure { ReminderLogger.warn("reminder.registration_repair.failure", mapOf("reason" to reason), it) }
        return true
    }
}

/** A missing or later system alarm indicates loss of the earliest own registration. */
private fun systemLostEarliest(context: Context, earliestOwnMillis: Long): Boolean {
    val next = runCatching {
        context.getSystemService(AlarmManager::class.java)?.nextAlarmClock
    }.getOrNull()
    return next == null || next.triggerTime > earliestOwnMillis + 60_000L
}

class AlarmPreNoticeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmKey = AlarmPreNoticeScheduler.alarmKeyOf(intent) ?: return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                AlarmRegistrationRepair.repairIfMissing(app, reason = "pre_notice")
                val record = DataStoreReminderRepository(app).systemAlarmRecordsFlow.first()
                    .firstOrNull { it.alarmKey == alarmKey && it.enabled }
                if (record != null && record.triggerAtMillis == AlarmPreNoticeScheduler.triggerAtOf(intent)) {
                    val preferences = DataStoreUserPreferencesRepository(app).preferencesFlow.first()
                    if (preferences.alarmPreNotice.enabled && alarmDaySuppression(
                        record.triggerAtMillis, record.allowOnHoliday, BeijingTime.zone, preferences.reminderDayPolicy(),
                        preferences.holidayCalendar, preferences.temporaryScheduleOverrides,
                    ) == null) {
                        ClassNoticeNotifier.notify(
                            app,
                            record.toPreviewContent(app),
                            preferences.classNotice,
                            ClassNoticeGateway.theme(app),
                        )
                    }
                }
                AlarmPreNoticeScheduler.reschedule(app)
                ClassNoticeOverlay.awaitShown()
            } catch (error: Throwable) {
                ReminderLogger.warn("alarm_pre_notice.deliver.failure", emptyMap(), error)
            } finally {
                pending.finish()
            }
        }
    }
}

private fun SystemAlarmRecord.toPreviewContent(context: Context): ClassNoticeNotifier.Content {
    val clock = Instant.ofEpochMilli(triggerAtMillis).atZone(BeijingTime.zone).toLocalTime()
    val clockText = "%02d:%02d".format(clock.hour, clock.minute)
    val title = listOf(displayTitle, alarmLabel, message)
        .firstOrNull { !it.isNullOrBlank() }
        ?: context.getString(R.string.alarm_pre_notice_default_title)
    val detail = listOf(displayMessage, message).firstOrNull { !it.isNullOrBlank() && it != title }.orEmpty()
    val minutes = ((triggerAtMillis - System.currentTimeMillis() + 30_000L) / 60_000L).toInt().coerceAtLeast(1)
    return ClassNoticeNotifier.Content(
        courseTitle = title,
        location = detail,
        timeRange = context.getString(R.string.alarm_pre_notice_ring_at, clockText),
        minutesUntilStart = minutes,
        startAtMillis = triggerAtMillis,
        kind = ClassNoticeNotifier.Kind.AlarmPreview,
    )
}
