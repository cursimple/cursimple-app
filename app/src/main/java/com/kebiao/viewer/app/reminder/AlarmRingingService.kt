package com.kebiao.viewer.app.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
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
import com.kebiao.viewer.core.data.DataStoreUserPreferencesRepository
import com.kebiao.viewer.core.data.reminder.DataStoreReminderRepository
import com.kebiao.viewer.core.reminder.ReminderCoordinator
import com.kebiao.viewer.core.reminder.dispatch.AppAlarmClockIntents
import com.kebiao.viewer.core.reminder.logging.ReminderLogger
import com.kebiao.viewer.core.reminder.model.ReminderPlan
import com.kebiao.viewer.core.reminder.model.TriggeredAppAlarmFinishAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AlarmRingingService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Default)
    private val finishMutex = Mutex()
    private var ringJob: Job? = null
    private var vibrationStopJob: Job? = null
    private var ringtone: Ringtone? = null
    private var activeVibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentAlarm: ActiveAlarm? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                requestFinish(reason = "user_stop", snooze = false, intent = intent)
                return START_NOT_STICKY
            }
            ACTION_SNOOZE -> {
                requestFinish(reason = "user_snooze", snooze = true, intent = intent)
                return START_NOT_STICKY
            }
            ACTION_RING -> startRinging(intent)
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ringJob?.cancel()
        vibrationStopJob?.cancel()
        stopPlayback()
        stopForegroundCompat()
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun startRinging(intent: Intent) {
        ringJob?.cancel()
        vibrationStopJob?.cancel()
        stopPlayback()
        val alarm = intent.toActiveAlarm()
        currentAlarm = alarm
        runCatching {
            startForegroundCompat(alarm)
        }.onFailure { error ->
            ReminderLogger.warn(
                "reminder.app_alarm_clock.ringing.foreground.failure",
                mapOf("alarmKey" to alarm.alarmKey),
                error,
            )
            stopSelf()
            return
        }
        serviceScope.launch(Dispatchers.IO) {
            AlarmRuntimeMaintenance.onAlarmStarted(applicationContext)
        }
        ringJob = serviceScope.launch {
            val prefs = DataStoreUserPreferencesRepository(applicationContext).preferencesFlow.first()
            val repeatCount = prefs.alarmRepeatCount.coerceIn(1, 10)
            val durationMillis = prefs.alarmRingDurationSeconds.coerceIn(5, 600) * 1000L
            val intervalMillis = prefs.alarmRepeatIntervalSeconds.coerceIn(5, 3600) * 1000L
            val delayMillis = System.currentTimeMillis() - alarm.triggerAtMillis
            if (alarm.triggerAtMillis > 0L && delayMillis > MISSED_ALARM_THRESHOLD_MILLIS) {
                ReminderLogger.warn(
                    "reminder.app_alarm_clock.ringing.late",
                    mapOf("alarmKey" to alarm.alarmKey, "delayMillis" to delayMillis),
                )
            }
            repeat(repeatCount) { index ->
                val round = index + 1
                ReminderLogger.info(
                    "reminder.app_alarm_clock.ringing.round.start",
                    mapOf("alarmKey" to alarm.alarmKey, "round" to round, "repeatCount" to repeatCount),
                )
                acquireWakeLock(durationMillis + WAKE_LOCK_EXTRA_MILLIS)
                startTone(alarm.ringtoneUri)
                vibrate(durationMillis)
                delay(durationMillis)
                stopPlayback()
                ReminderLogger.info(
                    "reminder.app_alarm_clock.ringing.round.finish",
                    mapOf("alarmKey" to alarm.alarmKey, "round" to round),
                )
                if (round < repeatCount) {
                    delay(intervalMillis)
                }
            }
            finishRinging(alarm = alarm, reason = "finished", snooze = false)
        }
    }

    private fun requestFinish(reason: String, snooze: Boolean, intent: Intent?) {
        val alarm = intent?.toActiveAlarm()?.takeIf { it.alarmKey.isNotBlank() } ?: currentAlarm
        ringJob?.cancel()
        ringJob = null
        serviceScope.launch {
            finishRinging(alarm = alarm, reason = reason, snooze = snooze)
        }
    }

    private suspend fun finishRinging(alarm: ActiveAlarm?, reason: String, snooze: Boolean) {
        finishMutex.withLock {
            stopPlayback()
            if (alarm != null) {
                finishTriggeredAlarm(alarm, snooze)
            }
            runPostFinishMaintenance()
            currentAlarm = null
            ReminderLogger.info(
                "reminder.app_alarm_clock.ringing.stop",
                mapOf(
                    "reason" to reason,
                    "snooze" to snooze,
                    "alarmKey" to alarm?.alarmKey.orEmpty(),
                ),
            )
            stopForegroundCompat()
            stopSelf()
        }
    }

    private suspend fun finishTriggeredAlarm(alarm: ActiveAlarm, snooze: Boolean) {
        if (alarm.alarmKey.isBlank()) return
        withContext(Dispatchers.IO) {
            runCatching {
                val repository = DataStoreReminderRepository(applicationContext)
                val action = if (snooze) {
                    TriggeredAppAlarmFinishAction.Snooze(alarm.toSnoozePlan())
                } else {
                    TriggeredAppAlarmFinishAction.Dismiss
                }
                val result = ReminderCoordinator(
                    context = applicationContext,
                    repository = repository,
                ).finishTriggeredAppAlarm(
                    alarmKey = alarm.alarmKey,
                    ruleId = alarm.ruleId,
                    action = action,
                )
                ReminderLogger.info(
                    "reminder.app_alarm_clock.ringing.finish_result",
                    mapOf(
                        "alarmKey" to alarm.alarmKey,
                        "ruleId" to alarm.ruleId,
                        "snooze" to snooze,
                        "snoozeCreated" to result.snoozeCreated,
                        "message" to result.message,
                    ),
                )
            }.onFailure { error ->
                ReminderLogger.warn(
                    "reminder.app_alarm_clock.ringing.finish.failure",
                    mapOf("alarmKey" to alarm.alarmKey, "ruleId" to alarm.ruleId, "snooze" to snooze),
                    error,
                )
            }
        }
    }

    private fun ActiveAlarm.toSnoozePlan(): ReminderPlan {
        val triggerAtMillis = System.currentTimeMillis() + SNOOZE_DELAY_MILLIS
        return ReminderPlan(
            planId = "${planId.ifBlank { alarmKey }}_snooze_$triggerAtMillis",
            ruleId = ruleId,
            pluginId = pluginId.ifBlank { "snooze" },
            title = title.ifBlank { "课程提醒" },
            message = "已延后 5 分钟",
            triggerAtMillis = triggerAtMillis,
            ringtoneUri = ringtoneUri,
            courseId = courseId,
        )
    }

    private suspend fun runPostFinishMaintenance() {
        withContext(Dispatchers.IO) {
            AlarmRuntimeMaintenance.onAlarmFinished(applicationContext)
        }
    }

    private fun startForegroundCompat(alarm: ActiveAlarm) {
        val stopIntent = PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            serviceIntent(ACTION_STOP, alarm),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snoozeIntent = PendingIntent.getService(
            this,
            SNOOZE_REQUEST_CODE,
            serviceIntent(ACTION_SNOOZE, alarm),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val fullScreenIntent = PendingIntent.getActivity(
            this,
            FULL_SCREEN_REQUEST_CODE,
            Intent(this, AlarmRingingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putAlarmExtras(alarm)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(alarm.title.ifBlank { "课程提醒" })
            .setContentText(alarm.message.ifBlank { "课程即将开始" })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(fullScreenIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .addAction(0, "停止", stopIntent)
            .addAction(0, "延后 5 分钟", snoozeIntent)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        ReminderLogger.info("reminder.app_alarm_clock.ringing.foreground.start", mapOf("alarmKey" to alarm.alarmKey))
    }

    private fun serviceIntent(actionName: String, alarm: ActiveAlarm): Intent =
        Intent(this, AlarmRingingService::class.java).apply {
            action = actionName
            putAlarmExtras(alarm)
        }

    private fun startTone(rawUri: String?) {
        runCatching {
            stopTone()
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val candidates = buildList {
                rawUri?.takeIf { it.isNotBlank() }?.let { add(Uri.parse(it)) }
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.let(::add)
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)?.let(::add)
            }.distinct()
            for (uri in candidates) {
                val candidate = runCatching {
                    RingtoneManager.getRingtone(applicationContext, uri)
                }.getOrNull() ?: continue
                ringtone = candidate.apply {
                    audioAttributes = attributes
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        isLooping = true
                    }
                    play()
                }
                if (ringtone?.isPlaying == true) return
            }
            ReminderLogger.warn("reminder.app_alarm_clock.ringing.tone.empty", emptyMap())
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
            activeVibrator = vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0L, 800L, 800L), 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0L, 800L, 800L), 0)
            }
            vibrationStopJob?.cancel()
            vibrationStopJob = serviceScope.launch {
                delay(durationMillis)
                runCatching { vibrator.cancel() }
                if (activeVibrator == vibrator) {
                    activeVibrator = null
                }
            }
        }.onFailure { error ->
            ReminderLogger.warn("reminder.app_alarm_clock.ringing.vibrate.failure", emptyMap(), error)
        }
    }

    private fun stopVibration() {
        runCatching {
            vibrationStopJob?.cancel()
            vibrationStopJob = null
            activeVibrator?.cancel()
            activeVibrator = null
        }.onFailure { error ->
            ReminderLogger.warn("reminder.app_alarm_clock.ringing.vibrate_stop.failure", emptyMap(), error)
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

    private fun stopPlayback() {
        stopTone()
        stopVibration()
        releaseWakeLock()
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
                enableVibration(true)
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }.onFailure { error ->
            ReminderLogger.warn("reminder.app_alarm_clock.ringing.channel.failure", emptyMap(), error)
        }
    }

    private fun Intent.toActiveAlarm(): ActiveAlarm = ActiveAlarm(
        alarmKey = getStringExtra(AppAlarmClockIntents.EXTRA_ALARM_KEY).orEmpty(),
        ruleId = getStringExtra(AppAlarmClockIntents.EXTRA_RULE_ID).orEmpty(),
        pluginId = getStringExtra(AppAlarmClockIntents.EXTRA_PLUGIN_ID).orEmpty(),
        planId = getStringExtra(AppAlarmClockIntents.EXTRA_PLAN_ID).orEmpty(),
        courseId = getStringExtra(AppAlarmClockIntents.EXTRA_COURSE_ID)?.takeIf { it.isNotBlank() },
        title = getStringExtra(AppAlarmClockIntents.EXTRA_TITLE).orEmpty(),
        message = getStringExtra(AppAlarmClockIntents.EXTRA_MESSAGE).orEmpty(),
        ringtoneUri = getStringExtra(AppAlarmClockIntents.EXTRA_RINGTONE_URI)?.takeIf { it.isNotBlank() },
        triggerAtMillis = getLongExtra(AppAlarmClockIntents.EXTRA_TRIGGER_AT_MILLIS, 0L),
    )

    private fun Intent.putAlarmExtras(alarm: ActiveAlarm): Intent = apply {
        putExtra(AppAlarmClockIntents.EXTRA_ALARM_KEY, alarm.alarmKey)
        putExtra(AppAlarmClockIntents.EXTRA_RULE_ID, alarm.ruleId)
        putExtra(AppAlarmClockIntents.EXTRA_PLUGIN_ID, alarm.pluginId)
        putExtra(AppAlarmClockIntents.EXTRA_PLAN_ID, alarm.planId)
        putExtra(AppAlarmClockIntents.EXTRA_COURSE_ID, alarm.courseId)
        putExtra(AppAlarmClockIntents.EXTRA_TRIGGER_AT_MILLIS, alarm.triggerAtMillis)
        putExtra(AppAlarmClockIntents.EXTRA_TITLE, alarm.title)
        putExtra(AppAlarmClockIntents.EXTRA_MESSAGE, alarm.message)
        putExtra(AppAlarmClockIntents.EXTRA_RINGTONE_URI, alarm.ringtoneUri)
    }

    private data class ActiveAlarm(
        val alarmKey: String,
        val ruleId: String,
        val pluginId: String,
        val planId: String,
        val courseId: String?,
        val title: String,
        val message: String,
        val ringtoneUri: String?,
        val triggerAtMillis: Long,
    )

    companion object {
        const val ACTION_RING = AppAlarmClockIntents.ACTION_RING
        const val ACTION_STOP = "com.kebiao.viewer.action.ALARM_STOP"
        const val ACTION_SNOOZE = "com.kebiao.viewer.action.ALARM_SNOOZE"
        private const val CHANNEL_ID = "course_alarm_ringing"
        private const val NOTIFICATION_ID = 7401
        private const val STOP_REQUEST_CODE = 7402
        private const val SNOOZE_REQUEST_CODE = 7403
        private const val FULL_SCREEN_REQUEST_CODE = 7404
        private const val WAKE_LOCK_EXTRA_MILLIS = 10_000L
        private const val MISSED_ALARM_THRESHOLD_MILLIS = 5 * 60 * 1000L
        private const val SNOOZE_DELAY_MILLIS = 5 * 60 * 1000L
    }
}
