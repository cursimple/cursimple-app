package com.kebiao.viewer.app

import android.app.Application
import com.kebiao.viewer.core.data.DataStoreScheduleRepository
import com.kebiao.viewer.core.data.ScheduleRepository
import com.kebiao.viewer.core.data.plugin.DataStorePluginRegistryRepository
import com.kebiao.viewer.core.data.reminder.DataStoreReminderRepository
import com.kebiao.viewer.core.data.widget.DataStoreWidgetPreferencesRepository
import com.kebiao.viewer.core.plugin.PluginManager
import com.kebiao.viewer.core.reminder.ReminderCoordinator
import com.kebiao.viewer.feature.widget.ScheduleWidgetUpdater
import kotlinx.coroutines.runBlocking

class AppContainer(
    private val app: Application,
) {
    val scheduleRepository: ScheduleRepository = DataStoreScheduleRepository(app)
    val pluginRegistryRepository = DataStorePluginRegistryRepository(app)
    val reminderRepository = DataStoreReminderRepository(app)
    val widgetPreferencesRepository = DataStoreWidgetPreferencesRepository(app)
    val pluginManager = PluginManager(
        context = app,
        registryRepository = pluginRegistryRepository,
    )
    val reminderCoordinator = ReminderCoordinator(
        context = app,
        repository = reminderRepository,
    )

    init {
        runBlocking {
            pluginManager.ensureBundledPlugin("plugin-dev/demo-campus-v2")
            pluginManager.ensureBundledPlugin("plugin-dev/demo-web-campus-v2")
            pluginManager.ensureBundledPlugin("plugin-dev/yangtzeu-eams-v2")
        }
    }

    suspend fun refreshWidgets() {
        ScheduleWidgetUpdater.refreshAll(app)
    }
}
