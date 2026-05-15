package com.x500x.cursimple.app

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import com.x500x.cursimple.BuildConfig
import com.x500x.cursimple.app.download.DownloadPurpose
import com.x500x.cursimple.app.download.DownloadRequest
import com.x500x.cursimple.app.download.MirrorDownloadResult
import com.x500x.cursimple.app.download.MirrorDownloader
import com.x500x.cursimple.core.data.DataStoreManualCourseRepository
import com.x500x.cursimple.core.data.DataStoreScheduleRepository
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.ManualCourseRepository
import com.x500x.cursimple.core.data.ScheduleRepository
import com.x500x.cursimple.core.data.UserPreferencesRepository
import com.x500x.cursimple.core.data.UserPreferences
import com.x500x.cursimple.core.data.plugin.DataStorePluginComponentRepository
import com.x500x.cursimple.core.data.plugin.DataStorePluginRegistryRepository
import com.x500x.cursimple.core.data.reminder.DataStoreReminderRepository
import com.x500x.cursimple.core.data.term.DataStoreTermProfileRepository
import com.x500x.cursimple.core.data.term.TermProfileRepository
import com.x500x.cursimple.core.data.widget.DataStoreWidgetPreferencesRepository
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.DailySchedule
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.termStartLocalDate
import com.x500x.cursimple.core.plugin.PluginManager
import com.x500x.cursimple.core.plugin.component.PluginComponentInstaller
import com.x500x.cursimple.core.plugin.market.MarketIndexRepository
import com.x500x.cursimple.core.reminder.ReminderCoordinator
import com.x500x.cursimple.core.reminder.ReminderSyncWindows
import com.x500x.cursimple.core.reminder.model.ReminderAlarmSettings
import com.x500x.cursimple.core.reminder.model.ReminderSyncReason
import com.x500x.cursimple.core.reminder.model.SystemAlarmSyncSummary
import com.x500x.cursimple.app.reminder.SystemAlarmCheckScheduler
import com.x500x.cursimple.feature.widget.ScheduleWidgetUpdater
import com.x500x.cursimple.feature.widget.ScheduleWidgetWorkScheduler
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class AppContainer(
    private val app: Application,
) {
    private val containerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val termProfileRepository: TermProfileRepository = DataStoreTermProfileRepository(app)
    private val scheduleStore = DataStoreScheduleRepository(app, termProfileRepository)
    val scheduleRepository: ScheduleRepository = scheduleStore
    val pluginRegistryRepository = DataStorePluginRegistryRepository(app)
    val reminderRepository = DataStoreReminderRepository(app)
    val widgetPreferencesRepository = DataStoreWidgetPreferencesRepository(app)
    val userPreferencesRepository: UserPreferencesRepository = DataStoreUserPreferencesRepository(app)
    private val manualStore = DataStoreManualCourseRepository(app, termProfileRepository)
    val manualCourseRepository: ManualCourseRepository = manualStore
    private val sharedDownloader = MirrorDownloader(
        userAgent = "CurSimple/${BuildConfig.VERSION_NAME}",
    )
    private val marketIndexRepository = MarketIndexRepository(
        fetchText = { url -> downloadTextViaMirrors(url) },
        downloadBytes = { url -> downloadBytesViaMirrors(url) },
    )
    val pluginComponentRepository = DataStorePluginComponentRepository(app)
    val pluginComponentInstaller = PluginComponentInstaller(
        componentRoot = File(app.filesDir, "plugin-components-v1"),
        repository = pluginComponentRepository,
    )
    val pluginManager = PluginManager(
        context = app,
        registryRepository = pluginRegistryRepository,
        componentRepository = pluginComponentRepository,
        marketIndexRepository = marketIndexRepository,
    )

    private val temporaryScheduleOverridesState = userPreferencesRepository.preferencesFlow
        .map { it.temporaryScheduleOverrides }
        .stateIn(containerScope, SharingStarted.Eagerly, emptyList())
    private val alarmSettingsState = userPreferencesRepository.preferencesFlow
        .map { it.toReminderAlarmSettings() }
        .stateIn(
            containerScope,
            SharingStarted.Eagerly,
            UserPreferences().toReminderAlarmSettings(),
        )

    val reminderCoordinator = ReminderCoordinator(
        context = app,
        repository = reminderRepository,
        temporaryScheduleOverridesProvider = { temporaryScheduleOverridesState.value },
        alarmSettingsProvider = { alarmSettingsState.value },
    )

    val bootstrapJob: Job = containerScope.launch {
        // Bootstrap term list: if empty, seed from any existing legacy termStartDate
        // so users keep their schedule after the upgrade.
        val legacyTermStart = userPreferencesRepository.preferencesFlow.first()
            .termStartDate?.toString()
        val activeTermId = termProfileRepository.ensureBootstrapped(
            defaultName = "默认学期",
            legacyTermStartDateIso = legacyTermStart,
        )
        scheduleStore.migrateLegacyScheduleIfNeeded(activeTermId)
        manualStore.migrateLegacyManualIfNeeded(activeTermId)
    }

    private suspend fun awaitBootstrap() = bootstrapJob.join()

    suspend fun downloadPluginComponentPackage(url: String): ByteArray {
        return downloadBytesViaMirrors(url)
    }

    private suspend fun downloadTextViaMirrors(url: String): String {
        return when (val result = sharedDownloader.downloadText(
            request = downloadRequestFor(url),
            accept = "application/json",
        )) {
            is MirrorDownloadResult.Success -> result.value
            is MirrorDownloadResult.Failure -> throw IllegalStateException(result.message)
        }
    }

    private suspend fun downloadBytesViaMirrors(url: String): ByteArray {
        return when (val result = sharedDownloader.downloadBytes(
            request = downloadRequestFor(url),
        )) {
            is MirrorDownloadResult.Success -> result.value
            is MirrorDownloadResult.Failure -> throw IllegalStateException(result.message)
        }
    }

    private fun downloadRequestFor(url: String): DownloadRequest {
        return DownloadRequest(
            purpose = inferDownloadPurpose(url),
            url = url,
        )
    }

    private fun inferDownloadPurpose(url: String): DownloadPurpose {
        if (url.startsWith("file:", ignoreCase = true)) return DownloadPurpose.LocalFile
        val uri = runCatching { URI(url) }.getOrNull() ?: return DownloadPurpose.DirectUrl
        val host = uri.host.orEmpty()
        val path = uri.path.orEmpty()
        return when {
            host.equals("raw.githubusercontent.com", ignoreCase = true) -> DownloadPurpose.GithubRaw
            host.equals("github.com", ignoreCase = true) &&
                path.contains("/releases/download/", ignoreCase = true) -> DownloadPurpose.GithubRelease
            else -> DownloadPurpose.DirectUrl
        }
    }

    suspend fun refreshWidgets(timingProfile: TermTimingProfile? = null) {
        awaitBootstrap()
        if (timingProfile != null) {
            widgetPreferencesRepository.saveTimingProfile(timingProfile)
        }
        ScheduleWidgetUpdater.refreshAll(app)
        scheduleSystemAlarmChecks(timingProfile)
    }

    suspend fun refreshScheduleOutputs() {
        awaitBootstrap()
        refreshWidgets()
        val schedule = reminderSchedule() ?: return
        val timingProfile = widgetPreferencesRepository.timingProfileFlow.first() ?: return
        val pluginId = scheduleRepository.lastPluginIdFlow.first()
        reminderCoordinator.syncAlarmsForWindow(
            pluginId = pluginId,
            schedule = schedule,
            timingProfile = timingProfile,
            window = ReminderSyncWindows.todayFromNow(timingProfile),
            reason = ReminderSyncReason.ScheduleChanged,
        )
        userPreferencesRepository.markAlarmPollAt(System.currentTimeMillis())
        scheduleSystemAlarmChecks(timingProfile)
    }

    suspend fun runSystemAlarmCheck(reason: ReminderSyncReason) {
        awaitBootstrap()
        val schedule = reminderSchedule()
        val timingProfile = widgetPreferencesRepository.timingProfileFlow.first()
        val pluginId = scheduleRepository.lastPluginIdFlow.first()
        if (schedule != null && timingProfile != null) {
            userPreferencesRepository.markAlarmPollAt(System.currentTimeMillis())
            val window = when (reason) {
                ReminderSyncReason.DailyNextDay -> ReminderSyncWindows.nextDay(timingProfile)
            ReminderSyncReason.AfterClassToday,
            ReminderSyncReason.RuleCreatedToday,
            ReminderSyncReason.ScheduleChanged,
            ReminderSyncReason.WidgetRefresh,
            ReminderSyncReason.AlarmRuntime -> ReminderSyncWindows.todayFromNow(timingProfile)
            }
            reminderCoordinator.syncAlarmsForWindow(
                pluginId = pluginId,
                schedule = schedule,
                timingProfile = timingProfile,
                window = window,
                reason = reason,
            )
        }
        scheduleSystemAlarmChecks(timingProfile)
    }

    suspend fun tryRunSharedAlarmPoll(
        reason: ReminderSyncReason = ReminderSyncReason.WidgetRefresh,
        nowMillis: Long = System.currentTimeMillis(),
    ): SystemAlarmSyncSummary {
        awaitBootstrap()
        val claimed = userPreferencesRepository.tryClaimAlarmPoll(
            nowMillis = nowMillis,
            minIntervalMillis = SHARED_ALARM_POLL_INTERVAL_MILLIS,
        )
        if (!claimed) return emptySystemAlarmSyncSummary()
        val schedule = reminderSchedule()
        val timingProfile = widgetPreferencesRepository.timingProfileFlow.first()
        val pluginId = scheduleRepository.lastPluginIdFlow.first()
        val summary = if (schedule != null && timingProfile != null) {
            reminderCoordinator.syncAlarmsForWindow(
                pluginId = pluginId,
                schedule = schedule,
                timingProfile = timingProfile,
                window = ReminderSyncWindows.todayFromNow(timingProfile, nowMillis),
                reason = reason,
                nowMillis = nowMillis,
            )
        } else {
            emptySystemAlarmSyncSummary()
        }
        scheduleSystemAlarmChecks(timingProfile)
        return summary
    }

    suspend fun runSharedAlarmIntegrityCheck(
        reason: ReminderSyncReason,
        includeTomorrow: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
        clearExpiredRecords: Boolean = true,
    ): List<SystemAlarmSyncSummary> {
        awaitBootstrap()
        val claimed = userPreferencesRepository.tryClaimAlarmPoll(
            nowMillis = nowMillis,
            minIntervalMillis = SHARED_ALARM_POLL_INTERVAL_MILLIS,
        )
        if (!claimed) return emptyList()
        val schedule = reminderSchedule()
        val timingProfile = widgetPreferencesRepository.timingProfileFlow.first()
        val pluginId = scheduleRepository.lastPluginIdFlow.first()
        if (schedule == null || timingProfile == null) {
            scheduleSystemAlarmChecks(timingProfile)
            return emptyList()
        }
        val summaries = mutableListOf<SystemAlarmSyncSummary>()
        summaries += reminderCoordinator.syncAlarmsForWindow(
            pluginId = pluginId,
            schedule = schedule,
            timingProfile = timingProfile,
            window = ReminderSyncWindows.todayFromNow(timingProfile, nowMillis),
            reason = reason,
            nowMillis = nowMillis,
            clearExpiredRecords = clearExpiredRecords,
        )
        if (includeTomorrow) {
            summaries += reminderCoordinator.syncAlarmsForWindow(
                pluginId = pluginId,
                schedule = schedule,
                timingProfile = timingProfile,
                window = ReminderSyncWindows.nextDay(timingProfile, nowMillis),
                reason = ReminderSyncReason.DailyNextDay,
                nowMillis = nowMillis,
                clearExpiredRecords = clearExpiredRecords,
            )
        }
        scheduleSystemAlarmChecks(timingProfile)
        return summaries
    }

    suspend fun scheduleSystemAlarmChecks(timingProfile: TermTimingProfile? = null) {
        awaitBootstrap()
        val profile = timingProfile ?: widgetPreferencesRepository.timingProfileFlow.first() ?: return
        SystemAlarmCheckScheduler.scheduleDailyNextDayCheck(app, profile)
        SystemAlarmCheckScheduler.scheduleNextAfterClassCheck(app, profile)
    }

    suspend fun ensureAlarmRuntimeHealth() {
        awaitBootstrap()
        val timingProfile = widgetPreferencesRepository.timingProfileFlow.first()
        scheduleSystemAlarmChecks(timingProfile)
        ScheduleWidgetWorkScheduler.schedule(app)
        logAlarmRuntimeHealth()
    }

    suspend fun runAlarmFollowUpSync(
        nowMillis: Long = System.currentTimeMillis(),
        clearExpiredRecords: Boolean = true,
    ): List<SystemAlarmSyncSummary> {
        awaitBootstrap()
        val schedule = reminderSchedule()
        val timingProfile = widgetPreferencesRepository.timingProfileFlow.first()
        val pluginId = scheduleRepository.lastPluginIdFlow.first()
        if (schedule == null || timingProfile == null) {
            scheduleSystemAlarmChecks(timingProfile)
            return emptyList()
        }
        userPreferencesRepository.markAlarmPollAt(nowMillis)
        val summaries = mutableListOf<SystemAlarmSyncSummary>()
        summaries += reminderCoordinator.syncAlarmsForWindow(
            pluginId = pluginId,
            schedule = schedule,
            timingProfile = timingProfile,
            window = ReminderSyncWindows.todayFromNow(timingProfile, nowMillis),
            reason = ReminderSyncReason.AfterClassToday,
            nowMillis = nowMillis,
            clearExpiredRecords = clearExpiredRecords,
        )
        if (shouldSyncNextDayAfterAlarm(timingProfile, nowMillis)) {
            summaries += reminderCoordinator.syncAlarmsForWindow(
                pluginId = pluginId,
                schedule = schedule,
                timingProfile = timingProfile,
                window = ReminderSyncWindows.nextDay(timingProfile, nowMillis),
                reason = ReminderSyncReason.DailyNextDay,
                nowMillis = nowMillis,
                clearExpiredRecords = clearExpiredRecords,
            )
        }
        scheduleSystemAlarmChecks(timingProfile)
        return summaries
    }

    suspend fun normalizeTimingProfileForActiveTerm(timingProfile: TermTimingProfile?): TermTimingProfile? {
        if (timingProfile == null) {
            return null
        }
        awaitBootstrap()
        val activeTermId = termProfileRepository.activeTermId()
        val activeTerm = termProfileRepository.termsFlow.first()
            .firstOrNull { it.id == activeTermId }
        val activeTermStart = activeTerm?.termStartDate?.let(::parseIsoDate)
        val pluginTermStart = runCatching { timingProfile.termStartLocalDate() }.getOrNull()
        val canonicalTermStart = activeTermStart ?: pluginTermStart

        if (canonicalTermStart != null) {
            if (activeTermId.isNotBlank() && activeTermStart != canonicalTermStart) {
                termProfileRepository.setTermStartDate(activeTermId, canonicalTermStart.toString())
            }
            val prefsTermStart = userPreferencesRepository.preferencesFlow.first().termStartDate
            if (prefsTermStart != canonicalTermStart) {
                userPreferencesRepository.setTermStartDate(canonicalTermStart)
            }
            val canonicalIso = canonicalTermStart.toString()
            return if (timingProfile.termStartDate == canonicalIso) {
                timingProfile
            } else {
                timingProfile.copy(termStartDate = canonicalIso)
            }
        }

        return timingProfile
    }

    private companion object {
        const val SHARED_ALARM_POLL_INTERVAL_MILLIS = 40L * 60L * 1000L
        val NEXT_DAY_SYNC_START_TIME: LocalTime = LocalTime.of(22, 0)
        const val ALARM_RINGING_CHANNEL_ID = "course_alarm_ringing"

        fun parseIsoDate(value: String): LocalDate? =
            runCatching { LocalDate.parse(value) }.getOrNull()
    }

    private fun shouldSyncNextDayAfterAlarm(_timingProfile: TermTimingProfile, nowMillis: Long): Boolean {
        val zone = ZoneId.systemDefault()
        val localTime = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalTime()
        return !localTime.isBefore(NEXT_DAY_SYNC_START_TIME)
    }

    private fun logAlarmRuntimeHealth() {
        val alarmManager = app.getSystemService(AlarmManager::class.java)
        val notificationManager = app.getSystemService(NotificationManager::class.java)
        val powerManager = app.getSystemService(PowerManager::class.java)
        val exactAlarmEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)
        val notificationsEnabled = NotificationManagerCompat.from(app).areNotificationsEnabled()
        val fullScreenIntentEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            runCatching { notificationManager.canUseFullScreenIntent() }.getOrDefault(false)
        val channelImportance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.getNotificationChannel(ALARM_RINGING_CHANNEL_ID)?.importance ?: 0
        } else {
            NotificationManager.IMPORTANCE_HIGH
        }
        val ignoringBatteryOptimizations = runCatching {
            powerManager.isIgnoringBatteryOptimizations(app.packageName)
        }.getOrDefault(false)
        ReminderLogger.info(
            "reminder.app_alarm_clock.runtime_health",
            mapOf(
                "exactAlarmEnabled" to exactAlarmEnabled,
                "notificationsEnabled" to notificationsEnabled,
                "fullScreenIntentEnabled" to fullScreenIntentEnabled,
                "ringingChannelImportance" to channelImportance,
                "ignoringBatteryOptimizations" to ignoringBatteryOptimizations,
            ),
        )
    }

    private suspend fun reminderSchedule(): TermSchedule? {
        val schedule = scheduleRepository.scheduleFlow.first()
        val manualCourses = manualCourseRepository.manualCoursesFlow.first()
        return mergeManualCoursesForReminders(schedule, manualCourses)
    }

    private fun mergeManualCoursesForReminders(
        schedule: TermSchedule?,
        manualCourses: List<CourseItem>,
    ): TermSchedule? {
        if (schedule == null && manualCourses.isEmpty()) return null
        val allCourses = schedule?.dailySchedules.orEmpty().flatMap { it.courses } + manualCourses
        val dailySchedules = allCourses
            .groupBy { it.time.dayOfWeek }
            .toSortedMap()
            .map { (day, courses) ->
                DailySchedule(
                    dayOfWeek = day,
                    courses = courses.sortedWith(
                        compareBy<CourseItem> { it.time.startNode }
                            .thenBy { it.time.endNode }
                            .thenBy { it.title },
                    ),
                )
            }
        return TermSchedule(
            termId = schedule?.termId ?: "manual",
            updatedAt = schedule?.updatedAt ?: java.time.OffsetDateTime.now().toString(),
            dailySchedules = dailySchedules,
        )
    }
}

private fun UserPreferences.toReminderAlarmSettings(): ReminderAlarmSettings = ReminderAlarmSettings(
    backend = alarmBackend,
    ringtoneUri = alarmRingtoneUri,
    alertMode = alarmAlertMode,
    ringDurationSeconds = alarmRingDurationSeconds,
    repeatIntervalSeconds = alarmRepeatIntervalSeconds,
    repeatCount = alarmRepeatCount,
)

private fun emptySystemAlarmSyncSummary(): SystemAlarmSyncSummary = SystemAlarmSyncSummary(
    submittedCount = 0,
    createdCount = 0,
    skippedExistingCount = 0,
    skippedUnrepresentableCount = 0,
    results = emptyList(),
)

