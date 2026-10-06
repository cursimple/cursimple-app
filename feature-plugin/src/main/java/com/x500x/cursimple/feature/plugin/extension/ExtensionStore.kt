package com.x500x.cursimple.feature.plugin.extension

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * One locked, persisted JSON archive per component; shared [StateFlow] serves same-process
 * consumers.
 */
class ExtensionStore internal constructor(private val dir: File) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val mutex = Mutex()
    private val state = MutableStateFlow<Map<String, ExtensionData>>(emptyMap())

    @Volatile
    private var loaded = false

    val all: StateFlow<Map<String, ExtensionData>> = state.asStateFlow()

    fun flow(pluginId: String): Flow<ExtensionData> =
        state.map { it[pluginId] ?: ExtensionData(pluginId) }.distinctUntilChanged()

    suspend fun get(pluginId: String): ExtensionData {
        ensureLoaded()
        return state.value[pluginId] ?: ExtensionData(pluginId)
    }

    suspend fun update(pluginId: String, transform: (ExtensionData) -> ExtensionData): ExtensionData {
        ensureLoaded()
        return mutex.withLock {
            val current = state.value[pluginId] ?: ExtensionData(pluginId)
            val next = transform(current)
            if (next != current || pluginId !in state.value) {
                withContext(Dispatchers.IO) { fileOf(pluginId).writeText(json.encodeToString(ExtensionData.serializer(), next)) }
                state.value = state.value + (pluginId to next)
            }
            next
        }
    }

    /** Running tasks may update existing archives only; never recreate data after uninstall. */
    suspend fun updateIfPresent(pluginId: String, transform: (ExtensionData) -> ExtensionData?): ExtensionData? {
        ensureLoaded()
        return mutex.withLock {
            val current = state.value[pluginId] ?: return@withLock null
            val next = transform(current) ?: return@withLock null
            if (next != current) {
                withContext(Dispatchers.IO) { fileOf(pluginId).writeText(json.encodeToString(ExtensionData.serializer(), next)) }
                state.value = state.value + (pluginId to next)
            }
            next
        }
    }

    suspend fun remove(pluginId: String) {
        ensureLoaded()
        mutex.withLock {
            withContext(Dispatchers.IO) {
                fileOf(pluginId).delete()
                dir.parentFile?.let { ExtensionDownloads.downloadDir(it, pluginId).deleteRecursively() }
            }
            state.value = state.value - pluginId
        }
    }

    suspend fun ensureLoaded() {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            val entries = withContext(Dispatchers.IO) {
                dir.mkdirs()
                dir.listFiles { file -> file.extension == "json" }.orEmpty().mapNotNull { file ->
                    runCatching { json.decodeFromString(ExtensionData.serializer(), file.readText()) }
                        .getOrNull()
                        ?.let { it.pluginId to it }
                }
            }
            state.value = entries.toMap()
            loaded = true
        }
    }

    private fun fileOf(pluginId: String): File {
        val id = pluginId.takeIf { SAFE_ID.matches(it) } ?: error("unsafe plugin id: $pluginId")
        dir.mkdirs()
        return File(dir, "$id.json")
    }

    companion object {
        private val SAFE_ID = Regex("[A-Za-z0-9._-]+")

        @Volatile
        private var instance: ExtensionStore? = null

        fun get(context: Context): ExtensionStore = instance ?: synchronized(this) {
            instance ?: ExtensionStore(File(context.applicationContext.filesDir, "extensions-v1")).also { instance = it }
        }
    }
}
