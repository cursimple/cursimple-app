package com.x500x.cursimple.app.holiday

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.x500x.cursimple.R
import com.x500x.cursimple.app.ClassScheduleApplication
import com.x500x.cursimple.app.MainActivity
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.reminder.model.ReminderSyncReason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** 假期前一晚检查一次，明天放假却还排着提醒时给出关掉的建议。 */
class HolidayEveNoticeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val repository = DataStoreUserPreferencesRepository(context)
        val prefs = repository.preferencesFlow.first()
        val scheduledDates = scheduledReminderDates(context)
        val notice = holidayEveNotice(
            today = BeijingTime.today(),
            holidayCalendar = prefs.holidayCalendar,
            temporaryScheduleOverrides = prefs.temporaryScheduleOverrides,
            skipRemindersOnHoliday = prefs.skipRemindersOnHoliday,
            mutedDates = prefs.reminderMutedDates.mapNotNull {
                runCatching { LocalDate.parse(it) }.getOrNull()
            }.toSet(),
            reminderCountOn = { date -> scheduledDates.count { it == date } },
        )
        if (notice is HolidayEveNotice.SuggestMute) {
            context.postHolidayEveNotice(notice)
        }
        return Result.success()
    }

    /** 数的是已经排上的闹钟，与用户当天真正会被叫醒的次数一致。 */
    private suspend fun scheduledReminderDates(context: Context): List<LocalDate> = runCatching {
        val container = (context as? ClassScheduleApplication)?.appContainer
            ?: return emptyList()
        container.reminderRepository.getSystemAlarmRecords().map { record ->
            Instant.ofEpochMilli(record.triggerAtMillis).atZone(BeijingTime.zone).toLocalDate()
        }
    }.getOrDefault(emptyList())

    companion object {
        private const val WORK_NAME = "holiday_eve_notice"
        private val NOTICE_TIME: LocalTime = LocalTime.of(20, 0)

        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            val target = if (now.toLocalTime().isBefore(NOTICE_TIME)) {
                LocalDateTime.of(now.toLocalDate(), NOTICE_TIME)
            } else {
                LocalDateTime.of(now.toLocalDate().plusDays(1), NOTICE_TIME)
            }
            val request = PeriodicWorkRequestBuilder<HolidayEveNoticeWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, target).toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}

private const val CHANNEL_ID = "holiday_eve_notice"
private const val NOTIFICATION_ID = 0x48454E
internal const val EXTRA_MUTE_DATE = "mute_date"

private fun Context.postHolidayEveNotice(notice: HolidayEveNotice.SuggestMute) {
    // 用户没给通知权限时不必构造通知
    if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    createHolidayEveChannel()
    val holidayName = notice.holidayNameRes?.let(::getString)
        ?: notice.holidayName?.takeIf { it.isNotBlank() }
        ?: getString(R.string.holiday_eve_generic_name)
    val muteIntent = PendingIntent.getBroadcast(
        this,
        notice.date.hashCode(),
        Intent(this, HolidayEveMuteReceiver::class.java).putExtra(EXTRA_MUTE_DATE, notice.date.toString()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val openIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(getString(R.string.holiday_eve_notice_title, holidayName))
        .setContentText(getString(R.string.holiday_eve_notice_body, notice.reminderCount))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .setContentIntent(openIntent)
        .addAction(0, getString(R.string.holiday_eve_notice_action_mute), muteIntent)
        .build()
    runCatching {
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }
}

private fun Context.createHolidayEveChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    runCatching {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.holiday_eve_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

/** 通知上「关掉明天的提醒」的落点。 */
class HolidayEveMuteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val date = intent.getStringExtra(EXTRA_MUTE_DATE) ?: return
        val pending = goAsync()
        runCatching {
            runBlocking {
                DataStoreUserPreferencesRepository(context.applicationContext)
                    .setReminderMuted(date, muted = true)
                (context.applicationContext as? ClassScheduleApplication)
                    ?.appContainer
                    ?.tryRunSharedAlarmPoll(ReminderSyncReason.WidgetRefresh)
            }
        }
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        pending.finish()
    }
}
