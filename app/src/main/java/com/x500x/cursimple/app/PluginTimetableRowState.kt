package com.x500x.cursimple.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.x500x.cursimple.core.plugin.PluginManager
import com.x500x.cursimple.core.plugin.storage.PluginFileStore
import com.x500x.cursimple.core.plugin.ui.PluginUiSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Display declarations follow installed packages, independently of importing a timetable. */
@Composable
internal fun rememberPluginTimetableRowDefinitions(manager: PluginManager): State<Map<String, PluginUiSchema>> {
    val records by manager.installedPluginsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current.applicationContext
    return produceState<Map<String, PluginUiSchema>>(emptyMap(), records) {
        value = withContext(Dispatchers.IO) {
            val files = PluginFileStore(context)
            records.filterNot { it.isExtension }.sortedByDescending { it.versionCode }.distinctBy { it.pluginId }
                .associate { record -> record.pluginId to runCatching {
                    files.loadUiSchema(record)
                }.getOrDefault(PluginUiSchema()) }
        }
    }
}
