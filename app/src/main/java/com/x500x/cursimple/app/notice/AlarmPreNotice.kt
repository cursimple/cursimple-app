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
 * 闹钟响前的预告。
 *
 * 和上课通知一样一次只挂一个闹钟——最近那个闹钟的预告点；发完再算下一个。
 * 预告点顺带当一次响铃前的体检：这时候闹钟要是已经被系统清掉了，立刻重挂，
 * 离响铃还有几分钟，补得上。
 */
object AlarmPreNoticeScheduler {
    private const val ACTION_PRE_NOTICE = "com.x500x.cursimple.action.ALARM_PRE_NOTICE"
    private const val REQUEST_CODE = 0x0C21
    private const val EXTRA_ALARM_KEY = "alarmKey"
    private const val EXTRA_TRIGGER_AT = "triggerAt"

    private val lock = Mutex()

    /** 按当前闹钟列表和设置重挂；关着或没有要响的闹钟时只取消。 */
    suspend fun reschedule(context: Context) = lock.withLock {
        val app = context.applicationContext
        cancel(app)
        val settings = DataStoreUserPreferencesRepository(app).preferencesFlow.first().alarmPreNotice
        if (!settings.enabled) return@withLock
        val leadMillis = settings.advanceMinutes * 60_000L
        val now = System.currentTimeMillis()
        val next = DataStoreReminderRepository(app).systemAlarmRecordsFlow.first()
            .filter { it.enabled && it.triggerAtMillis - leadMillis > now }
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

/**
 * 应用自管的闹钟里有没有已经不在系统里的；有就全部重挂一遍。
 *
 * 只在查出缺了才重挂：全量重挂会重写整张闹钟表，巡检每 15 分钟一次，没必要每次都写。
 */
object AlarmRegistrationRepair {
    suspend fun repairIfMissing(context: Context, reason: String): Boolean {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val verifier = AppAlarmClockRegistrationVerifier(app)
        val upcoming = DataStoreReminderRepository(app).systemAlarmRecordsFlow.first()
            .filter { it.backend == ReminderAlarmBackend.AppAlarmClock && it.enabled && it.triggerAtMillis > now }
        if (upcoming.isEmpty()) return false
        var missing = upcoming.count { !verifier.isRegistered(it) }
        // 光看 PendingIntent 还在不够：Android 14 及以下强行停止、厂商清理闹钟都可能只删闹钟、
        // 留着 PendingIntent。再对一下系统眼里的「下一个闹钟」：课简最早那个闹钟之前，
        // 系统的下一个闹钟要么就是它，要么是别家更早的；若系统的下一个比它还晚或者干脆没有，
        // 说明它已经不在了
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

/** 系统记的下一个用户闹钟比 [earliestOwnMillis] 晚（或没有），说明课简最早那个闹钟已经丢了。 */
private fun systemLostEarliest(context: Context, earliestOwnMillis: Long): Boolean {
    val next = runCatching {
        context.getSystemService(AlarmManager::class.java)?.nextAlarmClock
    }.getOrNull()
    // 容差一分钟：到达判定和排程时刻可能有秒级出入
    return next == null || next.triggerTime > earliestOwnMillis + 60_000L
}

/** 预告点到了：先体检这个闹钟，再发预告，最后排下一个。 */
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
                // 闹钟在这期间被删了、关了或改了时间，就不预告这一条
                if (record != null && record.triggerAtMillis == AlarmPreNoticeScheduler.triggerAtOf(intent)) {
                    val preferences = DataStoreUserPreferencesRepository(app).preferencesFlow.first()
                    if (preferences.alarmPreNotice.enabled) {
                        ClassNoticeNotifier.notify(
                            app,
                            record.toPreviewContent(app),
                            preferences.classNotice,
                            ClassNoticeGateway.theme(app),
                        )
                    }
                }
                AlarmPreNoticeScheduler.reschedule(app)
                ClassNoticeOverlay.awaitGone()
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
