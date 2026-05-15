package com.x500x.cursimple.core.plugin.component

import kotlinx.coroutines.flow.Flow

interface PluginComponentRepository {
    val installedComponentsFlow: Flow<List<InstalledPluginComponentRecord>>

    suspend fun getInstalledComponents(): List<InstalledPluginComponentRecord>

    suspend fun find(componentId: String): InstalledPluginComponentRecord?

    suspend fun save(record: InstalledPluginComponentRecord)

    suspend fun remove(componentId: String)
}
