package com.x500x.cursimple.app

import com.x500x.cursimple.feature.widget.CalendarWidgetReceiver
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import com.x500x.cursimple.app.download.MirrorDownloader
import com.x500x.cursimple.app.download.mirrorDownloaderLabels
import com.x500x.cursimple.app.download.SharedPrefsMirrorPreferenceStore
import com.x500x.cursimple.app.holiday.HolidayCalendarSyncer
import com.x500x.cursimple.app.holiday.HolidayEveNoticeWorker
import com.x500x.cursimple.app.holiday.HolidaySyncOutcome
import com.x500x.cursimple.app.holiday.holidaySyncYears
import com.x500x.cursimple.core.data.AppLocale
import com.x500x.cursimple.core.data.reminder.DataStoreReminderRepository
import com.x500x.cursimple.app.notice.AlarmPreNoticeScheduler
import android.os.Build
import com.x500x.cursimple.app.reminder.AlarmRuntimeMaintenance
import com.x500x.cursimple.app.reminder.AlarmSyncScheduler
import com.x500x.cursimple.app.reminder.ReminderGuardJobService
import com.x500x.cursimple.app.util.AppDiagnosticsFileSink
import com.x500x.cursimple.app.util.AppDiagnosticsLogger
import com.x500x.cursimple.app.util.CategoryFilteredPluginSink
import com.x500x.cursimple.app.util.CategoryFilteredSink
import com.x500x.cursimple.app.util.LogCleanupScheduler
import com.x500x.cursimple.app.util.PluginFileLogSink
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.plugin.logging.PluginLogger
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import com.x500x.cursimple.core.reminder.model.ReminderSyncReason
import com.x500x.cursimple.feature.widget.ScheduleWidgetWorkScheduler
import com.x500x.cursimple.feature.widget.applyWidgetProviderVisibility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ClassScheduleApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Catch up missed notices only while the class has not started.
        var startedActivities = 0
        registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) {
                val becameForeground = ++startedActivities == 1
                if (becameForeground) {
                    appScope.launch {
                        runCatching {
                            com.x500x.cursimple.app.notice.ClassNoticeGateway.catchUpIfMissed(activity.applicationContext)
                        }.onFailure { ReminderLogger.warn("class_notice.catch_up.failure", emptyMap(), it) }
                        syncDateDataIfDue()
                        runCatching { HolidayEveNoticeWorker.checkTonight(activity.applicationContext) }
                            .onFailure { ReminderLogger.warn("holiday.eve_notice.check_failed", emptyMap(), it) }
                        runCatching {
                            com.x500x.cursimple.app.greeting.FestivalGreeting.postIfDue(activity.applicationContext, catchUp = true)
                        }
                    }
                }
            }

            override fun onActivityStopped(activity: android.app.Activity) {
                if (startedActivities > 0) startedActivities--
                if (startedActivities == 0) {
                    // Complete scheduling before the process can be suspended in the background.
                    val app = activity.applicationContext
                    appScope.launch {
                        runCatching { com.x500x.cursimple.app.reminder.ReminderGuardJobService.onAppBackground(app) }
                            .onFailure { ReminderLogger.warn("reminder.silent_guard.background.failure", emptyMap(), it) }
                    }
                }
            }

            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) = Unit
            override fun onActivityResumed(activity: android.app.Activity) = Unit
            override fun onActivityPaused(activity: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        })
        val diagnosticsSink = CategoryFilteredSink(this, AppDiagnosticsFileSink(this))
        AppDiagnosticsLogger.setSink(diagnosticsSink)
        ReminderLogger.setSink(diagnosticsSink)
        PluginLogger.setSink(CategoryFilteredPluginSink(this, PluginFileLogSink(this)))
        AppDiagnosticsLogger.info(
            "app.lifecycle.on_create",
            mapOf(
                "sdk" to Build.VERSION.SDK_INT,
                "android" to Build.VERSION.RELEASE,
                "packageName" to packageName,
            ),
        )
        appContainer = AppContainer(this)
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            runCatching {
                com.x500x.cursimple.feature.plugin.extension.ExtensionWebViewPool.prewarm(this)
            }
        }
        // Read exit history before startup work displaces its limited records.
        com.x500x.cursimple.app.reminder.ForceStopMonitor.onProcessStart(this)
        ScheduleWidgetWorkScheduler.schedule(this)
        com.x500x.cursimple.feature.widget.WidgetDayChangeWatcher.register(this)
        LogCleanupScheduler.schedule(this)

        AlarmSyncScheduler.schedulePeriodicSync(this)
        AlarmSyncScheduler.scheduleDailyGuard(this)
        HolidayEveNoticeWorker.schedule(this)
        com.x500x.cursimple.app.greeting.FestivalGreetingWorker.schedule(this)
        com.x500x.cursimple.app.extension.ExtensionSyncWorker.schedule(this)
        // The notification outlet performs no network work without bound targets.
        appContainer.notificationDeliveryCoordinator
        com.x500x.cursimple.feature.widget.ComponentWidgetHooks.render = appContainer.extensionCoordinator::renderWidget
        // The widget guard supplements delayed WorkManager component sync.
        com.x500x.cursimple.feature.widget.WidgetGuardHooks.onGuardTick = {
            appContainer.extensionCoordinator.syncDueInBackground("widget_guard")
        }

        appScope.launch {
            // Hide vendor-specific duplicate providers elsewhere; query packages off the UI thread.
            applyWidgetProviderVisibility(this@ClassScheduleApplication)
        }

        appScope.launch {
            AppLocale.syncCacheFrom(this@ClassScheduleApplication, appContainer.userPreferencesRepository)
        }

        appScope.launch {
            appContainer.bootstrapJob.join()
            syncHolidayCalendar()
        }

        // Remove the obsolete foreground guard notification channel.
        removeLegacyKeepAliveChannel()

        appScope.launch {
            appContainer.bootstrapJob.join()
            // Enable silent guard alarms and jobs together; no persistent notification is needed.
            appContainer.userPreferencesRepository.preferencesFlow
                .map { it.alarmKeepAliveEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    ReminderGuardJobService.applyPreference(this@ClassScheduleApplication)
                    if (enabled) {
                        runCatching { AlarmRuntimeMaintenance.onAlarmStarted(this@ClassScheduleApplication) }
                            .onFailure { ReminderLogger.warn("reminder.silent_guard.resync.failure", emptyMap(), it) }
                    }
                }
        }

        appScope.launch {
            appContainer.bootstrapJob.join()
            combine(
                DataStoreReminderRepository(this@ClassScheduleApplication).systemAlarmRecordsFlow
                    .map { records -> records.filter { it.enabled }.map { it.alarmKey to it.triggerAtMillis }.toSet() },
                appContainer.userPreferencesRepository.preferencesFlow.map { it.alarmPreNotice },
            ) { records, settings -> records to settings }
                .distinctUntilChanged()
                .collect {
                    runCatching { AlarmPreNoticeScheduler.reschedule(this@ClassScheduleApplication) }
                        .onFailure { error -> ReminderLogger.warn("alarm_pre_notice.reschedule.failure", emptyMap(), error) }
                }
        }

        appScope.launch {
            appContainer.bootstrapJob.join()
            // Force-stop clears alarms and blocks background delivery until the app is reopened.
            runCatching { appContainer.ensureAlarmRuntimeHealth() }
                .onFailure { error ->
                    ReminderLogger.warn("reminder.startup.health_check.failure", emptyMap(), error)
                }
            appContainer.tryRunSharedAlarmPoll(ReminderSyncReason.WidgetRefresh)
        }

        appScope.launch {
            appContainer.bootstrapJob.join()
            appContainer.userPreferencesRepository.preferencesFlow
                .map { it.debugForcedDateTime }
                .distinctUntilChanged()
                .collect { forced ->
                    BeijingTime.setForcedNow(forced)
                    appContainer.refreshWidgets()
                }
        }

        appScope.launch {
            appContainer.bootstrapJob.join()
            appContainer.userPreferencesRepository.preferencesFlow
                .map { it.appTimeZoneId }
                .distinctUntilChanged()
                .collect { zoneId ->
                    BeijingTime.setOverrideZone(
                        zoneId?.let { runCatching { java.time.ZoneId.of(it) }.getOrNull() },
                    )
                    appContainer.refreshWidgets()
                }
        }

        appScope.launch {
            appContainer.bootstrapJob.join()
            appContainer.userPreferencesRepository.preferencesFlow
                .map { it.themeAccent to it.themeCustomColorArgb }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    appContainer.refreshWidgets()
                }
        }

        appScope.launch {
            appContainer.bootstrapJob.join()
            appContainer.memoRepository.notesFlow.distinctUntilChanged().collect {
                appContainer.notificationDeliveryCoordinator.onMemosChanged()
                runCatching { com.x500x.cursimple.feature.widget.MemoTodoWidgetReceiver.updateWidgets(this@ClassScheduleApplication) }
                    .onFailure { error -> AppDiagnosticsLogger.warn("widget.memo.refresh.failure", emptyMap(), error) }
            }
        }

        appScope.launch {
            appContainer.bootstrapJob.join()
            appContainer.scheduleEventRepository.eventsFlow
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    runCatching { CalendarWidgetReceiver.updateWidgets(this@ClassScheduleApplication) }
                        .onFailure { error -> AppDiagnosticsLogger.warn("widget.calendar.events_refresh.failure", emptyMap(), error) }
                }
        }

        appScope.launch {
            appContainer.bootstrapJob.join()
            appContainer.widgetPreferencesRepository.themePreferencesFlow
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    appContainer.refreshWidgets()
                }
        }
    }

    /** Refresh holiday data when stale; retain cached data on failure. */
    /**
     * Refresh calendar data on foreground entry, with a one-hour cooldown; background launches
     * do not fetch.
     */
    private suspend fun syncDateDataIfDue() {
        val prefs = getSharedPreferences(DATE_DATA_PREFS, MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_DATE_DATA_SYNCED_AT, 0L) < DATE_DATA_MIN_INTERVAL_MILLIS) return
        prefs.edit().putLong(KEY_DATE_DATA_SYNCED_AT, now).apply()
        appContainer.bootstrapJob.join()
        syncHolidayCalendar(force = true)
        runCatching {
            com.x500x.cursimple.app.greeting.FestivalDataset.sync(this, dateDataDownloader())
        }.onFailure { AppDiagnosticsLogger.warn("date_data.festival_sync.failure", emptyMap(), it) }
    }

    private fun dateDataDownloader() = MirrorDownloader(
        labels = mirrorDownloaderLabels(),
        preferenceStore = SharedPrefsMirrorPreferenceStore(this),
    )

    private suspend fun syncHolidayCalendar(force: Boolean = false) {
        runCatching {
            val repository = appContainer.userPreferencesRepository
            val cached = repository.preferencesFlow.first().holidayCalendar.syncedYears
            val syncer = HolidayCalendarSyncer(dateDataDownloader())
            val updated = syncer
                .sync(years = holidaySyncYears(BeijingTime.today()), cached = cached, force = force)
                .filterIsInstance<HolidaySyncOutcome.Updated>()
                .map { it.year }
            if (updated.isNotEmpty()) {
                repository.putSyncedHolidayYears(updated)
                appContainer.refreshWidgets()
            }
        }
    }

    private fun removeLegacyKeepAliveChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            getSystemService(NotificationManager::class.java)
                ?.deleteNotificationChannel(LEGACY_KEEP_ALIVE_CHANNEL_ID)
        }
    }

    private companion object {
        const val LEGACY_KEEP_ALIVE_CHANNEL_ID = "course_alarm_keep_alive"
        const val DATE_DATA_PREFS = "date_data_sync"
        const val KEY_DATE_DATA_SYNCED_AT = "synced_at"
        const val DATE_DATA_MIN_INTERVAL_MILLIS = 60 * 60 * 1000L
    }
}
