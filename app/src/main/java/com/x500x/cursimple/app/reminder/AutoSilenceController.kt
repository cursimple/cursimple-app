package com.x500x.cursimple.app.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.x500x.cursimple.R
import com.x500x.cursimple.app.ClassScheduleApplication
import com.x500x.cursimple.core.data.AutoSilenceMode
import com.x500x.cursimple.core.data.AutoSilenceSession
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.InterruptionFilterValues
import com.x500x.cursimple.core.data.RingerModeValues
import com.x500x.cursimple.core.data.UserPreferences
import com.x500x.cursimple.core.data.UserPreferencesRepository
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.termStartLocalDate
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 自动静音能否生效的体检结果。 */
data class AutoSilenceReadiness(
    val mode: AutoSilenceMode,
    val notificationPolicyGranted: Boolean,
    val doNotDisturbAllowsAlarms: Boolean,
    val alarmVolumeAudible: Boolean,
) {
    /** 缺少必需授权时为 false，此时不允许开启，也不会尝试切换手机状态。 */
    val permissionSatisfied: Boolean
        get() = when (mode) {
            AutoSilenceMode.Vibrate -> true
            AutoSilenceMode.Silent, AutoSilenceMode.DoNotDisturb -> notificationPolicyGranted
        }

    /** 阻止开启的原因的文案资源，为 null 表示可以开启。 */
    val blockingReasonRes: Int?
        get() = when {
            !permissionSatisfied -> R.string.autosilence_block_no_dnd_permission
            mode == AutoSilenceMode.DoNotDisturb && !doNotDisturbAllowsAlarms ->
                R.string.autosilence_block_dnd_mutes_alarms

            else -> null
        }

    /** 不阻止开启，但需要提示用户的问题。 */
    val warningRes: Int?
        get() = if (blockingReasonRes == null && !alarmVolumeAudible) {
            R.string.autosilence_warn_alarm_volume_zero
        } else {
            null
        }
}

/**
 * 上课时段自动静音的执行入口。
 *
 * 状态切换只走两条路：铃声模式（AudioManager）与勿扰级别（NotificationManager），
 * 两者都不影响闹钟音频流，勿扰也只会写入「仅优先级」，绝不会写入完全静音。
 */
object AutoSilenceController {

    const val ACTION_BOUNDARY = "com.x500x.cursimple.action.AUTO_SILENCE_BOUNDARY"
    const val ACTION_RESTORE_NOW = "com.x500x.cursimple.action.AUTO_SILENCE_RESTORE_NOW"

    /**
     * 重新判断当前该不该静音，并把状态推到应该在的位置。
     *
     * 所有触发点（开关、边界闹钟、开机、时间变更、巡检 Worker、闹钟响铃前后）都汇聚到这里，
     * 保证任何一条路径单独失效时其它路径仍能纠正状态。
     */
    suspend fun evaluate(context: Context, reason: String) {
        val appContext = context.applicationContext
        val repository = repositoryOf(appContext)
        val prefs = runCatching { repository.preferencesFlow.first() }.getOrElse { error ->
            ReminderLogger.warn("reminder.auto_silence.prefs.failure", mapOf("reason" to reason), error)
            return
        }
        val nowMillis = System.currentTimeMillis()
        val session = prefs.autoSilenceSession
        val enabled = prefs.autoSilence.enabled

        if (isAutoSilenceSessionExpired(session, nowMillis)) {
            ReminderLogger.warn(
                "reminder.auto_silence.session.expired",
                mapOf(
                    "reason" to reason,
                    "startedAtMillis" to session.startedAtMillis,
                    "plannedEndAtMillis" to session.plannedEndAtMillis,
                ),
            )
            performRestore(appContext, repository, session, reason = "expired", suppressUntilMillis = 0L)
            scheduleFollowUp(appContext, enabled = enabled, sessionActive = false, blocks = emptyList())
            return
        }

        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        val blocks = loadClassBlocks(appContext, now.toLocalDate())
        if (blocks == null) {
            if (session.active) {
                ReminderLogger.warn("reminder.auto_silence.schedule.unavailable", mapOf("reason" to reason))
                performRestore(appContext, repository, session, reason = "schedule_unavailable", suppressUntilMillis = 0L)
            }
            scheduleFollowUp(appContext, enabled = enabled, sessionActive = false, blocks = emptyList())
            return
        }

        when (val decision = decideAutoSilence(now, nowMillis, blocks, session, enabled)) {
            is AutoSilenceDecision.Enter -> applySilence(
                context = appContext,
                repository = repository,
                prefs = prefs,
                block = decision.block,
                nowMillis = nowMillis,
                zone = zone,
                reason = reason,
            )

            AutoSilenceDecision.Keep -> showActiveNotification(appContext, session.mode, session.plannedEndAtMillis, zone)
            AutoSilenceDecision.Restore -> performRestore(
                context = appContext,
                repository = repository,
                session = session,
                reason = reason,
                suppressUntilMillis = 0L,
            )

            AutoSilenceDecision.Idle -> cancelStatusNotification(appContext)
        }

        val sessionStillActive = readSessionActive(repository)
        scheduleFollowUp(appContext, enabled = enabled, sessionActive = sessionStillActive, blocks = blocks)
    }

