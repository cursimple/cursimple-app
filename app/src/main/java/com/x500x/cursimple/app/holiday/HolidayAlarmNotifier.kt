package com.x500x.cursimple.app.holiday

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.x500x.cursimple.R
import com.x500x.cursimple.app.MainActivity
import com.x500x.cursimple.core.reminder.AlarmDaySuppression

/** 跳过闹钟的说明只放通知栏，不响铃、不震动、不额外弹窗。 */
object HolidayAlarmNotifier {
    private const val CHANNEL = "holiday_alarm_status"
    private const val ID = 0x48414C

    fun postSkipped(context: Context, title: String, reason: AlarmDaySuppression) {
        post(
            context,
            context.getString(if (reason == AlarmDaySuppression.Holiday) R.string.holiday_alarm_skipped_title else R.string.holiday_alarm_muted_title),
            context.getString(if (reason == AlarmDaySuppression.Holiday) R.string.holiday_alarm_skipped_body else R.string.holiday_alarm_muted_body, title),
        )
    }

    fun postTomorrow(context: Context, name: String) = post(
        context,
        context.getString(R.string.holiday_eve_notice_title, name),
        context.getString(R.string.holiday_alarm_tomorrow_body),
    )

    private fun post(context: Context, title: String, text: String): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                    NotificationChannel(CHANNEL, context.getString(R.string.holiday_alarm_status_channel), NotificationManager.IMPORTANCE_LOW)
                        .apply { setSound(null, null); enableVibration(false) },
                )
            }
            val open = PendingIntent.getActivity(
                context, ID,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            NotificationManagerCompat.from(context).notify(
                ID,
                NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setContentIntent(open).setAutoCancel(true).setSilent(true).setPriority(NotificationCompat.PRIORITY_LOW).build(),
            )
        }.isSuccess
    }
}
