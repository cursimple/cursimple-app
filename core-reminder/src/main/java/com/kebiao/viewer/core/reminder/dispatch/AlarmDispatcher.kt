package com.kebiao.viewer.core.reminder.dispatch

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.kebiao.viewer.core.reminder.model.AlarmDispatchChannel
import com.kebiao.viewer.core.reminder.model.AlarmDispatchResult
import com.kebiao.viewer.core.reminder.model.AlarmDismissResult
import com.kebiao.viewer.core.reminder.model.ReminderPlan
import com.kebiao.viewer.core.reminder.model.SystemAlarmRecord
import com.kebiao.viewer.core.reminder.model.systemAlarmLabel
import com.kebiao.viewer.core.reminder.logging.ReminderLogger
import java.time.Instant

interface AlarmDispatcher {
    suspend fun dispatch(plan: ReminderPlan): AlarmDispatchResult
}

interface AlarmDismisser {
    suspend fun dismiss(record: SystemAlarmRecord): AlarmDismissResult
}

class SystemAlarmClockDispatcher(
    private val context: Context,
) : AlarmDispatcher {
    override suspend fun dispatch(plan: ReminderPlan): AlarmDispatchResult {
        val trigger = Instant.ofEpochMilli(plan.triggerAtMillis).atZone(java.time.ZoneId.systemDefault())
        val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(android.provider.AlarmClock.EXTRA_HOUR, trigger.hour)
            putExtra(android.provider.AlarmClock.EXTRA_MINUTES, trigger.minute)
            putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, plan.systemAlarmLabel())
            if (!plan.ringtoneUri.isNullOrBlank()) {
                putExtra(android.provider.AlarmClock.EXTRA_RINGTONE, plan.ringtoneUri)
            }
            putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
        }
        ReminderLogger.info(
            "reminder.system_clock.dispatch.start",
            mapOf("ruleId" to plan.ruleId, "planId" to plan.planId, "triggerAtMillis" to plan.triggerAtMillis),
        )
        return runCatching {
            context.startActivity(intent)
            ReminderLogger.info(
                "reminder.system_clock.dispatch.success",
                mapOf("ruleId" to plan.ruleId, "planId" to plan.planId),
            )
            AlarmDispatchResult(
                channel = AlarmDispatchChannel.SystemClock,
                succeeded = true,
                message = "系统时钟已接受创建请求",
            )
        }.getOrElse {
            val message = when (it) {
                is ActivityNotFoundException -> "系统时钟应用不可用"
                is SecurityException -> "系统拒绝创建闹钟"
                else -> it.message ?: "创建系统闹钟失败"
            }
            ReminderLogger.warn(
                "reminder.system_clock.dispatch.failure",
                mapOf("ruleId" to plan.ruleId, "planId" to plan.planId, "reason" to message),
                it,
            )
            AlarmDispatchResult(
                channel = AlarmDispatchChannel.SystemClock,
                succeeded = false,
                message = message,
            )
        }
    }
}

class SystemAlarmClockDismisser(
    private val context: Context,
) : AlarmDismisser {
    override suspend fun dismiss(record: SystemAlarmRecord): AlarmDismissResult {
        val label = record.alarmLabel ?: record.message
        val intent = Intent(android.provider.AlarmClock.ACTION_DISMISS_ALARM).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(android.provider.AlarmClock.EXTRA_ALARM_SEARCH_MODE, android.provider.AlarmClock.ALARM_SEARCH_MODE_LABEL)
            putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label)
        }
        ReminderLogger.info(
            "reminder.system_clock.dismiss.start",
            mapOf(
                "ruleId" to record.ruleId,
                "planId" to record.planId,
                "alarmKey" to record.alarmKey,
                "triggerAtMillis" to record.triggerAtMillis,
            ),
        )
        return runCatching {
            context.startActivity(intent)
            ReminderLogger.info(
                "reminder.system_clock.dismiss.success",
                mapOf("ruleId" to record.ruleId, "planId" to record.planId, "alarmKey" to record.alarmKey),
            )
            AlarmDismissResult(
                alarmKey = record.alarmKey,
                succeeded = true,
                message = "系统时钟已接受删除请求",
            )
        }.getOrElse {
            val message = when (it) {
                is ActivityNotFoundException -> "系统时钟应用不可用"
                is SecurityException -> "系统拒绝删除闹钟"
                else -> it.message ?: "删除系统闹钟失败"
            }
            ReminderLogger.warn(
                "reminder.system_clock.dismiss.failure",
                mapOf("ruleId" to record.ruleId, "planId" to record.planId, "alarmKey" to record.alarmKey, "reason" to message),
                it,
            )
            AlarmDismissResult(
                alarmKey = record.alarmKey,
                succeeded = false,
                message = message,
            )
        }
    }
}
