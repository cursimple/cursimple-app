package com.kebiao.viewer.core.reminder.dispatch

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.kebiao.viewer.core.reminder.model.AlarmDispatchChannel
import com.kebiao.viewer.core.reminder.model.AlarmDispatchResult
import com.kebiao.viewer.core.reminder.model.ReminderPlan
import com.kebiao.viewer.core.reminder.logging.ReminderLogger
import java.time.Instant

interface AlarmDispatcher {
    suspend fun dispatch(plan: ReminderPlan): AlarmDispatchResult
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
            putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, plan.title)
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