    /**
     * 立刻恢复用户原来的状态。
     *
     * [suppressUntilBlockEnd] 为真时，本节课结束之前不再自动静音，用于用户手动点「立即恢复」。
     */
    suspend fun restoreNow(context: Context, reason: String, suppressUntilBlockEnd: Boolean) {
        val appContext = context.applicationContext
        val repository = repositoryOf(appContext)
        val prefs = runCatching { repository.preferencesFlow.first() }.getOrElse { error ->
            ReminderLogger.warn("reminder.auto_silence.prefs.failure", mapOf("reason" to reason), error)
            return
        }
        val session = prefs.autoSilenceSession
        val suppressUntilMillis = if (suppressUntilBlockEnd) session.plannedEndAtMillis else 0L
        performRestore(appContext, repository, session, reason, suppressUntilMillis)
        scheduleFollowUp(appContext, enabled = prefs.autoSilence.enabled, sessionActive = false, blocks = emptyList())
    }

    /** 读取当前授权与系统设置，判断 [mode] 能否安全启用。 */
    fun readiness(context: Context, mode: AutoSilenceMode): AutoSilenceReadiness {
        val appContext = context.applicationContext
        val notificationManager = appContext.getSystemService(NotificationManager::class.java)
        val policyGranted = runCatching {
            notificationManager?.isNotificationPolicyAccessGranted == true
        }.getOrDefault(false)
        return AutoSilenceReadiness(
            mode = mode,
            notificationPolicyGranted = policyGranted,
            doNotDisturbAllowsAlarms = doNotDisturbAllowsAlarms(notificationManager, policyGranted),
            alarmVolumeAudible = alarmVolumeAudible(appContext),
        )
    }

    /** 跳转到系统的勿扰访问授权列表，需要用户在列表里找到本应用手动打开。 */
    fun notificationPolicySettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    private fun repositoryOf(appContext: Context): UserPreferencesRepository =
        (appContext as? ClassScheduleApplication)?.appContainer?.userPreferencesRepository
            ?: DataStoreUserPreferencesRepository(appContext)

    private suspend fun readSessionActive(repository: UserPreferencesRepository): Boolean =
        runCatching { repository.preferencesFlow.first().autoSilenceSession.active }.getOrDefault(false)

