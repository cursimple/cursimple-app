package com.kebiao.viewer.core.js

import android.content.Context
import com.kebiao.viewer.core.kernel.plugin.PluginCatalog
import com.kebiao.viewer.core.kernel.plugin.PluginDescriptor
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PluginCatalogAssetSource(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : PluginCatalog {

    @Volatile
    private var cachedDescriptors: List<PluginDescriptor>? = null

    override suspend fun list(): List<PluginDescriptor> = withContext(Dispatchers.IO) {
        cachedDescriptors ?: readIndex().also { cachedDescriptors = it }
    }

    override suspend fun find(pluginId: String): PluginDescriptor? {
        return list().firstOrNull { it.id == pluginId }
    }

    override suspend fun loadScript(descriptor: PluginDescriptor): String = withContext(Dispatchers.IO) {
        context.assets.open(descriptor.entryAsset).bufferedReader().use { it.readText() }
    }

    private fun readIndex(): List<PluginDescriptor> {
        val content = context.assets.open(INDEX_ASSET).bufferedReader().use { it.readText() }
        return json.decodeFromString(ListSerializer(PluginDescriptor.serializer()), content)
    }

    private companion object {
        const val INDEX_ASSET = "plugins/index.json"
    }
}
