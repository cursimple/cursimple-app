package com.kebiao.viewer.core.kernel.service

import com.kebiao.viewer.core.kernel.plugin.PluginCatalog
import com.kebiao.viewer.core.kernel.plugin.PluginSyncRequest
import com.kebiao.viewer.core.kernel.plugin.SchedulePluginExecutor
import com.kebiao.viewer.core.kernel.plugin.SyncFailure
import com.kebiao.viewer.core.kernel.plugin.SyncResult

class ScheduleSyncService(
    private val pluginCatalog: PluginCatalog,
    private val pluginExecutor: SchedulePluginExecutor,
) {

    suspend fun listPlugins() = pluginCatalog.list()

    suspend fun sync(request: PluginSyncRequest): SyncResult {
        val descriptor = pluginCatalog.find(request.pluginId)
            ?: return SyncResult.Failure(SyncFailure.PluginNotFound)

        val script = runCatching { pluginCatalog.loadScript(descriptor) }
            .getOrElse {
                return SyncResult.Failure(
                    SyncFailure.PluginLoadFailed(it.message ?: "插件脚本加载失败"),
                )
            }

        return runCatching { pluginExecutor.execute(descriptor, script, request) }
            .fold(
                onSuccess = { SyncResult.Success(it) },
                onFailure = {
                    SyncResult.Failure(
                        SyncFailure.ExecutionFailed(it.message ?: "插件执行失败"),
                    )
                },
            )
    }
}

