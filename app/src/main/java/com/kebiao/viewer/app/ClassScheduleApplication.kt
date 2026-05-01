package com.kebiao.viewer.app

import android.app.Application
import com.kebiao.viewer.app.util.PluginFileLogSink
import com.kebiao.viewer.core.plugin.logging.PluginLogger
import com.kebiao.viewer.feature.widget.ScheduleWidgetWorkScheduler

class ClassScheduleApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        PluginLogger.setSink(PluginFileLogSink(this))
        appContainer = AppContainer(this)
        ScheduleWidgetWorkScheduler.schedule(this)
    }
}
