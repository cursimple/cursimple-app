package com.x500x.cursimple.app.reminder

import android.content.Intent
import com.x500x.cursimple.core.reminder.dispatch.AppAlarmClockIntents
import com.x500x.cursimple.core.reminder.model.AlarmAlertMode

internal data class ActiveAlarm(
    val alarmKey: String,
    val ruleId: String,
    val pluginId: String,
    val planId: String,
    val courseId: String?,
    val title: String,
    val message: String,
    val ringtoneUri: String?,
    val alertMode: AlarmAlertMode?,
    val triggerAtMillis: Long,
    val ringDurationSeconds: Int?,
    val repeatIntervalSeconds: Int?,
    val repeatCount: Int?,
)

/** 闹钟在 intent 之间传递的全部 extra，响铃、通知按钮与锁屏界面共用这一份字段表。 */
internal fun ActiveAlarm.toAlarmExtras(): Map<String, Any> = buildMap {
    put(AppAlarmClockIntents.EXTRA_ALARM_KEY, alarmKey)
    put(AppAlarmClockIntents.EXTRA_RULE_ID, ruleId)
    put(AppAlarmClockIntents.EXTRA_PLUGIN_ID, pluginId)
    put(AppAlarmClockIntents.EXTRA_PLAN_ID, planId)
    courseId?.let { put(AppAlarmClockIntents.EXTRA_COURSE_ID, it) }
    put(AppAlarmClockIntents.EXTRA_TRIGGER_AT_MILLIS, triggerAtMillis)
    put(AppAlarmClockIntents.EXTRA_TITLE, title)
    put(AppAlarmClockIntents.EXTRA_MESSAGE, message)
    ringtoneUri?.let { put(AppAlarmClockIntents.EXTRA_RINGTONE_URI, it) }
    alertMode?.let { put(AppAlarmClockIntents.EXTRA_ALERT_MODE, it.name) }
    ringDurationSeconds?.let { put(AppAlarmClockIntents.EXTRA_RING_DURATION_SECONDS, it) }
    repeatIntervalSeconds?.let { put(AppAlarmClockIntents.EXTRA_REPEAT_INTERVAL_SECONDS, it) }
    repeatCount?.let { put(AppAlarmClockIntents.EXTRA_REPEAT_COUNT, it) }
}

internal fun activeAlarmFromExtras(readExtra: (String) -> Any?): ActiveAlarm = ActiveAlarm(
    alarmKey = readExtra(AppAlarmClockIntents.EXTRA_ALARM_KEY).asStringOrEmpty(),
    ruleId = readExtra(AppAlarmClockIntents.EXTRA_RULE_ID).asStringOrEmpty(),
    pluginId = readExtra(AppAlarmClockIntents.EXTRA_PLUGIN_ID).asStringOrEmpty(),
    planId = readExtra(AppAlarmClockIntents.EXTRA_PLAN_ID).asStringOrEmpty(),
    courseId = readExtra(AppAlarmClockIntents.EXTRA_COURSE_ID).asNonBlankString(),
    title = readExtra(AppAlarmClockIntents.EXTRA_TITLE).asStringOrEmpty(),
    message = readExtra(AppAlarmClockIntents.EXTRA_MESSAGE).asStringOrEmpty(),
    ringtoneUri = readExtra(AppAlarmClockIntents.EXTRA_RINGTONE_URI).asNonBlankString(),
    alertMode = readExtra(AppAlarmClockIntents.EXTRA_ALERT_MODE).asNonBlankString()
        ?.let { runCatching { AlarmAlertMode.valueOf(it) }.getOrNull() },
    triggerAtMillis = readExtra(AppAlarmClockIntents.EXTRA_TRIGGER_AT_MILLIS) as? Long ?: 0L,
    ringDurationSeconds = readExtra(AppAlarmClockIntents.EXTRA_RING_DURATION_SECONDS).asPositiveInt(),
    repeatIntervalSeconds = readExtra(AppAlarmClockIntents.EXTRA_REPEAT_INTERVAL_SECONDS).asPositiveInt(),
    repeatCount = readExtra(AppAlarmClockIntents.EXTRA_REPEAT_COUNT).asPositiveInt(),
)

internal fun Intent.putAlarmExtras(alarm: ActiveAlarm): Intent = apply {
    alarm.toAlarmExtras().forEach { (key, value) ->
        when (value) {
            is Int -> putExtra(key, value)
            is Long -> putExtra(key, value)
            is String -> putExtra(key, value)
        }
    }
}

@Suppress("DEPRECATION")
internal fun Intent.toActiveAlarm(): ActiveAlarm {
    val bundle = extras
    return activeAlarmFromExtras { key -> bundle?.get(key) }
}

private fun Any?.asStringOrEmpty(): String = (this as? String).orEmpty()

private fun Any?.asNonBlankString(): String? = (this as? String)?.takeIf { it.isNotBlank() }

private fun Any?.asPositiveInt(): Int? = (this as? Int)?.takeIf { it > 0 }
