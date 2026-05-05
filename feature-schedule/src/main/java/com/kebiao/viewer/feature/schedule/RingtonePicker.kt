package com.kebiao.viewer.feature.schedule

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.widget.Toast

internal fun alarmRingtonePickerIntent(): Intent =
    Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
    }

internal fun launchAlarmRingtonePicker(
    context: Context,
    launch: (Intent) -> Unit,
) {
    try {
        launch(alarmRingtonePickerIntent())
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "当前系统没有可用的铃声选择器", Toast.LENGTH_SHORT).show()
    }
}
