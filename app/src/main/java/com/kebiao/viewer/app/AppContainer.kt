package com.kebiao.viewer.app

import android.app.Application
import com.kebiao.viewer.core.data.DataStoreScheduleRepository
import com.kebiao.viewer.core.data.ScheduleRepository
import com.kebiao.viewer.core.js.DefaultJsHostBridge
import com.kebiao.viewer.core.js.PluginCatalogAssetSource
import com.kebiao.viewer.core.js.QuickJsScheduleExecutor
import com.kebiao.viewer.core.kernel.service.ScheduleSyncService
import com.kebiao.viewer.feature.widget.ScheduleWidgetUpdater

class AppContainer(
    private val app: Application,
) {
    val scheduleRepository: ScheduleRepository = DataStoreScheduleRepository(app)

    private val jsHostBridge = DefaultJsHostBridge()
    private val pluginCatalog = PluginCatalogAssetSource(app)
    private val pluginExecutor = QuickJsScheduleExecutor(jsHostBridge)

    val scheduleSyncService = ScheduleSyncService(
        pluginCatalog = pluginCatalog,
        pluginExecutor = pluginExecutor,
    )

    suspend fun refreshWidgets() {
        ScheduleWidgetUpdater.refreshAll(app)
    }
}

