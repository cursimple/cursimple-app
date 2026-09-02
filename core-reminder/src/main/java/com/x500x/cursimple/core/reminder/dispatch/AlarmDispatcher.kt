package com.x500x.cursimple.core.reminder.dispatch

import android.app.ActivityManager
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
import com.x500x.cursimple.core.reminder.model.ReminderMessage
import com.x500x.cursimple.core.reminder.model.ReminderPlan
import com.x500x.cursimple.core.reminder.model.ReminderAlarmBackend
import com.x500x.cursimple.core.reminder.model.SystemAlarmRecord
import com.x500x.cursimple.core.reminder.model.appAlarmRequestCode
import com.x500x.cursimple.core.reminder.model.reminderNotificationTitleText
import com.x500x.cursimple.core.reminder.model.reminderPlanMessageText
import com.x500x.cursimple.core.reminder.model.reminderPlanTitleText
import com.x500x.cursimple.core.reminder.model.systemAlarmKey
import com.x500x.cursimple.core.reminder.model.systemAlarmLabel
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import com.x500x.cursimple.core.reminder.R
import java.time.Instant

interface AlarmDispatcher {
    suspend fun dispatch(plan: ReminderPlan): AlarmDispatchResult
}

interface AlarmDismisser {
    suspend fun dismiss(record: SystemAlarmRecord): AlarmDismissResult
}

fun interface AlarmRegistrationVerifier {
    fun isRegistered(record: SystemAlarmRecord): Boolean
}

/** 判断当前进程是否处于可以直接拉起 Activity 的状态。 */
fun interface ForegroundActivityStartGate {
    fun canStartActivity(): Boolean
}

/**
 * 用进程重要度判断前台 Activity 启动条件。
 * Android 10 起后台进程的 Activity 启动会被静默丢弃，只有进程持有可见 Activity 时才放行。
 */
class ProcessImportanceActivityStartGate : ForegroundActivityStartGate {
    override fun canStartActivity(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return runCatching {
            val state = ActivityManager.RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(state)
            state.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }.getOrDefault(false)
    }
}

