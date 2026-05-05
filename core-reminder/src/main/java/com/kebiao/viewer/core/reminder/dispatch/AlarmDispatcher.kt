package com.kebiao.viewer.core.reminder.dispatch

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
                message = "已尝试创建系统闹钟",
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

class AppAlarmDispatcher(
    private val context: Context,
) : AlarmDispatcher {
    override suspend fun dispatch(plan: ReminderPlan): AlarmDispatchResult {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!alarmManager.canScheduleExactAlarmCompat()) {
            val message = "缺少精确闹钟权限，请在系统设置允许闹钟与提醒"
            ReminderLogger.warn(
                "reminder.app_alarm.permission_missing",
                mapOf("ruleId" to plan.ruleId, "planId" to plan.planId, "sdk" to Build.VERSION.SDK_INT),
            )
            return AlarmDispatchResult(
                channel = AlarmDispatchChannel.AppAlarm,
                succeeded = false,
                message = message,
            )
        }
        val intent = ReminderAlarmReceiver.createIntent(context, plan)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            intent.getIntExtra(ReminderAlarmReceiver.EXTRA_REQUEST_CODE, 0),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(
                context,
                pendingIntent.hashCode(),
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        ReminderLogger.info(
            "reminder.app_alarm.dispatch.start",
            mapOf("ruleId" to plan.ruleId, "planId" to plan.planId, "triggerAtMillis" to plan.triggerAtMillis),
        )
        return runCatching {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(plan.triggerAtMillis, launchIntent),
                pendingIntent,
            )
            ReminderLogger.info(
                "reminder.app_alarm.dispatch.success",
                mapOf("ruleId" to plan.ruleId, "planId" to plan.planId),
            )
            AlarmDispatchResult(
                channel = AlarmDispatchChannel.AppAlarm,
                succeeded = true,
                message = "已创建应用提醒",
            )
        }.getOrElse {
            val message = when (it) {
                is SecurityException -> "系统拒绝创建应用提醒"
                else -> it.message ?: "创建应用提醒失败"
            }
            ReminderLogger.warn(
                "reminder.app_alarm.dispatch.failure",
                mapOf("ruleId" to plan.ruleId, "planId" to plan.planId, "reason" to message),
                it,
            )
            AlarmDispatchResult(
                channel = AlarmDispatchChannel.AppAlarm,
                succeeded = false,
                message = message,
            )
        }
    }
}

class HybridAlarmDispatcher(
    private val systemDispatcher: SystemAlarmClockDispatcher,
    private val appDispatcher: AppAlarmDispatcher,
) : AlarmDispatcher {
    override suspend fun dispatch(plan: ReminderPlan): AlarmDispatchResult {
        val systemResult = systemDispatcher.dispatch(plan)
        if (systemResult.succeeded) {
            return systemResult
        }
        return appDispatcher.dispatch(plan)
    }
}

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
        val ringtone = intent.getStringExtra(EXTRA_RINGTONE_URI)
        val notificationManager = NotificationManagerCompat.from(context)
        val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, 0)
        if (!notificationManager.areNotificationsEnabled()) {
            ReminderLogger.warn(
                "reminder.notification.disabled",
                mapOf("requestCode" to requestCode),
            )
            return
        }
        runCatching {
            val channelId = ensureChannel(context, ringtone)
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(requestCode, notification)
            ReminderLogger.info("reminder.notification.posted", mapOf("requestCode" to requestCode))
        }.onFailure {
            ReminderLogger.warn(
                "reminder.notification.failure",
                mapOf("requestCode" to requestCode),
                it,
            )
        }
    }

    private fun ensureChannel(context: Context, ringtone: String?): String {
        val channelId = "schedule_reminder_${ringtone?.hashCode() ?: 0}"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(
                    channelId,
                    "课表提醒",
                    NotificationManager.IMPORTANCE_HIGH,
                )
                if (!ringtone.isNullOrBlank()) {
                    channel.setSound(
                        Uri.parse(ringtone),
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .build(),
                    )
                }
                manager.createNotificationChannel(channel)
            }
        }
        return channelId
    }

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_RINGTONE_URI = "extra_ringtone_uri"
        const val EXTRA_REQUEST_CODE = "extra_request_code"

        fun createIntent(context: Context, plan: ReminderPlan): Intent {
            return Intent(context, ReminderAlarmReceiver::class.java).apply {
                putExtra(EXTRA_TITLE, plan.title)
                putExtra(EXTRA_MESSAGE, plan.message)
                putExtra(EXTRA_RINGTONE_URI, plan.ringtoneUri)
                putExtra(EXTRA_REQUEST_CODE, plan.planId.hashCode())
            }
        }
    }
}

private fun AlarmManager.canScheduleExactAlarmCompat(): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || runCatching {
        canScheduleExactAlarms()
    }.getOrDefault(false)
}
