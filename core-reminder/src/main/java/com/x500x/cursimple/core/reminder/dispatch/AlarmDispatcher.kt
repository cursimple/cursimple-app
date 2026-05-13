package com.x500x.cursimple.core.reminder.dispatch

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.x500x.cursimple.core.reminder.model.AlarmDispatchChannel
import com.x500x.cursimple.core.reminder.model.AlarmDispatchResult
import com.x500x.cursimple.core.reminder.model.AlarmDismissResult
import com.x500x.cursimple.core.reminder.model.ReminderPlan
import com.x500x.cursimple.core.reminder.model.SystemAlarmRecord
import com.x500x.cursimple.core.reminder.model.appAlarmRequestCode
import com.x500x.cursimple.core.reminder.model.systemAlarmKey
import com.x500x.cursimple.core.reminder.model.systemAlarmLabel
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import java.time.Instant

interface AlarmDispatcher {
    suspend fun dispatch(plan: ReminderPlan): AlarmDispatchResult
}

interface AlarmDismisser {
    suspend fun dismiss(record: SystemAlarmRecord): AlarmDismissResult
}

object AppAlarmClockIntents {
    const val ACTION_TRIGGER = "com.x500x.cursimple.action.APP_ALARM_TRIGGER"
    const val ACTION_RING = "com.x500x.cursimple.action.ALARM_RING"
    const val RECEIVER_CLASS_NAME = "com.x500x.cursimple.app.reminder.AppAlarmReceiver"
    const val SERVICE_CLASS_NAME = "com.x500x.cursimple.app.reminder.AlarmRingingService"
    const val EXTRA_ALARM_KEY = "com.x500x.cursimple.extra.ALARM_KEY"
    const val EXTRA_RULE_ID = "com.x500x.cursimple.extra.RULE_ID"
    const val EXTRA_PLUGIN_ID = "com.x500x.cursimple.extra.PLUGIN_ID"
    const val EXTRA_PLAN_ID = "com.x500x.cursimple.extra.PLAN_ID"
    const val EXTRA_COURSE_ID = "com.x500x.cursimple.extra.COURSE_ID"
    const val EXTRA_TRIGGER_AT_MILLIS = "com.x500x.cursimple.extra.TRIGGER_AT_MILLIS"
    const val EXTRA_TITLE = "com.x500x.cursimple.extra.TITLE"
    const val EXTRA_MESSAGE = "com.x500x.cursimple.extra.MESSAGE"
    const val EXTRA_RINGTONE_URI = "com.x500x.cursimple.extra.RINGTONE_URI"
}

class AppAlarmClockDispatcher(
    private val context: Context,
) : AlarmDispatcher {
    override suspend fun dispatch(plan: ReminderPlan): AlarmDispatchResult {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!alarmManager.canScheduleExactAlarmCompat()) {
            ReminderLogger.warn(
                "reminder.app_alarm_clock.dispatch.permission_missing",
                mapOf("ruleId" to plan.ruleId, "planId" to plan.planId, "triggerAtMillis" to plan.triggerAtMillis),
            )
            return AlarmDispatchResult(
                channel = AlarmDispatchChannel.AppAlarmClock,
                succeeded = false,
                message = "精确闹钟权限未开启",
            )
        }
        val requestCode = plan.appAlarmRequestCode()
        val operation = appAlarmOperationIntent(appContext, plan, requestCode)
        val showIntent = appAlarmShowIntent(appContext, plan, requestCode)
        ReminderLogger.info(
            "reminder.app_alarm_clock.dispatch.start",
            mapOf(
                "ruleId" to plan.ruleId,
                "planId" to plan.planId,
                "requestCode" to requestCode,
                "triggerAtMillis" to plan.triggerAtMillis,
            ),
        )
        return runCatching {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(plan.triggerAtMillis, showIntent),
                operation,
            )
            ReminderLogger.info(
                "reminder.app_alarm_clock.dispatch.success",
                mapOf("ruleId" to plan.ruleId, "planId" to plan.planId, "requestCode" to requestCode),
            )
            AlarmDispatchResult(
                channel = AlarmDispatchChannel.AppAlarmClock,
                succeeded = true,
                message = "App 自管闹钟已设置",
            )
        }.getOrElse {
            val message = when (it) {
                is SecurityException -> "系统拒绝设置精确闹钟"
                else -> it.message ?: "设置 App 自管闹钟失败"
            }
            ReminderLogger.warn(
                "reminder.app_alarm_clock.dispatch.failure",
                mapOf("ruleId" to plan.ruleId, "planId" to plan.planId, "reason" to message),
                it,
            )
            AlarmDispatchResult(
                channel = AlarmDispatchChannel.AppAlarmClock,
                succeeded = false,
                message = message,
            )
        }
    }
}

class AppAlarmClockDismisser(
    private val context: Context,
) : AlarmDismisser {
    override suspend fun dismiss(record: SystemAlarmRecord): AlarmDismissResult {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val requestCode = record.requestCode ?: (record.alarmKey.hashCode() and Int.MAX_VALUE)
        val pendingIntent = appAlarmOperationIntent(appContext, record, requestCode)
        val legacyReceiverIntent = legacyAppAlarmReceiverIntent(appContext, record, requestCode)
        ReminderLogger.info(
            "reminder.app_alarm_clock.dismiss.start",
            mapOf(
                "ruleId" to record.ruleId,
                "planId" to record.planId,
                "alarmKey" to record.alarmKey,
                "requestCode" to requestCode,
                "triggerAtMillis" to record.triggerAtMillis,
            ),
        )
        return runCatching {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            alarmManager.cancel(legacyReceiverIntent)
            legacyReceiverIntent.cancel()
            ReminderLogger.info(
                "reminder.app_alarm_clock.dismiss.success",
                mapOf("ruleId" to record.ruleId, "planId" to record.planId, "alarmKey" to record.alarmKey),
            )
            AlarmDismissResult(
                alarmKey = record.alarmKey,
                succeeded = true,
                message = "App 自管闹钟已取消",
            )
        }.getOrElse {
            val message = it.message ?: "取消 App 自管闹钟失败"
            ReminderLogger.warn(
                "reminder.app_alarm_clock.dismiss.failure",
                mapOf("ruleId" to record.ruleId, "planId" to record.planId, "alarmKey" to record.alarmKey),
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
                channel = AlarmDispatchChannel.SystemClockApp,
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
                channel = AlarmDispatchChannel.SystemClockApp,
                succeeded = false,
                message = message,
            )
        }
    }
}

