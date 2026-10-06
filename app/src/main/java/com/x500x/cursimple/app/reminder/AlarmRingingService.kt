package com.x500x.cursimple.app.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.widget.Toast
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioFocusRequest
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.x500x.cursimple.R
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.reminder.DataStoreReminderRepository
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.reminder.ReminderCoordinator
import com.x500x.cursimple.core.reminder.alarmDaySuppression
import com.x500x.cursimple.core.reminder.dispatch.AppAlarmClockDismisser
import com.x500x.cursimple.core.data.reminderDayPolicy
import com.x500x.cursimple.core.data.UserPreferences
import com.x500x.cursimple.app.holiday.HolidayAlarmNotifier
import com.x500x.cursimple.core.reminder.dispatch.AppAlarmClockIntents
import com.x500x.cursimple.core.reminder.dispatch.alarmRampVolume
import com.x500x.cursimple.core.reminder.dispatch.ALARM_VOLUME_RAMP_MILLIS
import com.x500x.cursimple.core.reminder.dispatch.alarmArrivalOutcome
import com.x500x.cursimple.core.reminder.dispatch.AlarmArrivalOutcome
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import com.x500x.cursimple.core.reminder.model.AlarmAlertMode
import com.x500x.cursimple.core.reminder.model.ReminderPlan
import com.x500x.cursimple.core.reminder.model.TriggeredAppAlarmFinishAction
import com.x500x.cursimple.core.reminder.model.reminderMessageText
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
    private val arrivalMutex = Mutex()
    private var ringJob: Job? = null
    private var vibrationStopJob: Job? = null
    private var ringtone: Ringtone? = null
    private var activeVibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var volumeRampJob: Job? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    @Volatile
    private var duckFactor: Float = 1f
    private var currentAlarm: ActiveAlarm? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        createPlaceholderChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Meet the foreground-start deadline with a placeholder unless a real ringing notification already exists.
        if (currentAlarm == null && ringJob?.isActive != true) {
            startPlaceholderForeground()
        }
        when (intent?.action) {
            ACTION_STOP -> {
                requestFinish(reason = "user_stop", snooze = false, intent = intent)
                return START_NOT_STICKY
            }
            ACTION_SNOOZE -> {
                requestFinish(reason = "user_snooze", snooze = true, intent = intent)
                return START_NOT_STICKY
            }
            ACTION_RING -> {
                com.x500x.cursimple.app.notice.ClassNoticeNotifier.cancelAlarmPreview(this)
                startRinging(intent, startId)
            }
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

    private fun startRinging(intent: Intent, startId: Int) {
        // Hold the wake lock while loading holiday policy during cold startup.
        if (currentAlarm == null && ringJob?.isActive != true) acquireWakeLock(STARTUP_WAKE_LOCK_MILLIS)
        serviceScope.launch {
            try {
                arrivalMutex.withLock {
                    val alarm = intent.toActiveAlarm()
                    val now = System.currentTimeMillis()
                    val prefs = runCatching { DataStoreUserPreferencesRepository(applicationContext).preferencesFlow.first() }
                        .getOrElse {
                            ReminderLogger.warn("reminder.holiday.preferences_read_failed", emptyMap(), it)
                            UserPreferences()
                        }
                    val repository = DataStoreReminderRepository(applicationContext)
                    val record = runCatching { repository.getSystemAlarmRecords().firstOrNull { it.alarmKey == alarm.alarmKey } }.getOrNull()
                    if (record?.enabled == false) {
                        withContext(Dispatchers.Main) { retireStart(startId) }
                        return@withLock
                    }
                    val allow = record?.allowOnHoliday ?: alarm.allowOnHoliday
                    fun suppression(at: Long) = alarmDaySuppression(
                        at, allow, BeijingTime.zone, prefs.reminderDayPolicy(), prefs.holidayCalendar, prefs.temporaryScheduleOverrides,
                    )
                    val reason = suppression(alarm.triggerAtMillis.takeIf { it > 0L } ?: now) ?: suppression(now)
                    if (reason != null) {
                        val outdated = intent.getLongExtra(EXTRA_GENERATION, 0L) < AlarmArrivalLedger.currentGeneration(applicationContext)
                        val first = !outdated && (alarm.alarmKey.isBlank() || AlarmArrivalLedger.claim(applicationContext, alarm.alarmKey, alarm.triggerAtMillis))
                        if (first) {
                            ReminderLogger.info("reminder.holiday.skipped", mapOf("alarmKey" to alarm.alarmKey, "reason" to reason.name))
                            AlarmRingHistory.record(applicationContext, AlarmRingOutcome.Skipped, alarm.title)
                            HolidayAlarmNotifier.postSkipped(applicationContext, alarm.title.ifBlank { alarm.message }, reason)
                            if (record != null) {
                                AppAlarmClockDismisser(applicationContext).dismiss(record)
                                repository.removeSystemAlarmRecord(record.alarmKey, record.backend)
                            }
                            // Consume this occurrence without deleting its recurring course rule.
                            runPostFinishMaintenance()
                        }
                        withContext(Dispatchers.Main) { retireStart(startId) }
                        return@withLock
                    }
                    intent.putExtra(AppAlarmClockIntents.EXTRA_ALLOW_ON_HOLIDAY, allow)
                    withContext(Dispatchers.Main) { startAllowedAlarm(intent, startId) }
                }
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                ReminderLogger.warn("reminder.holiday.check_failed", emptyMap(), error)
                withContext(Dispatchers.Main) { retireStart(startId) }
            }
        }
    }

    private fun startAllowedAlarm(intent: Intent, startId: Int) {
        // Acquire a startup wake lock only before ringing; a duplicate arrival must not shorten the active ringing lock.
        if (currentAlarm == null && ringJob?.isActive != true) {
            acquireWakeLock(STARTUP_WAKE_LOCK_MILLIS)
        }
        val alarm = intent.toActiveAlarm()
        val claimed = alarm.alarmKey.isBlank() ||
            AlarmArrivalLedger.claim(applicationContext, alarm.alarmKey, alarm.triggerAtMillis)
        val outcome = alarmArrivalOutcome(
            triggerAtMillis = alarm.triggerAtMillis,
            nowMillis = System.currentTimeMillis(),
            alreadyHandled = !claimed,
            intentGeneration = intent.getLongExtra(EXTRA_GENERATION, 0L),
            currentGeneration = AlarmArrivalLedger.currentGeneration(applicationContext),
        )
        when (outcome) {
            AlarmArrivalOutcome.Duplicate, AlarmArrivalOutcome.Outdated -> {
                ReminderLogger.info(
                    "reminder.app_alarm_clock.ringing.skipped",
                    mapOf("alarmKey" to alarm.alarmKey, "outcome" to outcome::class.simpleName.orEmpty()),
                )
                // A duplicate channel arrival must not stop an already-ringing service.
                retireStart(startId)
                return
            }
            is AlarmArrivalOutcome.Missed -> {
                ReminderLogger.warn(
                    "reminder.app_alarm_clock.ringing.missed",
                    mapOf("alarmKey" to alarm.alarmKey, "delayMillis" to outcome.delayMillis),
                )
                AlarmRingHistory.record(
                    context = applicationContext,
                    outcome = AlarmRingOutcome.Missed,
                    label = alarm.title,
                    atMillis = alarm.triggerAtMillis,
                )
                notifyMissedAlarm(alarm)
                serviceScope.launch(Dispatchers.IO) {
                    finishTriggeredAlarm(alarm, snooze = false)
                    runPostFinishMaintenance()
                    retireStart(startId)
                }
                return
            }
            is AlarmArrivalOutcome.RingLate -> ReminderLogger.warn(
                "reminder.app_alarm_clock.ringing.late",
                mapOf("alarmKey" to alarm.alarmKey, "delayMillis" to outcome.delayMillis),
            )
            AlarmArrivalOutcome.Ring -> Unit
        }

        ringJob?.cancel()
        vibrationStopJob?.cancel()
        stopPlayback()
        currentAlarm = alarm
        runCatching {
            startForegroundCompat(alarm)
        }.onFailure { error ->
            ReminderLogger.warn(
                "reminder.app_alarm_clock.ringing.foreground.failure",
                mapOf("alarmKey" to alarm.alarmKey),
                error,
            )
            if (alarm.alarmKey.isNotBlank()) {
                AlarmArrivalLedger.release(applicationContext, alarm.alarmKey, alarm.triggerAtMillis)
            }
            retireStart(startId)
            return
        }
        AlarmRingHistory.record(
            context = applicationContext,
            outcome = AlarmRingOutcome.Rang,
            label = alarm.title,
        )
        serviceScope.launch(Dispatchers.IO) {
            AlarmRuntimeMaintenance.onAlarmStarted(applicationContext)
        }
        ringJob = serviceScope.launch {
            if (!isRecordStillValid(alarm)) {
                ReminderLogger.warn(
                    "reminder.app_alarm_clock.ringing.record_gone",
                    mapOf("alarmKey" to alarm.alarmKey),
                )
                finishRinging(alarm = null, reason = "record_gone", snooze = false)
                return@launch
            }
            val prefs = DataStoreUserPreferencesRepository(applicationContext).preferencesFlow.first()
            val repeatCount = (alarm.repeatCount ?: prefs.alarmRepeatCount).coerceIn(1, 10)
            val durationMillis = (alarm.ringDurationSeconds ?: prefs.alarmRingDurationSeconds)
                .coerceIn(5, 600) * 1000L
            val intervalMillis = (alarm.repeatIntervalSeconds ?: prefs.alarmRepeatIntervalSeconds)
                .coerceIn(5, 3600) * 1000L
            val alertMode = alarm.alertMode ?: prefs.alarmAlertMode
            val ringtoneUri = alarm.ringtoneUri ?: prefs.alarmRingtoneUri
            repeat(repeatCount) { index ->
                // Recheck date muting and holiday policy before every repeated ring.
                val livePrefs = DataStoreUserPreferencesRepository(applicationContext).preferencesFlow.first()
                val suppressed = alarmDaySuppression(
                    System.currentTimeMillis(), alarm.allowOnHoliday, BeijingTime.zone,
                    livePrefs.reminderDayPolicy(), livePrefs.holidayCalendar, livePrefs.temporaryScheduleOverrides,
                )
                if (suppressed != null) {
                    HolidayAlarmNotifier.postSkipped(applicationContext, alarm.title, suppressed)
                    AlarmRingHistory.record(applicationContext, AlarmRingOutcome.Skipped, alarm.title)
                    finishRinging(alarm, reason = "holiday_or_muted", snooze = false)
                    return@launch
                }
                val round = index + 1
                ReminderLogger.info(
                    "reminder.app_alarm_clock.ringing.round.start",
                    mapOf("alarmKey" to alarm.alarmKey, "round" to round, "repeatCount" to repeatCount),
                )
                acquireWakeLock(durationMillis + WAKE_LOCK_EXTRA_MILLIS)
                if (alertMode != AlarmAlertMode.VibrateOnly) {
                    startTone(ringtoneUri)
                }
                if (alertMode != AlarmAlertMode.RingOnly) {
                    vibrate(durationMillis)
                }
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

    /** Discard unnecessary starts without stopping an active ringing instance. */
    private fun retireStart(startId: Int) {
        if (currentAlarm != null || ringJob?.isActive == true) return
        stopSelf(startId)
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
                val resultMessage = result.localizedMessage
                    ?.let { applicationContext.reminderMessageText(it) }
                    ?: result.message
                ReminderLogger.info(
                    "reminder.app_alarm_clock.ringing.finish_result",
                    mapOf(
                        "alarmKey" to alarm.alarmKey,
                        "ruleId" to alarm.ruleId,
                        "snooze" to snooze,
                        "snoozeCreated" to result.snoozeCreated,
                        "message" to resultMessage,
                    ),
                )
                // Report failed snooze scheduling so the user knows it will not ring again.
                if (snooze && !result.snoozeCreated) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            applicationContext,
                            getString(R.string.alarm_snooze_failed_reason, resultMessage),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }.onFailure { error ->
                ReminderLogger.warn(
                    "reminder.app_alarm_clock.ringing.finish.failure",
                    mapOf("alarmKey" to alarm.alarmKey, "ruleId" to alarm.ruleId, "snooze" to snooze),
                    error,
                )
                if (snooze) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            applicationContext,
                            getString(R.string.alarm_snooze_failed),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    }

    private fun ActiveAlarm.toSnoozePlan(): ReminderPlan {
        val triggerAtMillis = System.currentTimeMillis() + SNOOZE_DELAY_MILLIS
        return ReminderPlan(
            planId = "${planId.ifBlank { alarmKey }}_snooze_$triggerAtMillis",
            ruleId = ruleId,
            pluginId = pluginId.ifBlank { "snooze" },
            title = title.ifBlank { getString(R.string.alarm_default_title) },
            message = getString(R.string.alarm_snoozed),
            triggerAtMillis = triggerAtMillis,
            ringtoneUri = ringtoneUri,
            alertMode = alertMode,
            courseId = courseId,
            ringDurationSeconds = ringDurationSeconds,
            repeatIntervalSeconds = repeatIntervalSeconds,
            repeatCount = repeatCount,
            allowOnHoliday = allowOnHoliday,
        )
    }

    /**
     * Reject stale schedules for deleted or disabled rules. Preserve manual alarms and
     * uncertain lookups to avoid missing a valid ring.
     */
    private suspend fun isRecordStillValid(alarm: ActiveAlarm): Boolean {
        if (alarm.alarmKey.isBlank()) return true
        return withContext(Dispatchers.IO) {
            runCatching {
                val records = DataStoreReminderRepository(applicationContext).getSystemAlarmRecords()
                if (records.isEmpty()) return@runCatching true
                val record = records.firstOrNull { it.alarmKey == alarm.alarmKey }
                record == null || record.enabled
            }.getOrDefault(true)
        }
    }

    private suspend fun runPostFinishMaintenance() {
        withContext(Dispatchers.IO) {
            AlarmRuntimeMaintenance.onAlarmFinished(applicationContext)
        }
    }

    private fun createPlaceholderChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val channel = NotificationChannel(
                PLACEHOLDER_CHANNEL_ID,
                getString(R.string.alarm_service_channel_name),
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                setSound(null, null)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /** Silent placeholder satisfies the foreground deadline for non-ringing branches. */
    private fun startPlaceholderForeground() {
        runCatching {
            val notification = NotificationCompat.Builder(this, PLACEHOLDER_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.alarm_default_title))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { error ->
            ReminderLogger.warn("reminder.app_alarm_clock.ringing.placeholder_foreground.failure", emptyMap(), error)
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
            alarmActivityOptions(),
        )

        val liveStatusText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            "🔔 " + getString(R.string.alarm_ringing_notification)
        } else {
            null
        }

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(alarm.title.ifBlank { getString(R.string.alarm_default_title) })
            .setContentText(alarm.message.ifBlank { getString(R.string.alarm_default_message) })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(fullScreenIntent)
            .apply {
                if (canUseFullScreenIntentCompat()) {
                    setFullScreenIntent(fullScreenIntent, true)
                } else {
                    ReminderLogger.warn("reminder.app_alarm_clock.ringing.full_screen_denied", emptyMap())
                }
            }
            .addAction(0, getString(R.string.alarm_stop), stopIntent)
            .addAction(0, getString(R.string.alarm_snooze), snoozeIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            notificationBuilder.setRequestPromotedOngoing(true)
            liveStatusText?.let { notificationBuilder.setShortCriticalText(it) }
        }

        val notification = notificationBuilder.build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        ReminderLogger.info("reminder.app_alarm_clock.ringing.foreground.start", mapOf("alarmKey" to alarm.alarmKey))
    }

    private fun alarmActivityOptions(): Bundle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return null
        val backgroundStartMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
        } else {
            @Suppress("DEPRECATION")
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        }
        return ActivityOptions.makeBasic().apply {
            setPendingIntentCreatorBackgroundActivityStartMode(backgroundStartMode)
        }.toBundle()
    }

    private fun serviceIntent(actionName: String, alarm: ActiveAlarm): Intent =
        Intent(this, AlarmRingingService::class.java).apply {
            action = actionName
            putAlarmExtras(alarm)
        }

    private fun startTone(rawUri: String?) {
        runCatching {
            stopTone()
            val attributes = alarmAudioAttributes()
            val candidates = buildList {
                rawUri?.takeIf { it.isNotBlank() }?.let { add(Uri.parse(it)) }
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.let(::add)
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)?.let(::add)
            }.distinct()
            requestAudioFocus(attributes)
            for (uri in candidates) {
                val candidate = runCatching {
                    RingtoneManager.getRingtone(applicationContext, uri)
                }.getOrNull() ?: continue
                ringtone = candidate.apply {
                    audioAttributes = attributes
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        isLooping = true
                        volume = alarmRampVolume(0L)
                    }
                    play()
                }
                if (ringtone?.isPlaying == true) {
                    startVolumeRamp()
                    return
                }
            }
            ReminderLogger.warn("reminder.app_alarm_clock.ringing.tone.empty", emptyMap())
        }.onFailure { error ->
            ReminderLogger.warn("reminder.app_alarm_clock.ringing.tone.failure", emptyMap(), error)
        }
    }

    private fun startVolumeRamp() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        volumeRampJob?.cancel()
        val startedAt = System.currentTimeMillis()
        volumeRampJob = serviceScope.launch {
            while (true) {
                val elapsed = System.currentTimeMillis() - startedAt
                val target = alarmRampVolume(elapsed) * duckFactor
                runCatching { ringtone?.volume = target }
                if (elapsed >= ALARM_VOLUME_RAMP_MILLIS && duckFactor == 1f) break
                delay(VOLUME_RAMP_STEP_MILLIS)
            }
        }
    }

    private fun requestAudioFocus(attributes: AudioAttributes) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val manager = getSystemService(AudioManager::class.java) ?: return
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener { change ->
                    duckFactor = when (change) {
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                        -> DUCKED_VOLUME_FACTOR
                        else -> 1f
                    }
                    if (duckFactor != 1f) startVolumeRamp()
                }
                .build()
            audioFocusRequest = request
            manager.requestAudioFocus(request)
        }.onFailure { error ->
            ReminderLogger.warn("reminder.app_alarm_clock.ringing.audio_focus.failure", emptyMap(), error)
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val request = audioFocusRequest ?: return
        audioFocusRequest = null
        runCatching { getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(request) }
    }

    private fun alarmAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun stopTone() {
        volumeRampJob?.cancel()
        volumeRampJob = null
        duckFactor = 1f
        abandonAudioFocus()
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
            val pattern = longArrayOf(0L, 800L, 800L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(pattern, 0),
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0), alarmAudioAttributes())
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, 0, alarmAudioAttributes())
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

    /** Full-screen alarm display requires permitted full-screen intent access on API 34+. */
    private fun canUseFullScreenIntentCompat(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        return runCatching {
            getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        }.getOrDefault(true)
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

    /** Report completely missed alarms rather than silently dropping them. */
    private fun notifyMissedAlarm(alarm: ActiveAlarm) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    MISSED_CHANNEL_ID,
                    getString(R.string.alarm_missed_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = getString(R.string.alarm_missed_channel_description)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            }
            val time = java.time.Instant.ofEpochMilli(alarm.triggerAtMillis)
                .atZone(BeijingTime.zone)
                .toLocalTime()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
            val notification = NotificationCompat.Builder(this, MISSED_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.alarm_missed_title, time))
                .setContentText(alarm.title.ifBlank { alarm.message })
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                ReminderLogger.warn("reminder.app_alarm_clock.ringing.missed_notify.denied", emptyMap())
                return@runCatching
            }
            androidx.core.app.NotificationManagerCompat.from(this)
                .notify(MISSED_NOTIFICATION_ID + (alarm.alarmKey.hashCode() and 0xFF), notification)
        }.onFailure { error ->
            ReminderLogger.warn("reminder.app_alarm_clock.ringing.missed_notify.failure", emptyMap(), error)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.alarm_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = getString(R.string.alarm_channel_description)
                enableVibration(true)
                // The service owns audio playback; keep the channel silent to avoid duplicate sound.
                setSound(null, null)
                // Allow the alarm notification through DND so full-screen presentation remains available.
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }.onFailure { error ->
            ReminderLogger.warn("reminder.app_alarm_clock.ringing.channel.failure", emptyMap(), error)
        }
    }

    companion object {
        const val ACTION_RING = AppAlarmClockIntents.ACTION_RING
        const val ACTION_STOP = "com.x500x.cursimple.action.ALARM_STOP"
        const val ACTION_SNOOZE = "com.x500x.cursimple.action.ALARM_SNOOZE"
        internal const val CHANNEL_ID = "course_alarm_ringing"
        // Start foreground promptly on every branch; use the silent placeholder when no ring starts.
        private const val PLACEHOLDER_CHANNEL_ID = "course_alarm_service"
        private const val NOTIFICATION_ID = 7401
        private const val STOP_REQUEST_CODE = 7402
        private const val SNOOZE_REQUEST_CODE = 7403
        private const val FULL_SCREEN_REQUEST_CODE = 7404
        private const val WAKE_LOCK_EXTRA_MILLIS = 10_000L

        private const val STARTUP_WAKE_LOCK_MILLIS = 60_000L
        private const val VOLUME_RAMP_STEP_MILLIS = 500L
        private const val DUCKED_VOLUME_FACTOR = 0.2f
        private const val MISSED_CHANNEL_ID = "course_alarm_missed"
        private const val MISSED_NOTIFICATION_ID = 7500
        const val EXTRA_GENERATION = "com.x500x.cursimple.extra.ALARM_GENERATION"
        private const val SNOOZE_DELAY_MILLIS = 5 * 60 * 1000L
    }
}