object SystemAlarmClockMessages {
    val DISPATCH_REQUIRES_FOREGROUND: ReminderMessage = ReminderMessage.SystemClockDispatchRequiresForeground
    val DISMISS_REQUIRES_FOREGROUND: ReminderMessage = ReminderMessage.SystemClockDismissRequiresForeground
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
    const val EXTRA_ALERT_MODE = "com.x500x.cursimple.extra.ALERT_MODE"
    const val EXTRA_RING_DURATION_SECONDS = "com.x500x.cursimple.extra.RING_DURATION_SECONDS"
    const val EXTRA_REPEAT_INTERVAL_SECONDS = "com.x500x.cursimple.extra.REPEAT_INTERVAL_SECONDS"
    const val EXTRA_REPEAT_COUNT = "com.x500x.cursimple.extra.REPEAT_COUNT"
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
                message = context.getString(R.string.reminder_exact_alarm_permission_off),
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
                message = context.getString(R.string.reminder_app_alarm_set),
            )
        }.getOrElse {
            val message = when (it) {
                is SecurityException -> context.getString(R.string.reminder_exact_alarm_denied)
                else -> it.message ?: context.getString(R.string.reminder_app_alarm_set_failed)
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
                message = context.getString(R.string.reminder_app_alarm_cancelled),
            )
        }.getOrElse {
            val message = it.message ?: context.getString(R.string.reminder_app_alarm_cancel_failed)
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

class AppAlarmClockRegistrationVerifier(
    private val context: Context,
) : AlarmRegistrationVerifier {
    override fun isRegistered(record: SystemAlarmRecord): Boolean {
        if (record.backend != ReminderAlarmBackend.AppAlarmClock) return true
        val appContext = context.applicationContext
        val requestCode = record.requestCode ?: (record.alarmKey.hashCode() and Int.MAX_VALUE)
        return appAlarmOperationIntentOrNull(
            context = appContext,
            record = record,
            requestCode = requestCode,
            flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) != null
    }
}

class SystemAlarmClockDispatcher(
    private val context: Context,
    private val foregroundGate: ForegroundActivityStartGate = ProcessImportanceActivityStartGate(),
) : AlarmDispatcher {
    override suspend fun dispatch(plan: ReminderPlan): AlarmDispatchResult {
        if (!foregroundGate.canStartActivity()) {
            ReminderLogger.warn(
                "reminder.system_clock.dispatch.foreground_unavailable",
                mapOf(
                    "ruleId" to plan.ruleId,
                    "planId" to plan.planId,
                    "triggerAtMillis" to plan.triggerAtMillis,
                ),
            )
            return AlarmDispatchResult(
                channel = AlarmDispatchChannel.SystemClockApp,
                succeeded = false,
                message = "",
                localizedMessage = SystemAlarmClockMessages.DISPATCH_REQUIRES_FOREGROUND,
            )
        }
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
                message = context.getString(R.string.reminder_system_clock_create_accepted),
            )
        }.getOrElse {
            val message = when (it) {
                is ActivityNotFoundException -> context.getString(R.string.reminder_system_clock_unavailable)
                is SecurityException -> context.getString(R.string.reminder_system_clock_create_denied)
                else -> it.message ?: context.getString(R.string.reminder_system_clock_create_failed)
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

private fun appAlarmOperationIntentOrNull(
    context: Context,
    record: SystemAlarmRecord,
    requestCode: Int,
    flags: Int,
): PendingIntent? = appAlarmServicePendingIntentOrNull(
    context = context,
    requestCode = requestCode,
    intent = appAlarmServiceIntent(context, record),
    flags = flags,
)

private fun appAlarmServicePendingIntent(
    context: Context,
    requestCode: Int,
    intent: Intent,
): PendingIntent = appAlarmServicePendingIntentOrNull(
    context = context,
    requestCode = requestCode,
    intent = intent,
    flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
) ?: error("App alarm PendingIntent was not created")

private fun appAlarmServicePendingIntentOrNull(
    context: Context,
    requestCode: Int,
    intent: Intent,
    flags: Int,
): PendingIntent? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        PendingIntent.getForegroundService(
            context.applicationContext,
            requestCode,
            intent,
            flags,
        )
    } else {
        PendingIntent.getService(
            context.applicationContext,
            requestCode,
            intent,
            flags,
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
        putExtra(AppAlarmClockIntents.EXTRA_TITLE, context.reminderPlanTitleText(plan))
        putExtra(AppAlarmClockIntents.EXTRA_MESSAGE, context.reminderPlanMessageText(plan))
        putExtra(AppAlarmClockIntents.EXTRA_RINGTONE_URI, plan.ringtoneUri)
        plan.alertMode?.let { putExtra(AppAlarmClockIntents.EXTRA_ALERT_MODE, it.name) }
        plan.ringDurationSeconds?.let { putExtra(AppAlarmClockIntents.EXTRA_RING_DURATION_SECONDS, it) }
        plan.repeatIntervalSeconds?.let { putExtra(AppAlarmClockIntents.EXTRA_REPEAT_INTERVAL_SECONDS, it) }
        plan.repeatCount?.let { putExtra(AppAlarmClockIntents.EXTRA_REPEAT_COUNT, it) }
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
        putExtra(
            AppAlarmClockIntents.EXTRA_TITLE,
            record.titleContent?.let { context.reminderNotificationTitleText(it) } ?: record.displayTitle.orEmpty(),
        )
        putExtra(AppAlarmClockIntents.EXTRA_MESSAGE, record.message)
        putExtra(AppAlarmClockIntents.EXTRA_RINGTONE_URI, record.ringtoneUriOverride)
        record.alertModeOverride?.let { putExtra(AppAlarmClockIntents.EXTRA_ALERT_MODE, it.name) }
        record.ringDurationSeconds?.let { putExtra(AppAlarmClockIntents.EXTRA_RING_DURATION_SECONDS, it) }
        record.repeatIntervalSeconds?.let { putExtra(AppAlarmClockIntents.EXTRA_REPEAT_INTERVAL_SECONDS, it) }
        record.repeatCount?.let { putExtra(AppAlarmClockIntents.EXTRA_REPEAT_COUNT, it) }
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
    private val foregroundGate: ForegroundActivityStartGate = ProcessImportanceActivityStartGate(),
) : AlarmDismisser {
    override suspend fun dismiss(record: SystemAlarmRecord): AlarmDismissResult {
        if (!foregroundGate.canStartActivity()) {
            ReminderLogger.warn(
                "reminder.system_clock.dismiss.foreground_unavailable",
                mapOf(
                    "ruleId" to record.ruleId,
                    "planId" to record.planId,
                    "alarmKey" to record.alarmKey,
                ),
            )
            return AlarmDismissResult(
                alarmKey = record.alarmKey,
                succeeded = false,
                message = "",
                localizedMessage = SystemAlarmClockMessages.DISMISS_REQUIRES_FOREGROUND,
            )
        }
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
                message = context.getString(R.string.reminder_system_clock_delete_accepted),
            )
        }.getOrElse {
            val message = when (it) {
                is ActivityNotFoundException -> context.getString(R.string.reminder_system_clock_unavailable)
                is SecurityException -> context.getString(R.string.reminder_system_clock_delete_denied)
                else -> it.message ?: context.getString(R.string.reminder_system_clock_delete_failed)
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
