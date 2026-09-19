package com.x500x.cursimple.app.reminder

import android.content.Context
import android.content.Intent
import com.x500x.cursimple.core.reminder.permission.AlarmSettingsIntents

/**
 * 设置入口的薄封装。
 *
 * 真正的候选列表在 core-reminder 的 [AlarmSettingsIntents] 里，
 * 这里只保留旧调用点用的单个 Intent 形态；新代码优先用候选列表，
 * 免得第一条打不开时什么都不发生。
 */
object AlarmPermissionIntents {
    fun exactAlarmSettingsIntent(context: Context): Intent =
        AlarmSettingsIntents.exactAlarm(context).first()

    fun batteryOptimizationIntent(context: Context): Intent =
        AlarmSettingsIntents.batteryOptimization(context).first()

    fun fullScreenIntentSettingsIntent(context: Context): Intent =
        AlarmSettingsIntents.fullScreenIntent(context).first()

    fun appDetailsIntent(context: Context): Intent =
        AlarmSettingsIntents.appDetails(context)
}
