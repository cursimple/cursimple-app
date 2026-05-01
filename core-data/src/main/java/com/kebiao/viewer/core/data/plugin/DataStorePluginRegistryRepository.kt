package com.kebiao.viewer.core.data.plugin

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kebiao.viewer.core.plugin.install.InstalledPluginRecord
import com.kebiao.viewer.core.plugin.install.PluginRegistryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.pluginRegistryStore by preferencesDataStore(name = "plugin_registry_store")

class DataStorePluginRegistryRepository(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : PluginRegistryRepository {
    private val store = context.applicationContext.pluginRegistryStore

    override val installedPluginsFlow: Flow<List<InstalledPluginRecord>> = store.data.map { preferences ->
        preferences.decodeInstalledPlugins()
    }

    override suspend fun getInstalledPlugins(): List<InstalledPluginRecord> {
        return installedPluginsFlow.first()
    }

    override suspend fun find(pluginId: String): InstalledPluginRecord? {
        return getInstalledPlugins().firstOrNull { it.pluginId == pluginId }
    }

    override suspend fun saveInstalledPlugin(record: InstalledPluginRecord) {
        store.edit { preferences ->
            val current = preferences.decodeInstalledPlugins()
            val next = current
                .filterNot { it.pluginId == record.pluginId }
                .plus(record)
                .sortedBy { it.name }
            preferences[KEY_INSTALLED_PLUGINS] = json.encodeToString(
                ListSerializer(InstalledPluginRecord.serializer()),
                next,
            )
        }
    }

    override suspend fun removeInstalledPlugin(pluginId: String) {
        store.edit { preferences ->
            val next = preferences.decodeInstalledPlugins().filterNot { it.pluginId == pluginId }
            preferences[KEY_INSTALLED_PLUGINS] = json.encodeToString(
                ListSerializer(InstalledPluginRecord.serializer()),
                next,
            )
        }
    }

    private companion object {
        val KEY_INSTALLED_PLUGINS = stringPreferencesKey("installed_plugins")
    }

    private fun Preferences.decodeInstalledPlugins(): List<InstalledPluginRecord> {
        return this[KEY_INSTALLED_PLUGINS]?.let { raw ->
            runCatching {
                json.decodeFromString(ListSerializer(InstalledPluginRecord.serializer()), raw)
            }.getOrDefault(emptyList())
        }.orEmpty()
    }
}
