package com.x500x.cursimple.app

import android.app.Application
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
import com.x500x.cursimple.app.reminder.AlarmKeepAliveService
import com.x500x.cursimple.app.reminder.AlarmSyncScheduler
import com.x500x.cursimple.app.reminder.ReminderGuardJobService
import com.x500x.cursimple.app.util.AppDiagnosticsFileSink
import com.x500x.cursimple.app.util.AppDiagnosticsLogger
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
        // 要赶在别的启动任务之前：系统记的退出原因只保留最近几条
        com.x500x.cursimple.app.reminder.ForceStopMonitor.onProcessStart(this)
        ScheduleWidgetWorkScheduler.schedule(this)
        // 零点精确闹钟之外的第二道保险：进程活着时换天/解锁就把过期的小组件重画
        com.x500x.cursimple.feature.widget.WidgetDayChangeWatcher.register(this)
        LogCleanupScheduler.schedule(this)

        // 调度闹钟同步 WorkManager 任务
        AlarmSyncScheduler.schedulePeriodicSync(this)
        AlarmSyncScheduler.scheduleDailyGuard(this)
        HolidayEveNoticeWorker.schedule(this)

        appScope.launch {
            // 非厂商机型上把 MIUI/vivo 副本 receiver 收起来，选择器里每个小组件才只出现一次。
            // 要查包管理器，放在后台做，不占启动那一帧
            applyWidgetProviderVisibility(this@ClassScheduleApplication)
        }

        appScope.launch {
            // 备份恢复等路径会绕过设置界面直接改语言，启动时对齐一次同步副本
            AppLocale.syncCacheFrom(this@ClassScheduleApplication, appContainer.userPreferencesRepository)
        }

        appScope.launch {
            appContainer.bootstrapJob.join()
            syncHolidayCalendar()
        }

        appScope.launch {
            appContainer.bootstrapJob.join()
            // 守护服务跟着开关走：开着就保证它在，关掉就收回那条常驻通知
            appContainer.userPreferencesRepository.preferencesFlow
                .map { it.alarmKeepAliveEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled) {
                        AlarmKeepAliveService.start(this@ClassScheduleApplication, rescheduleAlarms = true)
                    } else {
                        AlarmKeepAliveService.stop(this@ClassScheduleApplication)
                    }
                }
        }

        appScope.launch {
            appContainer.bootstrapJob.join()
            // 巡检任务跟着上课提醒与守护的开关走，两个都关了才撤
            appContainer.userPreferencesRepository.preferencesFlow
                .map { it.classNotice.enabled to it.alarmKeepAliveEnabled }
                .distinctUntilChanged()
                .collect { ReminderGuardJobService.applyPreference(this@ClassScheduleApplication) }
        }

        appScope.launch {
            appContainer.bootstrapJob.join()
            // 闹钟增删改或预告设置一变，最近那个闹钟就可能换了，预告跟着重排
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
            // 强制停止会清空本应用全部已注册的闹钟，且此后广播与 WorkManager 都不再投递，
            // 只有用户重新打开应用这一个时机能发现并补回来
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
                    // 时区无法解析时按跟随设备处理，不让一个坏值把全应用的时间算错
                    BeijingTime.setOverrideZone(
                        zoneId?.let { runCatching { java.time.ZoneId.of(it) }.getOrNull() },
                    )
                    appContainer.refreshWidgets()
                }
        }

        appScope.launch {
            appContainer.bootstrapJob.join()
            appContainer.userPreferencesRepository.preferencesFlow
                // 自选色换了颜色、主题色本身没变时也得重画小组件
                .map { it.themeAccent to it.themeCustomColorArgb }
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
            val syncer = HolidayCalendarSyncer(
                MirrorDownloader(
                    labels = mirrorDownloaderLabels(),
                    preferenceStore = SharedPrefsMirrorPreferenceStore(this),
                ),
            )
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
