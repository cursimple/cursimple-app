package com.x500x.cursimple.core.data.plugin

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.x500x.cursimple.core.data.AppBackupStores
import com.x500x.cursimple.core.data.PreferencesStoreSnapshot
import com.x500x.cursimple.core.data.exportSnapshot
import com.x500x.cursimple.core.data.restoreSnapshot
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.install.PluginCompatibilityStatus
import com.x500x.cursimple.core.plugin.install.PluginRegistryRepository
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

    suspend fun exportBackupSnapshot(): PreferencesStoreSnapshot =
        store.exportSnapshot(AppBackupStores.PLUGIN_REGISTRY)

    suspend fun restoreBackupSnapshot(snapshot: PreferencesStoreSnapshot) {
        store.restoreSnapshot(snapshot)
    }

    private companion object {
        val KEY_INSTALLED_PLUGINS = stringPreferencesKey("installed_plugins")
    }

    private fun Preferences.decodeInstalledPlugins(): List<InstalledPluginRecord> {
        return this[KEY_INSTALLED_PLUGINS]?.let { raw ->
            runCatching {
                json.decodeFromString(ListSerializer(InstalledPluginRecord.serializer()), raw)
                    .map { record -> record.normalizeCompatibility() }
            }.getOrDefault(emptyList())
        }.orEmpty()
    }

    private fun InstalledPluginRecord.normalizeCompatibility(): InstalledPluginRecord {
        if (compatibilityStatus == PluginCompatibilityStatus.Incompatible) {
            return this
        }
        val incompatibleReason = when {
            apiVersion <= 0 -> "旧版插件记录缺少 API 版本"
            entry.isBlank() -> "旧版插件记录缺少 JS 入口文件"
            else -> null
        }
        return if (incompatibleReason == null) {
            this
        } else {
            copy(
                compatibilityStatus = PluginCompatibilityStatus.Incompatible,
                compatibilityMessage = incompatibleReason,
            )
        }
    }
}
