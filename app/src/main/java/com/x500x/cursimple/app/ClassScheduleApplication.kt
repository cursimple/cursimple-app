package com.x500x.cursimple.app

import android.app.Application
import android.content.Context
import com.x500x.cursimple.app.download.MirrorDownloader
import com.x500x.cursimple.app.download.mirrorDownloaderLabels
import com.x500x.cursimple.app.holiday.HolidayCalendarSyncer
import com.x500x.cursimple.app.holiday.HolidayEveNoticeWorker
import com.x500x.cursimple.app.holiday.HolidaySyncOutcome
import com.x500x.cursimple.app.holiday.holidaySyncYears
import com.x500x.cursimple.core.data.AppLocale
import android.os.Build
import com.x500x.cursimple.app.reminder.AlarmSyncScheduler
import com.x500x.cursimple.app.util.AppDiagnosticsFileSink
import com.x500x.cursimple.app.util.AppDiagnosticsLogger
import com.x500x.cursimple.app.util.LogCleanupScheduler
import com.x500x.cursimple.app.util.PluginFileLogSink
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.plugin.logging.PluginLogger
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import com.x500x.cursimple.core.reminder.model.ReminderSyncReason
import com.x500x.cursimple.feature.widget.ScheduleWidgetWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        val diagnosticsSink = AppDiagnosticsFileSink(this)
        AppDiagnosticsLogger.setSink(diagnosticsSink)
        ReminderLogger.setSink(diagnosticsSink)
        PluginLogger.setSink(PluginFileLogSink(this))
        AppDiagnosticsLogger.info(
            "app.lifecycle.on_create",
            mapOf(
                "sdk" to Build.VERSION.SDK_INT,
                "android" to Build.VERSION.RELEASE,
                "packageName" to packageName,
            ),
        )
        appContainer = AppContainer(this)
        // 备份恢复等路径会绕过设置界面直接改语言，启动时对齐一次同步副本
        AppLocale.syncCacheFrom(this, appContainer.userPreferencesRepository)
        ScheduleWidgetWorkScheduler.schedule(this)
        LogCleanupScheduler.schedule(this)

        // 调度闹钟同步 WorkManager 任务
        AlarmSyncScheduler.schedulePeriodicSync(this)
        AlarmSyncScheduler.scheduleDailyGuard(this)
        HolidayEveNoticeWorker.schedule(this)

        appScope.launch {
            appContainer.bootstrapJob.join()
            syncHolidayCalendar()
        }

        appScope.launch {
            appContainer.bootstrapJob.join()
            appContainer.scheduleSystemAlarmChecks()
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
                .map { it.themeAccent }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    appContainer.refreshWidgets()
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

    /**
     * 取回当年与次年的放假安排。
     * 缓存足够新时同步器自己跳过，不会每次启动都联网；取不到就沿用已有数据。
     */
    private suspend fun syncHolidayCalendar() {
        runCatching {
            val repository = appContainer.userPreferencesRepository
            val cached = repository.preferencesFlow.first().holidayCalendar.syncedYears
            val syncer = HolidayCalendarSyncer(MirrorDownloader(labels = mirrorDownloaderLabels()))
            val updated = syncer
                .sync(years = holidaySyncYears(BeijingTime.today()), cached = cached)
                .filterIsInstance<HolidaySyncOutcome.Updated>()
                .map { it.year }
            if (updated.isNotEmpty()) {
                repository.putSyncedHolidayYears(updated)
                appContainer.refreshWidgets()
            }
        }
    }

}
