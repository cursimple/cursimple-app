package com.x500x.cursimple.core.data.plugin

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.x500x.cursimple.core.plugin.component.InstalledPluginComponentRecord
import com.x500x.cursimple.core.plugin.component.PluginComponentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.pluginComponentStore by preferencesDataStore(name = "plugin_component_store")

class DataStorePluginComponentRepository(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : PluginComponentRepository {
    private val store = context.applicationContext.pluginComponentStore

    override val installedComponentsFlow: Flow<List<InstalledPluginComponentRecord>> = store.data.map { preferences ->
        preferences.decodeInstalledComponents()
    }

    override suspend fun getInstalledComponents(): List<InstalledPluginComponentRecord> {
        return installedComponentsFlow.first()
    }

    override suspend fun find(componentId: String): InstalledPluginComponentRecord? {
        return getInstalledComponents().firstOrNull { it.id == componentId }
    }

    override suspend fun save(record: InstalledPluginComponentRecord) {
        store.edit { preferences ->
            val next = preferences.decodeInstalledComponents()
                .filterNot { it.id == record.id }
                .plus(record)
                .sortedBy { it.id }
            preferences[KEY_INSTALLED_COMPONENTS] = json.encodeToString(
                ListSerializer(InstalledPluginComponentRecord.serializer()),
                next,
            )
        }
    }

    override suspend fun remove(componentId: String) {
        store.edit { preferences ->
            val next = preferences.decodeInstalledComponents().filterNot { it.id == componentId }
            preferences[KEY_INSTALLED_COMPONENTS] = json.encodeToString(
                ListSerializer(InstalledPluginComponentRecord.serializer()),
                next,
            )
        }
    }

    private fun Preferences.decodeInstalledComponents(): List<InstalledPluginComponentRecord> {
        return this[KEY_INSTALLED_COMPONENTS]?.let { raw ->
            runCatching {
                json.decodeFromString(ListSerializer(InstalledPluginComponentRecord.serializer()), raw)
            }.getOrDefault(emptyList())
        }.orEmpty()
    }

    private companion object {
        val KEY_INSTALLED_COMPONENTS = stringPreferencesKey("installed_components")
    }
}
