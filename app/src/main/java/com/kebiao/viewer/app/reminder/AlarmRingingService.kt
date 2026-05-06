package com.kebiao.viewer.app.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.kebiao.viewer.R
import com.kebiao.viewer.core.data.reminder.DataStoreReminderRepository
import com.kebiao.viewer.core.data.DataStoreUserPreferencesRepository
import com.kebiao.viewer.core.reminder.dispatch.AppAlarmClockIntents
import com.kebiao.viewer.core.reminder.logging.ReminderLogger
import com.kebiao.viewer.core.reminder.model.ReminderAlarmBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AlarmRingingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ringJob: Job? = null
    private var ringtone: Ringtone? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRinging("user_stop")
                return START_NOT_STICKY
            }
            ACTION_RING -> startRinging(intent)
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopRinging("destroy")
        super.onDestroy()
    }

    private fun startRinging(intent: Intent) {
        ringJob?.cancel()
        val alarmKey = intent.getStringExtra(AppAlarmClockIntents.EXTRA_ALARM_KEY).orEmpty()
        val title = intent.getStringExtra(AppAlarmClockIntents.EXTRA_TITLE).orEmpty()
        val message = intent.getStringExtra(AppAlarmClockIntents.EXTRA_MESSAGE).orEmpty()
        val ringtoneUri = intent.getStringExtra(AppAlarmClockIntents.EXTRA_RINGTONE_URI)
        val triggerAtMillis = intent.getLongExtra(AppAlarmClockIntents.EXTRA_TRIGGER_AT_MILLIS, 0L)
        runCatching {
            startForegroundCompat(alarmKey, title, message)
        }.onFailure { error ->
            ReminderLogger.warn(
                "reminder.app_alarm_clock.ringing.foreground.failure",
                mapOf("alarmKey" to alarmKey),
                error,
            )
            stopSelf()
            return
        }
        ringJob = serviceScope.launch {
            val prefs = DataStoreUserPreferencesRepository(applicationContext).preferencesFlow.first()
            val repeatCount = prefs.alarmRepeatCount.coerceIn(1, 10)
            val durationMillis = prefs.alarmRingDurationSeconds.coerceIn(5, 600) * 1000L
            val intervalMillis = prefs.alarmRepeatIntervalSeconds.coerceIn(5, 3600) * 1000L
            val delayMillis = System.currentTimeMillis() - triggerAtMillis
            if (triggerAtMillis > 0L && delayMillis > MISSED_ALARM_THRESHOLD_MILLIS) {
                ReminderLogger.warn(
                    "reminder.app_alarm_clock.ringing.late",
                    mapOf("alarmKey" to alarmKey, "delayMillis" to delayMillis),
                )
            }
            repeat(repeatCount) { index ->
                val round = index + 1
                ReminderLogger.info(
                    "reminder.app_alarm_clock.ringing.round.start",
                    mapOf("alarmKey" to alarmKey, "round" to round, "repeatCount" to repeatCount),
                )
                acquireWakeLock(durationMillis + WAKE_LOCK_EXTRA_MILLIS)
                startTone(ringtoneUri)
                vibrate(durationMillis)
                delay(durationMillis)
                stopTone()
                releaseWakeLock()
                ReminderLogger.info(
                    "reminder.app_alarm_clock.ringing.round.finish",
                    mapOf("alarmKey" to alarmKey, "round" to round),
                )
                if (round < repeatCount) {
                    delay(intervalMillis)
                }
            }
            DataStoreReminderRepository(applicationContext).removeSystemAlarmRecord(
                alarmKey,
                ReminderAlarmBackend.AppAlarmClock,
            )
            stopRinging("finished")
        }
    }

    private fun startForegroundCompat(alarmKey: String, title: String, message: String) {
        val stopIntent = PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            Intent(this, AlarmRingingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title.ifBlank { "课程提醒" })
            .setContentText(message.ifBlank { "课程即将开始" })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .addAction(0, "停止", stopIntent)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        ReminderLogger.info("reminder.app_alarm_clock.ringing.foreground.start", mapOf("alarmKey" to alarmKey))
    }

    private fun startTone(rawUri: String?) {
        runCatching {
            stopTone()
            val uri = rawUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(applicationContext, uri)?.also { it.play() }
        }.onFailure { error ->
            ReminderLogger.warn("reminder.app_alarm_clock.ringing.tone.failure", emptyMap(), error)
        }
    }

    private fun stopTone() {
        runCatching {
            ringtone?.stop()
            ringtone = null
        }.onFailure { error ->
            ReminderLogger.warn("reminder.app_alarm_clock.ringing.tone_stop.failure", emptyMap(), error)
        }
    }

    private fun vibrate(durationMillis: Long) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0L, 800L, 800L), 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0L, 800L, 800L), 0)
            }
            serviceScope.launch {
                delay(durationMillis)
                runCatching { vibrator.cancel() }
            }
        }.onFailure { error ->
            ReminderLogger.warn("reminder.app_alarm_clock.ringing.vibrate.failure", emptyMap(), error)
        }
    }

    private fun acquireWakeLock(timeoutMillis: Long) {
        runCatching {
            releaseWakeLock()
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:CourseAlarm").apply {
                setReferenceCounted(false)
                acquire(timeoutMillis)
            }
        }.onFailure { error ->
            ReminderLogger.warn("reminder.app_alarm_clock.ringing.wakelock_acquire.failure", emptyMap(), error)
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            wakeLock?.takeIf { it.isHeld }?.release()
            wakeLock = null
        }.onFailure { error ->
            ReminderLogger.warn("reminder.app_alarm_clock.ringing.wakelock_release.failure", emptyMap(), error)
        }
    }

    private fun stopRinging(reason: String) {
        ringJob?.cancel()
        ringJob = null
        stopTone()
        releaseWakeLock()
        ReminderLogger.info("reminder.app_alarm_clock.ringing.stop", mapOf("reason" to reason))
        stopForegroundCompat()
        stopSelf()
    }

    private fun stopForegroundCompat() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }.onFailure { error ->
            ReminderLogger.warn("reminder.app_alarm_clock.ringing.stop_foreground.failure", emptyMap(), error)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "课程闹钟",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "课程提醒响铃"
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }.onFailure { error ->
            ReminderLogger.warn("reminder.app_alarm_clock.ringing.channel.failure", emptyMap(), error)
        }
    }

    companion object {
        const val ACTION_RING = "com.kebiao.viewer.action.ALARM_RING"
        const val ACTION_STOP = "com.kebiao.viewer.action.ALARM_STOP"
        private const val CHANNEL_ID = "course_alarm_ringing"
        private const val NOTIFICATION_ID = 7401
        private const val STOP_REQUEST_CODE = 7402
        private const val WAKE_LOCK_EXTRA_MILLIS = 10_000L
        private const val MISSED_ALARM_THRESHOLD_MILLIS = 5 * 60 * 1000L
    }
}
