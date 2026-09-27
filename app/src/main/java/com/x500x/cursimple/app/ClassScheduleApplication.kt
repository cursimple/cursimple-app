package com.x500x.cursimple.app

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
        // 应用回到前台时补发那条错过了的上课通知：提醒点过了、课还没开始，
        // 打开应用就要弹出来，别一节都没提醒过
        var startedActivities = 0
        registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) {
                val becameForeground = ++startedActivities == 1
                if (becameForeground) {
                    appScope.launch {
                        runCatching {
                            com.x500x.cursimple.app.notice.ClassNoticeGateway.catchUpIfMissed(activity.applicationContext)
                        }.onFailure { ReminderLogger.warn("class_notice.catch_up.failure", emptyMap(), it) }
                        // 放假安排、节日节气这些和日期有关的数据，打开 App 时静默刷新一次
                        syncDateDataIfDue()
                        // 节日问候白天没发出去（省电、进程被收走），打开 App 时补上
                        runCatching {
                            com.x500x.cursimple.app.greeting.FestivalGreeting.postIfDue(activity.applicationContext, catchUp = true)
                        }
                    }
                }
            }

            override fun onActivityStopped(activity: android.app.Activity) {
                if (startedActivities > 0) startedActivities--
                if (startedActivities == 0) {
                    // 退到后台的这一刻进程一定还活着，趁现在把排程补齐：
                    // 之后被系统回收、被厂商冻结，挂在系统里的精确闹钟照样会把提醒送到
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
        com.x500x.cursimple.app.greeting.FestivalGreetingWorker.schedule(this)

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

        // 早先的常驻守护服务已经拿掉，它那个「提醒守护」渠道留在系统通知设置里只会让人困惑
        removeLegacyKeepAliveChannel()

        appScope.launch {
            appContainer.bootstrapJob.join()
            // 静默守护：巡检闹钟和巡检任务跟着开关挂上或撤掉，都不挂通知。
            // 开着时每次启动顺手重挂一遍上课提醒、体检闹钟，退到后台前先把排程补齐
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
    /**
     * 打开 App 时静默刷新和日期有关的数据：放假安排、节日节气日期表。
     * 只在回到前台时做——闹钟、巡检在后台拉起进程时不联网；一小时内来回切换也不重复下。
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
