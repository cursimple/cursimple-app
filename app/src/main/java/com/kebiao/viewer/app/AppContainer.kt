package com.kebiao.viewer.app

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import com.kebiao.viewer.core.data.DataStoreManualCourseRepository
import com.kebiao.viewer.core.data.DataStoreScheduleRepository
import com.kebiao.viewer.core.data.DataStoreUserPreferencesRepository
import com.kebiao.viewer.core.data.ManualCourseRepository
import com.kebiao.viewer.core.data.ScheduleRepository
import com.kebiao.viewer.core.data.UserPreferencesRepository
import com.kebiao.viewer.core.data.UserPreferences
import com.kebiao.viewer.core.data.plugin.DataStorePluginRegistryRepository
import com.kebiao.viewer.core.data.reminder.DataStoreReminderRepository
import com.kebiao.viewer.core.data.term.DataStoreTermProfileRepository
import com.kebiao.viewer.core.data.term.TermProfileRepository
import com.kebiao.viewer.core.data.widget.DataStoreWidgetPreferencesRepository
import com.kebiao.viewer.core.kernel.model.CourseItem
import com.kebiao.viewer.core.kernel.model.DailySchedule
import com.kebiao.viewer.core.kernel.model.TermSchedule
import com.kebiao.viewer.core.kernel.model.TermTimingProfile
import com.kebiao.viewer.core.kernel.model.termStartLocalDate
import com.kebiao.viewer.core.plugin.PluginManager
import com.kebiao.viewer.core.reminder.ReminderCoordinator
import com.kebiao.viewer.core.reminder.ReminderSyncWindows
import com.kebiao.viewer.core.reminder.model.ReminderAlarmSettings
import com.kebiao.viewer.core.reminder.model.ReminderSyncReason
import com.kebiao.viewer.core.reminder.model.SystemAlarmSyncSummary
import com.kebiao.viewer.app.reminder.SystemAlarmCheckScheduler
import com.kebiao.viewer.feature.widget.ScheduleWidgetUpdater
import com.kebiao.viewer.feature.widget.ScheduleWidgetWorkScheduler
import com.kebiao.viewer.core.reminder.logging.ReminderLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    val pluginManager = PluginManager(
        context = app,
        registryRepository = pluginRegistryRepository,
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

    val bundledPluginCatalog: List<BundledPluginEntry> = listOf(
        BundledPluginEntry(
            pluginId = YANGTZEU_PLUGIN_ID,
            assetRoot = "plugin-dev/yangtzeu-eams-v2",
            name = "长江大学教务插件",
            description = "通过 ATrust + EAMS 抓取课表的内置插件。",
        ),
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

        // Clean up plugins that are no longer offered (e.g. legacy demo plugins from
        // earlier builds). Do NOT auto-install bundled plugins — the user adds them
        // explicitly from the plugin market.
        val catalogById = bundledPluginCatalog.associateBy { it.pluginId }
        val installedPlugins = pluginManager.getInstalledPlugins()
        installedPlugins
            .filter { it.isBundled && it.pluginId in catalogById }
            .forEach { plugin ->
                runCatching {
                    pluginManager.ensureBundledPlugin(catalogById.getValue(plugin.pluginId).assetRoot)
                }
            }
        installedPlugins
            .filterNot { it.pluginId in catalogById }
            .forEach { runCatching { pluginManager.removePlugin(it.pluginId) } }
    }

    private suspend fun awaitBootstrap() = bootstrapJob.join()

    suspend fun installBundledPlugin(pluginId: String) {
        awaitBootstrap()
        val entry = bundledPluginCatalog.firstOrNull { it.pluginId == pluginId } ?: return
        pluginManager.ensureBundledPlugin(entry.assetRoot)
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
                ReminderSyncReason.WidgetRefresh -> ReminderSyncWindows.todayFromNow(timingProfile)
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
        const val YANGTZEU_PLUGIN_ID = "yangtzeu-eams-v2"
        const val SHARED_ALARM_POLL_INTERVAL_MILLIS = 40L * 60L * 1000L
        val NEXT_DAY_SYNC_START_TIME: LocalTime = LocalTime.NOON
        const val ALARM_RINGING_CHANNEL_ID = "course_alarm_ringing"

        fun parseIsoDate(value: String): LocalDate? =
            runCatching { LocalDate.parse(value) }.getOrNull()
    }

    private fun shouldSyncNextDayAfterAlarm(timingProfile: TermTimingProfile, nowMillis: Long): Boolean {
        val zone = ZoneId.of(timingProfile.timezone)
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

data class BundledPluginEntry(
    val pluginId: String,
    val assetRoot: String,
    val name: String,
    val description: String,
)
