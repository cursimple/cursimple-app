package com.kebiao.viewer.app

import android.app.Application
import com.kebiao.viewer.app.util.PluginFileLogSink
import com.kebiao.viewer.core.kernel.time.BeijingTime
import com.kebiao.viewer.core.plugin.logging.PluginLogger
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
        PluginLogger.setSink(PluginFileLogSink(this))
        appContainer = AppContainer(this)
        ScheduleWidgetWorkScheduler.schedule(this)

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