    /**
     * 读取今天和明天的上课时间段。
     *
     * 返回 null 表示课表数据暂时读不出来，调用方按最保守的方式处理。
     */
    private suspend fun loadClassBlocks(appContext: Context, today: LocalDate): List<ClassBlock>? {
        val app = appContext as? ClassScheduleApplication ?: return null
        return runCatching {
            val container = app.appContainer
            container.bootstrapJob.join()
            val timingProfile = container.widgetPreferencesRepository.timingProfileFlow.first()
            // 没有节次时间表或没有开学日期都排不出上课时段，视为今明两天没有课
            val termStart = timingProfile?.termStartLocalDate()
            if (timingProfile == null || termStart == null) {
                emptyList()
            } else {
                val prefs = container.userPreferencesRepository.preferencesFlow.first()
                val scheduleCourses: List<CourseItem> = container.scheduleRepository.scheduleFlow.first()
                    ?.dailySchedules
                    ?.flatMap { it.courses }
                    .orEmpty()
                val courses = scheduleCourses + container.manualCourseRepository.manualCoursesFlow.first()
                (0L..1L).flatMap { offset ->
                    resolveClassBlocks(
                        date = today.plusDays(offset),
                        courses = courses,
                        timingProfile = timingProfile,
                        termStart = termStart,
                        overrides = prefs.temporaryScheduleOverrides,
                        holidayCalendar = prefs.holidayCalendar,
                    )
                }
            }
        }.getOrElse { error ->
            ReminderLogger.warn("reminder.auto_silence.schedule.failure", emptyMap(), error)
            null
        }
    }

    private suspend fun applySilence(
        context: Context,
        repository: UserPreferencesRepository,
        prefs: UserPreferences,
        block: ClassBlock,
        nowMillis: Long,
        zone: ZoneId,
        reason: String,
    ) {
        val mode = prefs.autoSilence.mode
        val blockingReasonRes = readiness(context, mode).blockingReasonRes
        if (blockingReasonRes != null) {
            val blockingReason = context.getString(blockingReasonRes)
            ReminderLogger.warn(
                "reminder.auto_silence.apply.blocked",
                mapOf("mode" to mode.name, "reason" to reason, "blocking" to blockingReason),
            )
            showProblemNotification(
                context,
                context.getString(R.string.autosilence_not_applied),
                blockingReason,
            )
            return
        }

        val audioManager = context.getSystemService(AudioManager::class.java)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val currentRingerMode = audioManager?.ringerMode ?: RingerModeValues.UNKNOWN
        val currentFilter = currentInterruptionFilter(notificationManager)
        val targetRingerMode = resolveRingerModeToApply(mode, currentRingerMode)
        val targetFilter = resolveInterruptionFilterToApply(mode, currentFilter)
        if (targetRingerMode == null && targetFilter == null) {
            ReminderLogger.info(
                "reminder.auto_silence.apply.already_quiet",
                mapOf("mode" to mode.name, "ringerMode" to currentRingerMode, "filter" to currentFilter),
            )
            return
        }

        val applied = runCatching {
            targetRingerMode?.let { audioManager?.ringerMode = it }
            targetFilter?.let { notificationManager?.setInterruptionFilter(it) }
        }
        if (applied.isFailure) {
            ReminderLogger.warn(
                "reminder.auto_silence.apply.failure",
                mapOf("mode" to mode.name, "reason" to reason),
                applied.exceptionOrNull(),
            )
            showProblemNotification(
                context,
                context.getString(R.string.autosilence_not_applied),
                context.getString(R.string.autosilence_switch_rejected),
            )
            return
        }

        val plannedEndAtMillis = block.end.atZone(zone).toInstant().toEpochMilli()
        repository.saveAutoSilenceSession(
            AutoSilenceSession(
                active = true,
                mode = mode,
                previousRingerMode = if (targetRingerMode != null) currentRingerMode else RingerModeValues.UNKNOWN,
                previousInterruptionFilter = if (targetFilter != null) currentFilter else InterruptionFilterValues.UNKNOWN,
                appliedRingerMode = targetRingerMode ?: RingerModeValues.UNKNOWN,
                appliedInterruptionFilter = targetFilter ?: InterruptionFilterValues.UNKNOWN,
                startedAtMillis = nowMillis,
                plannedEndAtMillis = plannedEndAtMillis,
                suppressedUntilMillis = 0L,
            ),
        )
        ReminderLogger.info(
            "reminder.auto_silence.apply",
            mapOf(
                "mode" to mode.name,
                "reason" to reason,
                "previousRingerMode" to currentRingerMode,
                "previousFilter" to currentFilter,
                "plannedEndAtMillis" to plannedEndAtMillis,
            ),
        )
        showActiveNotification(context, mode, plannedEndAtMillis, zone)
    }

