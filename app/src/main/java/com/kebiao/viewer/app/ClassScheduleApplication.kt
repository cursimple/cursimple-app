package com.kebiao.viewer.app

import android.app.Application
import android.os.Build
import com.kebiao.viewer.app.util.AppDiagnosticsFileSink
import com.kebiao.viewer.app.util.AppDiagnosticsLogger
import com.kebiao.viewer.app.util.LogCleanupScheduler
import com.kebiao.viewer.app.util.PluginFileLogSink
import com.kebiao.viewer.core.kernel.time.BeijingTime
import com.kebiao.viewer.core.plugin.logging.PluginLogger
import com.kebiao.viewer.core.reminder.logging.ReminderLogger
import com.kebiao.viewer.feature.widget.ScheduleWidgetWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ClassScheduleApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

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
        ScheduleWidgetWorkScheduler.schedule(this)
        LogCleanupScheduler.schedule(this)
        appScope.launch {
            appContainer.scheduleSystemAlarmChecks()
        }

        appScope.launch {
            appContainer.userPreferencesRepository.preferencesFlow
                .map { it.debugForcedDateTime }
                .distinctUntilChanged()
                .collect { forced ->
                    BeijingTime.setForcedNow(forced)
                    appContainer.refreshWidgets()
                }
        }
    }
}
