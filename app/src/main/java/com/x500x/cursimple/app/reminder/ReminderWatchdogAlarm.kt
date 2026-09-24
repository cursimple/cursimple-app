package com.x500x.cursimple.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.x500x.cursimple.core.reminder.logging.ReminderLogger

/**
 * 提醒的看门狗闹钟：每 15 分钟响一次，确认守护服务还在、上课提醒还挂着，然后把自己续上。
 *
 * 和 [ReminderGuardJobService] 分工：JobScheduler 由系统挑时机跑，省电但可能被推迟，
 * 而且 Android 12 起从后台拉不起前台服务；精确闹钟触发的广播是少数被放行拉起前台服务的时机，
 * 守护被杀后靠它才能真正回来。两条都挂着，一条被厂商拦掉还有另一条。
 */
object ReminderWatchdogAlarm {
    const val ACTION_WATCHDOG = "com.x500x.cursimple.action.REMINDER_WATCHDOG"
    private const val REQUEST_CODE = 0x0C1F
    private const val INTERVAL_MILLIS = 15 * 60 * 1000L

    /** 还没挂着才挂：链条在跑时每次启动都重挂，会把下一次一直往后推。 */
    fun ensureScheduled(context: Context) {
        if (pendingIntent(context, PendingIntent.FLAG_NO_CREATE) != null) return
        scheduleNext(context)
    }

    /** 响过之后续下一环。 */
    fun scheduleNext(context: Context) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(AlarmManager::class.java) ?: return
        val operation = pendingIntent(app, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val triggerAt = System.currentTimeMillis() + INTERVAL_MILLIS
        runCatching {
            // 没有精确闹钟权限就退回不精确的：晚一点巡检也比不巡检强，只是拉不起前台服务
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            }
        }.onFailure { ReminderLogger.warn("reminder.watchdog.schedule.failure", emptyMap(), it) }
    }

    fun cancel(context: Context) {
        val operation = pendingIntent(context, PendingIntent.FLAG_NO_CREATE) ?: return
        runCatching {
            context.applicationContext.getSystemService(AlarmManager::class.java)?.cancel(operation)
            operation.cancel()
        }
    }

    private fun pendingIntent(context: Context, extraFlags: Int): PendingIntent? {
        val app = context.applicationContext
        return PendingIntent.getBroadcast(
            app,
            REQUEST_CODE,
            Intent(app, AlarmKeepAliveRestartReceiver::class.java)
                .setAction(ACTION_WATCHDOG)
                .setPackage(app.packageName)
                // 前台优先级投递：后台广播队列排长时会晚好几分钟
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
            PendingIntent.FLAG_IMMUTABLE or extraFlags,
        )
    }
}