private fun AlarmManager.canScheduleExactAlarmCompat(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || runCatching {
        canScheduleExactAlarms()
    }.getOrDefault(false)

private fun appAlarmOperationIntent(
    context: Context,
    plan: ReminderPlan,
    requestCode: Int,
): PendingIntent = appAlarmServicePendingIntent(
    context = context,
    requestCode = requestCode,
    intent = appAlarmServiceIntent(context, plan),
)

private fun appAlarmOperationIntent(
    context: Context,
    record: SystemAlarmRecord,
    requestCode: Int,
): PendingIntent = appAlarmServicePendingIntent(
    context = context,
    requestCode = requestCode,
    intent = appAlarmServiceIntent(context, record),
)

private fun appAlarmServicePendingIntent(
    context: Context,
    requestCode: Int,
    intent: Intent,
): PendingIntent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        PendingIntent.getForegroundService(
            context.applicationContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    } else {
        PendingIntent.getService(
            context.applicationContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

private fun appAlarmServiceIntent(
    context: Context,
    plan: ReminderPlan,
): Intent =
    Intent(AppAlarmClockIntents.ACTION_RING).apply {
        component = ComponentName(context.packageName, AppAlarmClockIntents.SERVICE_CLASS_NAME)
        putExtra(AppAlarmClockIntents.EXTRA_ALARM_KEY, plan.systemAlarmKey())
        putExtra(AppAlarmClockIntents.EXTRA_RULE_ID, plan.ruleId)
        putExtra(AppAlarmClockIntents.EXTRA_PLUGIN_ID, plan.pluginId)
        putExtra(AppAlarmClockIntents.EXTRA_PLAN_ID, plan.planId)
        putExtra(AppAlarmClockIntents.EXTRA_COURSE_ID, plan.courseId)
        putExtra(AppAlarmClockIntents.EXTRA_TRIGGER_AT_MILLIS, plan.triggerAtMillis)
        putExtra(AppAlarmClockIntents.EXTRA_TITLE, plan.title)
        putExtra(AppAlarmClockIntents.EXTRA_MESSAGE, plan.message)
        putExtra(AppAlarmClockIntents.EXTRA_RINGTONE_URI, plan.ringtoneUri)
    }

private fun appAlarmServiceIntent(
    context: Context,
    record: SystemAlarmRecord,
): Intent =
    Intent(AppAlarmClockIntents.ACTION_RING).apply {
        component = ComponentName(context.packageName, AppAlarmClockIntents.SERVICE_CLASS_NAME)
        putExtra(AppAlarmClockIntents.EXTRA_ALARM_KEY, record.alarmKey)
        putExtra(AppAlarmClockIntents.EXTRA_RULE_ID, record.ruleId)
        putExtra(AppAlarmClockIntents.EXTRA_PLUGIN_ID, record.pluginId)
        putExtra(AppAlarmClockIntents.EXTRA_PLAN_ID, record.planId)
        putExtra(AppAlarmClockIntents.EXTRA_COURSE_ID, record.courseId)
        putExtra(AppAlarmClockIntents.EXTRA_TRIGGER_AT_MILLIS, record.triggerAtMillis)
        putExtra(AppAlarmClockIntents.EXTRA_MESSAGE, record.message)
    }

private fun legacyAppAlarmReceiverIntent(
    context: Context,
    record: SystemAlarmRecord,
    requestCode: Int,
): PendingIntent =
    PendingIntent.getBroadcast(
        context.applicationContext,
        requestCode,
        Intent(AppAlarmClockIntents.ACTION_TRIGGER).apply {
            component = ComponentName(context.packageName, AppAlarmClockIntents.RECEIVER_CLASS_NAME)
            putExtra(AppAlarmClockIntents.EXTRA_ALARM_KEY, record.alarmKey)
            putExtra(AppAlarmClockIntents.EXTRA_RULE_ID, record.ruleId)
            putExtra(AppAlarmClockIntents.EXTRA_PLUGIN_ID, record.pluginId)
            putExtra(AppAlarmClockIntents.EXTRA_PLAN_ID, record.planId)
            putExtra(AppAlarmClockIntents.EXTRA_COURSE_ID, record.courseId)
            putExtra(AppAlarmClockIntents.EXTRA_TRIGGER_AT_MILLIS, record.triggerAtMillis)
            putExtra(AppAlarmClockIntents.EXTRA_MESSAGE, record.message)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

private fun appAlarmShowIntent(
    context: Context,
    plan: ReminderPlan,
    requestCode: Int,
): PendingIntent {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?: Intent(Intent.ACTION_MAIN).apply {
            setPackage(context.packageName)
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
    launchIntent.putExtra(AppAlarmClockIntents.EXTRA_ALARM_KEY, plan.systemAlarmKey())
    return PendingIntent.getActivity(
        context.applicationContext,
        requestCode,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
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
