package com.x500x.cursimple.app.reminder

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.x500x.cursimple.R
import com.x500x.cursimple.app.MainActivity
import com.x500x.cursimple.app.notice.ClassNoticeGateway
import com.x500x.cursimple.app.notice.ClassNoticeScheduler
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.reminder.DataStoreReminderRepository
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * 闹钟守护：一条常驻的前台服务。
 *
 * 系统的 AlarmManager 本来就能在应用没运行时把闹钟送到，问题出在国产系统上：
 * 从最近任务划掉应用常常等同于强停，一强停这个应用挂着的闹钟会被一起清掉，
 * 之后再没有任何事件能把它拉起来重排，于是「退出应用闹钟就不响了」。
 *
 * 常驻前台服务让进程留在后台，划掉任务时还能在 [onTaskRemoved] 里补一次重排，
 * 真被杀掉也有 [restartAlarm] 这条定时自愈的后路。代价是一条常驻通知，所以做成开关。
 */
class AlarmKeepAliveService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 是用户关掉的，还是被系统或厂商杀掉的：只有后者要自己回来 */
    private var stoppedOnPurpose = false

    // 上课提醒一发完就排下一节，常驻通知上的「下一次提醒」要跟着变
    private val classNoticeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        scope.launch {
            runCatching { refreshNotification() }
                .onFailure { ReminderLogger.warn("reminder.keep_alive.refresh.failure", emptyMap(), it) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // 拉起后必须尽快进入前台，先挂一条不带下一场时间的通知，读到数据再刷新文案
        startForegroundCompat(buildNotification(nextAlarmText = null))
        getSharedPreferences(ClassNoticeScheduler.STATE_PREFS, MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(classNoticeListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(buildNotification(nextAlarmText = null))
        if (intent?.action == ACTION_STOP) {
            stoppedOnPurpose = true
            stopSelfCompat()
            return START_NOT_STICKY
        }
        scope.launch {
            runCatching { refreshNotification() }
                .onFailure { ReminderLogger.warn("reminder.keep_alive.refresh.failure", emptyMap(), it) }
            if (intent?.getBooleanExtra(EXTRA_RESCHEDULE_ALARMS, false) == true) {
                runCatching { AlarmRuntimeMaintenance.onAlarmStarted(applicationContext) }
                    .onFailure { ReminderLogger.warn("reminder.keep_alive.resync.failure", emptyMap(), it) }
            }
        }
        // 被系统回收后要自己回来，守护才算连续
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        ReminderLogger.info("reminder.keep_alive.task_removed", emptyMap())
        // 划掉最近任务时进程随时可能被收走，这里先排一条几秒后的自启动闹钟兜底
        scheduleRestart(applicationContext)
        // 趁进程还在把上课提醒重挂一遍：部分系统划掉任务会连带清掉挂着的闹钟
        scope.launch {
            runCatching { ClassNoticeGateway.reschedule(applicationContext) }
                .onFailure { ReminderLogger.warn("reminder.keep_alive.class_notice.failure", emptyMap(), it) }
        }
    }

    override fun onDestroy() {
        // 不是用户关的就是被收走的（省电清理、内存回收）：排一条自启动闹钟，几秒后回来
        if (!stoppedOnPurpose) scheduleRestart(applicationContext)
        getSharedPreferences(ClassNoticeScheduler.STATE_PREFS, MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(classNoticeListener)
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun refreshNotification() {
        val now = BeijingTime.nowMillis(BeijingTime.zone)
        val nextAlarmAt = DataStoreReminderRepository(applicationContext)
            .systemAlarmRecordsFlow
            .first()
            .map { it.triggerAtMillis }
            .filter { it > now }
            .minOrNull()
        // 闹钟和上课提醒都靠这条守护，哪个先到就显示哪个
        val nextNoticeAt = ClassNoticeScheduler.nextFireAtMillis(applicationContext)?.takeIf { it > now }
        val nextAt = listOfNotNull(nextAlarmAt, nextNoticeAt).minOrNull()
        val text = nextAt?.let {
            getString(R.string.alarm_keep_alive_next, formatClock(it))
        }
        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun formatClock(triggerAtMillis: Long): String =
        Instant.ofEpochMilli(triggerAtMillis)
            .atZone(BeijingTime.zone)
            .format(CLOCK_FORMAT)

    private fun buildNotification(nextAlarmText: String?): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            CONTENT_REQUEST_CODE,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(getString(R.string.alarm_keep_alive_title))
            .setContentText(nextAlarmText ?: getString(R.string.alarm_keep_alive_idle))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        runCatching {
            // specialUse 这个类型是 Android 14 才有的。在更早的系统上传它会因为
            // 「清单里没有声明这个类型」被拒，服务进不了前台，接着就被系统判超时杀掉；
            // 那些版本改用不带类型的重载，由清单里的声明决定。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { error ->
            ReminderLogger.warn("reminder.keep_alive.start_foreground.failure", emptyMap(), error)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        // 渠道已存在时再建一次只会更新名称和描述，老用户也能看到改过的文案
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.alarm_keep_alive_channel),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = getString(R.string.alarm_keep_alive_channel_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    private fun stopSelfCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val CHANNEL_ID = "course_alarm_keep_alive"
        private const val NOTIFICATION_ID = 7601
        private const val CONTENT_REQUEST_CODE = 7602
        private const val RESTART_REQUEST_CODE = 7603
        private const val ACTION_STOP = "com.x500x.cursimple.action.KEEP_ALIVE_STOP"
        private const val EXTRA_RESCHEDULE_ALARMS = "com.x500x.cursimple.extra.KEEP_ALIVE_RESYNC"
        private const val RESTART_DELAY_MILLIS = 5_000L
        private val CLOCK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d HH:mm")

        /** 按用户开关决定守护服务的去留；读偏好失败时保持现状。 */
        suspend fun applyPreference(context: Context) {
            val enabled = runCatching {
                DataStoreUserPreferencesRepository(context.applicationContext)
                    .preferencesFlow
                    .first()
                    .alarmKeepAliveEnabled
            }.getOrNull() ?: return
            if (enabled) start(context, rescheduleAlarms = false) else stop(context)
        }

        fun start(context: Context, rescheduleAlarms: Boolean) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, AlarmKeepAliveService::class.java)
                .putExtra(EXTRA_RESCHEDULE_ALARMS, rescheduleAlarms)
            runCatching {
                ContextCompat.startForegroundService(appContext, intent)
            }.onFailure { error ->
                // 后台启动前台服务在部分系统上会被拒，守护开不起来也不能连累其它流程
                ReminderLogger.warn("reminder.keep_alive.start.failure", emptyMap(), error)
            }
        }

        fun stop(context: Context) {
            val appContext = context.applicationContext
            cancelRestart(appContext)
            runCatching {
                appContext.startService(
                    Intent(appContext, AlarmKeepAliveService::class.java).setAction(ACTION_STOP),
                )
            }.onFailure {
                runCatching { appContext.stopService(Intent(appContext, AlarmKeepAliveService::class.java)) }
            }
        }

        /** 进程被划掉后几秒把自己叫回来。 */
        private fun scheduleRestart(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val operation = restartPendingIntent(context, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
            // 必须是精确闹钟：Android 12 起只有精确闹钟触发的广播才被允许在后台拉起前台服务
            runCatching {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + RESTART_DELAY_MILLIS,
                    operation,
                )
            }.onFailure { error ->
                ReminderLogger.warn("reminder.keep_alive.restart_alarm.failure", emptyMap(), error)
            }
        }

        private fun cancelRestart(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val operation = restartPendingIntent(context, PendingIntent.FLAG_NO_CREATE) ?: return
            runCatching {
                alarmManager.cancel(operation)
                operation.cancel()
            }
        }

        private fun restartPendingIntent(context: Context, extraFlags: Int): PendingIntent? =
            PendingIntent.getBroadcast(
                context.applicationContext,
                RESTART_REQUEST_CODE,
                Intent(context.applicationContext, AlarmKeepAliveRestartReceiver::class.java)
                    .setAction(AlarmKeepAliveRestartReceiver.ACTION_RESTART)
                    .setPackage(context.packageName),
                PendingIntent.FLAG_IMMUTABLE or extraFlags,
            )
    }
}