    private suspend fun performRestore(
        context: Context,
        repository: UserPreferencesRepository,
        session: AutoSilenceSession,
        reason: String,
        suppressUntilMillis: Long,
    ) {
        if (!session.active) {
            cancelStatusNotification(context)
            if (suppressUntilMillis > 0L) {
                repository.clearAutoSilenceSession(suppressUntilMillis)
            }
            return
        }
        val audioManager = context.getSystemService(AudioManager::class.java)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val currentRingerMode = audioManager?.ringerMode ?: RingerModeValues.UNKNOWN
        val currentFilter = currentInterruptionFilter(notificationManager)
        val ringerModeToRestore = resolveRingerModeToRestore(session, currentRingerMode)
        val filterToRestore = resolveInterruptionFilterToRestore(session, currentFilter)

        val restored = runCatching {
            filterToRestore?.let { notificationManager?.setInterruptionFilter(it) }
            ringerModeToRestore?.let { audioManager?.ringerMode = it }
        }
        if (restored.isFailure) {
            ReminderLogger.warn(
                "reminder.auto_silence.restore.failure",
                mapOf("reason" to reason, "mode" to session.mode.name),
                restored.exceptionOrNull(),
            )
            showProblemNotification(
                context,
                context.getString(R.string.autosilence_not_restored),
                context.getString(R.string.autosilence_restore_rejected),
            )
            return
        }

        repository.clearAutoSilenceSession(suppressUntilMillis)
        cancelStatusNotification(context)
        ReminderLogger.info(
            "reminder.auto_silence.restore",
            mapOf(
                "reason" to reason,
                "mode" to session.mode.name,
                "ringerModeRestored" to (ringerModeToRestore ?: RingerModeValues.UNKNOWN),
                "filterRestored" to (filterToRestore ?: InterruptionFilterValues.UNKNOWN),
                "suppressedUntilMillis" to suppressUntilMillis,
            ),
        )
    }

    private fun scheduleFollowUp(
        context: Context,
        enabled: Boolean,
        sessionActive: Boolean,
        blocks: List<ClassBlock>,
    ) {
        if (enabled || sessionActive) {
            AlarmSyncScheduler.scheduleAutoSilenceGuard(context)
        } else {
            AlarmSyncScheduler.cancelAutoSilenceGuard(context)
        }
        if (!enabled && !sessionActive) {
            cancelBoundaryAlarm(context)
            return
        }
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        val boundary = nextClassBoundaryAfter(now, blocks)
        if (boundary == null) {
            cancelBoundaryAlarm(context)
            return
        }
        scheduleBoundaryAlarm(context, boundary.atZone(zone).toInstant().toEpochMilli())
    }

    private fun scheduleBoundaryAlarm(context: Context, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = boundaryPendingIntent(context)
        runCatching {
            val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
            if (exactAllowed) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
            ReminderLogger.info(
                "reminder.auto_silence.boundary.schedule",
                mapOf("triggerAtMillis" to triggerAtMillis, "exact" to exactAllowed),
            )
        }.onFailure { error ->
            ReminderLogger.warn(
                "reminder.auto_silence.boundary.schedule.failure",
                mapOf("triggerAtMillis" to triggerAtMillis),
                error,
            )
        }
    }

