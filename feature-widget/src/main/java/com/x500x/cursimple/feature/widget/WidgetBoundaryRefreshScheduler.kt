package com.x500x.cursimple.feature.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 按节次边界排布小组件刷新。
 *
 * 守护链按固定周期跳动，最坏会让上课状态滞后一整个周期；这里在课前提前量、上课与下课
 * 三个时刻各排一次，状态切换的那一刻小组件就会重画。两者互为补充，守护链仍是兜底。
 */
internal object WidgetBoundaryRefreshScheduler {

    fun reschedule(context: Context, slots: List<ClassSlotTime>, zone: ZoneId = ZoneId.systemDefault()) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val boundaries = widgetRefreshBoundaries(slots, LocalDateTime.now(zone), limit = SLOT_COUNT)
        // 节次变少或课表清空后，多余的槽位要撤掉，否则会一直空转
        for (index in boundaries.size until SLOT_COUNT) {
            cancelSlot(app, alarmManager, index)
        }
        boundaries.forEachIndexed { index, at ->
            val triggerAtMillis = at.atZone(zone).toInstant().toEpochMilli()
            runCatching {
                scheduleSlot(alarmManager, triggerAtMillis, pendingIntent(app, index, PendingIntent.FLAG_UPDATE_CURRENT))
            }.onFailure { error ->
                ReminderLogger.warn(
                    "widget.boundary_refresh.schedule.failure",
                    mapOf("index" to index, "triggerAtMillis" to triggerAtMillis),
                    error,
                )
            }
        }
    }

    private fun cancelSlot(context: Context, alarmManager: AlarmManager, index: Int) {
        val operation = pendingIntent(context, index, PendingIntent.FLAG_NO_CREATE) ?: return
        runCatching {
            alarmManager.cancel(operation)
            operation.cancel()
        }
    }

    private fun scheduleSlot(alarmManager: AlarmManager, triggerAtMillis: Long, operation: PendingIntent?) {
        if (operation == null) return
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)
        if (exactAllowed) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        } else {
            // 没有精确闹钟权限时退回不精确闹钟，刷新会晚一点但不至于不刷
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        }
    }

    private fun pendingIntent(context: Context, index: Int, flags: Int): PendingIntent? {
        val intent = Intent(context, WidgetAlarmGuardReceiver::class.java).apply {
            action = WidgetAlarmGuardScheduler.ACTION_GUARD_TICK
            setPackage(context.packageName)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BASE + index,
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    internal const val SLOT_COUNT = 6
    private const val REQUEST_CODE_BASE = 6420
}
