package com.kebiao.viewer.app

import android.app.Application
import com.kebiao.viewer.feature.widget.ScheduleWidgetWorkScheduler

class ClassScheduleApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
        ScheduleWidgetWorkScheduler.schedule(this)
    }
}