    private fun cancelBoundaryAlarm(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { alarmManager.cancel(boundaryPendingIntent(context)) }
    }

    private fun boundaryPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_BOUNDARY,
        Intent(context, AutoSilenceReceiver::class.java).apply { action = ACTION_BOUNDARY },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun currentInterruptionFilter(notificationManager: NotificationManager?): Int =
        runCatching { notificationManager?.currentInterruptionFilter }.getOrNull()
            ?: InterruptionFilterValues.UNKNOWN

    private fun doNotDisturbAllowsAlarms(notificationManager: NotificationManager?, policyGranted: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return true
        if (!policyGranted) return false
        val categories = runCatching { notificationManager?.notificationPolicy?.priorityCategories }.getOrNull()
            ?: return false
        return (categories and NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS) != 0
    }

    private fun alarmVolumeAudible(context: Context): Boolean {
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return true
        return runCatching { audioManager.getStreamVolume(AudioManager.STREAM_ALARM) > 0 }.getOrDefault(true)
    }

    private fun showActiveNotification(context: Context, mode: AutoSilenceMode, endAtMillis: Long, zone: ZoneId) {
        val endText = if (endAtMillis > 0L) {
            TIME_FORMATTER.format(Instant.ofEpochMilli(endAtMillis).atZone(zone).toLocalTime())
        } else {
            null
        }
        val text = buildString {
            append(context.getString(modeLabelRes(mode)))
            if (endText != null) {
                append(" · ")
                append(endText)
                append(context.getString(R.string.autosilence_restore_suffix))
            }
        }
        postNotification(
            context = context,
            channelId = CHANNEL_STATUS,
            channelName = context.getString(R.string.autosilence_channel_status),
            notificationId = NOTIFICATION_STATUS,
            title = context.getString(R.string.autosilence_active_title),
            text = text,
            ongoing = true,
            withRestoreAction = true,
        )
    }

    private fun showProblemNotification(context: Context, title: String, text: String) {
        postNotification(
            context = context,
            channelId = CHANNEL_ALERT,
            channelName = context.getString(R.string.autosilence_channel_problem),
            notificationId = NOTIFICATION_ALERT,
            title = title,
            text = text,
            ongoing = false,
            withRestoreAction = false,
        )
    }

    private fun postNotification(
        context: Context,
        channelId: String,
        channelName: String,
        notificationId: Int,
        title: String,
        text: String,
        ongoing: Boolean,
        withRestoreAction: Boolean,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        runCatching {
            ensureChannel(context, channelId, channelName)
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setOngoing(ongoing)
            if (withRestoreAction) {
                builder.addAction(0, context.getString(R.string.autosilence_restore_now), restoreNowPendingIntent(context))
            }
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        }.onFailure { error ->
            ReminderLogger.warn("reminder.auto_silence.notification.failure", mapOf("title" to title), error)
        }
    }

    private fun restoreNowPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_RESTORE_NOW,
        Intent(context, AutoSilenceReceiver::class.java).apply { action = ACTION_RESTORE_NOW },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun cancelStatusNotification(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_STATUS) }
    }

    private fun ensureChannel(context: Context, channelId: String, channelName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(channelId) != null) return
        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.autosilence_channel_description)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun modeLabelRes(mode: AutoSilenceMode): Int = when (mode) {
        AutoSilenceMode.Vibrate -> R.string.autosilence_mode_vibrate
        AutoSilenceMode.Silent -> R.string.autosilence_mode_silent
        AutoSilenceMode.DoNotDisturb -> R.string.autosilence_mode_dnd
    }

    private const val CHANNEL_STATUS = "auto_silence_status"
    private const val CHANNEL_ALERT = "auto_silence_alert"
    private const val NOTIFICATION_STATUS = 7411
    private const val NOTIFICATION_ALERT = 7412
    private const val REQUEST_BOUNDARY = 74_230
    private const val REQUEST_RESTORE_NOW = 74_231
    private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
}
