package com.kebiao.viewer.feature.widget

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal fun reconcileSystemAlarmsFromWidget(context: Context) {
    val appContext = context.applicationContext
    ScheduleWidgetWorkScheduler.schedule(appContext)
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        WidgetSystemAlarmSynchronizer.reconcileToday(appContext)
    }
}
