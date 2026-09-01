package com.x500x.cursimple.app

import android.app.Application
import android.content.Context
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
}
